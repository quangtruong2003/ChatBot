// data/ChatRepository.kt
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
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream
import javax.inject.Inject

class ChatRepository @Inject constructor(
    private val context: Context,
    private val generativeModel: GenerativeModel,
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
) {
    private val auth = FirebaseAuth.getInstance()
    private val userId: String
        get() = auth.currentUser?.uid ?: ""

    private val chatsCollection
        get() = db.collection("chats").document(userId).collection("messages")

    private val storageReference
        get() = storage.reference.child("images/$userId/")

    suspend fun uploadImage(imageUri: Uri): String? {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e("ChatRepository", "User not authenticated")
            return null
        }

        val imageRef = storageReference.child("${System.currentTimeMillis()}.png")

        val stream = context.contentResolver.openInputStream(imageUri)
        val data = stream?.readBytes() ?: return null

        return try {
            imageRef.putBytes(data).await()
            val url = imageRef.downloadUrl.await().toString()
            Log.d("ChatRepository", "Image uploaded, URL: $url")
            url
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error uploading image", e)
            null
        }
    }

    private suspend fun getFullPrompt(currentPrompt: String): String {
        val chatHistory = getChatHistory()
        return buildString {
            for (chat in chatHistory.reversed()) {
                append(if (chat.isFromUser) "User: " else "Bot: ")
                append(chat.prompt)
                append("\n")
            }
            append("User: ")
            append(currentPrompt)
        }
    }

    suspend fun getResponse(prompt: String): Chat {
        return try {
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

    suspend fun getResponseWithImage(prompt: String, imageUri: Uri): Chat {
        return try {
            val fullPrompt = getFullPrompt(prompt)

            // Chuyển đổi Uri thành Bitmap
            val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, imageUri)
                ImageDecoder.decodeBitmap(source)
            } else {
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(context.contentResolver, imageUri)
            }

            // Tạo Content instances cho hình ảnh và văn bản
            val imageContent = content {
                image(bitmap)
            }
            val textContent = content {
                text(fullPrompt)
            }

            // Gọi API với các Content instances
            val response = generativeModel.generateContent(imageContent, textContent)

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

    suspend fun getChatHistory(): List<Chat> {
        return try {
            val snapshot = chatsCollection.orderBy("id").get().await()
            snapshot.documents.mapNotNull { it.toObject(Chat::class.java) }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun insertChat(chat: Chat) {
        try {
            chatsCollection.add(chat).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteAllChats() {
        try {
            val batch = db.batch()
            val snapshot = chatsCollection.get().await()
            for (document in snapshot.documents) {
                batch.delete(document.reference)
            }
            batch.commit().await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
