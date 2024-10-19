// ChatState.kt
package com.ahmedapps.geminichatbot

import android.graphics.Bitmap
import com.ahmedapps.geminichatbot.data.Chat

data class ChatState(
    val chatList: List<Chat> = emptyList(),
    val prompt: String = "",
    val bitmap: Bitmap? = null,
    val isLoading: Boolean = false
)
