// data/Chat.kt
package com.ahmedapps.geminichatbot.data

import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.PropertyName

data class Chat(
    val id: String = "",
    val prompt: String = "",
    val imageUrl: String? = null,
    @get:PropertyName("fromUser") @set:PropertyName("fromUser")
    var isFromUser: Boolean = false,
    @get:PropertyName("error") @set:PropertyName("error")
    var isError: Boolean = false,
    val userId: String = "",
    @get:PropertyName("timestamp")
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromPrompt(
            prompt: String,
            imageUrl: String?,
            isFromUser: Boolean,
            isError: Boolean,
            userId: String
        ): Chat {
            return Chat(
                prompt = prompt,
                imageUrl = imageUrl,
                isFromUser = isFromUser,
                isError = isError,
                userId = userId,
                timestamp = System.currentTimeMillis()
            )
        }
    }
}
