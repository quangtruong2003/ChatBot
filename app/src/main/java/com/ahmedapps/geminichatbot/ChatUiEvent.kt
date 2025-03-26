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
    object ClearSearch : ChatUiEvent()
    data class DeleteSegment(val segment: ChatSegment) : ChatUiEvent()
    object RemoveImage : ChatUiEvent()
    data class RenameSegment(val segment: ChatSegment, val newTitle: String) : ChatUiEvent()
    object RefreshChats : ChatUiEvent()
    object StopResponse : ChatUiEvent()
}
