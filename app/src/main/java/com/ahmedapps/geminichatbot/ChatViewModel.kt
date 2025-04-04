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
import android.content.Context
import com.ahmedapps.geminichatbot.services.PDFProcessingService
import android.util.Log
import com.ahmedapps.geminichatbot.data.Participant
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: ChatRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

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
        "gemini-2.5-pro-exp-03-25" to "AI Coding",
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

    // Thêm trạng thái theo dõi việc xử lý hình ảnh
    private val _isImageProcessing = MutableStateFlow(false)
    val isImageProcessing = _isImageProcessing.asStateFlow()

    private val _isProcessingFile = MutableStateFlow(false)
    val isProcessingFile: StateFlow<Boolean> = _isProcessingFile

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    // Cache để lưu fileUri theo fileName
    private val fileUriCache = mutableMapOf<String, Uri>()

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
                if (event.prompt.isNotEmpty() || event.imageUri != null || _chatState.value.fileUri != null || _chatState.value.isEditing) {
                    currentResponseJob?.cancel()

                    currentResponseJob = viewModelScope.launch {
                        deleteEmptyNewSegment()
                        _chatState.update { it.copy(isLoading = true, isWaitingForResponse = true) }
                        
                        // Nếu đang chỉnh sửa, xử lý khác so với việc gửi tin nhắn mới
                        if (_chatState.value.isEditing && _chatState.value.editingChatId != null) {
                            _chatState.update { it.copy(isLoading = true) }

                            val editingChatId = _chatState.value.editingChatId!!
                            val timestamp = _chatState.value.editingChatTimestamp
                            val segmentId = _chatState.value.selectedSegment?.id

                            // Lấy thông tin ảnh/file đã lưu từ state
                            val editingImageUrl = _chatState.value.editingImageUrl
                            val editingFileUri = _chatState.value.editingFileUri
                            val editingFileName = _chatState.value.editingFileName

                            // Xóa các tin nhắn sau điểm chỉnh sửa
                            if (timestamp > 0 && segmentId != null) {
                                val editedChat = _chatState.value.chatList.find { it.id == editingChatId }
                                if (editedChat != null) {
                                    // Cập nhật tin nhắn trong repository (chỉ cập nhật prompt)
                                    // Thông tin ảnh/file không đổi khi edit text
                                    val updatedChat = editedChat.copy(prompt = event.prompt)
                                    repository.updateChat(updatedChat, segmentId)

                                    // Xóa các tin nhắn sau điểm chỉnh sửa trong repository
                                    repository.deleteMessagesAfterTimestamp(segmentId, timestamp)

                                    // Cập nhật danh sách tin nhắn trong state
                                    val updatedChatList = _chatState.value.chatList.map { chat ->
                                        if (chat.id == editingChatId) {
                                            chat.copy(prompt = event.prompt) // Chỉ cập nhật prompt ở đây
                                        } else {
                                            chat
                                        }
                                    }.filter { chat ->
                                        chat.timestamp <= timestamp
                                    }

                                    // Reset trạng thái edit và cập nhật list
                                    _chatState.update { it.copy(
                                        chatList = updatedChatList,
                                        isEditing = false,
                                        editingChatId = null,
                                        editingChatTimestamp = -1,
                                        editingImageUrl = null, // Reset thông tin edit
                                        editingFileUri = null,
                                        editingFileName = null,
                                        prompt = "", // Xóa prompt sau khi gửi edit
                                        isWaitingForResponse = true // Bắt đầu chờ response
                                    )}

                                    // Gửi yêu cầu lên API dựa trên loại nội dung gốc
                                    try {
                                        val chatResponse: Chat = when {
                                            editingImageUrl != null -> {
                                                Log.d("ChatViewModelEdit", "Sending edited prompt with original image URL.")
                                                repository.regenerateResponseWithImage(event.prompt, editingImageUrl, segmentId)
                                            }
                                            editingFileName != null -> {
                                                val cachedUri = fileUriCache[editingFileName]
                                                if (cachedUri != null) {
                                                    Log.d("ChatViewModelEdit", "Found cached Uri for edited file: $editingFileName")
                                                    val fileContent = getFileContent(cachedUri, editingFileName)
                                                    val promptWithFile = preparePromptWithFileContent(event.prompt, fileContent, editingFileName)
                                                    repository.getResponse(promptWithFile, segmentId)
                                                } else {
                                                    Log.w("ChatViewModelEdit", "No cached Uri for edited file: $editingFileName. Sending prompt with filename info.")
                                                    val promptWithFileNameInfo = "${event.prompt}\n\n(File đính kèm: $editingFileName)"
                                                    repository.getResponse(promptWithFileNameInfo, segmentId)
                                                }
                                            }
                                            else -> {
                                                Log.d("ChatViewModelEdit", "Sending edited text prompt.")
                                                repository.getResponse(event.prompt, segmentId)
                                            }
                                        }

                                        // Thêm response mới vào cuối danh sách đã lọc
                                        _chatState.update {
                                            it.copy(
                                                chatList = updatedChatList + chatResponse,
                                                isLoading = false,
                                                isWaitingForResponse = false,
                                                typedMessages = _typedMessagesIds.toSet() - chatResponse.id
                                            )
                                        }

                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        _chatState.update { it.copy(isLoading = false, isWaitingForResponse = false) }
                                    }

                                } else {
                                     _chatState.update { it.copy(isLoading = false, isWaitingForResponse = false) }
                                }
                            } else {
                                 _chatState.update { it.copy(isLoading = false, isWaitingForResponse = false) }
                            }
                        } else {
                             // Xử lý gửi tin nhắn thông thường (không phải edit) - giữ nguyên logic cũ
                             _chatState.update { it.copy(isLoading = true, isWaitingForResponse = true) }
                             deleteEmptyNewSegment()
                             when {
                                _chatState.value.fileUri != null -> {
                                    val fileUri = _chatState.value.fileUri!!
                                    val fileName = _chatState.value.fileName ?: "File"
                                    addPrompt(event.prompt, null, true, fileName)
                                    _chatState.update { it.copy(prompt = "", fileUri = null, fileName = null) }
                                    val selectedSegmentId = _chatState.value.selectedSegment?.id
                                    processAndSendWithFile(event.prompt, fileUri, fileName, selectedSegmentId)
                                }
                                event.imageUri != null -> {
                                    addPrompt(event.prompt, event.imageUri, false, null)
                                    val selectedSegmentId = _chatState.value.selectedSegment?.id
                                     _chatState.update { it.copy(prompt = "", imageUri = null) }
                                    _isImageProcessing.value = true
                                    getResponseWithImage(event.prompt, event.imageUri, selectedSegmentId)
                                }
                                else -> {
                                    addPrompt(event.prompt, null, false, null)
                                    val selectedSegmentId = _chatState.value.selectedSegment?.id
                                    _chatState.update { it.copy(prompt = "") }
                                    getResponse(event.prompt, selectedSegmentId)
                                }
                             }
                        }
                    }
                } else if (_chatState.value.isEditing) {
                     // Hủy edit nếu gửi prompt rỗng
                    _chatState.update { it.copy(
                        isEditing = false,
                        editingChatId = null,
                        editingChatTimestamp = -1,
                        editingImageUrl = null,
                        editingFileUri = null,
                        editingFileName = null,
                        prompt = ""
                    )}
                }
            }
            is ChatUiEvent.UpdatePrompt -> {
                 _chatState.update { it.copy(prompt = event.newPrompt) }
            }
            is ChatUiEvent.OnImageSelected -> {
                 _chatState.update { it.copy(imageUri = event.uri) }
                 // Không cần markAllMessagesAsTyped() ở đây
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
            is ChatUiEvent.RefreshChats -> {
                refreshChats()
            }
            is ChatUiEvent.StopResponse -> {
                stopCurrentResponse()
            }
            is ChatUiEvent.OnFileSelected -> {
                val uri = event.uri
                val fileName = getFileNameFromUri(context, uri) ?: "File không xác định"
                // Lưu vào cache
                fileUriCache[fileName] = uri
                _chatState.update { 
                    it.copy(
                        fileUri = uri,
                        fileName = fileName,
                        isFileUploading = false
                    )
                }
            }
            is ChatUiEvent.RemoveFile -> {
                _chatState.update { it.copy(fileUri = null, fileName = null) }
            }
            is ChatUiEvent.DeleteChat -> {
                deleteChat(event.chatId)
            }
            is ChatUiEvent.EditChat -> {
                // Tìm tin nhắn cần chỉnh sửa
                val chatToEdit = _chatState.value.chatList.find { it.id == event.chatId }
                if (chatToEdit != null) {
                    // Cập nhật trạng thái để hiển thị giao diện chỉnh sửa
                    // Lưu thông tin ảnh/file vào state
                    _chatState.update {
                        it.copy(
                            isEditing = true,
                            editingChatId = event.chatId,
                            editingChatTimestamp = event.timestamp,
                            prompt = event.message, // Đặt prompt hiện tại là nội dung đang edit
                            editingImageUrl = event.imageUrl, // Lưu imageUrl
                            editingFileUri = event.fileUri,   // Lưu fileUri
                            editingFileName = event.fileName  // Lưu fileName
                        )
                    }
                    // Focus đã được xử lý ở ChatScreen
                }
            }
            is ChatUiEvent.CancelEdit -> {
                // Hủy chế độ chỉnh sửa và xóa thông tin edit đã lưu
                _chatState.update {
                    it.copy(
                        isEditing = false,
                        editingChatId = null,
                        editingChatTimestamp = -1,
                        editingImageUrl = null,
                        editingFileUri = null,
                        editingFileName = null,
                        prompt = "" // Xóa prompt khi hủy
                    )
                }
            }
            is ChatUiEvent.RegenerateResponse -> {
                regenerateResponse(event.userPrompt, event.responseId, event.imageUrl, event.fileUri, event.fileName, event.timestamp)
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

        val imageUrl = imageUri?.let {
            repository.uploadImage(it)
        }

        // Nếu là tin nhắn file và không có nội dung, sử dụng chuỗi rỗng thay vì "Tôi đã gửi tệp..."
        val messageText = if (isFileMessage && prompt.isEmpty()) {
            "" // Tin nhắn trống, file sẽ hiển thị riêng
        } else {
            prompt // Sử dụng tin nhắn người dùng nhập
        }

        // Tạo một đối tượng Chat với metadata là file nếu cần
        val chat = Chat.fromPrompt(
            prompt = messageText,
            imageUrl = imageUrl,
            isFromUser = true,
            isError = false,
            userId = currentUserId,
            isFileMessage = isFileMessage,
            fileName = fileName
        )
        
        val segmentId = _chatState.value.selectedSegment?.id
        repository.insertChat(chat, segmentId)
        _chatState.update {
            it.copy(
                chatList = it.chatList + chat,
                prompt = "",
                imageUri = null,
                fileUri = null,
                fileName = null
            )
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
     * Lấy phản hồi từ GenerativeModel không kèm hình ảnh.
     * Bổ sung kiểm tra phản hồi tùy chỉnh trước khi gọi API.
     */
    suspend fun getResponse(prompt: String, selectedSegmentId: String?) {
        // Hủy job hiện tại nếu đang chạy
        currentResponseJob?.cancel()
        
        // Gán job mới
        currentResponseJob = viewModelScope.launch {
            // Cập nhật cả isLoading và isWaitingForResponse
            _chatState.update { it.copy(isLoading = true, isWaitingForResponse = true) }

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

                // Không cần xóa ID khỏi _typedMessagesIds ở đây vì nó chưa bao giờ được thêm vào
                // _typedMessagesIds.remove(chat.id) // Xóa dòng này

                _chatState.update {
                    it.copy(
                        chatList = it.chatList + chat,
                        isLoading = false, // Kết thúc loading chung
                        isWaitingForResponse = false, // Kết thúc chờ đợi
                        // Đảm bảo typedMessages không chứa ID mới (nếu có lỗi logic nào đó)
                        typedMessages = _typedMessagesIds.toSet() - chat.id
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Đặt lại cả isLoading và isWaitingForResponse khi có lỗi
                _chatState.update { it.copy(isLoading = false, isWaitingForResponse = false) } // Kết thúc loading/chờ đợi khi lỗi
            } finally {
                // Đảm bảo isLoading và isWaitingForResponse luôn được reset
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
            // Cập nhật cả isLoading và isWaitingForResponse
            _chatState.update { it.copy(isLoading = true, isWaitingForResponse = true) }

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
                        isLoading = false, // Kết thúc loading chung
                        isWaitingForResponse = false, // Kết thúc chờ đợi
                        isImageProcessing = false, // Kết thúc xử lý ảnh
                        // Đảm bảo typedMessages không chứa ID mới
                        typedMessages = _typedMessagesIds.toSet() - chat.id
                    )
                }
                return@launch
            }

            // Đánh dấu tất cả tin nhắn hiện tại là đã typed trước khi bắt đầu xử lý hình ảnh
            markAllMessagesAsTyped()

            // Nếu không có phản hồi tùy chỉnh, tiếp tục gọi API như bình thường
            try {
                val actualPrompt = if (prompt.isEmpty()) {
                    "Bạn hãy xem hình ảnh tôi gửi và cho tôi biết trong ảnh có gì? Bạn hãy nói cho tôi biết rõ mọi thứ trong ảnh. Bạn hãy tùy cơ ứng biến để thể hiện bạn là một người thông minh nhất thế giới khi đọc được nội dung của hình và đoán được mong muốn của người dùng về bức ảnh. Hãy tận dụng câu vai trò đã giao để hoàn thành câu trả lời"
                } else {
                    prompt
                }
                val chat = repository.getResponseWithImage(actualPrompt, imageUri, selectedSegmentId)
                val currentSegment = chatState.value.selectedSegment
                
                // Quan trọng: Đảm bảo tin nhắn mới KHÔNG được đánh dấu là đã typed
                _typedMessagesIds.remove(chat.id)
                
                _chatState.update {
                    it.copy(
                        chatList = it.chatList + chat,
                        isLoading = false,
                        isWaitingForResponse = false,
                        imageUri = null,
                        isImageProcessing = false,
                        typedMessages = _typedMessagesIds.toSet() - chat.id
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
                 // Đặt lại cả isLoading và isWaitingForResponse khi có lỗi
                _chatState.update { it.copy(isLoading = false, isWaitingForResponse = false) }
            } finally {
                // Đảm bảo isLoading, isWaitingForResponse và isImageProcessing luôn được reset
                _chatState.update { it.copy(isLoading = false, isWaitingForResponse = false, isImageProcessing = false) }
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
         viewModelScope.launch {
            _isProcessingFile.value = true // Bắt đầu xử lý file
            _chatState.update { it.copy(isLoading = true) } // Có thể gộp loading
            try {
                // ... (logic trích xuất nội dung file như cũ) ...
                 val mimeType = context.contentResolver.getType(fileUri)
                val fileContent = when {
                    mimeType?.contains("pdf") == true -> {
                        PDFProcessingService.extractTextFromPDF(context, fileUri)
                    }
                    mimeType?.contains("text/plain") == true -> {
                        val inputStream = context.contentResolver.openInputStream(fileUri)
                        inputStream?.bufferedReader()?.use { reader -> reader.readText() } ?: ""
                    }
                    else -> {
                        "File không hỗ trợ trích xuất nội dung"
                    }
                }

                val promptWithFile = if (fileContent.isNotBlank()) {
                    if (prompt.isEmpty()) {
                        "Đây là nội dung từ file $fileName, hãy tóm tắt thông tin chính:\n$fileContent"
                    } else {
                        "$prompt\n\nNội dung từ file $fileName:\n$fileContent"
                    }
                } else {
                    if (prompt.isEmpty()) {
                        "Đã nhận file $fileName nhưng không thể trích xuất nội dung."
                    } else {
                         "$prompt\n\n(File đính kèm: $fileName - không thể đọc nội dung)" // Thêm thông báo không đọc được nội dung
                    }
                }

                // Gửi đến model
                getResponse(promptWithFile, selectedSegmentId)

            } catch (e: Exception) {
                insertModelChat("Đã xảy ra lỗi khi xử lý file: ${e.message}", true)
                 _chatState.update { it.copy(isLoading = false) } // Dừng loading khi lỗi
            } finally {
                 _isProcessingFile.value = false // Kết thúc xử lý file
                 // isLoading sẽ được reset trong getResponse hoặc khối catch
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
                        // Trường hợp có hình ảnh
                        repository.regenerateResponseWithImage(userPrompt, imageUrl, segmentId)
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
                            
                            repository.getResponse(promptWithFile, segmentId)
                        } else {
                            // Không có Uri trong cache, gửi prompt với tên file
                            Log.d("ChatViewModel", "No cached Uri found for file: $fileName")
                            val promptWithFileNameInfo = "$userPrompt\n\n(Thông tin file: $fileName)"
                            repository.getResponse(promptWithFileNameInfo, segmentId)
                        }
                    }
                    else -> {
                        // Gọi API thông thường nếu không có hình ảnh hoặc file
                        repository.getResponse(userPrompt, segmentId)
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
}


