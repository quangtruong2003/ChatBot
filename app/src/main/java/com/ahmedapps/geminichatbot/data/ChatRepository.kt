package com.ahmedapps.geminichatbot.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.geometry.isEmpty
import com.ahmedapps.geminichatbot.BuildConfig
import com.ahmedapps.geminichatbot.di.GenerativeModelProvider
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

class ChatRepository @Inject constructor(
    private val context: Context,
    private val generativeModelProvider: GenerativeModelProvider,
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
) {
    private val auth = FirebaseAuth.getInstance()
    private val generativeModel: GenerativeModel
        get() = generativeModelProvider.getGenerativeModel()

    val userId: String
        get() = auth.currentUser?.uid.orEmpty()

    init {
        Log.d("ChatRepository", "Initialized with User ID: $userId")
    }

    private val segmentsCollection
        get() = db.collection("chats").document(userId).collection("segments")

    private val storageReference
        get() = storage.reference.child("images/$userId/")

    // Tiêu đề mặc định của một đoạn chat mới
    private val DEFAULT_SEGMENT_TITLE = "Đoạn chat mới"

    private var _hasUpdatedTitle: Boolean = false
    private var _selectedSegmentId: String? = null

    /**
     * Upload hình ảnh lên Firebase Storage và trả về URL download.
     */
    suspend fun uploadImage(imageUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val imageRef = storageReference.child("${System.currentTimeMillis()}.png")
            imageRef.putFile(imageUri).await()
            val url = imageRef.downloadUrl.await().toString()
            Log.d("ChatRepository", "Image uploaded, URL: $url")
            url
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error uploading image", e)
            null
        }
    }

    /**
     * Tạo prompt đầy đủ bằng cách nối lịch sử trò chuyện.
     * Khi có hình ảnh, nếu currentPrompt rỗng sẽ tìm prompt từ lịch sử chat.
     * Ngược lại, nếu không có hình ảnh, sẽ gộp tất cả tin nhắn (loại bỏ lỗi) kèm dòng "User: ".
     */
    private suspend fun getFullPrompt(
        currentPrompt: String,
        hasImage: Boolean,
        chatHistory: List<Chat>
    ): String = withContext(Dispatchers.IO) {
        if (hasImage) {
            // Khi có hình ảnh
            buildString {
                append("")
                if (currentPrompt.isEmpty()) {
                    // Tìm tin nhắn đầu tiên của người dùng
                    val reversedChatHistory = chatHistory.reversed()
                    // Tìm tin nhắn đầu tiên của người dùng không có hình ảnh và có prompt không trống
                    val firstTextChat = reversedChatHistory.firstOrNull { chat ->
                        chat.isFromUser && chat.imageUrl == null && chat.prompt.isNotEmpty()
                    }
                    val promptToUse = if (firstTextChat != null) {
                        // Sử dụng prompt của tin nhắn đầu tiên không có hình ảnh
                        firstTextChat.prompt
                    } else {
                        // Nếu không tìm thấy, tìm prompt của hình ảnh đầu tiên
                        val firstImageChat = chatHistory.firstOrNull { chat ->
                            chat.isFromUser && chat.imageUrl != null && chat.prompt.isNotEmpty()
                        }
                        firstImageChat?.prompt ?: "Bạn hãy xem hình ảnh tôi gửi và cho tôi biết trong ảnh có gì? Bạn hãy nói rõ mọi thứ trong ảnh. Nhớ là hãy trả lời theo vai trò của bạn được giao, nếu không có trả lời như bình thường."
                    }
                    append(promptToUse)
                } else {
                    append(currentPrompt)
                }
            }
        } else {
            chatHistory.filterNot { it.isError }
                .joinToString(separator = "\n") { chat ->
                    val sender = if (chat.isFromUser) "User" else ""
                    "$sender: ${chat.prompt}"
                } + "\nUser: $currentPrompt"
        }
    }

    /**
     * Xử lý phản hồi từ API và cập nhật đoạn chat.
     * Chỉ cập nhật tiêu đề nếu chat segment hiện tại đang có tiêu đề mặc định.
     */
    private suspend fun handleResponse(
        response: com.google.ai.client.generativeai.type.GenerateContentResponse?,
        chat: Chat,
        selectedSegmentId: String?
    ) {
        val responseText = response?.text
        if (responseText != null) {
            val updatedChat = chat.copy(prompt = responseText, isError = false)
            insertChat(updatedChat, selectedSegmentId)
            if (!_hasUpdatedTitle && _selectedSegmentId != null) {
                // Lấy tiêu đề hiện tại của đoạn chat
                val segmentSnapshot = segmentsCollection.document(_selectedSegmentId!!).get().await()
                val currentSegment = segmentSnapshot.toObject(ChatSegment::class.java)
                if (currentSegment?.title == DEFAULT_SEGMENT_TITLE) {
                    updateSegmentTitle(_selectedSegmentId, responseText)
                    _hasUpdatedTitle = true
                }
            }
        } else {
            val errorChat = chat.copy(prompt = "Error: Empty response or API error", isError = true)
            insertChat(errorChat, selectedSegmentId)
        }
    }

    /**
     * Lấy phản hồi từ GenerativeModel không kèm hình ảnh.
     */
    suspend fun getResponse(prompt: String, selectedSegmentId: String?): Chat = withContext(Dispatchers.IO) {
        try {
            val chatHistory = getChatHistoryForSegment(selectedSegmentId).filterNot { it.isError }
            val fullPrompt = getFullPrompt(prompt, hasImage = false, chatHistory = chatHistory)

            // Thêm log hiển thị prompt gửi lên API
            Log.d("ChatRepository", "Sending prompt (no image): $fullPrompt")

            val response = generativeModel.generateContent(fullPrompt)
            Log.d("ChatRepository", "API Response: ${response.text}")
            val responseText = response.text
            val chat = Chat.fromPrompt(
                prompt = "",
                imageUrl = null,
                isFromUser = false,
                isError = false,
                userId = userId
            )
            handleResponse(response, chat, selectedSegmentId)
            chat.copy(prompt = responseText ?: "Error: Empty response", isError = responseText == null)
        } catch (e: Exception) {
            e.printStackTrace()
            val chat = Chat.fromPrompt(
                prompt = "Error: ${e.localizedMessage}",
                imageUrl = null,
                isFromUser = false,
                isError = true,
                userId = userId
            )
            insertChat(chat, selectedSegmentId)
            chat
        }
    }

    /**
     * Lấy phản hồi từ GenerativeModel kèm hình ảnh (tối ưu: gửi hình ảnh và request đồng thời).
     */
    suspend fun getResponseWithImage(prompt: String, imageUri: Uri, selectedSegmentId: String?): Chat = coroutineScope {
        try {
            // Song song: lấy lịch sử chat và decode Bitmap
            val chatHistoryDeferred = async(Dispatchers.IO) {
                getChatHistoryForSegment(selectedSegmentId).filterNot { it.isError }
            }
            val bitmapDeferred = async(Dispatchers.IO) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        val source = ImageDecoder.createSource(context.contentResolver, imageUri)
                        ImageDecoder.decodeBitmap(source) { _, _, _ -> }
                    } else {
                        @Suppress("DEPRECATION")
                        MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
                    }
                } catch (e: Exception) {
                    Log.e("ChatRepository", "Error decoding bitmap: ${e.localizedMessage}")
                    null
                }
            }
            val chatHistory = chatHistoryDeferred.await()
            val bitmap = bitmapDeferred.await()
            if (bitmap == null) {
                val errorChat = Chat.fromPrompt(
                    prompt = "Error: Unable to decode image",
                    isFromUser = false,
                    isError = true,
                    userId = userId
                )
                insertChat(errorChat, selectedSegmentId)
                return@coroutineScope errorChat
            }

            val fullPrompt = getFullPrompt(prompt, hasImage = true, chatHistory = chatHistory)

            // Thêm log hiển thị prompt gửi lên API
            Log.d("ChatRepository", "Sending prompt (with image): $fullPrompt")

            // Tạo content với cả hình ảnh và text
            val content = content {
                image(bitmap)
                text(fullPrompt)
            }

            // Gọi API và upload ảnh đồng thời
            val responseDeferred = async(Dispatchers.IO) { generativeModel.generateContent(content) }
            val imageUrlDeferred = async(Dispatchers.IO) { uploadImage(imageUri) }

            val response = responseDeferred.await()
            val imageUrl = imageUrlDeferred.await()

            val responseText = response.text
            Log.d("ChatRepository", "API Response: $responseText")

            if (imageUrl == null) {
                val errorChat = Chat.fromPrompt(
                    prompt = "Error: Unable to upload image",
                    isFromUser = false,
                    isError = true,
                    userId = userId
                )
                insertChat(errorChat, selectedSegmentId)
                return@coroutineScope errorChat
            }

            val chat = Chat(
                prompt = "",
                imageUrl = imageUrl,
                isFromUser = false,
                isError = false,
                userId = userId
            )
            handleResponse(response, chat, selectedSegmentId)
            chat.copy(
                prompt = responseText ?: "Error: Empty response",
                isError = responseText == null,
                imageUrl = imageUrl
            )
        } catch (e: Exception) {
            e.printStackTrace()
            val chat = Chat(
                prompt = "Error: ${e.localizedMessage}",
                imageUrl = null,
                isFromUser = false,
                isError = true,
                userId = userId
            )
            insertChat(chat, selectedSegmentId)
            chat
        }
    }

    /**
     * Lấy lịch sử trò chuyện cho một đoạn chat cụ thể.
     */
    suspend fun getChatHistoryForSegment(segmentId: String?): List<Chat> = withContext(Dispatchers.IO) {
        if (segmentId.isNullOrEmpty()) return@withContext emptyList()
        try {
            segmentsCollection.document(segmentId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .await()
                .documents
                .map { it.toObject(Chat::class.java)?.copy(id = it.id) ?: Chat() }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error fetching chat history", e)
            emptyList()
        }
    }

    /**
     * Chèn một tin nhắn vào một đoạn chat cụ thể.
     */
    suspend fun insertChat(chat: Chat, segmentId: String? = null) = withContext(Dispatchers.IO) {
        try {
            val targetSegmentId = segmentId ?: getOrCreateDefaultSegmentId()
            _selectedSegmentId = targetSegmentId
            if (targetSegmentId != null) {
                val messagesCollection = getMessagesCollection(targetSegmentId)
                val docRef = messagesCollection.document()
                val messageId = docRef.id
                val chatWithId = chat.copy(id = messageId)
                docRef.set(chatWithId).await()
                Log.d("ChatRepository", "Inserted Chat message with ID: $messageId into segment: $targetSegmentId")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Tạo tiêu đề cho đoạn chat dựa trên phản hồi từ API.
     */
    private suspend fun generateChatSegmentTitleFromResponse(chat: String): String = withContext(Dispatchers.IO) {
        try {
            val prompt = "Đặt 1 tiêu đề duy nhất chính xác, ngắn gọn và xúc tích cho tin nhắn sau: '$chat'. Chỉ trả lời tiêu đề duy nhất. Tiêu đề phải hoàn hảo và hợp lí. Nên nhớ tiêu đề phải bao hàm đủ thông tin trong tin nhắn. Tiêu đề không dài quá 6 từ, không in đậm, chữ nghiêng và gạch chân"
            val response = generativeModel.generateContent(prompt)
            response.text?.trim() ?: "Untitled Segment"
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error generating segment title", e)
            "Untitled Segment"
        }
    }

    /**
     * Cập nhật tiêu đề của một đoạn chat cụ thể.
     */
    suspend fun updateSegmentTitle(segmentId: String?, newTitle: String) = withContext(Dispatchers.IO) {
        if (segmentId.isNullOrEmpty()) return@withContext
        try {
            segmentsCollection.document(segmentId)
                .update("title", newTitle)
                .await()
            Log.d("ChatRepository", "Updated segment title to: $newTitle for segment ID: $segmentId")
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error updating segment title", e)
        }
    }

    /**
     * Cập nhật tiêu đề của đoạn chat dựa trên phản hồi từ API.
     */
    suspend fun updateSegmentTitleFromResponse(segmentId: String?, firstResponse: String) = withContext(Dispatchers.IO) {
        if (segmentId.isNullOrEmpty()) return@withContext
        val newTitle = generateChatSegmentTitleFromResponse(firstResponse)
        updateSegmentTitle(segmentId, newTitle)
    }

    /**
     * Xóa tất cả các đoạn chat và tin nhắn của người dùng.
     */
    suspend fun deleteAllChats() = withContext(Dispatchers.IO) {
        try {
            val batch = db.batch()
            val segmentsSnapshot = segmentsCollection.get().await()
            for (segmentDoc in segmentsSnapshot.documents) {
                val messagesSnapshot = segmentDoc.reference.collection("messages").get().await()
                for (messageDoc in messagesSnapshot.documents) {
                    batch.delete(messageDoc.reference)
                }
                batch.delete(segmentDoc.reference)
            }
            batch.commit().await()
            Log.d("ChatRepository", "All chats deleted successfully.")
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error deleting all chats", e)
        }
    }

    /**
     * Lấy tất cả các đoạn chat.
     */
    suspend fun getChatSegments(): List<ChatSegment> = withContext(Dispatchers.IO) {
        try {
            segmentsCollection.orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(ChatSegment::class.java)?.copy(id = it.id) }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error fetching chat segments", e)
            emptyList()
        }
    }

    /**
     * Tìm kiếm các đoạn chat dựa trên query.
     */
    suspend fun searchChatSegments(query: String): List<ChatSegment> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            val normalizedQuery = removeVietnameseAccents(query).lowercase(Locale.getDefault())
            val allSegments = getChatSegments()
            allSegments.filter { segment ->
                val normalizedTitle = removeVietnameseAccents(segment.title).lowercase(Locale.getDefault())
                normalizedTitle.contains(normalizedQuery)
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error searching chat segments", e)
            emptyList()
        }
    }

    /**
     * Xóa một đoạn chat cụ thể cùng với tất cả các tin nhắn của nó.
     */
    suspend fun deleteChatSegment(segmentId: String) = withContext(Dispatchers.IO) {
        try {
            val segmentRef = segmentsCollection.document(segmentId)
            val messagesSnapshot = segmentRef.collection("messages").get().await()
            val batch = db.batch()
            for (messageDoc in messagesSnapshot.documents) {
                batch.delete(messageDoc.reference)
            }
            batch.delete(segmentRef)
            batch.commit().await()
            _hasUpdatedTitle = false
            Log.d("ChatRepository", "Deleted segment and its messages with ID: $segmentId")
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error deleting chat segment", e)
        }
    }

    /**
     * Thêm một đoạn chat mới với tiêu đề cụ thể.
     */
    suspend fun addChatSegment(title: String): String? = withContext(Dispatchers.IO) {
        try {
            val docRef = segmentsCollection.document()
            val newSegment = ChatSegment(
                id = docRef.id,
                title = title,
                createdAt = System.currentTimeMillis()
            )
            docRef.set(newSegment).await()
            _hasUpdatedTitle = false
            Log.d("ChatRepository", "Added ChatSegment with ID: ${docRef.id}")
            docRef.id
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error adding chat segment", e)
            null
        }
    }

    /**
     * Lấy hoặc tạo một đoạn chat mặc định nếu chưa có.
     */
    suspend fun getOrCreateDefaultSegmentId(): String? = withContext(Dispatchers.IO) {
        try {
            // Truy vấn các segment có title bằng DEFAULT_SEGMENT_TITLE
            val querySnapshot = segmentsCollection
                .whereEqualTo("title", DEFAULT_SEGMENT_TITLE)
                .get()
                .await()
            if (!querySnapshot.isEmpty) { // Use isEmpty instead of isNotEmpty
                // Nếu có, trả về ID của segment đầu tiên
                querySnapshot.documents.first().id
            } else {
                // Nếu chưa có, tạo mới segment mặc định
                addChatSegment(DEFAULT_SEGMENT_TITLE)
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error getting or creating default segment", e)
            null
        }
    }

    /**
     * Lấy reference đến collection messages của một đoạn chat cụ thể.
     */
    private fun getMessagesCollection(segmentId: String) =
        segmentsCollection.document(segmentId).collection("messages")

    /**
     * Loại bỏ dấu tiếng Việt khỏi chuỗi.
     */
    private fun removeVietnameseAccents(str: String): String {
        val normalizedString = Normalizer.normalize(str, Normalizer.Form.NFD)
        return normalizedString.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
    }

    /**
     * Cập nhật model sử dụng GenerativeModelProvider.
     */
    fun updateGenerativeModel(modelName: String) {
        generativeModelProvider.updateGenerativeModel(modelName)
        Log.d("ChatRepository", "Updated Generative Model to: $modelName")
    }
}
