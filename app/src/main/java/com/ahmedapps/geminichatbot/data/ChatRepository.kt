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
import com.ahmedapps.geminichatbot.di.GenerativeModelProvider
import com.ahmedapps.geminichatbot.utils.AudioConverter
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
import com.ahmedapps.geminichatbot.drawer.right.ApiSettingsState
import com.ahmedapps.geminichatbot.drawer.right.SafetyThreshold
import com.google.ai.client.generativeai.type.GenerationConfig
import com.google.firebase.firestore.WriteBatch
import kotlinx.coroutines.awaitAll
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.SafetySetting

class ChatRepository @Inject constructor(
    private val context: Context,
    private val generativeModelProvider: GenerativeModelProvider,
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
) {
    private val auth = FirebaseAuth.getInstance()

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
        resetSelectedSegmentId() // Reset segment đã chọn khi khởi tạo ChatRepository
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
     * Reset segment đã chọn để tránh tự động mở lại đoạn chat cũ khi khởi động lại app
     */
    private fun resetSelectedSegmentId() {
        _selectedSegmentId = null
        // Xóa giá trị lưu trong SharedPreferences nếu có
        if (userId.isNotEmpty()) {
            sharedPreferences.edit().remove("${userId}_selected_segment_id").apply()
            Log.d("ChatRepository", "Reset selected segment ID")
        }
    }

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
    suspend fun getResponse(
        prompt: String,
        selectedSegmentId: String?,
        apiSettings: ApiSettingsState
    ): Chat = withContext(Dispatchers.IO) {
        try {
            val chatHistory = getChatHistoryForSegment(selectedSegmentId).filterNot { it.isError }
            val fullPrompt = getFullPrompt(prompt, hasImage = false, chatHistory = chatHistory)

            // Tạo GenerationConfig từ apiSettings
            val (generationConfig, safetySettings) = buildGenerationConfig(apiSettings)

            Log.d("ChatRepository", "Sending prompt (no image): $fullPrompt")
            Log.d("APISettingsDebug", "getResponse - Applying config: $generationConfig")
            Log.d("ChatRepository", "Rules status: ${if (_rulesAI.isNotEmpty()) "Rules present (${_rulesAI.length} chars)" else "No rules found"}")
            Log.d("ChatRepository", "Chat history size: ${chatHistory.size}")
            if (chatHistory.isNotEmpty()) {
                Log.d("ChatRepository", "Chat history: ${chatHistory.size} messages, ${chatHistory.count { it.isFromUser }} from user")
            }

            // Lấy model đã được cấu hình từ provider
            val configuredModel = generativeModelProvider.createModelWithConfig(generationConfig, safetySettings)

            // Sửa cách gọi generateContent để phù hợp với API và truyền config
            val response = configuredModel.generateContent(content {
                text(fullPrompt)
            })

            Log.d("ChatRepository", "API Response: ${response.text}")
            val responseText = response.text
            val chat = Chat.fromPrompt(
                prompt = "",
                imageUrl = null,
                isFromUser = false,
                isError = false,
                userId = userId
            )
            
            if (responseText != null && !_hasUpdatedTitle && selectedSegmentId != null) {
                val segmentSnapshot = segmentsCollection.document(selectedSegmentId).get().await()
                val currentSegment = segmentSnapshot.toObject(ChatSegment::class.java)
                if (currentSegment?.title == DEFAULT_SEGMENT_TITLE) {
                    val generatedTitle = generateChatSegmentTitleFromResponse(responseText)
                    updateSegmentTitle(selectedSegmentId, generatedTitle)
                    _hasUpdatedTitle = true
                }
            }
            
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
    suspend fun getResponseWithImage(
        prompt: String,
        imageUri: Uri,
        selectedSegmentId: String?,
        apiSettings: ApiSettingsState
    ): Chat = coroutineScope {
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
                // Xử lý lỗi decode bitmap
                val errorChat = Chat.fromPrompt("Error: Unable to decode image", isFromUser = false, isError = true, userId = userId)
                insertChat(errorChat, selectedSegmentId)
                return@coroutineScope errorChat // Thoát sớm nếu bitmap null
            }

            val fullPrompt = getFullPrompt(prompt, hasImage = true, chatHistory = chatHistory)

            Log.d("ChatRepository", "Sending prompt (with image): $fullPrompt")

            // Tạo GenerationConfig
            val (generationConfig, safetySettings) = buildGenerationConfig(apiSettings)
            Log.d("APISettingsDebug", "getResponseWithImage - Applying config: $generationConfig")

            // Tạo content với cả hình ảnh và text
            val imageContent = content {
                image(bitmap)
                text(fullPrompt)
            }

            // Lấy model đã được cấu hình từ provider
            val configuredModel = generativeModelProvider.createModelWithConfig(generationConfig, safetySettings)

            // Gọi API và upload ảnh đồng thời
            val responseDeferred = async(Dispatchers.IO) {
                // Sử dụng configuredModel và không truyền config nữa
                configuredModel.generateContent(imageContent)
            }
            val imageUrlDeferred = async(Dispatchers.IO) { uploadImage(imageUri) }

            val response = responseDeferred.await()
            val imageUrl = imageUrlDeferred.await()

            // Truy cập response.text sau khi await
            val responseText = response.text
            Log.d("ChatRepository", "API Response: $responseText")

            if (imageUrl == null) {
                val errorChat = Chat.fromPrompt("Error: Unable to upload image", isFromUser = false, isError = true, userId = userId)
                insertChat(errorChat, selectedSegmentId)
                return@coroutineScope errorChat // Thoát sớm nếu upload ảnh lỗi
            }

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
            
            insertChat(chat, selectedSegmentId) // Lưu tin nhắn thành công
            
            chat // Trả về tin nhắn thành công
        } catch (e: Exception) { // Bắt lỗi chung cho coroutine scope
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
     * Tại hình ảnh từ URL và trả về Bitmap.
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
    suspend fun regenerateResponseWithImage(
        prompt: String,
        imageUrl: String,
        selectedSegmentId: String?,
        apiSettings: ApiSettingsState
    ): Chat = coroutineScope {
        try {
            // Song song: lấy lịch sử chat và decode Bitmap từ URL
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

            // Tạo GenerationConfig
            val (generationConfig, safetySettings) = buildGenerationConfig(apiSettings)
            Log.d("APISettingsDebug", "regenerateResponseWithImage - Applying config: $generationConfig")

            val contentBuilder = content {
                image(bitmap)
                text(fullPrompt)
            }

            // Lấy model đã được cấu hình từ provider
            val configuredModel = generativeModelProvider.createModelWithConfig(generationConfig, safetySettings)

            // Gọi API với generationConfig (sửa lỗi)
            // Sử dụng configuredModel và không truyền config nữa
            val response = configuredModel.generateContent(contentBuilder)

            val responseText = response.text
            Log.d("ChatRepository", "API Response: $responseText")

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
                imageUrl = imageUrl,
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
            // Lưu segmentId hiện tại vào SharedPreferences
            if (targetSegmentId != null && userId.isNotEmpty()) {
                sharedPreferences.edit()
                    .putString("${userId}_selected_segment_id", targetSegmentId)
                    .apply()
                Log.d("ChatRepository", "Saved selected segment ID: $targetSegmentId")
            }
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
            
            // Tạo GenerationConfig tối giản cho việc tạo tiêu đề
            val titleConfigBuilder = GenerationConfig.Builder()
            titleConfigBuilder.temperature = 0.2f // Nhiệt độ thấp để có tiêu đề nhất quán
            titleConfigBuilder.maxOutputTokens = 20 // Tiêu đề ngắn
            val titleConfig = titleConfigBuilder.build()
            
            // Sử dụng model đã cấu hình từ provider
            val baseModel = generativeModelProvider.createModelWithConfig(titleConfig, null)

            // Sửa cách gọi generateContent
            val response = baseModel.generateContent(content {
                text(prompt)
            })
            
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
     * Xóa tất cả các tin nhắn được tạo sau một thời điểm cụ thể
     */
    suspend fun deleteMessagesAfterTimestamp(segmentId: String, timestamp: Long, excludeIds: List<String> = emptyList()) = withContext(Dispatchers.IO) {
        try {
            val messagesCollection = getMessagesCollection(segmentId)
            
            // Lấy tất cả các tin nhắn có timestamp lớn hơn timestamp đã cho
            val query = messagesCollection.whereGreaterThan("timestamp", timestamp).get().await()
            
            // Xóa từng tin nhắn, bỏ qua các ID trong danh sách loại trừ
            for (document in query.documents) {
                val documentId = document.id
                if (!excludeIds.contains(documentId)) {
                    messagesCollection.document(documentId).delete().await()
                    Log.d("ChatRepository", "Đã xóa tin nhắn $documentId sau timestamp $timestamp")
                } else {
                    Log.d("ChatRepository", "Bỏ qua tin nhắn $documentId vì nằm trong danh sách loại trừ")
                }
            }
            
            val deletedCount = query.size() - excludeIds.size.coerceAtMost(query.size())
            Log.d("ChatRepository", "Đã xóa $deletedCount tin nhắn sau timestamp $timestamp (bỏ qua ${excludeIds.size.coerceAtMost(query.size())} tin nhắn)")
        } catch (e: Exception) {
            Log.e("ChatRepository", "Lỗi khi xóa tin nhắn sau timestamp: ${e.message}")
            throw e
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
    suspend fun getResponseWithAudio(
        prompt: String,
        audioUri: Uri,
        selectedSegmentId: String?,
        apiSettings: ApiSettingsState
    ): Chat = coroutineScope {
        try {
            // Song song: lấy lịch sử chat và tải file âm thanh lên Firebase Storage
            val chatHistoryDeferred = async(Dispatchers.IO) {
                getChatHistoryForSegment(selectedSegmentId).filterNot { it.isError }
            }
            val audioUrlDeferred = async(Dispatchers.IO) { uploadAudioFile(audioUri) }

            val chatHistory = chatHistoryDeferred.await()
            val audioUrl = audioUrlDeferred.await()

            if (audioUrl == null) {
                val errorChat = Chat.fromPrompt("Error: Không thể tải lên file âm thanh", isFromUser = false, isError = true, userId = userId)
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

            // Tạo GenerationConfig
            val (generationConfig, safetySettings) = buildGenerationConfig(apiSettings)
            Log.d("APISettingsDebug", "getResponseWithAudio - Applying config: $generationConfig")

            // Tạo content với âm thanh và văn bản
            val audioFile = uriToFile(audioUri)
            val audioBytes = audioFile.readBytes()
            val audioContent = content(role = "user") {
                if (prompt.isNotBlank()) {
                    text(fullPrompt)
                    blob("audio/ogg", audioBytes)
                } else {
                    blob("audio/ogg", audioBytes)
                }
            }
            if (audioFile.exists()) {
                audioFile.delete()
            }
            
            // Lấy model đã được cấu hình từ provider
            val configuredModel = generativeModelProvider.createModelWithConfig(generationConfig, safetySettings)

            // Gọi API và upload ảnh đồng thời
            val responseDeferred = async(Dispatchers.IO) {
                // Sử dụng configuredModel và không truyền config nữa
                configuredModel.generateContent(audioContent)
            }
            val imageUrlDeferred = async(Dispatchers.IO) { uploadImage(audioUri) }

            val response = responseDeferred.await()
            val imageUrl = imageUrlDeferred.await()

            // Truy cập response.text sau khi await
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
                imageUrl = imageUrl,
                isFromUser = false,
                isError = responseText == null,
                userId = userId,
                isFileMessage = true,
                fileName = AudioConverter.getFileNameFromUri(context, audioUri) ?: "audio.ogg"
            )
            
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
    suspend fun regenerateResponseWithAudio(
        prompt: String,
        audioUrl: String,
        selectedSegmentId: String?,
        apiSettings: ApiSettingsState
    ): Chat = coroutineScope {
        try {
            // Lấy lịch sử chat
            val chatHistory = getChatHistoryForSegment(selectedSegmentId).filterNot { it.isError }
            
            // Tạo GenerationConfig
            val (generationConfig, safetySettings) = buildGenerationConfig(apiSettings)
            Log.d("APISettingsDebug", "regenerateResponseWithAudio - Applying config: $generationConfig")

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
                
                val audioBytes = audioFile.readBytes()
                
                val fullPrompt = if (prompt.isNotEmpty()) {
                    getFullPrompt(prompt, hasImage = false, chatHistory = chatHistory)
                } else {
                    getFullPrompt("Đây là một đoạn âm thanh, hãy phân tích nội dung của nó.", hasImage = false, chatHistory = chatHistory)
                }
                
                Log.d("ChatRepository", "Regenerating prompt with audio: $fullPrompt")
                
                val contentBuilder = content(role = "user") {
                    if (prompt.isNotBlank()) {
                        text(fullPrompt)
                        blob("audio/ogg", audioBytes)
                    } else {
                        blob("audio/ogg", audioBytes)
                    }
                }
                
                // Lấy model đã được cấu hình từ provider
                val configuredModel = generativeModelProvider.createModelWithConfig(generationConfig, safetySettings)

                // Gọi API với config (sửa lỗi)
                // Sử dụng configuredModel và không truyền config nữa
                val response = configuredModel.generateContent(contentBuilder)
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
                // Nếu là URL Firebase
                val fullPrompt = if (prompt.isNotEmpty()) {
                    getFullPrompt(prompt, hasImage = false, chatHistory = chatHistory)
                } else {
                    getFullPrompt("Đây là một đoạn âm thanh, hãy phân tích nội dung của nó.", hasImage = false, chatHistory = chatHistory)
                }
                
                Log.d("ChatRepository", "Regenerating prompt with audio URL: $fullPrompt")
                
                // Lấy model đã được cấu hình từ provider
                val configuredModel = generativeModelProvider.createModelWithConfig(generationConfig, safetySettings)

                // Sửa cách gọi generateContent với text
                val response = configuredModel.generateContent(content {
                    text(fullPrompt)
                })
                
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
            val errorChat = Chat.fromPrompt(
                prompt = "Error: ${e.localizedMessage}",
                isFromUser = false,
                isError = true,
                userId = userId
            )
            insertChat(errorChat, selectedSegmentId)
            errorChat
        }
    }

    /**
     * Upload tệp văn bản lên Firebase Storage và trả về URL download.
     * Đồng thời trích xuất nội dung văn bản để gửi đến API.
     */
    suspend fun uploadTextFile(fileUri: Uri): Pair<String?, String> = withContext(Dispatchers.IO) {
        try {
            // Trích xuất nội dung file văn bản
            val textContent = readTextFromUri(fileUri)
            
            // Tải lên Firebase Storage
            val fileName = getFileNameFromUri(fileUri) ?: "${System.currentTimeMillis()}.txt"
            val textRef = storage.reference.child("texts/$userId/$fileName")
            textRef.putFile(fileUri).await()
            val url = textRef.downloadUrl.await().toString()
            
            Log.d("ChatRepository", "Text file uploaded, URL: $url, Content length: ${textContent.length}")
            Pair(url, textContent)
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error uploading text file", e)
            Pair(fileUri.toString(), "Error: Không thể đọc nội dung file (${e.message})")
        }
    }
    
    /**
     * Đọc nội dung file văn bản từ Uri
     */
    private fun readTextFromUri(uri: Uri): String {
        val stringBuilder = StringBuilder()
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = inputStream.bufferedReader()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    stringBuilder.append(line)
                    stringBuilder.append("\n")
                }
            }
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error reading text from URI", e)
            return "Error reading file: ${e.message}"
        }
        return stringBuilder.toString()
    }
    
    /**
     * Lấy tên file từ Uri
     */
    private fun getFileNameFromUri(uri: Uri): String? {
        var fileName: String? = null
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val displayNameIndex = it.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    fileName = it.getString(displayNameIndex)
                }
            }
        }
        return fileName
    }

    /**
     * Lấy phản hồi từ GenerativeModel kèm file văn bản.
     */
    suspend fun getResponseWithTextFile(
        prompt: String,
        fileUri: Uri,
        selectedSegmentId: String?,
        apiSettings: ApiSettingsState
    ): Chat = coroutineScope {
        try {
            // Song song: lấy lịch sử chat và tải file văn bản
            val chatHistoryDeferred = async(Dispatchers.IO) {
                getChatHistoryForSegment(selectedSegmentId).filterNot { it.isError }
            }
            val (fileUrl, textContent) = uploadTextFile(fileUri)
            val chatHistory = chatHistoryDeferred.await()

            // Tạo prompt kết hợp nội dung file và prompt người dùng
            val effectivePrompt = if (prompt.isNotEmpty()) {
                "$prompt\n\nNội dung file:\n$textContent"
            } else {
                "Hãy phân tích nội dung file văn bản sau:\n\n$textContent"
            }

            val fullPrompt = getFullPrompt(effectivePrompt, hasImage = false, chatHistory = chatHistory)
            Log.d("ChatRepository", "Sending prompt with text file, prompt length: ${fullPrompt.length}")

            // Tạo GenerationConfig
            val (generationConfig, safetySettings) = buildGenerationConfig(apiSettings)
            Log.d("APISettingsDebug", "getResponseWithTextFile - Applying config: $generationConfig")

            // Lấy model đã được cấu hình từ provider
            val configuredModel = generativeModelProvider.createModelWithConfig(generationConfig, safetySettings)

            // Sửa cách gọi generateContent
            val response = configuredModel.generateContent(content {
                text(fullPrompt)
            })
            
            val responseText = response.text
            Log.d("ChatRepository", "API Response for text file: $responseText")

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

            val fileName = getFileNameFromUri(fileUri) ?: "unknown_file.txt"
            val chat = Chat(
                prompt = responseText ?: "Error: Không nhận được phản hồi từ API",
                imageUrl = fileUrl,
                isFromUser = false,
                isError = responseText == null,
                userId = userId,
                isFileMessage = true,
                fileName = fileName
            )
            
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
     * Xử lý chỉnh sửa tin nhắn người dùng, xóa tin nhắn mới hơn và khởi tạo lại hội thoại từ điểm chỉnh sửa
     */
    suspend fun editChatMessage(
        chat: Chat,
        segmentId: String,
        apiSettings: ApiSettingsState,
        finalPrompt: String? = null
    ): Chat = withContext(Dispatchers.IO) {
        try {
            // Lấy toàn bộ tin nhắn trong segment
            val messagesCollection = getMessagesCollection(segmentId)
            
            // Tìm tin nhắn theo timestamp nếu là ID mới được tạo
            var currentChatMessage: Chat? = null
            var originalChatId = ""
            
            // Lấy tất cả tin nhắn trong segment theo thứ tự thời gian
            val allMessages = messagesCollection
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(Chat::class.java)?.copy(id = it.id) }
            
            Log.d("ChatRepository", "Tổng số tin nhắn trước khi chỉnh sửa: ${allMessages.size}")
            
            // Tìm tin nhắn gốc bằng cách kết hợp nhiều phương pháp
            
            // Phương pháp 1: Tìm theo ID chính xác
            try {
                val messageSnapshot = messagesCollection.document(chat.id).get().await()
                currentChatMessage = messageSnapshot.toObject(Chat::class.java)
                if (currentChatMessage != null) {
                    originalChatId = currentChatMessage.id
                    Log.d("ChatRepository", "Tìm thấy tin nhắn gốc theo ID: $originalChatId")
                }
            } catch (e: Exception) {
                Log.d("ChatRepository", "Không tìm thấy tin nhắn theo ID: ${chat.id}")
            }
            
            // Phương pháp 2: Tìm theo timestamp nếu phương pháp 1 thất bại
            if (currentChatMessage == null) {
                try {
                    // Tìm tin nhắn người dùng có cùng timestamp (hoặc gần bằng)
                    val messagesQuery = messagesCollection
                        .whereEqualTo("isFromUser", true)
                        .whereGreaterThanOrEqualTo("timestamp", chat.timestamp - 100)
                        .whereLessThanOrEqualTo("timestamp", chat.timestamp + 100)
                        .get()
                        .await()
                    
                    currentChatMessage = messagesQuery.documents
                        .mapNotNull { it.toObject(Chat::class.java)?.copy(id = it.id) }
                        .firstOrNull()
                    
                    if (currentChatMessage != null) {
                        originalChatId = currentChatMessage.id
                        Log.d("ChatRepository", "Tìm thấy tin nhắn gốc theo timestamp: $originalChatId")
                    }
                } catch (e: Exception) {
                    Log.e("ChatRepository", "Lỗi khi tìm tin nhắn theo timestamp: ${e.message}")
                }
            }
            
            // Phương pháp 3: Nếu vẫn không tìm được, tìm theo nội dung prompt nếu có
            if (currentChatMessage == null && chat.prompt.isNotEmpty()) {
                try {
                    // Tìm tất cả tin nhắn của người dùng
                    val userMessages = allMessages.filter { it.isFromUser }
                    
                    // Tìm tin nhắn có nội dung tương tự
                    currentChatMessage = userMessages.firstOrNull { 
                        it.prompt.contains(chat.prompt) || chat.prompt.contains(it.prompt) 
                    }
                    
                    if (currentChatMessage != null) {
                        originalChatId = currentChatMessage.id
                        Log.d("ChatRepository", "Tìm thấy tin nhắn gốc theo nội dung: $originalChatId")
                    }
                } catch (e: Exception) {
                    Log.e("ChatRepository", "Lỗi khi tìm tin nhắn theo nội dung: ${e.message}")
                }
            }
            
            // Nếu vẫn không tìm được, tạo mới và bỏ qua việc xóa
            if (currentChatMessage == null) {
                Log.d("ChatRepository", "Không tìm thấy tin nhắn gốc, tiếp tục với tin nhắn mới")
                
                // Thêm tin nhắn mới vào collection
                messagesCollection.document(chat.id).set(chat).await()
                
                // Xử lý phản hồi dựa trên tin nhắn mới
                val effectivePrompt = finalPrompt ?: chat.prompt
                
                val response = processMessageResponse(chat, effectivePrompt, segmentId, apiSettings)
                return@withContext response
            }
            
            // Đến đây, đã tìm được tin nhắn gốc
            val originalTimestamp = currentChatMessage.timestamp
            
            // PHẦN XÓA: Kết hợp nhiều cách để xóa triệt để
            
            // 1. Xóa theo vị trí trong danh sách
            val originalMessageIndex = allMessages.indexOfFirst { it.id == originalChatId }
            
            // Danh sách các ID tin nhắn cần xóa
            val messageIdsToDelete = mutableSetOf<String>()
            
            if (originalMessageIndex != -1) {
                // Lấy danh sách tin nhắn cần xóa (tin nhắn gốc và tất cả tin nhắn sau đó)
                val messagesToDelete = allMessages.drop(originalMessageIndex)
                
                // Thu thập tất cả ID cần xóa
                messagesToDelete.forEach { message ->
                    messageIdsToDelete.add(message.id)
                }
                
                Log.d("ChatRepository", "Số tin nhắn cần xóa theo vị trí: ${messagesToDelete.size}")
            }
            
            // 2. Xóa theo timestamp
            val messagesAfterTimestamp = messagesCollection
                .whereGreaterThanOrEqualTo("timestamp", originalTimestamp)
                .get()
                .await()
                .documents
            
            // Thu thập thêm ID từ phương thức tìm theo timestamp
            messagesAfterTimestamp.forEach { document ->
                messageIdsToDelete.add(document.id)
            }
            
            Log.d("ChatRepository", "Tổng số ID tin nhắn cần xóa (sau khi gộp): ${messageIdsToDelete.size}")
            
            // 3. Thực hiện xóa từng tin nhắn một để đảm bảo chắc chắn
            for (messageId in messageIdsToDelete) {
                try {
                    messagesCollection.document(messageId).delete().await()
                    Log.d("ChatRepository", "Đã xóa tin nhắn: $messageId")
                } catch (e: Exception) {
                    Log.e("ChatRepository", "Lỗi khi xóa tin nhắn $messageId: ${e.message}")
                }
            }
            
            // Chờ một chút để đảm bảo thao tác xóa hoàn tất
            kotlinx.coroutines.delay(300)
            
            // Kiểm tra lại xem tin nhắn đã được xóa chưa
            val remainingMessages = messagesCollection
                .whereGreaterThanOrEqualTo("timestamp", originalTimestamp)
                .get()
                .await()
                .documents
            
            if (remainingMessages.isNotEmpty()) {
                Log.d("ChatRepository", "Vẫn còn ${remainingMessages.size} tin nhắn chưa xóa, thử xóa lại")
                
                // Xóa lại nếu còn sót
                for (document in remainingMessages) {
                    try {
                        messagesCollection.document(document.id).delete().await()
                        Log.d("ChatRepository", "Đã xóa tin nhắn (lần 2): ${document.id}")
                    } catch (e: Exception) {
                        Log.e("ChatRepository", "Lỗi khi xóa tin nhắn lần 2 ${document.id}: ${e.message}")
                    }
                }
                
                // Chờ thêm để đảm bảo
                kotlinx.coroutines.delay(200)
            }
            
            // Thêm tin nhắn đã chỉnh sửa
            messagesCollection.document(chat.id).set(chat).await()
            Log.d("ChatRepository", "Tin nhắn mới đã chỉnh sửa: ${chat.id}, prompt: ${chat.prompt}")
            
            // Cho một khoảng thời gian để đảm bảo tin nhắn đã được lưu
            kotlinx.coroutines.delay(200)
            
            // Khởi tạo lại hội thoại dựa trên tin nhắn chỉnh sửa
            val effectivePrompt = finalPrompt ?: chat.prompt
            Log.d("ChatRepository", "Sử dụng prompt có nội dung file: ${finalPrompt != null}")
            
            val response = processMessageResponse(chat, effectivePrompt, segmentId, apiSettings)
            
            // Kiểm tra xem tin nhắn mới đã được thêm thành công chưa
            val newMessages = messagesCollection
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .await()
                .documents
                .size
                
            Log.d("ChatRepository", "Số tin nhắn sau khi chỉnh sửa: $newMessages")
            
            return@withContext response
            
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error editing chat message: ${e.message}", e)
            Chat(
                prompt = "Error: Không thể chỉnh sửa tin nhắn (${e.localizedMessage})",
                imageUrl = null,
                isFromUser = false,
                isError = true,
                userId = userId
            )
        }
    }
    
    // Hàm mới để xử lý phản hồi dựa trên tin nhắn đã chỉnh sửa
    private suspend fun processMessageResponse(
        chat: Chat,
        prompt: String,
        segmentId: String?,
        apiSettings: ApiSettingsState
    ): Chat {
        Log.d("APISettingsDebug", "processMessageResponse - Will use apiSettings: $apiSettings")
        return if (chat.imageUrl != null) {
            if (chat.isFileMessage) {
                // Xử lý theo loại file
                val fileName = chat.fileName ?: ""
                when {
                    // File âm thanh
                    fileName.endsWith(".ogg") || fileName.endsWith(".mp3") || 
                    fileName.endsWith(".wav") || fileName.endsWith(".m4a") -> {
                        Log.d("ChatRepository", "Regenerating audio file response: ${chat.imageUrl}")
                        regenerateResponseWithAudio(prompt, chat.imageUrl ?: "", segmentId, apiSettings)
                    }
                    
                    // File văn bản hoặc các loại file khác 
                    fileName.endsWith(".txt") || fileName.endsWith(".md") || 
                    fileName.endsWith(".json") || fileName.endsWith(".xml") || 
                    fileName.endsWith(".pdf") || fileName.endsWith(".doc") || 
                    fileName.endsWith(".docx") -> {
                        // Đối với file văn bản, gọi API với prompt đã chỉnh sửa
                        Log.d("ChatRepository", "Regenerating text file response with extracted content")
                        getResponse(prompt, segmentId, apiSettings)
                    }
                    
                    // Các file khác và file không xác định
                    else -> {
                        Log.d("ChatRepository", "Regenerating generic file response")
                        getResponse(prompt, segmentId, apiSettings)
                    }
                }
            } else {
                // Tin nhắn có hình ảnh
                Log.d("ChatRepository", "Regenerating image response: ${chat.imageUrl}")
                regenerateResponseWithImage(prompt, chat.imageUrl ?: "", segmentId, apiSettings)
            }
        } else {
            // Tin nhắn văn bản thông thường
            Log.d("ChatRepository", "Regenerating text response")
            getResponse(prompt, segmentId, apiSettings)
        }
    }

    /**
     * Tạo lại phản hồi cho tin nhắn có file đính kèm.
     */
    suspend fun regenerateResponseWithFile(
        prompt: String,
        fileUrl: String,
        fileName: String,
        selectedSegmentId: String?,
        apiSettings: ApiSettingsState
    ): Chat = coroutineScope {
        Log.d("APISettingsDebug", "regenerateResponseWithFile - Using apiSettings: $apiSettings")
        try {
            // Kiểm tra kiểu file để có xử lý phù hợp
            val fileExtension = fileName.substringAfterLast('.', "").lowercase()
            
            return@coroutineScope when {
                // File âm thanh
                fileExtension in listOf("ogg", "mp3", "wav", "m4a") -> {
                    regenerateResponseWithAudio(prompt, fileUrl, selectedSegmentId, apiSettings)
                }
                
                // File văn bản và PDF
                fileExtension in listOf("txt", "md", "json", "xml", "pdf", "doc", "docx") -> {
                    // Đối với file văn bản, gọi API với prompt đã chỉnh sửa
                    val effectivePrompt = if (prompt.isEmpty()) {
                        "Đây là file $fileName, hãy xử lý nội dung của nó."
                    } else {
                        "$prompt\n\n(File đính kèm: $fileName)"
                    }
                    
                    getResponse(effectivePrompt, selectedSegmentId, apiSettings)
                }
                
                // Các file khác
                else -> {
                    val effectivePrompt = if (prompt.isEmpty()) {
                        "Tôi đã đính kèm file $fileName, hãy hỗ trợ tôi với file này."
                    } else {
                        "$prompt\n\n(File đính kèm: $fileName)"
                    }
                    
                    getResponse(effectivePrompt, selectedSegmentId, apiSettings)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val errorChat = Chat.fromPrompt(
                prompt = "Error: ${e.localizedMessage}",
                isFromUser = false,
                isError = true,
                userId = userId
            )
            insertChat(errorChat, selectedSegmentId)
            errorChat
        }
    }

    // Hàm helper để xây dựng GenerationConfig
    private fun buildGenerationConfig(apiSettings: ApiSettingsState): Pair<GenerationConfig, List<SafetySetting>> {
        Log.d("APISettingsDebug", "buildGenerationConfig - Received settings: $apiSettings")
        val maxOutputTokens = try {
            apiSettings.outputLength.toIntOrNull()?.takeIf { it > 0 } ?: 8192
        } catch (e: NumberFormatException) {
            Log.w("ChatRepository", "Invalid outputLength: ${apiSettings.outputLength}, defaulting to 8192.")
            8192
        }

        val stopSequences = if (apiSettings.stopSequence.isNotBlank()) {
            listOf(apiSettings.stopSequence)
        } else {
            null
        }

        // Map SafetyThreshold enum của chúng ta sang BlockThreshold của SDK
        fun mapThreshold(threshold: SafetyThreshold): BlockThreshold {
            return when (threshold) {
                SafetyThreshold.BLOCK_NONE -> BlockThreshold.NONE
                SafetyThreshold.BLOCK_ONLY_HIGH -> BlockThreshold.ONLY_HIGH
                SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE -> BlockThreshold.MEDIUM_AND_ABOVE
                SafetyThreshold.BLOCK_LOW_AND_ABOVE -> BlockThreshold.LOW_AND_ABOVE
            }
        }

        // Tạo GenerationConfig với cú pháp đơn giản, tránh sử dụng các phương thức không tồn tại
        val generationConfigBuilder = GenerationConfig.Builder()
        generationConfigBuilder.temperature = apiSettings.temperature
        generationConfigBuilder.topP = apiSettings.topP
        generationConfigBuilder.maxOutputTokens = maxOutputTokens
        if (stopSequences != null) {
            generationConfigBuilder.stopSequences = stopSequences
        }
        
        // Cài đặt các giá trị mặc định cần thiết
        val generationConfig = generationConfigBuilder.build()
        
        // Tạo danh sách SafetySetting
        val safetySettingsList = listOf(
            SafetySetting(HarmCategory.HARASSMENT, mapThreshold(apiSettings.safetyHarassment)),
            SafetySetting(HarmCategory.HATE_SPEECH, mapThreshold(apiSettings.safetyHate)),
            SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, mapThreshold(apiSettings.safetySexuallyExplicit)),
            SafetySetting(HarmCategory.DANGEROUS_CONTENT, mapThreshold(apiSettings.safetyDangerous))
        )
        
        Log.d("APISettingsDebug", "buildGenerationConfig - Built config: $generationConfig with ${safetySettingsList.size} safety settings")
        
        return Pair(generationConfig, safetySettingsList)
    }
}
