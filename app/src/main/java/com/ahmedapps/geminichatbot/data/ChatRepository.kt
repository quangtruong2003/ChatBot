// data/ChatRepository.kt
package com.ahmedapps.geminichatbot.data

import android.graphics.Bitmap
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class ChatRepository @Inject constructor(
    private val generativeModel: GenerativeModel,
    private val chatDao: ChatDao
) {
    private suspend fun getFullPrompt(currentPrompt: String): String {
        val chatHistory = chatDao.getAllChats()
        return buildString {
            for (chat in chatHistory) {
                append(if (chat.isFromUser) "User: " else "Bot: ")
                append(chat.prompt)
                append("\n") // Thêm newline để phân tách các tin nhắn
            }
            append("User: ")
            append(currentPrompt)
        }
    }

    suspend fun getResponse(prompt: String): Chat {
        return try {
            val fullPrompt = getFullPrompt(prompt)// Lấy câu hỏi và lịch sử trò chuyện
            val response = withContext(Dispatchers.IO) {
                generativeModel.generateContent(fullPrompt)
            }
            Chat(
                prompt = response.text ?: "Error: Empty response",
                bitmap = null,
                isFromUser = false
            ).also {
                chatDao.insertChat(it) // Lưu vào database
            }
        } catch (e: Exception) {
            Chat(
                prompt = "Error: ${e.localizedMessage}",
                bitmap = null,
                isFromUser = false,
                isError = true
            ).also {
                chatDao.insertChat(it) // Lưu lỗi vào database nếu cần
            }
        }
    }

    suspend fun getResponseWithImage(prompt: String, bitmap: Bitmap): Chat {
        return try {
            val fullPrompt = getFullPrompt(prompt)

            val inputContent = content {
                image(bitmap)
                text(fullPrompt)
            }
            val response = withContext(Dispatchers.IO) {
                generativeModel.generateContent(inputContent)
            }
            Chat(
                prompt = response.text ?: "Error: Empty response",
                bitmap = null,
                isFromUser = false
            ).also {
                chatDao.insertChat(it) // Lưu vào database
            }
        } catch (e: Exception) {
            Chat(
                prompt = "Error: ${e.localizedMessage}",
                bitmap = null,
                isFromUser = false,
                isError = true
            ).also {
                chatDao.insertChat(it) // Lưu lỗi vào database nếu cần
            }
        }
    }

    suspend fun getChatHistory(): List<Chat> {
        return chatDao.getAllChats()
    }

    suspend fun insertChat(chat: Chat) {
        chatDao.insertChat(chat)
    }

    suspend fun deleteAllChats() {
        chatDao.deleteAllChats()
    }
}
