// ChatState.kt
package com.ahmedapps.geminichatbot

import android.net.Uri
import com.ahmedapps.geminichatbot.data.Chat
import com.ahmedapps.geminichatbot.data.ChatSegment

data class ChatState(
    val chatList: List<Chat> = emptyList(),
    val isLoading: Boolean = false,
    val prompt: String = "",
    val imageUri: Uri? = null,
    val chatSegments: List<ChatSegment> = emptyList(),
    val selectedSegment: ChatSegment? = null,
    val searchQuery: String = ""
)
