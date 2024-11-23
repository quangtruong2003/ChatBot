// ChatViewModel.kt
package com.ahmedapps.geminichatbot

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmedapps.geminichatbot.data.Chat
import com.ahmedapps.geminichatbot.data.ChatRepository
import com.ahmedapps.geminichatbot.data.ChatSegment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository
) : ViewModel() {

    private val _chatState = MutableStateFlow(ChatState())
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()

    // Biến để theo dõi việc cập nhật tiêu đề
    private var hasUpdatedTitle = false

    // MutableStateFlow cho search query với debounce
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
     * Loại bỏ dấu tiếng Việt khỏi chuỗi.
     */
    fun removeVietnameseAccents(str: String): String {
        val normalizedString = Normalizer.normalize(str, Normalizer.Form.NFD)
        return normalizedString.replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
    }

    /**
     * Xử lý tìm kiếm dựa trên query
     */
    private suspend fun handleSearchQuery(query: String) {
        if (query.isEmpty()) {
            loadChatSegments()
        } else {
            val allSegments = repository.getChatSegments()
            val normalizedQuery = removeVietnameseAccents(query).lowercase(Locale.getDefault())
            val results = allSegments.filter { segment ->
                val normalizedTitle = removeVietnameseAccents(segment.title).lowercase(Locale.getDefault())
                normalizedTitle.contains(normalizedQuery)
            }
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

                // Lấy tất cả các đoạn chat được sắp xếp theo thời gian tạo (mới nhất trước)
                val segments = repository.getChatSegments()

                if (segments.isEmpty()) {
                    // Nếu chưa có đoạn chat nào, tạo mới
                    createNewSegment()
                } else {
                    val latestSegment = segments.first() // Giả sử segments đã được sắp xếp giảm dần
                    val currentSelectedSegment = _chatState.value.selectedSegment

                    if (currentSelectedSegment?.id == latestSegment.id) {
                        // Nếu đang xem đoạn chat mới nhất, tạo đoạn chat mới
                        createNewSegment()
                    } else {
                        // Nếu đang xem đoạn chat cũ, chuyển về đoạn chat mới nhất
                        navigateToLatestSegment(latestSegment)
                    }
                }

                _chatState.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                e.printStackTrace()
                _chatState.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Tạo một đoạn chat mới.
     */
    private suspend fun createNewSegment() {
        val newSegmentTitle = "Đoạn chat mới" // Bạn có thể thêm timestamp nếu muốn
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
                    searchQuery = ""
                )
            }
            hasUpdatedTitle = false
        }
    }

    /**
     * Chuyển về đoạn chat mới nhất.
     */
    private suspend fun navigateToLatestSegment(latestSegment: ChatSegment) {
        _chatState.update {
            it.copy(
                selectedSegment = latestSegment,
                chatList = emptyList(),
                searchQuery = ""
            )
        }
        loadChatHistoryForSegment(latestSegment.id)
    }


    /**
     * Handles UI events.
     */
    fun onEvent(event: ChatUiEvent) {
        when (event) {
            is ChatUiEvent.SendPrompt -> {
                if (event.prompt.isNotEmpty() || event.imageUri != null) {
                    _chatState.update { it.copy(isLoading = true) }
                    viewModelScope.launch {
                        addPrompt(event.prompt, event.imageUri)
                        val selectedSegmentId = _chatState.value.selectedSegment?.id
                        if (event.imageUri != null) {
                            getResponseWithImage(event.prompt, event.imageUri, selectedSegmentId)
                        } else {
                            getResponse(event.prompt, selectedSegmentId)
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
            is ChatUiEvent.RemoveImage -> {
                _chatState.update { it.copy(imageUri = null) }
            }
        }
    }

    /**
     * Adds a user prompt and handles segment creation if necessary.
     */
    private suspend fun addPrompt(prompt: String, imageUri: Uri?) {
        val currentUserId = repository.userId
        if (currentUserId.isEmpty()) {
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
                imageUri = null
            )
        }
    }

    /**
     * Retrieves a response from the GenerativeModel.
     */
    private suspend fun getResponse(prompt: String, selectedSegmentId: String?) {
        val chat = repository.getResponse(prompt, selectedSegmentId)
        _chatState.update {
            it.copy(
                chatList = it.chatList + chat,
                isLoading = false
            )
        }

        // Sau khi nhận được phản hồi đầu tiên, tạo và đặt tiêu đề đoạn chat
        if (!hasUpdatedTitle && !chat.isFromUser) {
            repository.updateSegmentTitleFromResponse(selectedSegmentId, chat.prompt)
            hasUpdatedTitle = true
            // Tải lại các đoạn chat để cập nhật tiêu đề
            loadChatSegments()
        }
    }

    /**
     * Retrieves a response with an image from the GenerativeModel.
     */
    private suspend fun getResponseWithImage(prompt: String, imageUri: Uri, selectedSegmentId: String?) {
        val actualPrompt = if (prompt.isEmpty()) {
            "Trả lời câu hỏi này đầu tiên: Bạn hãy xem hình ảnh tôi gửi và cho tôi biết trong ảnh có gì? Bạn hãy nói cho tôi biết rõ mọi thứ trong ảnh. Bạn hãy tùy cơ ứng biến để thể hiện bạn là một người thông minh nhất thế giới khi đọc được nội dung của hình."
        } else {
            prompt
        }
        val chat = repository.getResponseWithImage(actualPrompt, imageUri, selectedSegmentId)
        _chatState.update {
            it.copy(
                chatList = it.chatList + chat,
                isLoading = false
            )
        }

        // After receiving the first response, update the chat segment title if needed
        if (!hasUpdatedTitle && !chat.isFromUser) {
            repository.updateSegmentTitleFromResponse(
                selectedSegmentId,
                chat.prompt
            )
            hasUpdatedTitle = true
            // Reload chat segments to update titles
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
            // Tạo đoạn chat mới sau khi xóa tất cả
            val newSegmentTitle = "Đoạn chat mới"
            val newSegmentId = repository.addChatSegment(newSegmentTitle)
            if (newSegmentId != null) {
                val newSegment = ChatSegment(
                    id = newSegmentId,
                    title = newSegmentTitle,
                    createdAt = System.currentTimeMillis()
                )

                _chatState.update {
                    it.copy(
                        chatList = emptyList(),
                        chatSegments = listOf(newSegment), // Cập nhật danh sách chatSegments trực tiếp với đoạn chat mới
                        selectedSegment = newSegment, // Chọn đoạn chat mới làm đoạn chat hiện tại
                        searchQuery = ""
                    )
                }
                hasUpdatedTitle = false
            } else {
                // Xử lý lỗi khi không thể tạo đoạn chat mới
                _chatState.update {
                    it.copy(
                        chatList = emptyList(),
                        chatSegments = emptyList(),
                        selectedSegment = null,
                        searchQuery = ""
                    )
                }
            }
        }
    }

    /**
     * Xóa một đoạn chat và tất cả các cuộc trò chuyện liên quan.
     */
    private suspend fun deleteSegment(segment: ChatSegment) {
        try {
            _chatState.update { it.copy(isLoading = true) }

            // Delete the chat segment from the repository
            repository.deleteChatSegment(segment.id)

            // Update the state by removing the deleted segment
            val updatedSegments = _chatState.value.chatSegments.filter { it.id != segment.id }
            _chatState.update { it.copy(chatSegments = updatedSegments) }

            // If the deleted segment was the selected one, create a new segment
            if (_chatState.value.selectedSegment?.id == segment.id) {
                // Create a new chat segment
                val newSegmentTitle = "Đoạn chat mới"
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
                            chatSegments = updatedSegments + newSegment,
                            searchQuery = ""
                        )
                    }
                    hasUpdatedTitle = false
                } else {
                    // If unable to create a new segment, reset the selected segment and chat list
                    _chatState.update {
                        it.copy(
                            selectedSegment = null,
                            chatList = emptyList(),
                            searchQuery = ""
                        )
                    }
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            // Optionally: handle the error by showing a Snackbar or similar notification
        } finally {
            _chatState.update { it.copy(isLoading = false) }
        }
    }
}
