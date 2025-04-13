// ChatState.kt
package com.ahmedapps.geminichatbot

import android.net.Uri
import com.ahmedapps.geminichatbot.data.Chat
import com.ahmedapps.geminichatbot.data.ChatSegment

data class ChatState(
        val prompt: String = "",
        val imageUri: Uri? = null,
        val fileUri: Uri? = null,
        val fileName: String? = null,
        val isFileUploading: Boolean = false,
        val isFileMessage: Boolean = false,
        val isAudioMessage: Boolean = false,
        val chatList: List<Chat> = emptyList(),
        val chatSegments: List<ChatSegment> = emptyList(),
        val selectedSegment: ChatSegment? = null,
        val searchQuery: String = "",
        val isLoading: Boolean = false,
        val isWaitingForResponse: Boolean = false,
        val isImageProcessing: Boolean = false,
        val typedMessages: Set<String> = emptySet(),
        val isEditing: Boolean = false,
        val editingChatId: String? = null,
        val editingChatTimestamp: Long = -1,
        val isProcessingFile: Boolean = false,
        val stopTypingMessageId: String? = null,

        val editingImageUrl: String? = null,
        val editingFileUri: Uri? = null, // Giữ lại Uri nếu logic EditChat cần
        val editingFileName: String? = null
    )
