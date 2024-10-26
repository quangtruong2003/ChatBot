// ChatViewModel.kt
package com.ahmedapps.geminichatbot

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmedapps.geminichatbot.data.Chat
import com.ahmedapps.geminichatbot.data.ChatRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository
) : ViewModel() {

    private val _chatState = MutableStateFlow(ChatState())
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()

    init {
        loadChatHistory()
    }

    private fun loadChatHistory() {
        viewModelScope.launch {
            val chats = repository.getChatHistory()
            _chatState.update { it.copy(chatList = chats) }
        }
    }

    fun onEvent(event: ChatUiEvent) {
        when (event) {
            is ChatUiEvent.SendPrompt -> {
                if (event.prompt.isNotEmpty()) {
                    _chatState.update { it.copy(isLoading = true) }
                    viewModelScope.launch {
                        addPrompt(event.prompt, event.imageUri)
                        if (event.imageUri != null) {
                            getResponseWithImage(event.prompt, event.imageUri)
                        } else {
                            getResponse(event.prompt)
                        }
                    }
                }
            }
            is ChatUiEvent.UpdatePrompt -> {
                _chatState.update { it.copy(prompt = event.newPrompt) }
            }
            is ChatUiEvent.OnImageSelected -> {
                _chatState.update { it.copy(imageUri = event.uri) }
            }
        }
    }

    private suspend fun addPrompt(prompt: String, imageUri: Uri?) {
        val imageUrl = imageUri?.let {
            repository.uploadImage(it)
        }
        val chat = Chat.fromPrompt(
            prompt = prompt,
            imageUrl = imageUrl,
            isFromUser = true,
            isError = false,
            userId = ""
        )
        repository.insertChat(chat)
        _chatState.update {
            it.copy(
                chatList = listOf(chat) + it.chatList,
                prompt = "",
                imageUri = null
            )
        }
    }

    private suspend fun getResponse(prompt: String) {
        val chat = repository.getResponse(prompt)
        _chatState.update {
            it.copy(
                chatList = listOf(chat) + it.chatList,
                isLoading = false
            )
        }
    }

    private suspend fun getResponseWithImage(prompt: String, imageUri: Uri) {
        val chat = repository.getResponseWithImage(prompt, imageUri)
        _chatState.update {
            it.copy(
                chatList = listOf(chat) + it.chatList,
                isLoading = false
            )
        }
    }

    fun clearChat() {
        viewModelScope.launch {
            repository.deleteAllChats()
            _chatState.update { it.copy(chatList = emptyList()) }
        }
    }
}
