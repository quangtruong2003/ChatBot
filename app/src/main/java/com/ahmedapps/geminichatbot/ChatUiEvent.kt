// ChatUiEvent.kt
package com.ahmedapps.geminichatbot

import android.net.Uri
import com.ahmedapps.geminichatbot.data.ChatSegment

sealed class ChatUiEvent {
    data class SendPrompt(val prompt: String, val imageUri: Uri?) : ChatUiEvent()
    data class UpdatePrompt(val newPrompt: String) : ChatUiEvent()
    data class OnImageSelected(val uri: Uri?) : ChatUiEvent()
    data class SearchSegments(val query: String) : ChatUiEvent()
    data class SelectSegment(val segment: ChatSegment) : ChatUiEvent()
}
