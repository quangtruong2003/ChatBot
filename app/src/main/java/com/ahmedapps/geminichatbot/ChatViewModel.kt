// ChatViewModel.kt
package com.ahmedapps.geminichatbot

import android.net.Uri
import androidx.compose.ui.platform.LocalFocusManager
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
import kotlinx.coroutines.Job

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository
) : ViewModel() {

    // Set để lưu trữ ID của các tin nhắn đã hiển thị hiệu ứng typing
    private val _typedMessagesIds = mutableSetOf<String>()
    val typedMessagesIds: Set<String> get() = _typedMessagesIds.toSet()
    
    // Hàm để đánh dấu một tin nhắn đã được hiển thị hiệu ứng typing
    fun markMessageAsTyped(messageId: String) {
        _typedMessagesIds.add(messageId)
        _chatState.update { it.copy(typedMessages = _typedMessagesIds.toSet()) }
    }
    
    // Kiểm tra xem một tin nhắn đã được hiển thị hiệu ứng typing chưa
    fun isMessageTyped(messageId: String): Boolean {
        return _chatState.value.typedMessages.contains(messageId)
    }

    val availableModels = listOf(
//        "gemini-1.5-flash-8b",
        "gemini-2.0-flash-exp",
        "gemini-2.0-flash-thinking-exp-01-21",
        //"gemini-exp-1206",
        "gemini-2.0-pro-exp-02-05",
        "gemini-2.5-pro-exp-03-25",
    )
    val modelDisplayNameMap = mapOf(
        //"gemini-1.5-flash-8b" to "AI Tốc độ",
        "gemini-2.0-flash-exp" to "AI Flash",
        "gemini-2.0-flash-thinking-exp-01-21" to "AI Thinking",
        //"gemini-exp-1206" to "AI Toàn năng",
        "gemini-2.0-pro-exp-02-05" to "AI Toàn năng",
        "gemini-2.5-pro-exp-03-25" to "AI Coding"
    )
    val modelIconMap = mapOf(
        //"AI Tốc độ" to R.drawable.ic_flash,
        "AI Flash" to R.drawable.ic_flash,
        "AI Lý luận" to R.drawable.ic_thandong,
        "AI Toàn năng" to R.drawable.ic_lyluan,
        "AI Coding" to R.drawable.ic_coder,
    )
    // Model đang được chọn (mặc định là model đầu tiên)
    private val _selectedModel = MutableStateFlow(availableModels[0])
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()


    // Hàm để chọn model
    fun selectModel(model: String) {
        _selectedModel.value = model
        // Cập nhật model trong AppModule
        viewModelScope.launch {
            repository.updateGenerativeModel(model)
        }
    }

    private val _chatState = MutableStateFlow(ChatState())
    val chatState: StateFlow<ChatState> = _chatState.asStateFlow()
    
    // Job hiện tại đang xử lý phản hồi
    private var currentResponseJob: Job? = null
    
    // Hàm để dừng phản hồi hiện tại
    fun stopCurrentResponse() {
        currentResponseJob?.cancel()
        
        // Thêm tin nhắn "Đã dừng" vào cuộc trò chuyện
        viewModelScope.launch {
            val chat = Chat.fromPrompt(
                prompt = "Đã dừng",
                imageUrl = null,
                isFromUser = false,
                isError = false, // Đặt isError = false để không hiển thị nền đỏ
                userId = repository.userId
            )
            
            val selectedSegmentId = _chatState.value.selectedSegment?.id
            repository.insertChat(chat, selectedSegmentId)
            
            // Đánh dấu tin nhắn này là đã hiển thị hiệu ứng typing
            _typedMessagesIds.add(chat.id)
            
            _chatState.update { 
                it.copy(
                    chatList = it.chatList + chat,
                    isLoading = false, 
                    isWaitingForResponse = false,
                    typedMessages = _typedMessagesIds.toSet()
                ) 
            }
        }
    }

    // Biến để theo dõi việc cập nhật tiêu đề
    private var hasUpdatedTitle = false

    // MutableStateFlow cho search query với debounce
    private val searchQueryFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            searchQueryFlow
                .debounce(300)
                .distinctUntilChanged()
                .collect { query ->
                    handleSearchQuery(query)
                }
        }

        viewModelScope.launch {
            // Load danh sách segment hiện có
            val segments = repository.getChatSegments()
            _chatState.update { it.copy(chatSegments = segments) }

            // Xóa các "Đoạn chat mới" rỗng
            deleteEmptyNewSegment()

            // Tạo luôn "Đoạn chat mới" khi vào lại app
            createNewSegment()
            
            // Đảm bảo ban đầu _typedMessagesIds đã được khởi tạo đầy đủ cho đoạn chat hiện tại
            _chatState.value.selectedSegment?.id?.let { segmentId ->
                val chats = repository.getChatHistoryForSegment(segmentId)
                // Đánh dấu tất cả tin nhắn từ bot là đã hiển thị hiệu ứng typing
                chats.forEach { chat ->
                    if (!chat.isFromUser) {
                        _typedMessagesIds.add(chat.id)
                    }
                }
                _chatState.update { it.copy(typedMessages = _typedMessagesIds.toSet()) }
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

                // Kiểm tra nếu query khớp chính xác với title
                if (normalizedTitle.contains(normalizedQuery)) {
                    return@filter true
                }

                // Tách title và query thành các từ
                val titleWords = normalizedTitle.split(" ")
                val queryWords = normalizedQuery.split(" ")

                // Kiểm tra xem tất cả các từ trong query có xuất hiện trong title không (không cần theo thứ tự)
                queryWords.all { queryWord -> titleWords.any { it.contains(queryWord) } }
            }
            _chatState.update { it.copy(chatSegments = results) }
        }
    }

    /**
     * Tải tất cả các đoạn hội thoại.
     */
    private fun loadChatSegments() {
        viewModelScope.launch {
            val segments = repository.getChatSegments()
            _chatState.update { it.copy(chatSegments = segments) }
        }
    }

    // Ngày
    fun getSortedChatSegments(): StateFlow<List<ChatSegment>> {
        return _chatState.map { it.chatSegments.sortedByDescending { segment -> segment.createdAt } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), _chatState.value.chatSegments)
    }


    /**
     * Tải lịch sử trò chuyện cho một đoạn cụ thể.
     */
    private fun loadChatHistoryForSegment(segmentId: String) {
        viewModelScope.launch {
            val chats = repository.getChatHistoryForSegment(segmentId)
            
            // Khi chuyển segment, xóa tất cả các ID đã đánh dấu trước đó
            _typedMessagesIds.clear()
            
            // Đánh dấu tất cả tin nhắn từ bot trong segment này là đã hiển thị hiệu ứng typing
            chats.forEach { chat ->
                if (!chat.isFromUser) {
                    _typedMessagesIds.add(chat.id)
                }
            }
            
            _chatState.update { 
                it.copy(
                    chatList = chats, 
                    isLoading = false,
                    typedMessages = _typedMessagesIds.toSet()
                ) 
            }
        }
    }

    /**
     * Làm mới trò chuyện bằng cách tạo một đoạn trò chuyện mới.
     */
    fun refreshChats() {
        viewModelScope.launch {
            try {
                _chatState.update { it.copy(isLoading = true) }

                val currentSelectedSegment = _chatState.value.selectedSegment
                val hasMessages = _chatState.value.chatList.isNotEmpty()

                // Kiểm tra nếu đang ở đoạn chat cũ (selectedSegment khác null và khác với "Đoạn chat mới")
                if (currentSelectedSegment != null && currentSelectedSegment.title != "Đoạn chat mới") {
                    // Lấy danh sách các segments mới nhất
                    val latestSegments = repository.getChatSegments()
                    if (latestSegments.isNotEmpty()) {
                        // Lấy segment mới nhất
                        val latestSegment = latestSegments.first()

                        // Kiểm tra nếu segment mới nhất là "Đoạn chat mới" và chưa có tin nhắn
                        if (latestSegment.title == "Đoạn chat mới" && repository.getChatHistoryForSegment(latestSegment.id).isEmpty()) {
                            // Chuyển về segment mới nhất
                            _chatState.update {
                                it.copy(
                                    selectedSegment = latestSegment,
                                    chatList = emptyList(), // Reset lại tin nhắn
                                    isLoading = false
                                )
                            }
                        } else {
                            // Nếu không có "Đoạn chat mới" rỗng, tạo mới
                            createNewSegment()
                            _chatState.update { it.copy(isLoading = false) }
                        }
                    } else {
                        // Nếu không có segment nào, tạo mới
                        createNewSegment()
                        _chatState.update { it.copy(isLoading = false) }
                    }
                } else {
                    // Nếu đang ở "Đoạn chat mới" hoặc chưa chọn segment, tạo mới
                    createNewSegment()
                    _chatState.update { it.copy(isLoading = false) }
                }
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
        val newSegmentTitle = "Đoạn chat mới"
        // Kiểm tra xem có segment nào với tiêu đề mặc định không
        val existingSegment = _chatState.value.chatSegments.find { it.title == newSegmentTitle }
        if (existingSegment != null) {
            // Nếu đã tồn tại, chọn segment đó và load lại lịch sử chat
            _chatState.update { current ->
                current.copy(
                    selectedSegment = existingSegment,
                    chatList = repository.getChatHistoryForSegment(existingSegment.id)
                )
            }
            return
        }
        // Nếu không có, tạo mới
        val newSegmentId = repository.addChatSegment(newSegmentTitle)
        if (newSegmentId != null) {
            val newSegment = ChatSegment(
                id = newSegmentId,
                title = newSegmentTitle,
                createdAt = System.currentTimeMillis()
            )
            _chatState.update { current ->
                current.copy(
                    selectedSegment = newSegment,
                    chatList = emptyList(),
                    chatSegments = current.chatSegments + newSegment,
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
                    // Hủy job cũ nếu có
                    currentResponseJob?.cancel()
                    
                    // Tạo job mới và lưu lại để có thể hủy
                    currentResponseJob = viewModelScope.launch {
                        deleteEmptyNewSegment()
                        _chatState.update { it.copy(isLoading = true, isWaitingForResponse = true) }
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
                _chatState.update { it.copy(searchQuery = event.query) }
                searchQueryFlow.value = event.query
            }
            is ChatUiEvent.SelectSegment -> {
                viewModelScope.launch {
                    hasUpdatedTitle = false
                    
                    // Xóa danh sách ID đã đánh dấu trước khi tải đoạn chat mới
                    _typedMessagesIds.clear()
                    
                    val chats = repository.getChatHistoryForSegment(event.segment.id)
                    
                    // Đánh dấu tất cả tin nhắn từ bot là đã hiển thị hiệu ứng typing
                    chats.forEach { chat ->
                        if (!chat.isFromUser) {
                            _typedMessagesIds.add(chat.id)
                        }
                    }
                    
                    _chatState.update {
                        it.copy(
                            selectedSegment = event.segment,
                            chatList = chats,
                            isLoading = false,
                            typedMessages = _typedMessagesIds.toSet()
                        )
                    }
                    searchQueryFlow.value = ""
                }
            }
            is ChatUiEvent.ClearSearch -> {
                _chatState.update { it.copy(searchQuery = "") }
                searchQueryFlow.value = ""
            }
            is ChatUiEvent.DeleteSegment -> {
                viewModelScope.launch {
                    deleteSegment(event.segment)
                }
            }
            is ChatUiEvent.RenameSegment -> {
                viewModelScope.launch {
                    // Cập nhật tiêu đề của đoạn chat dựa trên event mới
                    repository.updateSegmentTitle(event.segment.id, event.newTitle)
                    // Sau đó cập nhật lại danh sách đoạn chat trong state
                    val segments = repository.getChatSegments()
                    _chatState.update { it.copy(chatSegments = segments) }
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

    fun insertLocalUserChat(prompt: String) {
        viewModelScope.launch {
            val userId = repository.userId
            val chat = Chat.fromPrompt(
                prompt = prompt,
                imageUrl = null,
                isFromUser = true,
                isError = false,
                userId = userId
            )
            // Chỉ chèn vào Firestore, không gọi API
            repository.insertChat(chat, chatState.value.selectedSegment?.id)
            // Cập nhật lại UI
            _chatState.update {
                it.copy(chatList = it.chatList + chat)
            }
        }
    }


    /**
     * Lấy phản hồi từ GenerativeModel không kèm hình ảnh.
     * Bổ sung kiểm tra phản hồi tùy chỉnh trước khi gọi API.
     */
    suspend fun getResponse(prompt: String, selectedSegmentId: String?) {
        // Hủy job hiện tại nếu đang chạy
        currentResponseJob?.cancel()
        
        // Gán job mới
        currentResponseJob = viewModelScope.launch {
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
                        isLoading = false,
                        isWaitingForResponse = false
                    )
                }
                return@launch
            }

            try {
                val chat = repository.getResponse(prompt, selectedSegmentId)
                val currentSegment = chatState.value.selectedSegment
                
                // Đảm bảo không đánh dấu tin nhắn mới là đã typed
                _typedMessagesIds.remove(chat.id)
                
                _chatState.update {
                    it.copy(
                        chatList = it.chatList + chat,
                        isLoading = false,
                        isWaitingForResponse = false,
                        typedMessages = _typedMessagesIds.toSet()
                    )
                }

                // Chỉ cập nhật tiêu đề nếu đoạn chat chưa có tiêu đề và không phải tin nhắn của người dùng
                if (!hasUpdatedTitle && !chat.isFromUser && currentSegment?.title == "Đoạn chat mới") {
                    repository.updateSegmentTitleFromResponse(selectedSegmentId, chat.prompt)
                    hasUpdatedTitle = true
                    loadChatSegments() // Cập nhật lại danh sách đoạn chat
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _chatState.update { it.copy(isLoading = false, isWaitingForResponse = false) }
            }
        }
    }

     /**
     * Lấy phản hồi từ GenerativeModel kèm hình ảnh.
     * Bổ sung kiểm tra phản hồi tùy chỉnh trước khi gọi API.
     */
    private suspend fun getResponseWithImage(prompt: String, imageUri: Uri, selectedSegmentId: String?) {
        // Hủy job hiện tại nếu đang chạy
        currentResponseJob?.cancel()
        
        // Gán job mới
        currentResponseJob = viewModelScope.launch {
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
                        isLoading = false,
                        isWaitingForResponse = false
                    )
                }
                return@launch
            }

            // Nếu không có phản hồi tùy chỉnh, tiếp tục gọi API như bình thường
            try {
                val actualPrompt = if (prompt.isEmpty()) {
                    "Bạn hãy xem hình ảnh tôi gửi và cho tôi biết trong ảnh có gì? Bạn hãy nói cho tôi biết rõ mọi thứ trong ảnh. Bạn hãy tùy cơ ứng biến để thể hiện bạn là một người thông minh nhất thế giới khi đọc được nội dung của hình và đoán được mong muốn của người dùng về bức ảnh. Hãy tận dụng câu vai trò đã giao để hoàn thành câu trả lời"
                } else {
                    prompt
                }
                val chat = repository.getResponseWithImage(actualPrompt, imageUri, selectedSegmentId)
                val currentSegment = chatState.value.selectedSegment
                
                // Đảm bảo không đánh dấu tin nhắn mới là đã typed
                _typedMessagesIds.remove(chat.id)
                
                _chatState.update {
                    it.copy(
                        chatList = it.chatList + chat,
                        isLoading = false,
                        isWaitingForResponse = false,
                        typedMessages = _typedMessagesIds.toSet()
                    )
                }

                // Chỉ cập nhật tiêu đề nếu đoạn chat chưa có tiêu đề và không phải tin nhắn của người dùng
                if (!hasUpdatedTitle && !chat.isFromUser && currentSegment?.title == "Đoạn chat mới") {
                    repository.updateSegmentTitleFromResponse(selectedSegmentId, chat.prompt)
                    hasUpdatedTitle = true
                    loadChatSegments() // Cập nhật lại danh sách đoạn chat
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _chatState.update { it.copy(isLoading = false, isWaitingForResponse = false) }
            }
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
                createNewSegment()
            }

        } catch (e: Exception) {
            e.printStackTrace()
            // Optionally: handle the error by showing a Snackbar or similar notification
        } finally {
            _chatState.update { it.copy(isLoading = false) }
        }
    }
    /**
     * Xóa "Đoạn chat mới" nếu nó không có tin nhắn khi chọn đoạn chat khác.
     */
    private suspend fun deleteEmptyNewSegment() {
        val currentSegments = _chatState.value.chatSegments
        val selectedSegment = _chatState.value.selectedSegment

        // Tìm "Đoạn chat mới" chưa có tin nhắn
        val newSegmentToDelete = currentSegments.find { it.title == "Đoạn chat mới" && it.id != selectedSegment?.id }

        if (newSegmentToDelete != null) {
            val segmentIdToDelete = newSegmentToDelete.id
            val isSegmentEmpty = repository.getChatHistoryForSegment(segmentIdToDelete).isEmpty()

            if (isSegmentEmpty) {
                repository.deleteChatSegment(segmentIdToDelete)
                // Cập nhật lại danh sách chatSegments trong StateFlow
                _chatState.update { currentState ->
                    currentState.copy(
                        chatSegments = currentState.chatSegments.filter { it.id != segmentIdToDelete }
                    )
                }
            }
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
