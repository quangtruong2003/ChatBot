// ChatRepository.kt
package com.ahmedapps.geminichatbot.data

import android.content.Context
import android.graphics.Bitmap
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class ChatRepository @Inject constructor(
    private val context: Context,
    private val generativeModel: GenerativeModel,
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
) {
    private val auth = FirebaseAuth.getInstance()

    val userId: String
        get() {
            val uid = auth.currentUser?.uid ?: ""
            Log.d("ChatRepository", "Current User ID: $uid")
            return uid
        }

    init {
        Log.d("ChatRepository", "Initialized with User ID: $userId")
    }

    // Reference to "chats/{userId}/segments"
    private val segmentsCollection
        get() = db.collection("chats").document(userId).collection("segments")

    // Reference to "images/{userId}/"
    private val storageReference
        get() = storage.reference.child("images/$userId/")

    /**
     * Uploads an image to Firebase Storage and returns its download URL.
     */
    suspend fun uploadImage(imageUri: Uri): String? = withContext(Dispatchers.IO) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e("ChatRepository", "User not authenticated")
            return@withContext null
        }

        val imageRef = storageReference.child("${System.currentTimeMillis()}.png")

        context.contentResolver.openInputStream(imageUri)?.use { stream ->
            val data = stream.readBytes()
            try {
                imageRef.putBytes(data).await()
                val url = imageRef.downloadUrl.await().toString()
                Log.d("ChatRepository", "Image uploaded, URL: $url")
                url
            } catch (e: Exception) {
                Log.e("ChatRepository", "Error uploading image", e)
                null
            }
        } ?: run {
            Log.e("ChatRepository", "Failed to open InputStream for imageUri: $imageUri")
            null
        }
    }

    /**
     * Generates the full prompt by concatenating chat history.
     */
    private suspend fun getFullPrompt(currentPrompt: String): String = withContext(Dispatchers.IO) {
        val chatHistory = getChatHistoryForSegment(_selectedSegmentId)
        buildString {
            for (chat in chatHistory.reversed()) {
                append(if (chat.isFromUser) "User: " else "Bot: ")
                append(chat.prompt)
                append("\n")
            }
            append("User: ")
            append(currentPrompt)
        }
    }

    /**
     * Gets response from GenerativeModel without image.
     */
    suspend fun getResponse(prompt: String): Chat = withContext(Dispatchers.IO) {
        return@withContext try {
            val fullPrompt = getFullPrompt(prompt)
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
     * Gets response from GenerativeModel with image.
     */
    suspend fun getResponseWithImage(prompt: String, imageUri: Uri): Chat = withContext(Dispatchers.IO) {
        return@withContext try {
            val fullPrompt = getFullPrompt(prompt)

            // Convert Uri to Bitmap
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, imageUri)
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
            }

            // Create Content instances for image and text
            val imageContent = content {
                image(bitmap)
            }
            val textContent = content {
                text(fullPrompt)
            }

            // Call API with Content instances
            val response = generativeModel.generateContent(imageContent, textContent)

            // Upload image and get URL
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
     * Retrieves all chat messages for a specific segment.
     */
    suspend fun getChatHistoryForSegment(segmentId: String?): List<Chat> = withContext(Dispatchers.IO) {
        if (segmentId == null) return@withContext emptyList()
        return@withContext try {
            getMessagesCollection(segmentId)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get()
                .await()
                .documents
                .mapNotNull { it.toObject(Chat::class.java) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Inserts a chat message vào một đoạn chat cụ thể.
     * Đảm bảo rằng một đoạn chat đã được chọn; nếu không, tạo một đoạn chat mặc định.
     */
    private var _selectedSegmentId: String? = null // Biến lưu ID đoạn chat hiện tại
    private var _hasUpdatedTitle: Boolean = false // Biến theo dõi việc cập nhật tiêu đề

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
     * Generates a title for a chat segment based on the API response.
     */
    suspend fun generateChatSegmentTitleFromResponse(chat: String): String {
        return try {
            val prompt = "Đặt 1 tiêu đề duy nhất chính xác, ngắn gọn và xúc tích cho tin nhắn sau: + '$chat'. Và bạn hãy chỉ trả lời tiêu đề duy nhất bạn đặt. Tiêu đề phải hoàn hảo và hợp lí. Không có gì khác ngoài tiêu đề."
            val response = generativeModel.generateContent(prompt)
            response.text ?: "Untitled Segment"
        } catch (e: Exception) {
            e.printStackTrace()
            "Untitled Segment"
        }
    }

    /**
     * Updates the title of a specific chat segment.
     */
    suspend fun updateSegmentTitle(segmentId: String?, newTitle: String) = withContext(Dispatchers.IO) {
        if (segmentId == null) return@withContext
        try {
            val segmentRef = segmentsCollection.document(segmentId)
            segmentRef.update("title", newTitle).await()
            Log.d("ChatRepository", "Updated segment title to: $newTitle for segment ID: $segmentId")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Updates the title of a specific chat segment based on the API response.
     */
    suspend fun updateSegmentTitleFromResponse(segmentId: String?, firstResponse: String) {
        if (segmentId == null) return
        val newTitle = generateChatSegmentTitleFromResponse(firstResponse)
        updateSegmentTitle(segmentId, newTitle)
    }

    /**
     * Deletes all chat segments and their messages.
     * (Chú ý: Hàm này sẽ xóa tất cả các đoạn chat của người dùng.)
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
            e.printStackTrace()
        }
    }

    /**
     * Retrieves all chat segments.
     */
    suspend fun getChatSegments(): List<ChatSegment> = withContext(Dispatchers.IO) {
        return@withContext try {
            val snapshot = segmentsCollection.orderBy("createdAt", Query.Direction.DESCENDING).get().await()
            snapshot.documents.mapNotNull { it.toObject(ChatSegment::class.java)?.copy(id = it.id) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Searches chat segments based on a query string.
     */
    suspend fun searchChatSegments(query: String): List<ChatSegment> = withContext(Dispatchers.IO) {
        return@withContext try {
            val snapshot = segmentsCollection
                .whereGreaterThanOrEqualTo("title", query)
                .whereLessThanOrEqualTo("title", query + "\uf8ff")
                .orderBy("title")
                .get()
                .await()
            snapshot.documents.mapNotNull { it.toObject(ChatSegment::class.java)?.copy(id = it.id) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Deletes a specific chat segment along with its messages.
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
            Log.d("ChatRepository", "Deleted segment and its messages with ID: $segmentId")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Adds a new chat segment with a given title.
     */
    suspend fun addChatSegment(title: String): String? = withContext(Dispatchers.IO) {
        try {
            val docRef = segmentsCollection.document()
            val segmentId = docRef.id
            val newSegment = ChatSegment(
                id = segmentId,
                title = title,
                createdAt = System.currentTimeMillis()
            )
            docRef.set(newSegment).await()
            Log.d("ChatRepository", "Added ChatSegment with ID: $segmentId")
            return@withContext segmentId
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    /**
     * Retrieves or creates a default segment if none exists.
     */
    private suspend fun getOrCreateDefaultSegmentId(): String? = withContext(Dispatchers.IO) {
        try {
            // Sắp xếp các segment theo thời gian tạo giảm dần
            val segmentsSnapshot = segmentsCollection
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .await()

            return@withContext if (segmentsSnapshot.isEmpty) {
                // Tạo một segment mặc định nếu chưa có
                val defaultSegmentId = addChatSegment("Default Chat")
                defaultSegmentId
            } else {
                // Trả về ID của segment mới nhất
                segmentsSnapshot.documents.first().id
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    /**
     * Helper function to get messages collection reference.
     */
    private fun getMessagesCollection(segmentId: String) =
        segmentsCollection.document(segmentId).collection("messages")
}
