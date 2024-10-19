// ChatViewModel.kt
package com.ahmedapps.geminichatbot

import android.graphics.Bitmap
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
    val chatState = _chatState.asStateFlow()

    init {
        loadChatHistory()
    }

    private fun loadChatHistory() {
        viewModelScope.launch {
            val chats = repository.getChatHistory()
            _chatState.update {
                it.copy(
                    chatList = chats
                )
            }
        }
    }

    fun onEvent(event: ChatUiEvent) {
        when (event) {
            is ChatUiEvent.SendPrompt -> {
                if (event.prompt.isNotEmpty()) {
                    addPrompt(event.prompt, event.bitmap)

                    _chatState.update { it.copy(isLoading = true) }

                    if (event.bitmap != null) {
                        getResponseWithImage(event.prompt, event.bitmap)
                    } else {
                        getResponse(event.prompt)
                    }
                }
            }

            is ChatUiEvent.UpdatePrompt -> {
                _chatState.update {
                    it.copy(prompt = event.newPrompt)
                }
            }
        }
    }

    private fun addPrompt(prompt: String, bitmap: Bitmap?) {
        val chat = Chat(prompt = prompt, bitmap = bitmap, isFromUser = true)
        viewModelScope.launch {
            repository.insertChat(chat)
        }
        _chatState.update {
            it.copy(
                chatList = listOf(chat) + it.chatList,
                prompt = "",
                bitmap = null
            )
        }
    }

    private fun getResponse(prompt: String) {
        viewModelScope.launch {
            val chat = repository.getResponse(prompt)
            _chatState.update {
                it.copy(
                    chatList = listOf(chat) + it.chatList,
                    isLoading = false
                )
            }
        }
    }

    private fun getResponseWithImage(prompt: String, bitmap: Bitmap) {
        viewModelScope.launch {
            val chat = repository.getResponseWithImage(prompt, bitmap)
            _chatState.update {
                it.copy(
                    chatList = listOf(chat) + it.chatList,
                    isLoading = false
                )
            }
        }
    }
}
