// ChatState.kt
package com.ahmedapps.geminichatbot

import android.net.Uri
import com.ahmedapps.geminichatbot.data.Chat

data class ChatState(
    val chatList: List<Chat> = emptyList(),
    val prompt: String = "",
    val imageUri: Uri? = null,
    val isLoading: Boolean = false
)
