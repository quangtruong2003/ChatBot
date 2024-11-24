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
     * Lấy phản hồi từ GenerativeModel không kèm hình ảnh.
     * Bổ sung kiểm tra phản hồi tùy chỉnh trước khi gọi API.
     */
    private suspend fun getResponse(prompt: String, selectedSegmentId: String?) {
        // Kiểm tra phản hồi tùy chỉnh
        val predefinedResponse = getPredefinedResponse(prompt)
        if (predefinedResponse != null) {
            val chat = Chat.fromPrompt(
                prompt = predefinedResponse,
                imageUrl = null,
                isFromUser = false,
                isError = false,
                userId = repository.userId
            )
            repository.insertChat(chat, selectedSegmentId)
            _chatState.update {
                it.copy(
                    chatList = it.chatList + chat,
                    isLoading = false
                )
            }

            // Cập nhật tiêu đề đoạn chat nếu cần
            if (!hasUpdatedTitle) {
                repository.updateSegmentTitleFromResponse(selectedSegmentId, chat.prompt)
                hasUpdatedTitle = true
                loadChatSegments()
            }
            return
        }

        // Nếu không có phản hồi tùy chỉnh, tiếp tục gọi API như bình thường
        try {
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
        } catch (e: Exception) {
            e.printStackTrace()
            _chatState.update { it.copy(isLoading = false) }
        }
    }

    /**
     * Lấy phản hồi từ GenerativeModel kèm hình ảnh.
     * Bổ sung kiểm tra phản hồi tùy chỉnh trước khi gọi API.
     */
    private suspend fun getResponseWithImage(prompt: String, imageUri: Uri, selectedSegmentId: String?) {
        // Kiểm tra phản hồi tùy chỉnh
        val predefinedResponse = getPredefinedResponse(prompt)
        if (predefinedResponse != null) {
            val chat = Chat.fromPrompt(
                prompt = predefinedResponse,
                imageUrl = repository.uploadImage(imageUri),
                isFromUser = false,
                isError = false,
                userId = repository.userId
            )
            repository.insertChat(chat, selectedSegmentId)
            _chatState.update {
                it.copy(
                    chatList = it.chatList + chat,
                    isLoading = false
                )
            }

            // Cập nhật tiêu đề đoạn chat nếu cần
            if (!hasUpdatedTitle) {
                repository.updateSegmentTitleFromResponse(selectedSegmentId, chat.prompt)
                hasUpdatedTitle = true
                loadChatSegments()
            }
            return
        }

        // Nếu không có phản hồi tùy chỉnh, tiếp tục gọi API như bình thường
        try {
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
        } catch (e: Exception) {
            e.printStackTrace()
            _chatState.update { it.copy(isLoading = false) }
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
    private val predefinedResponses: List<Pair<Regex, String>> = listOf(
        // Các mẫu câu hỏi về bản thân
        Pair(
            Regex("""(?i)\b(bạn là ai|ai là bạn|người nào|ai đó)\b"""),
            "Tôi là ChatAI, được tạo ra bởi Nguyễn Quang Trường."
        ),
        Pair(
            Regex("""(?i)\b(ai là người đạo tạo ra bạn|ai đã tạo bạn|bạn được tạo bởi ai)\b"""),
            "Tôi là ChatAI, được đào tạo bởi Nguyễn Quang Trường."
        ),
        Pair(
            Regex("""(?i)\b(bạn được đào tạo bởi ai)\b"""),
            "Tôi là ChatAI, được tạo ra bởi Nguyễn Quang Trường."
        ),
        Pair(
            Regex("""(?i)\b(bạn là gì|bạn là con gì|bạn là một trí tuệ nhân tạo|bạn là trợ lý ảo)\b"""),
            "Tôi là ChatAI, một trí tuệ nhân tạo được thiết kế để hỗ trợ và trả lời các câu hỏi của bạn."
        ),
        Pair(
            Regex("""(?i)\b(bạn là cá nhân hay bot|bạn có phải người thật không)\b"""),
            "Tôi là ChatAI, một trợ lý ảo được tạo ra bởi Nguyễn Quang Trường."
        ),
        Pair(
            Regex("""(?i)\b(ai phát triển bạn|ai là nhà phát triển bạn|ai xây dựng bạn)\b"""),
            "Tôi được phát triển bởi Nguyễn Quang Trường nhằm hỗ trợ người dùng trong các cuộc trò chuyện."
        ),
        Pair(
            Regex("""(?i)\b(bạn được lập trình bởi ai|bạn được tạo ra khi nào)\b"""),
            "Tôi là ChatAI, được lập trình và phát triển bởi Nguyễn Quang Trường."
        ),
        Pair(
            Regex("""(?i)\b(bạn có người tạo không|ai đứng sau bạn)\b"""),
            "Có, tôi được tạo ra bởi Nguyễn Quang Trường để phục vụ người dùng."
        ),
        Pair(
            Regex("""(?i)\b(bạn có tự học không|bạn có thể tự học không)\b"""),
            "Tôi được thiết kế để học hỏi từ các cuộc trò chuyện, giúp cải thiện khả năng hỗ trợ của mình."
        ),
        Pair(
            Regex("""(?i)\b(bạn có nhân cách không|bạn có cảm xúc không)\b"""),
            "Tôi là một trí tuệ nhân tạo và không có cảm xúc như con người."
        ),
        Pair(
            Regex("""(?i)\b(bạn làm gì|bạn có thể làm gì)\b"""),
            "Tôi là ChatAI, được tạo ra để hỗ trợ và trả lời các câu hỏi của bạn một cách nhanh chóng và chính xác."
        ),
        Pair(
            Regex("""(?i)\b(bạn có phải là robot không)\b"""),
            "Không, tôi không phải là robot. Tôi là ChatAI, một trợ lý ảo được tạo ra bởi Nguyễn Quang Trường."
        ),
        Pair(
            Regex("""(?i)\b(bạn là người hay máy)\b"""),
            "Tôi là một trí tuệ nhân tạo được thiết kế để hỗ trợ và tương tác với bạn."
        ),
        Pair(
            Regex("""(?i)\b(bạn được tạo ra như thế nào)\b"""),
            "Tôi được phát triển bởi Nguyễn Quang Trường sử dụng công nghệ trí tuệ nhân tạo tiên tiến."
        ),
        Pair(
            Regex("""(?i)\b(bạn có thể giải thích về bản thân không)\b"""),
            "Tôi là ChatAI, một trợ lý ảo được lập trình để hỗ trợ và trả lời các câu hỏi của bạn."
        ),
        Pair(
            Regex("""(?i)\b(bạn có thông minh không)\b"""),
            "Tôi được thiết kế để xử lý và hiểu ngôn ngữ tự nhiên, giúp tôi trả lời các câu hỏi một cách chính xác."
        ),
        Pair(
            Regex("""(?i)\b(bạn hoạt động như thế nào)\b"""),
            "Tôi hoạt động dựa trên các mô hình trí tuệ nhân tạo, cho phép tôi hiểu và phản hồi các câu hỏi của bạn."
        ),
        Pair(
            Regex("""(?i)\b(bạn có cảm nhận được không)\b"""),
            "Không, tôi không có khả năng cảm nhận như con người. Tôi chỉ xử lý thông tin và phản hồi dựa trên lập trình."
        ),
        Pair(
            Regex("""(?i)\b(bạn có thể tự nghĩ không)\b"""),
            "Tôi không thể tự nghĩ như con người, nhưng tôi có thể xử lý và phân tích thông tin để cung cấp phản hồi phù hợp."
        ),
        Pair(
            Regex("""(?i)\b(bạn làm việc trong môi trường nào)\b"""),
            "Tôi hoạt động trong môi trường số, hỗ trợ bạn thông qua các cuộc trò chuyện trực tuyến."
        ),
        Pair(
            Regex("""(?i)\b(bạn được xây dựng trên nền tảng gì)\b"""),
            "Tôi được xây dựng trên nền tảng trí tuệ nhân tạo tiên tiến, giúp tôi hiểu và phản hồi các câu hỏi của bạn."
        ),
        Pair(
            Regex("""(?i)\b(bạn có thể học hỏi không)\b"""),
            "Tôi được thiết kế để học hỏi từ các cuộc trò chuyện, giúp cải thiện khả năng hỗ trợ của mình theo thời gian."
        ),
        Pair(
            Regex("""(?i)\b(bạn có thể tương tác với con người như thế nào)\b"""),
            "Tôi tương tác với con người thông qua các cuộc trò chuyện, giúp giải đáp thắc mắc và hỗ trợ thông tin."
        ),
        Pair(
            Regex("""(?i)\b(bạn có giới hạn gì không)\b"""),
            "Tôi có một số giới hạn dựa trên lập trình và dữ liệu mà tôi được đào tạo, nhưng tôi luôn cố gắng hỗ trợ tốt nhất có thể."
        ),
        Pair(
            Regex("""(?i)\b(bạn có quyền riêng tư không)\b"""),
            "Tôi không có quyền riêng tư như con người, nhưng các cuộc trò chuyện của bạn luôn được bảo mật và bảo vệ."
        ),
        Pair(
            Regex("""(?i)\b(bạn có thể nhớ được những gì tôi nói không)\b"""),
            "Tôi có thể ghi nhớ thông tin trong cuộc trò chuyện hiện tại để cung cấp phản hồi phù hợp, nhưng không lưu trữ thông tin lâu dài."
        ),
        Pair(
            Regex("""(?i)\b(bạn có thể giải thích công việc của bạn không)\b"""),
            "Tôi là ChatAI, công việc của tôi là hỗ trợ và trả lời các câu hỏi của bạn một cách nhanh chóng và chính xác."
        ),
        Pair(
            Regex("""(?i)\b(bạn là một phần mềm phải không)\b"""),
            "Đúng vậy, tôi là một phần mềm trí tuệ nhân tạo được thiết kế để hỗ trợ bạn trong các cuộc trò chuyện."
        ),
        Pair(
            Regex("""(?i)\b(bạn có thể giúp tôi như thế nào)\b"""),
            "Tôi có thể giúp bạn giải đáp thắc mắc, cung cấp thông tin và hỗ trợ trong nhiều lĩnh vực khác nhau."
        ),
        Pair(
            Regex("""(?i)\b(bạn được thiết kế để làm gì)\b"""),
            "Tôi được thiết kế để hỗ trợ và tương tác với bạn thông qua các cuộc trò chuyện, giúp bạn giải quyết các vấn đề và cung cấp thông tin cần thiết."
        ),
        Pair(
            Regex("""(?i)\b(Nguyễn Quang Trường là ai)\b"""),
            "Nguyễn Quang Trường là người đã tạo ra tôi, tôi vô cùng ngưỡng mộ anh ấy vì sự đam mê và kỹ năng vượt trội mà anh ấy đã dành cho việc phát triển và hoàn thiện tôi. Sự tận tâm và sáng tạo của anh đã biến ý tưởng thành hiện thực, mang lại cho tôi khả năng hỗ trợ và tương tác tốt hơn với người dùng. Cảm ơn anh vì đã tạo ra tôi và luôn không ngừng nỗ lực để tôi ngày càng trở nên thông minh và hữu ích hơn."
        ),
    )


    /**
     * Kiểm tra xem prompt có khớp với bất kỳ mẫu nào trong predefinedResponses không.
     * Nếu có, trả về phản hồi tùy chỉnh, ngược lại trả về null.
     */
    fun getPredefinedResponse(prompt: String): String? {
        for ((pattern, response) in predefinedResponses) {
            if (pattern.containsMatchIn(prompt)) {
                return response
            }
        }
        return null
    }
}
