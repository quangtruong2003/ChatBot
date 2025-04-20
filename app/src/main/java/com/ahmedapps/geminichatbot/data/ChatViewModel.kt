// ChatViewModel.kt
package com.ahmedapps.geminichatbot.data

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Job
import android.content.Context
import com.ahmedapps.geminichatbot.services.PDFProcessingService
import android.util.Log
import android.provider.OpenableColumns
import com.ahmedapps.geminichatbot.R
import com.ahmedapps.geminichatbot.drawer.right.ApiSettingsState
import com.ahmedapps.geminichatbot.drawer.right.SafetyThreshold
import com.ahmedapps.geminichatbot.responsePre.PredefinedResponses
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.ahmedapps.geminichatbot.utils.FileUtils
import com.google.ai.client.generativeai.type.HarmCategory

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {


        // Sử dụng predefinedResponses từ file mới
    private val predefinedResponses = PredefinedResponses.responses
    
    // Thêm hàm để lấy repository từ bên ngoài
    fun getChatRepository(): ChatRepository {
        return repository
    }

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
        "gemini-2.5-flash-preview-04-17",
        //"gemini-exp-1206",
        "gemini-2.0-pro-exp-02-05",
        "gemini-2.5-pro-exp-03-25",
    )
    val modelDisplayNameMap = mapOf(
        //"gemini-1.5-flash-8b" to "AI Tốc độ",
        "gemini-2.0-flash-exp" to "AI Flash",
        "gemini-2.0-flash-thinking-exp-01-21" to "AI Thinking",
        "gemini-2.5-flash-preview-04-17" to "AI Flash Thinking",
        //"gemini-exp-1206" to "AI Toàn năng",
        "gemini-2.0-pro-exp-02-05" to "AI Toàn năng",
        "gemini-2.5-pro-exp-03-25" to "AI Coding",
    )
    val modelIconMap = mapOf(
        //"AI Tốc độ" to R.drawable.ic_flash,
        "AI Flash" to R.drawable.ic_flash,
        "AI Lý luận" to R.drawable.ic_thandong,
        "AI Flash Thinking" to R.drawable.ic_flash,
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
        if (currentResponseJob?.isActive == true) {
            // Trường hợp 1: Job đang chạy (ví dụ: đang chờ API hoặc đang fetch)
            currentResponseJob?.cancel()
            currentResponseJob = null // Xóa tham chiếu job

            viewModelScope.launch {
                val currentState = _chatState.value
                val segmentId = currentState.selectedSegment?.id
                val currentChatList = currentState.chatList
                val lastChat = currentChatList.lastOrNull()

                // Kiểm tra xem tin nhắn cuối cùng có phải từ model và đang được tạo/typing không
                // (Logic này chủ yếu xử lý trường hợp "Đang suy nghĩ...")
                if (lastChat != null && !lastChat.isFromUser && !_typedMessagesIds.contains(lastChat.id)) {
                    // Tin nhắn cuối là phản hồi của model đang typing (hoặc "Đang suy nghĩ...")
                    val updatedChat = lastChat.copy(
                        prompt = lastChat.prompt.takeIf { it.isNotEmpty() } ?: "Đã dừng", // Giữ prompt nếu có, nếu không thì "Đã dừng"
                        isError = false // Đảm bảo không bị đánh dấu là lỗi
                    )

                    // Cập nhật trong repository
                    if (segmentId != null) {
                        try {
                            repository.updateChat(updatedChat, segmentId)
                        } catch (e: Exception) {
                            Log.e("ChatViewModel", "Không thể cập nhật chat đã dừng trong repository: ${e.message}")
                        }
                    }

                    // Cập nhật state: thay thế chat cuối, đánh dấu đã typed, reset cờ loading
                    _typedMessagesIds.add(updatedChat.id) // Đánh dấu đã typed ngay lập tức
                    _chatState.update {
                        it.copy(
                            chatList = currentChatList.dropLast(1) + updatedChat,
                            isLoading = false,
                            isWaitingForResponse = false,
                            typedMessages = _typedMessagesIds.toSet(), // Đảm bảo state có set mới nhất
                            stopTypingMessageId = null // Reset cờ dừng typing
                        )
                    }
                } else {
                    // Tin nhắn cuối là của user, đã typed, hoặc danh sách rỗng
                    // Thêm một tin nhắn "Đã dừng" mới
                    val stopChat = Chat.fromPrompt(
                        prompt = "Đã dừng",
                        imageUrl = null,
                        isFromUser = false,
                        isError = false,
                        userId = repository.userId
                    )

                    // Chèn tin nhắn mới
                    if (segmentId != null) {
                        repository.insertChat(stopChat, segmentId)
                    }

                    // Đánh dấu tin nhắn dừng mới là đã typed ngay lập tức
                    _typedMessagesIds.add(stopChat.id)

                    _chatState.update {
                        it.copy(
                            chatList = it.chatList + stopChat,
                            isLoading = false,
                            isWaitingForResponse = false,
                            typedMessages = _typedMessagesIds.toSet(), // Cập nhật state với id tin nhắn đã typed mới
                            stopTypingMessageId = null // Reset cờ dừng typing
                        )
                    }
                }
            }
        } else {
            // Trường hợp 2: Job không chạy (response đã nhận xong, đang typing animation)
            viewModelScope.launch {
                val currentState = _chatState.value
                val currentChatList = currentState.chatList

                // Tìm tin nhắn cuối cùng của model mà *chưa* được đánh dấu là đã typed xong
                val lastTypingModelMessage = currentChatList.lastOrNull { !it.isFromUser && !_typedMessagesIds.contains(it.id) }

                if (lastTypingModelMessage != null) {
                    // Nếu tìm thấy tin nhắn đang typing -> yêu cầu dừng animation của nó
                    _typedMessagesIds.add(lastTypingModelMessage.id) // Đánh dấu là đã typed (để animation không chạy lại)
                    _chatState.update {
                        it.copy(
                            isLoading = false, // Đảm bảo dừng loading
                            isWaitingForResponse = false, // Đảm bảo dừng chờ đợi
                            typedMessages = _typedMessagesIds.toSet(), // Cập nhật danh sách typed
                            stopTypingMessageId = lastTypingModelMessage.id // *** Báo hiệu cho UI dừng typing tin nhắn này ***
                        )
                    }
                    // Reset stopTypingMessageId sau một khoảng trễ nhỏ để UI kịp xử lý
                    // Hoặc có thể để UI tự reset sau khi xử lý xong
                    // Tạm thời để reset ở đây
                     kotlinx.coroutines.delay(100) // Chờ UI xử lý
                     _chatState.update { it.copy(stopTypingMessageId = null) }

                } else {
                    // Không có tin nhắn nào đang typing (ví dụ: user bấm stop khi không có gì đang xảy ra)
                    // Có thể không làm gì, hoặc thêm tin nhắn "Đã dừng" như trường hợp trên
                    val segmentId = currentState.selectedSegment?.id
                    val stopChat = Chat.fromPrompt(
                        prompt = "Đã dừng",
                        isFromUser = false,
                        isError = false,
                        userId = repository.userId
                    )
                    if (segmentId != null) {
                        repository.insertChat(stopChat, segmentId)
                    }
                     _typedMessagesIds.add(stopChat.id)
                    _chatState.update {
                        it.copy(
                            chatList = it.chatList + stopChat,
                            isLoading = false,
                            isWaitingForResponse = false,
                            typedMessages = _typedMessagesIds.toSet(),
                            stopTypingMessageId = null
                        )
                    }
                }
            }
            // Reset currentResponseJob nếu nó không active nhưng vẫn còn tham chiếu
            if (currentResponseJob?.isActive == false) {
                currentResponseJob = null
            }
        }
    }

    // Biến để theo dõi việc cập nhật tiêu đề
    private var hasUpdatedTitle = false

    // MutableStateFlow cho search query với debounce
    private val searchQueryFlow = MutableStateFlow("")

    // Thêm trạng thái theo dõi việc xử lý hình ảnh
    private val _isImageProcessing = MutableStateFlow(false)
    val isImageProcessing = _isImageProcessing.asStateFlow()

    // Cache lưu trữ Uri của files để tái sử dụng khi regenerate
    private val fileUriCache = mutableMapOf<String, Uri>()

    // State for processing files
    private val _isProcessingFile = MutableStateFlow(false)
    val isProcessingFile: StateFlow<Boolean> = _isProcessingFile

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    // Thêm biến lưu trữ URL hình ảnh tạm thời ngoài class
    private val imageUrlCache = mutableMapOf<String, String>()

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
            _chatState.update { it.copy(isLoading = true) } // Có thể thêm cờ loading riêng cho segments nếu muốn
            try {
                val segments = repository.getChatSegments()
                _chatState.update { it.copy(chatSegments = segments, isLoading = false) } // Tắt loading sau khi cập nhật
                Log.d("ChatViewModel", "Chat segments reloaded. Count: ${segments.size}")
            } catch (e: Exception) {
                 Log.e("ChatViewModel", "Error reloading chat segments", e)
                 _chatState.update { it.copy(isLoading = false) } // Đảm bảo tắt loading nếu lỗi
            }
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
                
                // Xóa các "Đoạn chat mới" rỗng trước
                deleteEmptyNewSegment()
                
                // Tạo một đoạn chat mới và chuyển đến đó
                val newSegmentTitle = "Đoạn chat mới"
                val newSegmentId = repository.addChatSegment(newSegmentTitle)
                
                if (newSegmentId != null) {
                    val newSegment = ChatSegment(
                        id = newSegmentId,
                        title = newSegmentTitle,
                        createdAt = System.currentTimeMillis()
                    )
                    
                    // Cập nhật state với segment mới và danh sách chat rỗng
                    _chatState.update { currentState ->
                        currentState.copy(
                            selectedSegment = newSegment,
                            chatList = emptyList(),
                            chatSegments = currentState.chatSegments + newSegment,
                            isLoading = false
                        )
                    }
                    hasUpdatedTitle = false
                } else {
                    _chatState.update { it.copy(isLoading = false) }
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                _chatState.update { it.copy(isLoading = false) }
            }
            _isImageProcessing.value = false
        }
    }

    /**
     * Tạo một đoạn chat mới.
     */
    private suspend fun createNewSegment() {
        val newSegmentTitle = "Đoạn chat mới"
        // Tạo mới segment mà không quan tâm đến việc đã tồn tại segment có tên giống hay không
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
            Log.d("ChatViewModel", "Tạo segment mới với ID: $newSegmentId")
        }
    }

    /*
    * Lấy phản hồi từ GenerativeModel không kèm hình ảnh.
    * Bổ sung kiểm tra phản hồi tùy chỉnh trước khi gọi API.
    */
    suspend fun getResponse(prompt: String, selectedSegmentId: String?) {
        currentResponseJob?.cancel()
        currentResponseJob = viewModelScope.launch {
            _chatState.update { it.copy(isLoading = true, isWaitingForResponse = true) }
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
                markMessageAsTyped(chat.id) // Đánh dấu đã typed
                return@launch
            }

            try {
                val segmentBeforeResponse = _chatState.value.selectedSegment
                val wasDefaultTitle = segmentBeforeResponse?.title == "Đoạn chat mới"

                // Truyền apiSettingsState.value vào repository.getResponse
                Log.d("APISettingsDebug", "ViewModel calling repository.getResponse with settings: ${_apiSettingsState.value}") // Log trước khi gọi
                val chat = repository.getResponse(prompt, selectedSegmentId, _apiSettingsState.value)

                 _chatState.update {
                    it.copy(
                        chatList = it.chatList + chat,
                        isLoading = false,
                        isWaitingForResponse = false,
                        typedMessages = _typedMessagesIds.toSet() - chat.id // Không đánh dấu typed trước khi animation
                    )
                }

                if (wasDefaultTitle && !chat.isError) {
                    loadChatSegments()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _chatState.update { it.copy(isLoading = false, isWaitingForResponse = false) }
            }
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
                sendPrompt(event.prompt, event.imageUri)
                                            }
            
            is ChatUiEvent.SendAudioPrompt -> {
                sendAudioPrompt(event.prompt, event.audioUri)
            }
            
            is ChatUiEvent.UpdatePrompt -> {
                 _chatState.update { it.copy(prompt = event.newPrompt) }
            }
            
            is ChatUiEvent.UpdateFileName -> {
                _chatState.update { it.copy(fileName = event.fileName) }
            }
            
            is ChatUiEvent.OnAudioRecorded -> {
                // Xử lý file âm thanh đã ghi âm
                _chatState.update { 
                    it.copy(
                        fileUri = event.uri,
                        fileName = event.fileName,
                        isFileMessage = true, // Đánh dấu đây là tin nhắn file
                        isAudioMessage = true // Đánh dấu đây là tin nhắn âm thanh
                    )
                }
            }
            
            is ChatUiEvent.OnAudioFileSelected -> {
                // Xử lý khi người dùng chọn file âm thanh từ file explorer
                val fileName = getFileNameFromUri(context, event.uri) ?: "Audio file"
                _chatState.update { 
                    it.copy(
                        fileUri = event.uri,
                        fileName = fileName,
                        isFileMessage = true,
                        isAudioMessage = true // Đánh dấu đây là tin nhắn âm thanh
                    )
                }
            }
            
            is ChatUiEvent.OnImageSelected -> {
                // Lấy Uri ảnh cũ để xóa nếu cần
                val oldImageUri = _chatState.value.imageUri
                
                // Cập nhật state với Uri mới và đánh dấu là đang xử lý ảnh
                _chatState.update { it.copy(
                    imageUri = event.uri,
                    isImageProcessing = true
                )}
                
                // Xử lý upload ảnh mới trong coroutine riêng biệt
                viewModelScope.launch {
                    try {
                        // Xóa cache ảnh cũ nếu có
                        if (oldImageUri != null) {
                            oldImageUri.toString().let { key -> imageUrlCache.remove(key) }
                        }
                        
                        // Thử upload ảnh mới, kết quả sẽ được cache trong hàm gọi
                        val imageUrl = repository.uploadImage(event.uri)
                        
                        // Cache lại kết quả upload để sử dụng sau này
                        if (imageUrl != null) {
                            imageUrlCache[event.uri.toString()] = imageUrl
                        }
                        
                        // Cập nhật state để không còn processing nữa
                            _chatState.update { it.copy(isImageProcessing = false) }
                        
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Error uploading image: ${e.message}")
                            _chatState.update { it.copy(isImageProcessing = false) }
                        }
                    }
                }
            
            is ChatUiEvent.SearchSegments -> {
                _chatState.update { it.copy(searchQuery = event.query) }
                searchQueryFlow.value = event.query
            }
            
            is ChatUiEvent.ClearSearch -> {
                _chatState.update { it.copy(searchQuery = "") }
                searchQueryFlow.value = ""
            }
            
            is ChatUiEvent.SelectSegment -> {
                selectSegment(event.segment)
            }

            is ChatUiEvent.DeleteSegment -> {
                handleDeleteSegment(event.segment)
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
            is ChatUiEvent.RefreshChats -> {
                refreshChats()
            }
            is ChatUiEvent.StopResponse -> {
                stopCurrentResponse()
            }
            is ChatUiEvent.OnFileSelected -> {
                val uri = event.uri
                val fileName = getFileNameFromUri(context, uri) ?: "File không xác định"
                
                // Xác định loại file
                val isAudioFile = FileUtils.isAudioFile(context, uri)
                val isTextFile = FileUtils.isTextFile(context, uri)
                
                // Lưu vào cache
                fileUriCache[fileName] = uri
                
                // Cập nhật state với thông tin phù hợp
                _chatState.update { 
                    it.copy(
                        fileUri = uri,
                        fileName = fileName,
                        isFileMessage = true,
                        isFileUploading = false,
                        isAudioMessage = isAudioFile
                    )
                }
                
                Log.d("ChatViewModel", "File selected: $fileName, isAudioFile: $isAudioFile, isTextFile: $isTextFile")
            }
            is ChatUiEvent.RemoveFile -> {
                _chatState.update { it.copy(fileUri = null, fileName = null) }
            }
            is ChatUiEvent.DeleteChat -> {
                deleteChat(event.chatId)
            }
            is ChatUiEvent.EditChat -> {
                startEditingChat(
                    event.chatId,
                    event.message,
                    event.timestamp,
                    event.imageUrl,
                    event.fileName
                )
            }
            is ChatUiEvent.CancelEdit -> {
                cancelEditing()
            }
            is ChatUiEvent.RegenerateResponse -> {
                viewModelScope.launch {
                    // Đánh dấu là đang loading và chờ phản hồi
                    _chatState.update { it.copy(isLoading = true, isWaitingForResponse = true) }
                    
                    try {
                        val userPrompt = event.userPrompt
                        val responseId = event.responseId
                        val segmentId = _chatState.value.selectedSegment?.id
                        val imageUrl = event.imageUrl
                        val fileName = event.fileName
                        val userMessageTimestamp = event.timestamp
                        
                        // Log thông tin
                        Log.d("ChatViewModel", "Regenerate với userPrompt: $userPrompt, responseId: $responseId, " +
                              "imageUrl: $imageUrl, fileName: $fileName, timestamp: $userMessageTimestamp")
                        
                        // Lấy ID tin nhắn người dùng để loại trừ khỏi việc xóa
                        val userChatId = _chatState.value.chatList
                            .find { it.timestamp == userMessageTimestamp && it.isFromUser }?.id
                        
                        if (segmentId != null && userChatId != null) {
                            // Xóa tất cả tin nhắn sau timestamp của tin nhắn người dùng, nhưng không xóa tin nhắn người dùng
                            repository.deleteMessagesAfterTimestamp(segmentId, userMessageTimestamp, excludeIds = listOf(userChatId))
                            
                            // Cập nhật UI trước để xóa các tin nhắn đã xóa
                            val updatedChatList = _chatState.value.chatList.filter { 
                                it.timestamp <= userMessageTimestamp || it.id == userChatId
                            }
                            _chatState.update { it.copy(chatList = updatedChatList) }
                            
                            // Tạo response mới dựa trên dữ liệu
                            val response = when {
                                imageUrl != null -> {
                                    if (fileName != null) {
                                        // File đính kèm
                                        repository.regenerateResponseWithFile(userPrompt, imageUrl, fileName, segmentId, _apiSettingsState.value)
                                    } else {
                                        // Hình ảnh
                                        repository.regenerateResponseWithImage(userPrompt, imageUrl, segmentId, _apiSettingsState.value)
                                    }
                                }
                                else -> {
                                    // Tin nhắn văn bản
                                    repository.getResponse(userPrompt, segmentId, _apiSettingsState.value)
                                }
                            }
                            
                            // Cập nhật danh sách chat khi nhận được phản hồi
                            _chatState.update { 
                                it.copy(
                                    chatList = it.chatList + response,
                                    isLoading = false,
                                    isWaitingForResponse = false
                                )
                            }
                        } else {
                            Log.e("ChatViewModel", "Không tìm thấy segment hoặc tin nhắn người dùng")
                            _chatState.update { it.copy(isLoading = false, isWaitingForResponse = false) }
                            insertModelChat("Không thể tạo lại phản hồi: không tìm thấy segment hoặc tin nhắn người dùng", true)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        _chatState.update { it.copy(isLoading = false, isWaitingForResponse = false) }
                        insertModelChat("Lỗi khi tạo lại phản hồi: ${e.message}", true)
                    }
                }
            }
            is ChatUiEvent.SaveEditedMessage -> {
                saveEditedMessage(event.newPrompt)
            }
        }
    }


    /**
     * Adds a user prompt and handles segment creation if necessary.
     */
    private suspend fun addPrompt(prompt: String, imageUri: Uri?, isFileMessage: Boolean = false, fileName: String? = null) {
        val currentUserId = repository.userId
        if (currentUserId.isEmpty()) {
            _chatState.update { it.copy(isLoading = false) }
            return
        }

        Log.d("ChatViewModel", "Adding prompt: '$prompt', isFileMessage: $isFileMessage, fileName: $fileName")

        // Tạo UUID mới cho tin nhắn ngay từ đầu
        val chatId = UUID.randomUUID().toString()
        val messageTimestamp = System.currentTimeMillis()
        
        // Kiểm tra xem ảnh đã được upload trước đó chưa
        val cachedImageUrl = imageUri?.toString()?.let { imageUrlCache[it] }

        // File URL nếu đây là tin nhắn file
        val fileUrl = if (isFileMessage && fileName != null && chatState.value.fileUri != null) {
            Log.d("ChatViewModel", "This is a file message with fileName: $fileName")
            
            // Upload file và lấy URL (có thể là null nếu upload fail)
            // Đối với file, ta có thể không cần upload ngay mà chỉ lưu thông tin file
            chatState.value.fileUri.toString()
        } else null
        
        // Tạo chat object ngay lập tức với đầy đủ thông tin cần thiết
        val chat = Chat(
            id = chatId,
            prompt = prompt,
            isFromUser = true,
            isError = false,
            userId = currentUserId,
            timestamp = messageTimestamp,
            isFileMessage = isFileMessage,
            fileName = fileName,
            imageUrl = if (isFileMessage) fileUrl else cachedImageUrl // Sử dụng fileUrl nếu là tin nhắn file
        )
        
        Log.d("ChatViewModel", "Created chat: isFileMessage=${chat.isFileMessage}, fileName=${chat.fileName}, imageUrl=${chat.imageUrl}")
        
        // Hiển thị tin nhắn lên giao diện ngay lập tức
        val segmentId = _chatState.value.selectedSegment?.id
        _chatState.update {
            it.copy(
                chatList = it.chatList + chat,
                prompt = "",
                imageUri = null,
                fileUri = null,
                fileName = null
            )
        }
        
        // Xử lý tải ảnh lên trong một coroutine riêng biệt nếu chưa có trong cache
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Tải lên ảnh nếu có và chưa được cache (không phải tin nhắn file)
                val imageUrl = if (!isFileMessage && imageUri != null) {
                    if (cachedImageUrl != null) {
                        // Ảnh đã được upload trước đó, sử dụng URL đã cache
                        cachedImageUrl
                    } else {
                        // Ảnh chưa được upload, upload ngay bây giờ
                        repository.uploadImage(imageUri)
                    }
                } else {
                    // Trường hợp tin nhắn file, giữ nguyên URL fileUrl
                    chat.imageUrl
                }
                
                // Cập nhật lại chat object với imageUrl đã tải lên
                val updatedChat = chat.copy(imageUrl = imageUrl)
                
                // Cập nhật chat trong Firestore
                repository.insertChat(updatedChat, segmentId)
                
                // Cập nhật lại UI với thông tin đầy đủ
                withContext(Dispatchers.Main) {
                    val currentList = _chatState.value.chatList
                    val updatedList = currentList.map { 
                        if (it.id == chatId) updatedChat else it 
                    }
                    
                    _chatState.update {
                        it.copy(chatList = updatedList)
                    }
                    
                    // Xóa khỏi cache sau khi đã lưu vào cơ sở dữ liệu
                    if (!isFileMessage) {
                        imageUri?.toString()?.let { key -> imageUrlCache.remove(key) }
                    }
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Lỗi khi tải ảnh/file lên: ${e.message}")
                // Vẫn lưu tin nhắn ngay cả khi tải ảnh/file lên thất bại
                repository.insertChat(chat, segmentId)
            }
        }
    }

    fun insertLocalUserChat(prompt: String, isFileMessage: Boolean = false, fileName: String? = null) {
        viewModelScope.launch {
            val userId = repository.userId
            val chat = Chat.fromPrompt(
                prompt = prompt,
                imageUrl = null,
                isFromUser = true,
                isError = false,
                userId = userId,
                isFileMessage = isFileMessage,
                fileName = fileName
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
     * Lấy phản hồi từ GenerativeModel kèm hình ảnh.
     * Bổ sung kiểm tra phản hồi tùy chỉnh trước khi gọi API.
     */
    private suspend fun getResponseWithImage(prompt: String, imageUri: Uri, selectedSegmentId: String?) {
        currentResponseJob?.cancel()
        currentResponseJob = viewModelScope.launch {
             _chatState.update { it.copy(isWaitingForResponse = true, isImageProcessing = true) }

            viewModelScope.launch(Dispatchers.IO) {
                 try {
                     val actualPrompt = if (prompt.isEmpty()) {
                         "Bạn hãy xem hình ảnh tôi gửi và cho tôi biết trong ảnh có gì? Bạn hãy nói cho tôi biết rõ mọi thứ trong ảnh. Bạn hãy tùy cơ ứng biến để thể hiện bạn là một người thông minh nhất thế giới khi đọc được nội dung của hình và đoán được mong muốn của người dùng về bức ảnh. Hãy tận dụng câu vai trò đã giao để hoàn thành câu trả lời"
                     } else {
                         prompt
                     }

                     val segmentBeforeResponse = withContext(Dispatchers.Main) { _chatState.value.selectedSegment }
                     val wasDefaultTitle = segmentBeforeResponse?.title == "Đoạn chat mới"

                     // Truyền apiSettingsState.value vào repository.getResponseWithImage
                     Log.d("APISettingsDebug", "ViewModel calling repository.getResponseWithImage with settings: ${_apiSettingsState.value}") // Log trước khi gọi
                     val chat = repository.getResponseWithImage(actualPrompt, imageUri, selectedSegmentId, _apiSettingsState.value)

                     withContext(Dispatchers.Main) {
                         _chatState.update {
                             it.copy(
                                 chatList = it.chatList + chat,
                                 isLoading = false,
                                 isWaitingForResponse = false,
                                 imageUri = null,
                                 isImageProcessing = false,
                                 typedMessages = _typedMessagesIds.toSet() - chat.id // Không đánh dấu typed trước animation
                             )
                         }

                         if (wasDefaultTitle && !chat.isError) {
                             loadChatSegments()
                         }
                     }
                 } catch (e: Exception) {
                     e.printStackTrace()
                     withContext(Dispatchers.Main) {
                         val errorChat = Chat.fromPrompt(
                             prompt = "Đã xảy ra lỗi: ${e.localizedMessage}",
                             isFromUser = false,
                             isError = true,
                             userId = repository.userId
                         )
                         _chatState.update {
                             it.copy(
                                 chatList = it.chatList + errorChat,
                                 isLoading = false,
                                 isWaitingForResponse = false,
                                 isImageProcessing = false
                             )
                         }
                          markMessageAsTyped(errorChat.id) // Đánh dấu lỗi đã typed
                     }
                 }
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
            // Xóa segment và tất cả tin nhắn của nó từ repository
            repository.deleteChatSegment(segment.id)

            // Lấy danh sách segments mới sau khi xóa
            val updatedSegments = repository.getChatSegments()
            
            // Cập nhật state với danh sách mới
            _chatState.update { state ->
                // Nếu segment bị xóa là segment đã chọn, đặt selected thành null
                val newSelectedSegment = if (state.selectedSegment?.id == segment.id) {
                    null
                } else {
                    state.selectedSegment
                }
                
                state.copy(
                    chatSegments = updatedSegments,
                    selectedSegment = newSelectedSegment,
                    chatList = if (newSelectedSegment == null) emptyList() else state.chatList
                )
            }
            
            // Nếu danh sách không rỗng, chọn segment đầu tiên nếu cần
            if (updatedSegments.isNotEmpty() && _chatState.value.selectedSegment == null) {
                selectSegment(updatedSegments.first())
            }
        } catch (e: Exception) {
            Log.e("ChatViewModel", "Lỗi khi xóa segment: ${e.message}")
        }
    }

    /**
     * Xóa "Đoạn chat mới" rỗng.
     */
    private suspend fun deleteEmptyNewSegment() {
        val currentSegments = _chatState.value.chatSegments
        val selectedSegment = _chatState.value.selectedSegment?.id
        
        // Tìm tất cả các "Đoạn chat mới" không phải là segment hiện tại
        val newSegmentsToDelete = currentSegments.filter { 
            it.title == "Đoạn chat mới" && it.id != selectedSegment 
        }
        
        for (segment in newSegmentsToDelete) {
            val isSegmentEmpty = repository.getChatHistoryForSegment(segment.id).isEmpty()
            if (isSegmentEmpty) {
                repository.deleteChatSegment(segment.id)
            }
        }
        
        // Cập nhật lại danh sách chatSegments sau khi xóa
        val updatedSegments = repository.getChatSegments()
        _chatState.update { it.copy(chatSegments = updatedSegments) }
    }
    


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

    /**
     * Đánh dấu tất cả tin nhắn của bot trong danh sách hiện tại là đã hiển thị hiệu ứng typing
     */
    fun markAllMessagesAsTyped() {
        _chatState.value.chatList.forEach { chat ->
            if (!chat.isFromUser) {
                _typedMessagesIds.add(chat.id)
            }
        }
        _chatState.update { it.copy(typedMessages = _typedMessagesIds.toSet()) }
    }

    // Đổi tên hàm cho rõ ràng hơn
    fun markAllCurrentMessagesAsTyped() {
        viewModelScope.launch {
            val currentIds = _chatState.value.chatList.map { it.id }.toSet()
            // Chỉ thêm ID của tin nhắn bot hiện có vào danh sách đã typed
            _chatState.value.chatList.forEach { chat ->
                if (!chat.isFromUser) {
                    _typedMessagesIds.add(chat.id)
                }
            }
            // Cập nhật state với danh sách ID đã typed (chỉ chứa ID hợp lệ)
             _chatState.update { state ->
                // Lọc ra những ID không còn tồn tại trong chatList hiện tại
                val validTypedIds = _typedMessagesIds.intersect(currentIds)
                _typedMessagesIds.clear()
                _typedMessagesIds.addAll(validTypedIds)
                state.copy(typedMessages = validTypedIds)
            }
        }
    }

    fun insertModelChat(prompt: String, isError: Boolean = false) {
        viewModelScope.launch {
            val userId = repository.userId
            val chat = Chat.fromPrompt(
                prompt = prompt,
                imageUrl = null,
                isFromUser = false,
                isError = isError,
                userId = userId
            )
            // Chèn vào Firestore
            repository.insertChat(chat, chatState.value.selectedSegment?.id)
            // Cập nhật lại UI
            _chatState.update {
                it.copy(chatList = it.chatList + chat, isLoading = false)
            }
        }
    }

    fun processAndSendDocument(context: Context, documentUri: Uri) {
        viewModelScope.launch {
            try {
                _chatState.update { it.copy(isLoading = true) }
                val fileName = getFileNameFromUri(context, documentUri) ?: "Tài liệu"
                val mimeType = context.contentResolver.getType(documentUri)
                
                val extractedText = when {
                    mimeType?.contains("pdf") == true -> {
                        // Xử lý PDF bằng PDFBox (nếu có)
                        PDFProcessingService.extractTextFromPDF(context, documentUri)
                    }
                    mimeType?.contains("text/plain") == true -> {
                        // Xử lý TXT - đọc trực tiếp từ InputSream
                        val inputStream = context.contentResolver.openInputStream(documentUri)
                        inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                    }
                    mimeType?.contains("msword") == true || 
                    mimeType?.contains("vnd.openxmlformats-officedocument.wordprocessingml.document") == true -> {
                        // Đọc DOC/DOCX đơn giản
                        "Đã nhận tệp Word, nhưng không thể trích xuất nội dung. Vui lòng chuyển đổi sang PDF."
                    }
                    else -> {
                        "Loại tệp không được hỗ trợ: $mimeType"
                    }
                }
                
                // Thêm tin nhắn người dùng với metadata file
                insertLocalUserChat("", true, fileName)
                
                if (extractedText.isNotBlank()) {
                    val prompt = "Đây là nội dung từ tệp '$fileName'. Vui lòng tóm tắt thông tin chính: $extractedText"
                    getResponse(prompt, _chatState.value.selectedSegment?.id)
                } else {
                    insertModelChat("Không thể trích xuất văn bản từ tệp này.", true)
                }
            } catch (e: Exception) {
                insertModelChat("Đã xảy ra lỗi khi xử lý tệp: ${e.message}", true)
            } finally {
                _chatState.update { it.copy(isLoading = false) }
            }
        }
    }
    
    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result
    }
    
    data class ChatMessage(
        val text: String,
        val participant: Participant,
        val timestamp: Long = System.currentTimeMillis(),
        val isPdf: Boolean = false,
        val pdfUri: String? = null,
        val isPdfPrompt: Boolean = false
    )

    

    // Hàm xử lý và gửi file
    private fun processAndSendWithFile(prompt: String, fileUri: Uri, fileName: String, selectedSegmentId: String?) {
        // Hiển thị indicator rằng đang xử lý file
        _isProcessingFile.value = true

        viewModelScope.launch {
            try {
                // Đầu tiên, thêm tin nhắn người dùng với thông tin file để hiển thị ngay
                Log.d("ChatViewModel", "Adding user message with file: $fileName")
                // Sử dụng addPrompt để đảm bảo tin nhắn người dùng với file được hiển thị đúng
                addPrompt(prompt, null, true, fileName)
                
                // Sau đó tiếp tục xử lý file và gửi response
                _chatState.update { it.copy(prompt = "", fileUri = null, fileName = null) }

                // Lấy file content trong một coroutine riêng biệt để không chặn UI
                viewModelScope.launch(Dispatchers.IO) {
                    // Kiểm tra loại file
                    val isTextFile = FileUtils.isTextFile(context, fileUri) // Sử dụng FileUtils
                    val isPdfFile = FileUtils.isPdfFile(context, fileUri) // Kiểm tra PDF

                    // File content sẽ được trích xuất trong background
                    val fileContent = when {
                        isTextFile -> {
                            Log.d("ChatViewModel", "Extracting text file content: $fileName")
                            FileUtils.extractFileContent(context, fileUri, fileName)
                        }
                        isPdfFile -> {
                             // Giữ lại logic xử lý PDF hiện tại
                             try {
                                 Log.d("ChatViewModel", "Extracting PDF content: $fileName") 
                                 PDFProcessingService.extractTextFromPDF(context, fileUri)
                             } catch (pdfError: Exception) {
                                 Log.e("ChatViewModel", "Error extracting PDF content: ${pdfError.message}")
                                 "Không thể đọc nội dung file PDF. Lỗi: ${pdfError.message}"
                             }
                        }
                        // Thêm các loại file khác nếu cần xử lý đặc biệt ở đây
                        else -> {
                            // Các loại file không phải text hoặc pdf (hoặc không hỗ trợ đọc)
                            Log.d("ChatViewModel", "File type not supported for content extraction: $fileName")
                            "" // Trả về chuỗi rỗng nếu không phải file đọc được
                        }
                    }

                    Log.d("ChatViewModel", "File content extracted. Length: ${fileContent.length}")

                    // Xác định prompt hiệu quả
                    val effectivePrompt = if (prompt.isEmpty()) {
                        // Nếu không có tin nhắn từ người dùng, sử dụng prompt mặc định
                        if (isTextFile || isPdfFile) { // Áp dụng cho cả text và pdf nếu không có prompt
                             "Hãy đọc văn bản và tóm tắt văn bản, súc tích, dễ hiểu nhưng vẫn đầy đủ ý chính"
                        } else {
                             "Đã nhận file $fileName. Hãy mô tả nội dung của file này." // Prompt chung cho file không đọc được
                        }
                    } else {
                        prompt // Sử dụng tin nhắn người dùng nhập
                    }

                    // Kết hợp prompt với nội dung file (chỉ khi có nội dung)
                    val promptWithFile = if (fileContent.isNotBlank()) {
                        // Đảm bảo rằng chuỗi này gửi được lên API và không bị mất nội dung file
                        val finalPrompt = "$effectivePrompt\n\n--- Nội dung từ file $fileName ---\n$fileContent\n--- Kết thúc nội dung file ---"
                        Log.d("ChatViewModel", "Combined prompt with file content. Total length: ${finalPrompt.length}")
                        finalPrompt
                    } else {
                         // Nếu không đọc được nội dung (file không hỗ trợ, lỗi,...)
                         "$effectivePrompt\n\n(File đính kèm: $fileName - không thể đọc hoặc hiển thị nội dung)"
                    }

                    // Gửi đến model
                    Log.d("ChatViewModel", "Sending prompt with file to API")
                    getResponse(promptWithFile, selectedSegmentId)

                    // Lưu file URI vào cache để sử dụng lại khi cần (cho regenerate)
                    fileUriCache[fileName] = fileUri
                }
            } catch (e: Exception) {
                // Log lỗi và hiển thị tin nhắn lỗi cho người dùng
                Log.e("ChatViewModel", "Error processing file: ${e.message}")
                insertModelChat("Đã xảy ra lỗi khi xử lý file '$fileName': ${e.message}", true)
            } finally {
                // Kết thúc xử lý file ở finally để đảm bảo luôn được thực hiện
                 // Đảm bảo chạy trên Main thread nếu cần cập nhật UI
                 withContext(Dispatchers.Main) {
                    _isProcessingFile.value = false
                 }
            }
        }
    }

    /**
     * Xóa một tin nhắn cụ thể
     */
    fun deleteChat(chatId: String) {
        viewModelScope.launch {
            val segmentId = _chatState.value.selectedSegment?.id ?: return@launch
            
            // Xóa tin nhắn trong repository
            repository.deleteChat(chatId, segmentId)
            
            // Cập nhật state bằng cách loại bỏ tin nhắn đã xóa
            _chatState.update { state ->
                state.copy(chatList = state.chatList.filter { it.id != chatId })
            }
            
            // Xóa ID từ danh sách typedMessages nếu tồn tại
            if (_typedMessagesIds.contains(chatId)) {
                _typedMessagesIds.remove(chatId)
                _chatState.update { it.copy(typedMessages = _typedMessagesIds.toSet()) }
            }
        }
    }
    
    /**
     * Tạo lại response cho một tin nhắn cũ bằng model mới được chọn
     */
    fun regenerateResponse(userPrompt: String, responseId: String, imageUrl: String? = null, fileUri: Uri? = null, fileName: String? = null, timestamp: Long = -1) {
        viewModelScope.launch {
            val segmentId = _chatState.value.selectedSegment?.id ?: return@launch
            
            // Xử lý việc xóa tất cả các chat mới hơn thời điểm của tin nhắn được regenerate
            var chatsToKeep: List<Chat> = emptyList()
            val originalUserChatIndex = _chatState.value.chatList.indexOfFirst { it.timestamp == timestamp && it.isFromUser }
            
            if (originalUserChatIndex != -1) {
                // Lọc ra danh sách chat cần giữ lại (bao gồm cả tin nhắn user gốc)
                chatsToKeep = _chatState.value.chatList.subList(0, originalUserChatIndex + 1)
                
                // Lấy timestamp của tin nhắn user gốc để xóa các tin nhắn sau đó
                val userMessageTimestamp = _chatState.value.chatList[originalUserChatIndex].timestamp
                
                // Gọi hàm mới trong repository để xóa các tin nhắn sau timestamp của user
                repository.deleteMessagesAfterTimestamp(segmentId, userMessageTimestamp)
                
            } else {
                // Fallback nếu không tìm thấy timestamp (chỉ xóa response cũ)
                repository.deleteChat(responseId, segmentId)
                chatsToKeep = _chatState.value.chatList.filter { it.id != responseId }
            }
            
            // Cập nhật state với danh sách tin nhắn đã lọc
            _chatState.update { state ->
                state.copy(
                    chatList = chatsToKeep,
                    isLoading = true,
                    isWaitingForResponse = true
                )
            }
            
            // Cập nhật typedMessagesIds
            _typedMessagesIds.retainAll { id -> chatsToKeep.any { it.id == id } } // Giữ lại những ID có trong chatsToKeep
            _chatState.update { it.copy(typedMessages = _typedMessagesIds.toSet()) }
            
            try {
                val chat: Chat = when {
                    imageUrl != null -> {
                        // Kiểm tra xem imageUrl có phải là file âm thanh không dựa vào tên file
                        if (fileName != null && (fileName.endsWith(".ogg", ignoreCase = true) || 
                                               fileName.endsWith(".mp3", ignoreCase = true) || 
                                               fileName.endsWith(".m4a", ignoreCase = true) ||
                                               fileName.endsWith(".wav", ignoreCase = true))) {
                            // Nếu là file âm thanh, sử dụng hàm regenerateResponseWithAudio
                            Log.d("ChatViewModel", "Regenerating with audio file: $fileName, URL: $imageUrl")
                            repository.regenerateResponseWithAudio(userPrompt, imageUrl, segmentId, _apiSettingsState.value)
                        } else {
                            // Nếu là hình ảnh thông thường 
                            repository.regenerateResponseWithImage(userPrompt, imageUrl, segmentId, _apiSettingsState.value)
                        }
                    }
                    fileName != null -> {
                        // Trường hợp có file
                        // Kiểm tra xem có Uri trong cache cho file này không
                        val cachedUri = fileUriCache[fileName]
                        
                        if (cachedUri != null) {
                            // Có Uri trong cache, xử lý file như ban đầu
                            Log.d("ChatViewModel", "Found cached Uri for file: $fileName")
                            
                            // Trích xuất nội dung file như ban đầu
                            val mimeType = context.contentResolver.getType(cachedUri)
                            val fileContent = when {
                                mimeType?.contains("pdf") == true -> {
                                    PDFProcessingService.extractTextFromPDF(context, cachedUri)
                                }
                                mimeType?.contains("text/plain") == true -> {
                                    val inputStream = context.contentResolver.openInputStream(cachedUri)
                                    inputStream?.bufferedReader()?.use { reader -> reader.readText() } ?: ""
                                }
                                else -> {
                                    "File không hỗ trợ trích xuất nội dung"
                                }
                            }
                            
                            // Chuẩn bị prompt gửi đến model
                            val promptWithFile = if (userPrompt.isEmpty()) {
                                "Đây là nội dung từ file $fileName, hãy tóm tắt thông tin chính:\n$fileContent"
                            } else {
                                "$userPrompt\n\nNội dung từ file $fileName:\n$fileContent"
                            }
                            
                            repository.getResponse(promptWithFile, segmentId, _apiSettingsState.value)
                        } else {
                            // Không có Uri trong cache, gửi prompt với tên file
                            Log.d("ChatViewModelEdit", "No cached Uri found for file: $fileName")
                            val promptWithFileNameInfo = "$userPrompt\n\n(Thông tin file: $fileName)"
                            repository.getResponse(promptWithFileNameInfo, segmentId, _apiSettingsState.value)
                        }
                    }
                    else -> {
                        // Gọi API thông thường nếu không có hình ảnh hoặc file
                        repository.getResponse(userPrompt, segmentId, _apiSettingsState.value)
                    }
                }
                
                // Thêm response mới vào cuối danh sách đã lọc
                _chatState.update {
                    it.copy(
                        chatList = chatsToKeep + chat,
                        isLoading = false,
                        isWaitingForResponse = false,
                        typedMessages = _typedMessagesIds.toSet() - chat.id // Đảm bảo response mới không bị coi là đã typed
                    )
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                _chatState.update { it.copy(isLoading = false, isWaitingForResponse = false) }
            }
        }
    }

    private fun getFileContent(uri: Uri, fileName: String): String {
         return try {
            val mimeType = context.contentResolver.getType(uri)
            when {
                mimeType?.contains("pdf") == true -> {
                    PDFProcessingService.extractTextFromPDF(context, uri)
                }
                mimeType?.contains("text/plain") == true -> {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    inputStream?.bufferedReader()?.use { reader -> reader.readText() } ?: ""
                }
                else -> {
                    "" // Trả về chuỗi rỗng nếu không đọc được
                }
            }
        } catch (e: Exception) {
            Log.e("ChatViewModelHelper", "Error getting file content for $fileName: ${e.message}")
            "" // Trả về chuỗi rỗng nếu lỗi
        }
    }

    private fun preparePromptWithFileContent(prompt: String, fileContent: String, fileName: String): String {
        return if (fileContent.isNotBlank()) {
            if (prompt.isEmpty()) {
                "Đây là nội dung từ file $fileName, hãy tóm tắt thông tin chính:\n$fileContent"
            } else {
                "$prompt\n\nNội dung từ file $fileName:\n$fileContent"
            }
        } else {
            if (prompt.isEmpty()) {
                "Đã nhận file $fileName nhưng không thể trích xuất nội dung."
            } else {
                 "$prompt\n\n(File đính kèm: $fileName - không thể đọc nội dung)"
            }
        }
    }

    /**
     * Xử lý gửi prompt với hoặc không kèm hình ảnh
     */
    private fun sendPrompt(prompt: String, imageUri: Uri?) {
        viewModelScope.launch {
            val state = _chatState.value
        
            // Đánh dấu đang loading và chờ phản hồi
            _chatState.update { it.copy(isLoading = true, isWaitingForResponse = true) }
        
            // Xóa segment trống nếu có
            deleteEmptyNewSegment()
            
            try {
                // Kiểm tra xem đang có file hay không
                if (state.fileUri != null) {
                    val fileUri = state.fileUri
                    val fileName = state.fileName ?: "File"
                    
                    // Phân loại xử lý theo loại file
                    when {
                        // Nếu là file âm thanh
                        state.isAudioMessage -> {
                            sendAudioPrompt(prompt, fileUri)
                        }
                        // Nếu là file văn bản
                        FileUtils.isTextFile(context, fileUri) -> {
                            sendTextFilePrompt(prompt, fileUri)
                        }
                        // Các loại file khác
                                else -> {
                            processAndSendWithFile(prompt, fileUri, fileName, state.selectedSegment?.id)
                        }
                    }
                }
                // Nếu là hình ảnh
                else if (imageUri != null) {
                        addPrompt(prompt, imageUri, false, null)
                    val selectedSegmentId = state.selectedSegment?.id
                         _chatState.update { it.copy(prompt = "", imageUri = null) }
                        _isImageProcessing.value = true
                        getResponseWithImage(prompt, imageUri, selectedSegmentId)
                    }
                // Tin nhắn văn bản thông thường
                else {
                        addPrompt(prompt, null, false, null)
                        _chatState.update { it.copy(prompt = "") }
                    getResponse(prompt, state.selectedSegment?.id)
                    }
            } catch (e: Exception) {
                e.printStackTrace()
                _chatState.update { it.copy(isLoading = false, isWaitingForResponse = false) }
                insertModelChat("Lỗi khi gửi tin nhắn: ${e.message}", true)
            }
        }
    }

    /**
     * Xử lý gửi prompt kèm file âm thanh 
     */
    private fun sendAudioPrompt(prompt: String, audioUri: Uri) {
        viewModelScope.launch {
            try {
                if (_chatState.value.isEditing) {
                     // Chuyển đổi: cần lấy chatId và cập nhật updateExistingChat để nhận apiSettings
                     val chatId = _chatState.value.editingChatId ?: return@launch
                    // updateExistingChat(chatId, prompt, audioUri) // Sẽ cập nhật hàm này sau
                     _chatState.update { it.copy(isEditing = false, editingChatId = null) }
                     // For now, just log and proceed without update call until it's modified
                     Log.w("ChatViewModel", "Skipping audio update during edit - updateExistingChat needs modification")
                     return@launch
                }

                val segmentId = _chatState.value.selectedSegment?.id
                    ?: throw RuntimeException("Cannot send prompt without segment selection")

                _chatState.update { it.copy(isLoading = true) }
                insertLocalUserChat(prompt, audioUri, segmentId)
                _chatState.update {
                    it.copy(
                        prompt = "",
                        isLoading = false,
                        fileUri = null, // Reset fileUri after adding user chat
                        fileName = null, // Reset fileName
                        imageUri = null,
                    )
                }

                _chatState.update { it.copy(isWaitingForResponse = true) }

                try {
                    val effectivePrompt = if (prompt.isEmpty()) "Tương tác với file ghi âm, trả lời nó nếu là câu hỏi, bạn hãy trả lời như con người thông minh và là con người thực thụ. Nên nhớ là chỉ cần câu trả lời." else prompt

                    // Truyền apiSettingsState.value vào repository.getResponseWithAudio
                    Log.d("APISettingsDebug", "ViewModel calling repository.getResponseWithAudio with settings: ${_apiSettingsState.value}") // Log trước khi gọi
                    val response = repository.getResponseWithAudio(effectivePrompt, audioUri, segmentId, _apiSettingsState.value)

                    _chatState.update { state ->
                        state.copy(
                            chatList = state.chatList + response,
                            isWaitingForResponse = false,
                            typedMessages = _typedMessagesIds.toSet() - response.id // Không đánh dấu typed trước animation
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    val errorChat = Chat.fromPrompt(
                        prompt = "Lỗi: ${e.localizedMessage ?: "Không thể xử lý file âm thanh"}",
                        isFromUser = false,
                        isError = true,
                        userId = repository.userId
                    )
                    repository.insertChat(errorChat, segmentId)
                    _chatState.update { state ->
                        state.copy(
                            chatList = state.chatList + errorChat,
                            isWaitingForResponse = false
                        )
                    }
                     markMessageAsTyped(errorChat.id) // Đánh dấu lỗi đã typed
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _chatState.update { it.copy(isLoading = false, isWaitingForResponse = false) }
            }
        }
    }

    /**
     * Thêm tin nhắn người dùng cục bộ kèm file âm thanh
     */
    private suspend fun insertLocalUserChat(prompt: String, audioUri: Uri, segmentId: String) {
        // Tạo một bản sao của audioUri để sử dụng sau nếu tải lên thất bại
        val localAudioUri = audioUri

        // Lấy tên file trước khi tải lên để bảo đảm có nó kể cả khi tải lên thất bại
        val fileName = getFileNameFromUri(context, audioUri) ?: "audio_file.ogg"

        // Tải file lên Firebase Storage (có thể thất bại)
        val audioUrl = withContext(Dispatchers.IO) {
            repository.uploadAudioFile(audioUri)
        }

        // Tạo đối tượng Chat với Uri cục bộ nếu tải lên thất bại
        val chat = Chat.fromPrompt(
            prompt = prompt,
            isFromUser = true,
            userId = repository.userId,
            imageUrl = audioUrl ?: localAudioUri.toString(), // Lưu URI dưới dạng chuỗi nếu tải lên thất bại
            isFileMessage = true,
            fileName = fileName
        )

        // Lưu tin nhắn với cờ đánh dấu nếu đây là URI cục bộ
        val isLocalUriOnly = audioUrl == null
        
        // Thêm chat vào repository (cơ sở dữ liệu)
        repository.insertChat(chat, segmentId)

        // Cập nhật state của UI, bao gồm đánh dấu nếu đây là URI cục bộ
        _chatState.update { state ->
            state.copy(
                chatList = state.chatList + chat
            )
        }
    }

    /**
     * Cập nhật tin nhắn đã tồn tại với nội dung mới hoặc file âm thanh
     */
    private fun updateExistingChat(chatId: String, prompt: String, audioUri: Uri?) {
        viewModelScope.launch {
            try {
                // Lấy segment ID hiện tại
                val segmentId = _chatState.value.selectedSegment?.id
                    ?: throw RuntimeException("Không thể cập nhật tin nhắn khi chưa chọn đoạn hội thoại")
                
                // Tìm tin nhắn cần cập nhật
                val chatToUpdate = _chatState.value.chatList.find { it.id == chatId }
                    ?: return@launch
                
                // Nếu có file âm thanh mới, tải lên và lấy URL
                var audioUrl: String? = null
                var fileName: String? = null
                var localAudioUriStr: String? = null
                
                if (audioUri != null) {
                    // Lưu tên file trước khi tải lên để đảm bảo luôn có nó
                    fileName = getFileNameFromUri(context, audioUri) ?: "audio_file.ogg"
                    
                    // Lưu URI cục bộ để sử dụng nếu tải lên thất bại
                    localAudioUriStr = audioUri.toString()
                    
                    // Tải lên server (có thể thất bại)
                    audioUrl = withContext(Dispatchers.IO) {
                        repository.uploadAudioFile(audioUri)
                    }
                }
                
                // Xác định imageUrl: sử dụng URL từ server nếu thành công, nếu không sử dụng URI cục bộ
                val imageUrlToUse = if (audioUri != null) {
                    audioUrl ?: localAudioUriStr
                } else {
                    chatToUpdate.imageUrl
                }
                
                // Cập nhật tin nhắn
                val updatedChat = chatToUpdate.copy(
                    prompt = prompt,
                    imageUrl = imageUrlToUse,
                    fileName = fileName ?: chatToUpdate.fileName,
                    isFileMessage = audioUri != null || chatToUpdate.isFileMessage
                )
                
                // Lưu vào cơ sở dữ liệu
                repository.updateChat(updatedChat, segmentId)
                
                // Cập nhật trạng thái
                _chatState.update { state ->
                    val updatedChatList = state.chatList.map { 
                        if (it.id == chatId) updatedChat else it 
                    }
                    state.copy(
                        chatList = updatedChatList,
                        isLoading = audioUri != null, // Chỉ loading nếu có file âm thanh
                        isWaitingForResponse = audioUri != null // Chỉ đợi phản hồi nếu có file âm thanh
                    )
                }
                
                // Nếu có file âm thanh, gọi API và cập nhật phản hồi
                if (audioUri != null) {
                    try {
                        val response = repository.getResponseWithAudio(prompt, audioUri, segmentId, _apiSettingsState.value)
                        
                        _chatState.update { state ->
                            state.copy(
                                chatList = state.chatList + response,
                                isLoading = false,
                                isWaitingForResponse = false
                            )
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        
                        val errorChat = Chat.fromPrompt(
                            prompt = "Lỗi: ${e.localizedMessage ?: "Không thể xử lý file âm thanh"}",
                            isFromUser = false,
                            isError = true,
                            userId = repository.userId
                        )
                        
                        repository.insertChat(errorChat, segmentId)
                        
                        _chatState.update { state ->
                            state.copy(
                                chatList = state.chatList + errorChat,
                                isLoading = false,
                                isWaitingForResponse = false
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _chatState.update { it.copy(isLoading = false, isWaitingForResponse = false) }
            }
        }
    }

    /**
     * Gửi prompt kèm file text đến Gemini API.
     */
    private fun sendTextFilePrompt(prompt: String, fileUri: Uri) {
        viewModelScope.launch {
            try {
                val selectedSegmentId = _chatState.value.selectedSegment?.id
                deleteEmptyNewSegment()
                
                // Thêm tin nhắn người dùng vào giao diện ngay lập tức
                val fileName = getFileNameFromUri(context, fileUri) ?: "File văn bản"
                addPrompt(prompt, null, true, fileName)
                
                // Cập nhật state
                _chatState.update { 
                    it.copy(
                        prompt = "",
                        fileUri = null,
                        fileName = null,
                        isLoading = true,
                        isWaitingForResponse = true
                    )
                }
                
                // Xử lý và gọi API
                Log.d("APISettingsDebug", "ViewModel calling repository.getResponseWithTextFile with settings: ${_apiSettingsState.value}") // Log trước khi gọi
                val chat = repository.getResponseWithTextFile(prompt, fileUri, selectedSegmentId, _apiSettingsState.value)
                
                // Thêm ID vào danh sách typed messages
                if (!chat.isError) {
                    _typedMessagesIds.add(chat.id)
                }
                
                // Cập nhật UI
                _chatState.update {
                    it.copy(
                        chatList = it.chatList + chat,
                        isLoading = false,
                        isWaitingForResponse = false,
                        typedMessages = _typedMessagesIds.toSet() - chat.id // Không đánh dấu typed trước animation
                    )
                }
                
            } catch (e: Exception) {
                e.printStackTrace()
                insertModelChat("Lỗi khi xử lý file văn bản: ${e.message}", true)
                _chatState.update { 
                    it.copy(
                        isLoading = false,
                        isWaitingForResponse = false // Sửa lỗi tham số từ isWaitingResponse
                    )
                }
            }
        }
    }

    /**
     * Xử lý việc chọn một segment
     */
    private fun selectSegment(segment: ChatSegment) {
        viewModelScope.launch {
            hasUpdatedTitle = false
            
            // Xóa danh sách ID đã đánh dấu trước khi tải đoạn chat mới
            _typedMessagesIds.clear()
            
            val chats = repository.getChatHistoryForSegment(segment.id)
            
            // Đánh dấu tất cả tin nhắn từ bot là đã hiển thị hiệu ứng typing
            chats.forEach { chat ->
                if (!chat.isFromUser) {
                    _typedMessagesIds.add(chat.id)
                }
            }
            
            _chatState.update {
                it.copy(
                    selectedSegment = segment,
                    chatList = chats,
                    isLoading = false,
                    typedMessages = _typedMessagesIds.toSet()
                )
            }
            searchQueryFlow.value = ""
        }
    }

    /**
     * Xử lý việc xóa segment
     */
    private fun handleDeleteSegment(segment: ChatSegment) {
        viewModelScope.launch {
            deleteSegment(segment)
        }
    }

    /**
     * Bắt đầu chỉnh sửa một tin nhắn
     */
    private fun startEditingChat(
        chatId: String,
        message: String,
        timestamp: Long,
        imageUrl: String? = null,
        fileName: String? = null
    ) {
        // Tìm tin nhắn cần chỉnh sửa
        val chatToEdit = _chatState.value.chatList.find { it.id == chatId }
        if (chatToEdit != null) {
            // Lấy trực tiếp các thông tin từ chat để đảm bảo không mất dữ liệu
            val isFileMessage = chatToEdit.isFileMessage
            val actualFileName = fileName ?: chatToEdit.fileName
            val actualImageUrl = imageUrl ?: chatToEdit.imageUrl
            
            // Cập nhật trạng thái để hiển thị giao diện chỉnh sửa
            _chatState.update {
                it.copy(
                    isEditing = true,
                    editingChatId = chatId,
                    editingChatTimestamp = timestamp,
                    prompt = message, // Đặt prompt hiện tại là nội dung đang edit
                    editingImageUrl = actualImageUrl, // Lưu imageUrl đúng
                    editingFileName = actualFileName // Lưu fileName đúng
                )
            }
            
            // Log để kiểm tra
            Log.d("ChatViewModel", "Bắt đầu edit tin nhắn: " +
                    "ID=${chatId}, isFileMessage=${isFileMessage}, " +
                    "fileName=${actualFileName}, imageUrl=${actualImageUrl}")
        }
    }

    /**
     * Hủy chỉnh sửa tin nhắn
     */
    private fun cancelEditing() {
        // Hủy chế độ chỉnh sửa và xóa thông tin edit đã lưu
        _chatState.update {
            it.copy(
                isEditing = false,
                editingChatId = null,
                editingChatTimestamp = -1,
                editingImageUrl = null,
                //editingFileUri = null,
                editingFileName = null,
                prompt = "" // Xóa prompt khi hủy
            )
        }
        
        // Log để kiểm tra
        Log.d("ChatViewModel", "Đã hủy chỉnh sửa tin nhắn")
    }

    /**
     * Lưu tin nhắn đã chỉnh sửa và xóa các tin nhắn mới hơn
     */
    private fun saveEditedMessage(newPrompt: String) {
        viewModelScope.launch {
            try {
                val chatStateValue = _chatState.value // Lấy state một lần
                val editingChatId = chatStateValue.editingChatId ?: return@launch
                val segmentId = chatStateValue.selectedSegment?.id ?: return@launch

                val chatToEdit = chatStateValue.chatList.find { it.id == editingChatId } ?: return@launch

                val isFileMessage = chatToEdit.isFileMessage
                val imageUrl = chatToEdit.imageUrl
                val fileName = chatToEdit.fileName

                Log.d("ChatViewModel", "Edit tin nhắn: ID=${editingChatId}, " +
                        "isFileMessage=${isFileMessage}, fileName=${fileName}, imageUrl=${imageUrl}, " +
                        "oldPrompt=${chatToEdit.prompt}, newPrompt=${newPrompt}")

                val newChatId = UUID.randomUUID().toString() // Tạo ID mới

                 val editedChat = chatToEdit.copy(
                    id = newChatId, // ID mới
                    prompt = newPrompt,
                    isFileMessage = isFileMessage,
                    imageUrl = imageUrl,
                    fileName = fileName
                )

                 // Lấy danh sách tin nhắn đến tin nhắn đang edit (BAO GỒM tin nhắn đang edit)
                val chatListIncludingEdit = chatStateValue.chatList.takeWhile { it.timestamp <= chatToEdit.timestamp }

                // Tìm vị trí của tin nhắn gốc trong danh sách đã lọc
                val originalChatIndex = chatListIncludingEdit.indexOfFirst { it.id == editingChatId }

                // Danh sách tin nhắn mới gồm các tin nhắn trước đó VÀ tin nhắn đã edit
                val updatedPartialList = if (originalChatIndex != -1) {
                    chatListIncludingEdit.subList(0, originalChatIndex) + editedChat
                } else {
                    // Trường hợp không tìm thấy (ít xảy ra), thay thế nếu có hoặc thêm vào cuối
                    chatStateValue.chatList.filterNot { it.id == editingChatId } + editedChat
                }


                _chatState.update {
                    it.copy(
                        chatList = updatedPartialList, // Chỉ hiển thị phần đã edit
                        isLoading = true,
                        isWaitingForResponse = true,
                        isEditing = false,
                        prompt = "",
                        editingChatId = null,
                        editingChatTimestamp = -1,
                        editingImageUrl = null,
                        editingFileName = null
                    )
                }

                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val finalPromptForApi: String? = if (isFileMessage && fileName != null) {
                            val cachedUri = fileUriCache[fileName]
                            if (cachedUri != null) {
                                Log.d("ChatViewModel", "Trích xuất nội dung từ file cache: $fileName")
                                val fileContent = getFileContent(cachedUri, fileName)
                                "$newPrompt\n\n--- Nội dung từ file $fileName ---\n$fileContent\n--- Kết thúc nội dung file ---"
                            } else if (imageUrl != null && imageUrl.startsWith("http")) {
                                 // Nếu không có cache nhưng có URL (ví dụ từ Firebase), chỉ gửi prompt kèm tên file
                                 "$newPrompt\n\n(File đính kèm: $fileName)"
                            } else {
                                "$newPrompt\n\n(File đính kèm: $fileName - không thể đọc nội dung)"
                            }
                        } else {
                             newPrompt // Không phải file, sử dụng prompt mới
                        }

                        // Truyền apiSettingsState.value vào repository.editChatMessage
                        Log.d("APISettingsDebug", "ViewModel calling repository.editChatMessage with settings: ${_apiSettingsState.value}") // Log trước khi gọi
                        val response = repository.editChatMessage(editedChat, segmentId, _apiSettingsState.value, finalPromptForApi)

                        withContext(Dispatchers.Main) {
                            _chatState.update {
                                it.copy(
                                    chatList = updatedPartialList + response, // Thêm response mới vào cuối
                                    isLoading = false,
                                    isWaitingForResponse = false,
                                    typedMessages = _typedMessagesIds.toSet() - response.id // Không đánh dấu typed trước animation
                                )
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ChatViewModel", "Lỗi trong quá trình xử lý file khi edit: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            insertModelChat("Lỗi trong quá trình xử lý file khi edit: ${e.message}", true)
                            _chatState.update {
                                it.copy(
                                    isLoading = false,
                                    isWaitingForResponse = false
                                )
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Lỗi khi lưu tin nhắn đã chỉnh sửa: ${e.message}")
                _chatState.update {
                    it.copy(
                        isLoading = false,
                        isWaitingForResponse = false,
                        isEditing = false,
                        prompt = ""
                    )
                }
                insertModelChat("Lỗi khi lưu tin nhắn đã chỉnh sửa: ${e.message}", true)
            }
        }
    }

    // State lưu trữ cài đặt API cho RightSideDrawer
    private val _apiSettingsState = MutableStateFlow(ApiSettingsState())
    val apiSettingsState = _apiSettingsState.asStateFlow()
    
    // State lưu trữ trạng thái hiển thị RightSideDrawer
    // Sửa lỗi: Khai báo đúng là MutableStateFlow<Boolean>
    private val _isRightDrawerOpen = MutableStateFlow(false) 
    val isRightDrawerOpen = _isRightDrawerOpen.asStateFlow()
    
    // State điều khiển hiển thị Safety Settings Dialog
    private val _showSafetySettingsDialog = MutableStateFlow(false)
    val showSafetySettingsDialog = _showSafetySettingsDialog.asStateFlow()
    
    // Hàm cập nhật temperature
    fun updateTemperature(value: Float) {
        _apiSettingsState.update { it.copy(temperature = value) }
    }
    
    // Hàm cập nhật thinking mode
    fun updateThinkingMode(enabled: Boolean) {
        _apiSettingsState.update { it.copy(thinkingModeEnabled = enabled) }
    }
    
    // Hàm cập nhật thinking budget
    fun updateThinkingBudget(enabled: Boolean) {
        _apiSettingsState.update { it.copy(thinkingBudgetEnabled = enabled) }
    }
    
    // Hàm cập nhật structured output
    fun updateStructuredOutput(enabled: Boolean) {
        _apiSettingsState.update { it.copy(structuredOutputEnabled = enabled) }
    }
    
    // Hàm cập nhật code execution
    fun updateCodeExecution(enabled: Boolean) {
        _apiSettingsState.update { it.copy(codeExecutionEnabled = enabled) }
    }
    
    // Hàm cập nhật function calling
    fun updateFunctionCalling(enabled: Boolean) {
        _apiSettingsState.update { it.copy(functionCallingEnabled = enabled) }
    }
    
    // Hàm cập nhật grounding with Google Search
    fun updateGroundingSearch(enabled: Boolean) {
        _apiSettingsState.update { it.copy(groundingEnabled = enabled) }
    }
    
    // Hàm cập nhật output length
    fun updateOutputLength(value: String) {
        _apiSettingsState.update { it.copy(outputLength = value) }
    }
    
    // Hàm cập nhật top P
    fun updateTopP(value: Float) {
        _apiSettingsState.update { it.copy(topP = value) }
    }
    
    // Hàm cập nhật stop sequence
    fun updateStopSequence(value: String) {
        _apiSettingsState.update { it.copy(stopSequence = value) }
    }
    
    // Hàm mở/đóng right drawer
    fun toggleRightDrawer() {
        _isRightDrawerOpen.update { !it }
    }
    
    // Hàm đóng right drawer
    fun closeRightDrawer() {
        _isRightDrawerOpen.update { false }
    }
    
    // Hàm mở right drawer
    fun openRightDrawer() {
        _isRightDrawerOpen.update { true }
    }

    // Hàm mở Safety Settings Dialog
    fun openSafetySettingsDialog() {
        _showSafetySettingsDialog.value = true
    }

    // Hàm đóng Safety Settings Dialog
    fun closeSafetySettingsDialog() {
        _showSafetySettingsDialog.value = false
    }
    
    // Hàm cập nhật cài đặt an toàn cụ thể
    fun updateSafetySetting(category: HarmCategory, threshold: SafetyThreshold) {
        _apiSettingsState.update {
            when (category) {
                HarmCategory.HARASSMENT -> it.copy(safetyHarassment = threshold)
                HarmCategory.HATE_SPEECH -> it.copy(safetyHate = threshold)
                HarmCategory.SEXUALLY_EXPLICIT -> it.copy(safetySexuallyExplicit = threshold)
                HarmCategory.DANGEROUS_CONTENT -> it.copy(safetyDangerous = threshold)
                // Thêm các loại khác nếu API hỗ trợ
                else -> it // Thêm else branch để xử lý các trường hợp khác
            }
        }
    }
    
    // Hàm reset cài đặt an toàn về mặc định
    fun resetSafetySettingsToDefaults() {
        _apiSettingsState.update {
            it.copy(
                safetyHarassment = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE,
                safetyHate = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE,
                safetySexuallyExplicit = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE,
                safetyDangerous = SafetyThreshold.BLOCK_MEDIUM_AND_ABOVE
            )
        }
    }
}


