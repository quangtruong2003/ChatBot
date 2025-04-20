// ChatUiEvent.kt
package com.ahmedapps.geminichatbot.data

import android.net.Uri

sealed class ChatUiEvent {
    data class SendPrompt(val prompt: String, val imageUri: Uri?) : ChatUiEvent()
    data class SendAudioPrompt(val prompt: String, val audioUri: Uri) : ChatUiEvent()
    data class UpdatePrompt(val newPrompt: String) : ChatUiEvent()
    data class OnImageSelected(val uri: Uri) : ChatUiEvent()
    data class OnAudioRecorded(val uri: Uri, val fileName: String) : ChatUiEvent()
    data class OnAudioFileSelected(val uri: Uri) : ChatUiEvent()
    data class SearchSegments(val query: String) : ChatUiEvent()
    data class SelectSegment(val segment: ChatSegment) : ChatUiEvent()
    object ClearSearch : ChatUiEvent()
    data class DeleteSegment(val segment: ChatSegment) : ChatUiEvent()
    data class RenameSegment(val segment: ChatSegment, val newTitle: String) : ChatUiEvent()
    object RemoveImage : ChatUiEvent()
    object RefreshChats : ChatUiEvent()
    object StopResponse : ChatUiEvent()
    data class OnFileSelected(val uri: Uri) : ChatUiEvent()
    object RemoveFile : ChatUiEvent()
    data class DeleteChat(val chatId: String) : ChatUiEvent()
    data class UpdateFileName(val fileName: String) : ChatUiEvent()
    data class EditChat(
        val chatId: String,
        val message: String,
        val timestamp: Long,
        val imageUrl: String? = null,
        val fileName: String? = null
    ) : ChatUiEvent()
    data class SaveEditedMessage(val newPrompt: String) : ChatUiEvent()
    object CancelEdit : ChatUiEvent()
    data class RegenerateResponse(
        val userPrompt: String,
        val responseId: String,
        val imageUrl: String? = null,
        val fileUri: Uri? = null,
        val fileName: String? = null,
        val timestamp: Long = -1
    ) : ChatUiEvent()
}


