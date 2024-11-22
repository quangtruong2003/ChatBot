// ChatRepository.kt
package com.ahmedapps.geminichatbot.data

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

class ChatRepository @Inject constructor(
    private val context: Context,
    private val generativeModel: GenerativeModel,
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
) {
    private val auth = FirebaseAuth.getInstance()

    val userId: String
        get() = auth.currentUser?.uid.orEmpty()

    init {
        Log.d("ChatRepository", "Initialized with User ID: $userId")
    }

    // Reference đến "chats/{userId}/segments"
    private val segmentsCollection
        get() = db.collection("chats").document(userId).collection("segments")

    // Reference đến "images/{userId}/"
    private val storageReference
        get() = storage.reference.child("images/$userId/")

    /**
     * Biến trạng thái để theo dõi việc cập nhật tiêu đề đoạn chat
     */
    private var _hasUpdatedTitle: Boolean = false

    /**
     * Upload hình ảnh lên Firebase Storage và trả về URL download.
     */
    suspend fun uploadImage(imageUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val imageRef = storageReference.child("${System.currentTimeMillis()}.png")
            imageRef.putFile(imageUri).await()
            val url = imageRef.downloadUrl.await().toString()
            Log.d("ChatRepository", "Image uploaded, URL: $url")
            return@withContext url
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error uploading image", e)
            return@withContext null
        }
    }
    private suspend fun buildPromptWithHistory(currentPrompt: String, segmentId: String?): String = withContext(Dispatchers.IO) {
        val chatHistory = getChatHistoryForSegment(segmentId).filterNot { it.isError } // Lọc bỏ các tin nhắn lỗi

        // Xây dựng prompt với lịch sử trò chuyện
        val stringBuilder = StringBuilder()
        for (chat in chatHistory) {
            val sender = if (chat.isFromUser) "User" else ""
            stringBuilder.append("$sender: ${chat.prompt}\n")
        }
        stringBuilder.append("User: $currentPrompt")

        return@withContext stringBuilder.toString()
    }

    private var _selectedSegmentId: String? = null // Biến lưu ID đoạn chat hiện tại
    /**
     * Tạo prompt đầy đủ bằng cách nối lịch sử trò chuyện.
     */
    private suspend fun getFullPrompt(currentPrompt: String, hasImage: Boolean): String = withContext(Dispatchers.IO) {
        val chatHistory = getChatHistoryForSegment(_selectedSegmentId)
        if (hasImage) {
            // Khi có hình ảnh
            buildString {
                append("User: ")
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
                        firstImageChat?.prompt ?: "Trả lời câu hỏi này đầu tiên: Bạn hãy xem hình ảnh tôi gửi và cho tôi biết trong ảnh có gì? Bạn hãy nói cho tôi biết rõ mọi thứ trong ảnh. Nếu nó là 1 câu hỏi thì bạn hãy trả lời chính xác nó. Nếu nó là một văn bản thì bạn hãy viết toàn bộ văn bản đó ra câu trả lời của bạn và giải thích. Nếu không có văn bản thì không cần nói là không có văn bản hong hình ảnh. Bạn có thể tùy cơ ứng biến để thể hiện bạn là một người thông minh nhất thế giới."
                    }
                    append(promptToUse)
                } else {
                    // currentPrompt không trống
                    append(currentPrompt)
                }
            }
        } else {
            // Khi không có hình ảnh, có thể gửi kèm lịch sử trò chuyện nếu cần
            buildString {
                for (chat in chatHistory) {
                    if (chat.prompt.isNotEmpty()) {
                        append(if (chat.isFromUser) "User: " else "Assistant: ")
                        append(chat.prompt)
                        append("\n")
                    }
                }
                append("User: ")
                append(currentPrompt)
            }
        }
    }

    /**
     * Lấy phản hồi từ GenerativeModel không kèm hình ảnh.
     */
    suspend fun getResponse(prompt: String, selectedSegmentId: String?): Chat = withContext(Dispatchers.IO) {
        return@withContext try {
            val fullPrompt = getFullPrompt(prompt, hasImage = false)
            val response = generativeModel.generateContent(fullPrompt)
            val chat = Chat.fromPrompt(
                prompt = response.text ?: "Error: Empty response",
                imageUrl = null,
                isFromUser = false,
                isError = false,
                userId = userId
            )
            insertChat(chat)
            // Cập nhật tiêu đề đoạn chat nếu chưa cập nhật
            if (!_hasUpdatedTitle) {
                updateSegmentTitle(_selectedSegmentId, response.text ?: "Untitled Segment")
                _hasUpdatedTitle = true
            }
            chat
        } catch (e: Exception) {
            e.printStackTrace()
            val chat = Chat.fromPrompt(
                prompt = "Error: ${e.localizedMessage}",
                imageUrl = null,
                isFromUser = false,
                isError = true,
                userId = userId
            )
            insertChat(chat)
            chat
        }
    }

    /**
     * Lấy phản hồi từ GenerativeModel kèm hình ảnh.
     */
    suspend fun getResponseWithImage(prompt: String, imageUri: Uri, selectedSegmentId: String?): Chat = withContext(Dispatchers.IO) {
        return@withContext try {
            val fullPrompt = getFullPrompt(prompt, hasImage = true)
            Log.d("ChatRepository", "Full Prompt: $fullPrompt")

            // Chuyển đổi Uri thành Bitmap
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, imageUri)
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
            }

            // Tạo các Content instances cho hình ảnh và văn bản
            val imageContent = content {
                image(bitmap)
            }
            val textContent = content {
                text(fullPrompt)
            }

            // Gọi API với các Content instances
            val response = generativeModel.generateContent(imageContent, textContent)
            Log.d("ChatRepository", "API Response: ${response.text}")
            // Tải lên hình ảnh và lấy URL
            val imageUrl = uploadImage(imageUri)

            val chat = Chat(
                prompt = response.text ?: "Error: Empty response",
                imageUrl = imageUrl,
                isFromUser = false,
                isError = false,
                userId = userId
            )
            insertChat(chat)
            // Cập nhật tiêu đề đoạn chat nếu chưa cập nhật
            if (!_hasUpdatedTitle) {
                updateSegmentTitle(_selectedSegmentId, response.text ?: "Untitled Segment")
                _hasUpdatedTitle = true
            }
            chat
        } catch (e: Exception) {
            e.printStackTrace()
            val chat = Chat(
                prompt = "Error: ${e.localizedMessage}",
                imageUrl = null,
                isFromUser = false,
                isError = true,
                userId = userId
            )
            insertChat(chat)
            chat
        }
    }

    /**
     * Lấy lịch sử trò chuyện cho một đoạn chat cụ thể.
     */
    suspend fun getChatHistoryForSegment(segmentId: String?): List<Chat> = withContext(Dispatchers.IO) {
        if (segmentId.isNullOrEmpty()) return@withContext emptyList()
        return@withContext try {
            segmentsCollection.document(segmentId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(Chat::class.java)?.copy(id = it.id) }
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
            _selectedSegmentId = targetSegmentId // Cập nhật ID đoạn chat hiện tại
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
        return@withContext try {
            val prompt = "Đặt 1 tiêu đề duy nhất chính xác, ngắn gọn và xúc tích cho tin nhắn sau: '$chat'. Và bạn hãy chỉ trả lời tiêu đề duy nhất bạn đặt. Tiêu đề phải hoàn hảo và hợp lí. Không có gì khác ngoài tiêu đề."
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
        return@withContext try {
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
        return@withContext try {
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
            return@withContext docRef.id
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error adding chat segment", e)
            return@withContext null
        }

    }

    /**
     * Lấy hoặc tạo một đoạn chat mặc định nếu chưa có.
     */
    suspend fun getOrCreateDefaultSegmentId(): String? = withContext(Dispatchers.IO) {
        return@withContext try {
            val segmentsSnapshot = segmentsCollection
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(1)
                .get()
                .await()

            if (segmentsSnapshot.isEmpty) {
                addChatSegment("Đoạn chat mới")
            } else {
                segmentsSnapshot.documents.first().id
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error getting or creating default segment", e)
            return@withContext null
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
}
