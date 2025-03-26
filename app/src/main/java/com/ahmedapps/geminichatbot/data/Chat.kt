// data/Chat.kt
package com.ahmedapps.geminichatbot.data

import com.google.firebase.firestore.PropertyName
import java.util.*

data class Chat(
    @get:PropertyName("id")
    var id: String = "",
    @get:PropertyName("prompt")
    var prompt: String = "",
    @get:PropertyName("imageUrl")
    var imageUrl: String? = null,
    @get:PropertyName("isFromUser")
    var isFromUser: Boolean = true,
    @get:PropertyName("isError")
    var isError: Boolean = false,
    @get:PropertyName("timestamp")
    var timestamp: Long = System.currentTimeMillis(),
    @get:PropertyName("userId")
    var userId: String = "",
) {
    companion object {
        fun fromPrompt(
            prompt: String,
            imageUrl: String? = null,
            isFromUser: Boolean = true,
            isError: Boolean = false,
            userId: String
        ): Chat {
            val timestamp = System.currentTimeMillis()
            // Đảm bảo ID không bao giờ rỗng
            val safeId = if (UUID.randomUUID().toString().isEmpty()) {
                "chat_${timestamp}_${prompt.hashCode()}"
            } else {
                UUID.randomUUID().toString()
            }
            
            return Chat(
                id = safeId,
                prompt = prompt,
                imageUrl = imageUrl,
                isFromUser = isFromUser,
                isError = isError,
                userId = userId,
                timestamp = timestamp
            )
        }
    }
}
