// ChatViewModel.kt
package com.ahmedapps.geminichatbot

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmedapps.geminichatbot.data.Chat
import com.ahmedapps.geminichatbot.data.ChatRepository
import com.ahmedapps.geminichatbot.data.ChatSegment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository
) : ViewModel() {

    private val _chatState = MutableStateFlow(ChatState())
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()

    // Thêm biến để theo dõi việc cập nhật tiêu đề
    private var hasUpdatedTitle = false

    // Thêm MutableStateFlow cho search query với debounce
    private val searchQueryFlow = MutableStateFlow("")

    init {
        loadChatSegments()
        loadDefaultSegmentChatHistory()

        // Thu thập searchQueryFlow với debounce để xử lý tìm kiếm hiệu quả
        viewModelScope.launch {
            searchQueryFlow
                .debounce(300) // Chờ 300ms sau khi người dùng dừng nhập
                .distinctUntilChanged()
                .collect { query ->
                    handleSearchQuery(query)
                }
        }
    }

    /**
     * Xử lý tìm kiếm dựa trên query
     */
    private suspend fun handleSearchQuery(query: String) {
        if (query.isEmpty()) {
            loadChatSegments()
        } else {
            val results = repository.searchChatSegments(query)
            _chatState.update { it.copy(chatSegments = results) }
        }
    }

    /**
     * Loads all chat segments.
     */
    private fun loadChatSegments() {
        viewModelScope.launch {
            val segments = repository.getChatSegments()
            _chatState.update { it.copy(chatSegments = segments) }
        }
    }

    /**
     * Loads chat history for the selected segment or default segment.
     */
    private fun loadDefaultSegmentChatHistory() {
        viewModelScope.launch {
            val segments = repository.getChatSegments()
            if (segments.isNotEmpty()) {
                val defaultSegment = segments.first()
                _chatState.update { it.copy(selectedSegment = defaultSegment, isLoading = true) }
                loadChatHistoryForSegment(defaultSegment.id)
            }
        }
    }

    /**
     * Loads chat history for a specific segment.
     */
    private fun loadChatHistoryForSegment(segmentId: String) {
        viewModelScope.launch {
            val chats = repository.getChatHistoryForSegment(segmentId)
            _chatState.update { it.copy(chatList = chats, isLoading = false) }
        }
    }

    /**
     * Refreshes chats by creating a new chat segment.
     */
    fun refreshChats() {
        viewModelScope.launch {
            try {
                _chatState.update { it.copy(isLoading = true) }
                // Tạo một đoạn chat mới
                val newSegmentTitle = "Chat mới ${System.currentTimeMillis()}"
                val newSegmentId = repository.addChatSegment(newSegmentTitle)
                if (newSegmentId != null) {
                    val newSegment = ChatSegment(
                        id = newSegmentId,
                        title = newSegmentTitle,
                        createdAt = System.currentTimeMillis()
                    )
                    _chatState.update {
                        it.copy(
                            selectedSegment = newSegment,
                            chatList = emptyList(),
                            chatSegments = it.chatSegments + newSegment,
                            searchQuery = "" // Đặt lại search query khi tạo segment mới
                        )
                    }
                    hasUpdatedTitle = false // Reset flag khi tạo segment mới
                }
                _chatState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                e.printStackTrace()
                _chatState.update { it.copy(isLoading = false) }
            }
        }
    }


    /**
     * Handles UI events.
     */
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
            is ChatUiEvent.SearchSegments -> {
                // Cập nhật searchQuery và searchQueryFlow
                _chatState.update { it.copy(searchQuery = event.query) }
                searchQueryFlow.value = event.query
            }
            is ChatUiEvent.SelectSegment -> {
                _chatState.update { it.copy(selectedSegment = event.segment, isLoading = true) }
                hasUpdatedTitle = false // Reset flag khi chọn segment mới
                // Load chat messages cho segment đã chọn
                viewModelScope.launch {
                    val chats = repository.getChatHistoryForSegment(event.segment.id)
                    _chatState.update { it.copy(chatList = chats, isLoading = false) }
                }
                // Xóa nội dung tìm kiếm khi chọn đoạn chat
                viewModelScope.launch {
                    searchQueryFlow.value = ""
                }
            }
            is ChatUiEvent.ClearSearch -> {
                // Đặt lại searchQuery và searchQueryFlow về rỗng
                _chatState.update { it.copy(searchQuery = "") }
                searchQueryFlow.value = ""
            }
            is ChatUiEvent.DeleteSegment -> {
                viewModelScope.launch {
                    deleteSegment(event.segment)
                }
            }
        }
    }

    /**
     * Adds a user prompt and handles segment creation if necessary.
     */
    private suspend fun addPrompt(prompt: String, imageUri: Uri?) {
        val currentUserId = repository.userId
        if (currentUserId.isEmpty()) {
            Log.e("ChatViewModel", "User is not authenticated")
            _chatState.update { it.copy(isLoading = false) }
            return
        }

        val imageUrl = imageUri?.let {
            repository.uploadImage(it)
        }
        val chat = Chat.fromPrompt(
            prompt = prompt,
            imageUrl = imageUrl,
            isFromUser = true,
            isError = false,
            userId = currentUserId
        )
        val segmentId = _chatState.value.selectedSegment?.id
        repository.insertChat(chat, segmentId)
        _chatState.update {
            it.copy(
                chatList = it.chatList + chat,
                prompt = "",
                imageUri = null,
                isLoading = false,
            )
        }

        //        // Nếu đây là đoạn chat mới, tạo một ChatSegment mới
        //        if (_chatState.value.chatSegments.isEmpty() || _chatState.value.selectedSegment == null) {
        //            val title = repository.generateChatSegmentTitleFromResponse(prompt)
        //            val newSegmentId = repository.addChatSegment(title)
        //            if (newSegmentId != null) {
        //                val newSegment = ChatSegment(id = newSegmentId, title = title, createdAt = System.currentTimeMillis())
        //                _chatState.update { it.copy(selectedSegment = newSegment, chatSegments = it.chatSegments + newSegment) }
        //                hasUpdatedTitle = false
        //                loadChatHistoryForSegment(newSegmentId)
        //            }
        //        }
    }

    /**
     * Retrieves a response from the GenerativeModel.
     */
    private suspend fun getResponse(prompt: String) {
        val chat = repository.getResponse(prompt)
        _chatState.update {
            it.copy(
                chatList = it.chatList + chat,
                isLoading = false
            )
        }

        // Sau khi nhận được phản hồi đầu tiên, tạo và đặt tiêu đề đoạn chat
        if (!hasUpdatedTitle && !chat.isFromUser) {
            repository.updateSegmentTitleFromResponse(_chatState.value.selectedSegment?.id, chat.prompt)
            hasUpdatedTitle = true
            // Tải lại các đoạn chat để cập nhật tiêu đề
            loadChatSegments()
        }
    }

    /**
     * Retrieves a response with an image from the GenerativeModel.
     */
    private suspend fun getResponseWithImage(prompt: String, imageUri: Uri) {
        val chat = repository.getResponseWithImage(prompt, imageUri)
        _chatState.update {
            it.copy(
                chatList = it.chatList + chat,
                isLoading = false
            )
        }

        // Sau khi nhận được phản hồi đầu tiên, tạo và đặt tiêu đề đoạn chat
        if (!hasUpdatedTitle && !chat.isFromUser) {
            repository.updateSegmentTitleFromResponse(_chatState.value.selectedSegment?.id, chat.prompt)
            hasUpdatedTitle = true
            // Tải lại các đoạn chat để cập nhật tiêu đề
            loadChatSegments()
        }
    }

    /**
     * Clears all chat history.
     * (Chú ý: Hàm này sẽ xóa tất cả các đoạn chat của người dùng.)
     */
    fun clearChat() {
        viewModelScope.launch {
            repository.deleteAllChats()
            _chatState.update { it.copy(chatList = emptyList(), chatSegments = emptyList(), selectedSegment = null, searchQuery = "") }
        }
    }
    /**
     * Xóa một đoạn chat và tất cả các cuộc trò chuyện liên quan.
     */
    private suspend fun deleteSegment(segment: ChatSegment) {
        try {
            _chatState.update { it.copy(isLoading = true) }

            // Xóa đoạn chat từ repository
            repository.deleteChatSegment(segment.id)

            // Cập nhật trạng thái bằng cách loại bỏ đoạn chat đã xóa
            val updatedSegments = _chatState.value.chatSegments.filter { it.id != segment.id }
            _chatState.update { it.copy(chatSegments = updatedSegments) }

            // Nếu đoạn chat đã xóa là đoạn được chọn, chọn một đoạn khác
            if (_chatState.value.selectedSegment?.id == segment.id) {
                val newSelectedSegment = updatedSegments.firstOrNull()
                _chatState.update {
                    it.copy(
                        selectedSegment = newSelectedSegment,
                        chatList = newSelectedSegment?.let { seg -> repository.getChatHistoryForSegment(seg.id) } ?: emptyList()
                    )
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            // Tùy chọn: xử lý lỗi bằng cách hiển thị Snackbar hoặc thông báo tương tự
        } finally {
            _chatState.update { it.copy(isLoading = false) }
        }
    }
}
