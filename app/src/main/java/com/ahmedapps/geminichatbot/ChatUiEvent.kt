// ChatUiEvent.kt
package com.ahmedapps.geminichatbot

import android.net.Uri

sealed class ChatUiEvent {
    data class UpdatePrompt(val newPrompt: String) : ChatUiEvent()
    data class SendPrompt(
        val prompt: String,
        val imageUri: Uri?
    ) : ChatUiEvent()
    data class OnImageSelected(val uri: Uri?) : ChatUiEvent()
}
