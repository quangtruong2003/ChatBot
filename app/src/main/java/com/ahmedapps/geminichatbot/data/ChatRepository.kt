package com.ahmedapps.geminichatbot.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.geometry.isEmpty
import com.ahmedapps.geminichatbot.BuildConfig
import com.ahmedapps.geminichatbot.di.GenerativeModelProvider
import com.ahmedapps.geminichatbot.utils.AudioConverter
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import android.content.SharedPreferences
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.awaitAll
import com.google.ai.client.generativeai.type.Content

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

    // Thêm biến để lưu trữ rules
    private var _rulesAI: String = ""
    val rulesAI: String get() = _rulesAI
    
    // Thêm biến để lưu trạng thái bật/tắt rules
    private var _isRulesEnabled: Boolean = true
    val isRulesEnabled: Boolean get() = _isRulesEnabled

    // SharedPreferences để lưu trữ rules
    private val sharedPreferences: SharedPreferences by lazy {
        context.getSharedPreferences("gemini_chat_preferences", Context.MODE_PRIVATE)
    }

    init {
        Log.d("ChatRepository", "Initialized with User ID: $userId")
        loadRulesFromSharedPreferences() // Tải rules từ SharedPreferences thay vì Firestore
        loadRulesEnabledStateFromSharedPreferences() // Tải trạng thái bật/tắt rules
    }

    private val segmentsCollection
        get() = db.collection("chats").document(userId).collection("segments")

    private val storageReference
        get() = storage.reference.child("images/$userId/")

    private val audioStorageReference
        get() = storage.reference.child("audio/$userId/")

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
     * Upload tệp âm thanh lên Firebase Storage và trả về URL download.
     * Chuyển đổi từ M4A sang OGG (Vorbis) nếu cần thiết.
     */
    suspend fun uploadAudioFile(audioUri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            // Lấy đường dẫn file thực từ Uri
            val audioPath = AudioConverter.getPathFromUri(context, audioUri)
            if (audioPath == null) {
                Log.e("ChatRepository", "Cannot get file path from URI: $audioUri")
                return@withContext audioUri.toString() // Trả về URI cục bộ nếu không thể lấy đường dẫn
            }
            
            val originalFile = File(audioPath)
            
            // Kiểm tra mime type
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, audioUri)
            val mimeType = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
            retriever.release()
            
            // Tạo file OGG tạm
            val tempOggFile = File(context.cacheDir, "${System.currentTimeMillis()}.ogg")
            
            // Chuyển đổi sang OGG nếu không phải định dạng OGG
            val fileToUpload = if (mimeType?.contains("ogg", ignoreCase = true) != true) {
                Log.d("ChatRepository", "Converting audio to OGG: ${originalFile.name}")
                val isConverted = AudioConverter.convertToOgg(originalFile.path, tempOggFile.path)
                if (!isConverted) {
                    Log.e("ChatRepository", "Failed to convert audio to OGG")
                    return@withContext audioUri.toString() // Trả về URI cục bộ nếu không thể chuyển đổi
                }
                tempOggFile
            } else {
                originalFile
            }
            
            try {
                // Tải lên Firebase Storage
                val audioRef = audioStorageReference.child("${System.currentTimeMillis()}.ogg")
                audioRef.putFile(Uri.fromFile(fileToUpload)).await()
                val url = audioRef.downloadUrl.await().toString()
                
                // Xóa file tạm nếu đã tạo
                if (fileToUpload.path != originalFile.path && fileToUpload.exists()) {
                    fileToUpload.delete()
                }
                
                Log.d("ChatRepository", "Audio uploaded, URL: $url")
                return@withContext url
            } catch (e: Exception) {
                Log.e("ChatRepository", "Error uploading to Firebase Storage", e)
                
                // Trả về URI của file đã chuyển đổi (OGG) nếu có
                if (fileToUpload.exists() && fileToUpload.path != originalFile.path) {
                    val localUri = Uri.fromFile(fileToUpload).toString()
                    Log.d("ChatRepository", "Fallback to local OGG file: $localUri")
                    return@withContext localUri
                }
                
                // Nếu không có file OGG, trả về URI gốc
                Log.d("ChatRepository", "Fallback to original URI: $audioUri")
                return@withContext audioUri.toString()
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error processing audio file", e)
            // Trả về URI gốc thay vì null
            return@withContext audioUri.toString()
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
                // Thêm rules vào đầu prompt nếu có VÀ nếu rules đang được bật
                if (_rulesAI.isNotEmpty() && _isRulesEnabled) {
                    append(_rulesAI)
                    append("\n\n")
                }
                
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
            // Trường hợp tin nhắn văn bản
            val historyText = chatHistory.filterNot { it.isFromUser }
                .joinToString(separator = "\n") { chat ->
                    // Chỉ giữ lại tin nhắn từ bot để request tiếp theo
                    chat.prompt
                }
            
            // LUÔN thêm rules vào tin nhắn nếu có rules VÀ nếu rules đang được bật
            if (_rulesAI.isNotEmpty() && _isRulesEnabled) {
                Log.d("ChatRepository", "Adding rules to request. Rules: ${_rulesAI.take(50)}...")
                if (historyText.isEmpty()) {
                    // Nếu không có lịch sử chat từ bot, chỉ thêm rules và tin nhắn người dùng
                    "${_rulesAI}\n\nUser: $currentPrompt"
                } else {
                    // Nếu có lịch sử chat, thêm cả rules, lịch sử và tin nhắn hiện tại
                    "${_rulesAI}\n\n$historyText\nUser: $currentPrompt"
                }
            } else {
                // Không có rules hoặc rules đã bị tắt, chỉ gửi tin nhắn thông thường
                if (historyText.isEmpty()) {
                    "User: $currentPrompt"
                } else {
                    "$historyText\nUser: $currentPrompt"
                }
            }
        }
    }

    /**
     * Lấy phản hồi từ GenerativeModel không kèm hình ảnh.
     */
    suspend fun getResponse(prompt: String, selectedSegmentId: String?): Chat = withContext(Dispatchers.IO) {
        try {
            val chatHistory = getChatHistoryForSegment(selectedSegmentId).filterNot { it.isError }
            val fullPrompt = getFullPrompt(prompt, hasImage = false, chatHistory = chatHistory)

            // Thêm log hiển thị prompt gửi lên API chi tiết hơn
            Log.d("ChatRepository", "Sending prompt (no image): $fullPrompt")
            Log.d("ChatRepository", "Rules status: ${if (_rulesAI.isNotEmpty()) "Rules present (${_rulesAI.length} chars)" else "No rules found"}")
            Log.d("ChatRepository", "Chat history size: ${chatHistory.size}")
            if (chatHistory.isNotEmpty()) {
                Log.d("ChatRepository", "Chat history: ${chatHistory.size} messages, ${chatHistory.count { it.isFromUser }} from user")
            }

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
            
            // Xử lý cập nhật tiêu đề nhanh chóng nếu cần
            if (responseText != null && !_hasUpdatedTitle && selectedSegmentId != null) {
                val segmentSnapshot = segmentsCollection.document(selectedSegmentId).get().await()
                val currentSegment = segmentSnapshot.toObject(ChatSegment::class.java)
                if (currentSegment?.title == DEFAULT_SEGMENT_TITLE) {
                    val generatedTitle = generateChatSegmentTitleFromResponse(responseText)
                    updateSegmentTitle(selectedSegmentId, generatedTitle)
                    _hasUpdatedTitle = true
                }
            }
            
            // Lưu tin nhắn vào Firestore
            val updatedChat = if (responseText != null) {
                chat.copy(prompt = responseText, isError = false)
            } else {
                chat.copy(prompt = "Error: Empty response or API error", isError = true)
            }
            insertChat(updatedChat, selectedSegmentId)
            
            updatedChat
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

            // Xử lý cập nhật tiêu đề nhanh chóng nếu cần
            if (responseText != null && !_hasUpdatedTitle && selectedSegmentId != null) {
                val segmentSnapshot = segmentsCollection.document(selectedSegmentId).get().await()
                val currentSegment = segmentSnapshot.toObject(ChatSegment::class.java)
                if (currentSegment?.title == DEFAULT_SEGMENT_TITLE) {
                    val generatedTitle = generateChatSegmentTitleFromResponse(responseText)
                    updateSegmentTitle(selectedSegmentId, generatedTitle)
                    _hasUpdatedTitle = true
                }
            }

            val chat = Chat(
                prompt = responseText ?: "Error: Empty response",
                imageUrl = imageUrl,
                isFromUser = false,
                isError = responseText == null,
                userId = userId
            )
            
            // Lưu tin nhắn vào Firestore
            insertChat(chat, selectedSegmentId)
            
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
            insertChat(chat, selectedSegmentId)
            chat
        }
    }

    /**
     * Tải hình ảnh từ URL và trả về Bitmap.
     */
    private suspend fun downloadImageAsBitmap(imageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // Kiểm tra xem URL có phải là URL cục bộ ("file://...") không
            if (imageUrl.startsWith("file://")) {
                try {
                    // Đây là URI file cục bộ, đọc file trực tiếp
                    val file = File(Uri.parse(imageUrl).path ?: "")
                    if (file.exists()) {
                        return@withContext BitmapFactory.decodeFile(file.absolutePath)
                    } else {
                        Log.e("ChatRepository", "Local file not found: $imageUrl")
                        return@withContext null
                    }
                } catch (e: Exception) {
                    Log.e("ChatRepository", "Error decoding local file: $imageUrl", e)
                    return@withContext null
                }
            } else {
                // Đây là Firebase Storage URL, sử dụng Storage Reference
                val imageRef = storage.getReferenceFromUrl(imageUrl)
                val maxDownloadSizeBytes: Long = 10 * 1024 * 1024 // Giới hạn 10MB
                val bytes = imageRef.getBytes(maxDownloadSizeBytes).await()
                return@withContext BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error downloading image from URL: $imageUrl", e)
            return@withContext null
        }
    }

    /**
     * Lấy phản hồi từ GenerativeModel cho việc regenerate với imageUrl.
     */
    suspend fun regenerateResponseWithImage(prompt: String, imageUrl: String, selectedSegmentId: String?): Chat = coroutineScope {
        try {
            // Song song: lấy lịch sử chat và tải Bitmap từ URL
            val chatHistoryDeferred = async(Dispatchers.IO) {
                getChatHistoryForSegment(selectedSegmentId).filterNot { it.isError }
            }
            val bitmapDeferred = async(Dispatchers.IO) { downloadImageAsBitmap(imageUrl) }

            val chatHistory = chatHistoryDeferred.await()
            val bitmap = bitmapDeferred.await()

            if (bitmap == null) {
                val errorChat = Chat.fromPrompt(
                    prompt = "Error: Unable to download image for regeneration",
                    isFromUser = false,
                    isError = true,
                    userId = userId
                )
                insertChat(errorChat, selectedSegmentId)
                return@coroutineScope errorChat
            }

            val fullPrompt = getFullPrompt(prompt, hasImage = true, chatHistory = chatHistory)
            Log.d("ChatRepository", "Regenerating prompt (with image URL): $fullPrompt")

            val content = content {
                image(bitmap)
                text(fullPrompt)
            }

            val response = generativeModel.generateContent(content)
            Log.d("ChatRepository", "API Response (Regen Image): ${response.text}")

            val responseText = response.text
            
            // Xử lý cập nhật tiêu đề nhanh chóng nếu cần
            if (responseText != null && !_hasUpdatedTitle && selectedSegmentId != null) {
                val segmentSnapshot = segmentsCollection.document(selectedSegmentId).get().await()
                val currentSegment = segmentSnapshot.toObject(ChatSegment::class.java)
                if (currentSegment?.title == DEFAULT_SEGMENT_TITLE) {
                    val generatedTitle = generateChatSegmentTitleFromResponse(responseText)
                    updateSegmentTitle(selectedSegmentId, generatedTitle)
                    _hasUpdatedTitle = true
                }
            }
            
            val chat = Chat(
                prompt = responseText ?: "Error: Empty response",
                imageUrl = imageUrl, // Giữ lại imageUrl gốc
                isFromUser = false,
                isError = responseText == null,
                userId = userId
            )
            
            // Lưu tin nhắn vào Firestore
            insertChat(chat, selectedSegmentId)
            
            chat
        } catch (e: Exception) {
            e.printStackTrace()
            val errorChat = Chat(
                prompt = "Error: ${e.localizedMessage}",
                imageUrl = imageUrl, // Giữ lại imageUrl gốc
                isFromUser = false,
                isError = true,
                userId = userId
            )
            insertChat(errorChat, selectedSegmentId)
            errorChat
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
     * Sử dụng nhiều batch để xử lý nhanh hơn với số lượng lớn các document.
     */
    suspend fun deleteAllChats() = withContext(Dispatchers.IO) {
        try {
            // Tạo một danh sách để lưu trữ tất cả các tác vụ xóa
            val deleteOperations = mutableListOf<kotlinx.coroutines.Deferred<Unit>>()
            
            // Lấy tất cả segments
            val segmentsSnapshot = segmentsCollection.get().await()
            
            // Xử lý từng segment song song
            for (segmentDoc in segmentsSnapshot.documents) {
                val operation = async {
                    // Lấy tất cả messages của segment hiện tại
                    val messagesSnapshot = segmentDoc.reference.collection("messages").get().await()
                    
                    // Tạo nhiều batch nếu có quá nhiều message (giới hạn Firestore là 500 hoạt động/batch)
                    val batches = mutableListOf<WriteBatch>()
                    var currentBatch = db.batch()
                    var operationCount = 0
                    val BATCH_LIMIT = 450 // Đặt giới hạn nhỏ hơn 500 để an toàn
                    
                    // Thêm thao tác xóa từng tin nhắn vào batch
                    for (messageDoc in messagesSnapshot.documents) {
                        currentBatch.delete(messageDoc.reference)
                        operationCount++
                        
                        // Nếu đến giới hạn, lưu batch hiện tại và tạo batch mới
                        if (operationCount >= BATCH_LIMIT) {
                            batches.add(currentBatch)
                            currentBatch = db.batch()
                            operationCount = 0
                        }
                    }
                    
                    // Thêm xóa segment vào batch cuối cùng
                    currentBatch.delete(segmentDoc.reference)
                    batches.add(currentBatch)
                    
                    // Thực hiện tất cả các batch
                    batches.forEach { batch -> 
                        batch.commit().await() 
                    }
                }
                
                deleteOperations.add(operation)
            }
            
            // Chờ tất cả các tác vụ xóa hoàn thành
            deleteOperations.awaitAll()
            
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

    /**
     * Xóa một tin nhắn cụ thể dựa vào chatId và segmentId
     */
    suspend fun deleteChat(chatId: String, segmentId: String) = withContext(Dispatchers.IO) {
        try {
            val segmentRef = segmentsCollection.document(segmentId)
            val messageRef = segmentRef.collection("messages").document(chatId)
            messageRef.delete().await()
            Log.d("ChatRepository", "Deleted chat with ID: $chatId from segment: $segmentId")
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error deleting chat", e)
        }
    }

    /**
     * Cập nhật rules AI từ UserDetail và lưu vào SharedPreferences
     */
    suspend fun updateRulesAI(rules: String, isEnabled: Boolean = _isRulesEnabled) = withContext(Dispatchers.IO) {
        try {
            _rulesAI = rules
            _isRulesEnabled = isEnabled
            // Lưu rules và trạng thái vào SharedPreferences
            sharedPreferences.edit()
                .putString("${userId}_rules_ai", rules)
                .putBoolean("${userId}_rules_enabled", isEnabled)
                .apply()
            Log.d("ChatRepository", "Rules AI updated: $rules, Enabled: $isEnabled")
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error updating Rules AI", e)
        }
    }
    
    /**
     * Tải rules từ SharedPreferences khi khởi tạo ứng dụng
     */
    private fun loadRulesFromSharedPreferences() {
        try {
            if (userId.isNotEmpty()) {
                _rulesAI = sharedPreferences.getString("${userId}_rules_ai", "") ?: ""
                Log.d("ChatRepository", "Loaded Rules AI from SharedPreferences: $_rulesAI")
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error loading Rules AI from SharedPreferences", e)
        }
    }

    /**
     * Tải trạng thái bật/tắt rules từ SharedPreferences
     */
    private fun loadRulesEnabledStateFromSharedPreferences() {
        try {
            if (userId.isNotEmpty()) {
                _isRulesEnabled = sharedPreferences.getBoolean("${userId}_rules_enabled", true)
                Log.d("ChatRepository", "Loaded Rules AI enabled state: $_isRulesEnabled")
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error loading Rules AI enabled state", e)
        }
    }

    /**
     * Xóa tất cả các tin nhắn trong một segment sau một timestamp nhất định.
     */
    suspend fun deleteMessagesAfterTimestamp(segmentId: String, timestamp: Long) = withContext(Dispatchers.IO) {
        if (timestamp <= 0) return@withContext // Không làm gì nếu timestamp không hợp lệ
        try {
            val messagesCollection = getMessagesCollection(segmentId)
            val querySnapshot = messagesCollection
                .whereGreaterThan("timestamp", timestamp)
                .get()
                .await()
            
            if (!querySnapshot.isEmpty) {
                val batch = db.batch()
                querySnapshot.documents.forEach { doc ->
                    batch.delete(doc.reference)
                }
                batch.commit().await()
                Log.d("ChatRepository", "Deleted ${querySnapshot.size()} messages after timestamp $timestamp in segment $segmentId")
            } else {
                Log.d("ChatRepository", "No messages found after timestamp $timestamp to delete in segment $segmentId")
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error deleting messages after timestamp $timestamp in segment $segmentId", e)
        }
    }

    /**
     * Cập nhật một tin nhắn đã tồn tại.
     */
    suspend fun updateChat(chat: Chat, segmentId: String) = withContext(Dispatchers.IO) {
        try {
            val messageRef = segmentsCollection.document(segmentId)
                .collection("messages")
                .document(chat.id)
            
            // Sử dụng SetOptions.merge() để chỉ cập nhật các trường được chỉ định
            // mà không ghi đè toàn bộ document
            messageRef.set(chat, SetOptions.merge()).await()
            
            Log.d("ChatRepository", "Updated chat message with ID: ${chat.id}")
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error updating chat message: ${e.message}", e)
        }
    }

    /**
     * Cập nhật trạng thái bật/tắt rules AI
     */
    suspend fun updateRulesEnabledState(isEnabled: Boolean) = withContext(Dispatchers.IO) {
        try {
            _isRulesEnabled = isEnabled
            // Lưu trạng thái vào SharedPreferences
            sharedPreferences.edit()
                .putBoolean("${userId}_rules_enabled", isEnabled)
                .apply()
            Log.d("ChatRepository", "Rules AI enabled state updated: $isEnabled")
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error updating Rules AI enabled state", e)
        }
    }

    // Hàm hỗ trợ chuyển đổi Uri thành File
    private fun uriToFile(uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Không thể mở luồng dữ liệu từ URI")
        
        val tempFile = File.createTempFile("audio_", ".mp3", context.cacheDir)
        
        tempFile.outputStream().use { outputStream ->
            inputStream.copyTo(outputStream)
        }
        
        inputStream.close()
        return tempFile
    }

    /**
     * Lấy phản hồi từ GenerativeModel kèm file âm thanh.
     */
    suspend fun getResponseWithAudio(prompt: String, audioUri: Uri, selectedSegmentId: String?): Chat = coroutineScope {
        try {
            // Song song: lấy lịch sử chat và tải file âm thanh lên Firebase Storage
            val chatHistoryDeferred = async(Dispatchers.IO) {
                getChatHistoryForSegment(selectedSegmentId).filterNot { it.isError }
            }
            val audioUrlDeferred = async(Dispatchers.IO) { uploadAudioFile(audioUri) }

            val chatHistory = chatHistoryDeferred.await()
            val audioUrl = audioUrlDeferred.await()

            if (audioUrl == null) {
                val errorChat = Chat.fromPrompt(
                    prompt = "Error: Không thể tải lên file âm thanh",
                    isFromUser = false,
                    isError = true,
                    userId = userId
                )
                insertChat(errorChat, selectedSegmentId)
                return@coroutineScope errorChat
            }

            val fullPrompt = if (prompt.isNotEmpty()) {
                getFullPrompt(prompt, hasImage = false, chatHistory = chatHistory)
            } else {
                getFullPrompt("Đây là một đoạn âm thanh, hãy phân tích nội dung của nó.", hasImage = false, chatHistory = chatHistory)
            }

            Log.d("ChatRepository", "Sending prompt with audio: $fullPrompt")
            Log.d("ChatRepository", "Audio URL: $audioUrl")

            // Tạo tạm thời tệp audio để gửi đến API
            val audioFile = uriToFile(audioUri)
            
            // Đọc dữ liệu byte của file
            val audioBytes = audioFile.readBytes()
            
            // Tạo content với âm thanh và/hoặc văn bản sử dụng content builder API
            val content = content(role = "user") {
                if (prompt.isNotBlank()) {
                    // Nếu có cả văn bản và âm thanh
                    text(fullPrompt)
                    // Thêm phần âm thanh với mime type phù hợp
                    blob("audio/ogg", audioBytes)
                } else {
                    // Nếu chỉ có âm thanh
                    blob("audio/ogg", audioBytes)
                }
            }
            
            // Xóa file tạm sau khi đã đọc bytes
            if (audioFile.exists()) {
                audioFile.delete()
            }

            val response = generativeModel.generateContent(content)
            val responseText = response.text
            Log.d("ChatRepository", "API Response for audio: $responseText")

            // Xử lý cập nhật tiêu đề nhanh chóng nếu cần
            if (responseText != null && !_hasUpdatedTitle && selectedSegmentId != null) {
                val segmentSnapshot = segmentsCollection.document(selectedSegmentId).get().await()
                val currentSegment = segmentSnapshot.toObject(ChatSegment::class.java)
                if (currentSegment?.title == DEFAULT_SEGMENT_TITLE) {
                    val generatedTitle = generateChatSegmentTitleFromResponse(responseText)
                    updateSegmentTitle(selectedSegmentId, generatedTitle)
                    _hasUpdatedTitle = true
                }
            }

            // Tạo chat object với URL âm thanh
            val chat = Chat(
                prompt = responseText ?: "Error: Không nhận được phản hồi từ API",
                imageUrl = audioUrl, // Lưu URL âm thanh vào trường imageUrl
                isFromUser = false,
                isError = responseText == null,
                userId = userId,
                isFileMessage = true, // Đánh dấu là tin nhắn file
                fileName = AudioConverter.getFileNameFromUri(context, audioUri) ?: "audio.ogg" // Lưu tên file
            )
            
            // Lưu tin nhắn vào Firestore
            insertChat(chat, selectedSegmentId)
            
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
            insertChat(chat, selectedSegmentId)
            chat
        }
    }

    /**
     * Regenerate phản hồi cho tin nhắn âm thanh.
     */
    suspend fun regenerateResponseWithAudio(prompt: String, audioUrl: String, selectedSegmentId: String?): Chat = coroutineScope {
        try {
            // Lấy lịch sử chat
            val chatHistory = getChatHistoryForSegment(selectedSegmentId).filterNot { it.isError }
            
            // Kiểm tra xem URL có phải là URL cục bộ không
            if (audioUrl.startsWith("file://")) {
                // Xử lý file âm thanh cục bộ
                val audioFile = File(Uri.parse(audioUrl).path ?: "")
                if (!audioFile.exists()) {
                    val errorChat = Chat.fromPrompt(
                        prompt = "Error: Không thể tìm thấy file âm thanh để tạo lại tin nhắn",
                        isFromUser = false,
                        isError = true,
                        userId = userId
                    )
                    insertChat(errorChat, selectedSegmentId)
                    return@coroutineScope errorChat
                }
                
                // Đọc dữ liệu byte của file
                val audioBytes = audioFile.readBytes()
                
                // Tạo prompt đầy đủ
                val fullPrompt = if (prompt.isNotEmpty()) {
                    getFullPrompt(prompt, hasImage = false, chatHistory = chatHistory)
                } else {
                    getFullPrompt("Đây là một đoạn âm thanh, hãy phân tích nội dung của nó.", hasImage = false, chatHistory = chatHistory)
                }
                
                Log.d("ChatRepository", "Regenerating prompt with audio: $fullPrompt")
                
                // Tạo content với âm thanh và văn bản
                val content = content(role = "user") {
                    if (prompt.isNotBlank()) {
                        text(fullPrompt)
                        blob("audio/ogg", audioBytes)
                    } else {
                        blob("audio/ogg", audioBytes)
                    }
                }
                
                // Gọi API để nhận phản hồi
                val response = generativeModel.generateContent(content)
                val responseText = response.text
                Log.d("ChatRepository", "API Response for regenerated audio: $responseText")
                
                // Xử lý cập nhật tiêu đề
                if (responseText != null && !_hasUpdatedTitle && selectedSegmentId != null) {
                    val segmentSnapshot = segmentsCollection.document(selectedSegmentId).get().await()
                    val currentSegment = segmentSnapshot.toObject(ChatSegment::class.java)
                    if (currentSegment?.title == DEFAULT_SEGMENT_TITLE) {
                        val generatedTitle = generateChatSegmentTitleFromResponse(responseText)
                        updateSegmentTitle(selectedSegmentId, generatedTitle)
                        _hasUpdatedTitle = true
                    }
                }
                
                // Tạo và lưu tin nhắn với URL âm thanh gốc
                val chat = Chat(
                    prompt = responseText ?: "Error: Không nhận được phản hồi từ API",
                    imageUrl = audioUrl,
                    isFromUser = false,
                    isError = responseText == null,
                    userId = userId,
                    isFileMessage = true,
                    fileName = audioFile.name
                )
                
                insertChat(chat, selectedSegmentId)
                return@coroutineScope chat
            } else {
                // Nếu là URL Firebase, tải về để xử lý
                // Hiện tại chúng ta chỉ cần gọi lại API với URL gốc
                val fullPrompt = if (prompt.isNotEmpty()) {
                    getFullPrompt(prompt, hasImage = false, chatHistory = chatHistory)
                } else {
                    getFullPrompt("Đây là một đoạn âm thanh, hãy phân tích nội dung của nó.", hasImage = false, chatHistory = chatHistory)
                }
                
                Log.d("ChatRepository", "Regenerating prompt with audio URL: $fullPrompt")
                
                // Gọi API mà không có file âm thanh (chỉ với văn bản)
                val response = generativeModel.generateContent(fullPrompt)
                val responseText = response.text
                
                // Xử lý cập nhật tiêu đề
                if (responseText != null && !_hasUpdatedTitle && selectedSegmentId != null) {
                    val segmentSnapshot = segmentsCollection.document(selectedSegmentId).get().await()
                    val currentSegment = segmentSnapshot.toObject(ChatSegment::class.java)
                    if (currentSegment?.title == DEFAULT_SEGMENT_TITLE) {
                        val generatedTitle = generateChatSegmentTitleFromResponse(responseText)
                        updateSegmentTitle(selectedSegmentId, generatedTitle)
                        _hasUpdatedTitle = true
                    }
                }
                
                // Tạo và lưu tin nhắn với URL âm thanh gốc
                val chat = Chat(
                    prompt = responseText ?: "Error: Không nhận được phản hồi từ API",
                    imageUrl = audioUrl,
                    isFromUser = false,
                    isError = responseText == null,
                    userId = userId,
                    isFileMessage = true
                )
                
                insertChat(chat, selectedSegmentId)
                return@coroutineScope chat
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val errorChat = Chat(
                prompt = "Error: ${e.localizedMessage}",
                imageUrl = audioUrl,
                isFromUser = false, 
                isError = true,
                userId = userId,
                isFileMessage = true
            )
            insertChat(errorChat, selectedSegmentId)
            return@coroutineScope errorChat
        }
    }
}
