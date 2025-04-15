package com.ahmedapps.geminichatbot.ui.components

import android.Manifest
import android.content.ClipDescription
import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.PopupProperties
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ahmedapps.geminichatbot.ChatState
import com.ahmedapps.geminichatbot.ChatUiEvent
import com.ahmedapps.geminichatbot.ChatViewModel
import com.ahmedapps.geminichatbot.ExpandedChatInputBottomSheet
import com.ahmedapps.geminichatbot.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.ahmedapps.geminichatbot.crop
import com.ahmedapps.geminichatbot.ui.components.AudioPlayerComponent
import com.ahmedapps.geminichatbot.ui.components.VoiceRecordingBar
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.ahmedapps.geminichatbot.utils.FileUtils
import com.ahmedapps.geminichatbot.utils.AudioConverter
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

// Di chuyển hàm crop từ ChatScreen.kt để sử dụng cho dropdown menu
fun Modifier.crop(
    horizontal: Dp = 0.dp,
    vertical: Dp = 0.dp,
): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    fun Dp.toPxInt(): Int = this.toPx().toInt()

    layout(
        placeable.width - (horizontal * 2).toPxInt(),
        placeable.height - (vertical * 2).toPxInt()
    ) {
        placeable.placeRelative(-horizontal.toPx().toInt(), -vertical.toPx().toInt())
    }
}

fun sanitizeMessage(input: String): String {
    return input
        .lines() // Chia chuỗi thành danh sách các dòng
        .filter { it.isNotBlank() } // Loại bỏ các dòng trống
        .joinToString("\n") // Ghép lại thành một chuỗi với dấu xuống dòng
        .trim() // Loại bỏ khoảng trắng ở đầu và cuối
}

/**
 * Helper class để xử lý ghi âm và tạo file audio
 */
class AudioRecorderHelper(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false
    
    companion object {
        private const val TAG = "AudioRecorderHelper"
    }
    
    /**
     * Kiểm tra quyền ghi âm
     */
    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Bắt đầu ghi âm
     * @return Uri của file ghi âm nếu thành công, null nếu thất bại
     */
    fun startRecording(): File? {
        if (isRecording) {
            Log.d(TAG, "Đã đang ghi âm, bỏ qua yêu cầu")
            return null
        }
        
        try {
            // Tạo file đầu ra
            val audioDir = File(context.filesDir, "audio")
            if (!audioDir.exists()) {
                audioDir.mkdirs()
            }
            
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            outputFile = File(audioDir, "audio_$timeStamp.m4a")
            
            recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                MediaRecorder()
            }
            
            recorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputFile?.absolutePath)
                
                try {
                    prepare()
                    start()
                    isRecording = true
                    Log.d(TAG, "Bắt đầu ghi âm: ${outputFile?.absolutePath}")
                    return outputFile
                } catch (e: IOException) {
                    Log.e(TAG, "Lỗi chuẩn bị MediaRecorder: ${e.message}")
                    releaseRecorder()
                    outputFile = null
                    return null
                }
            }
            return null
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi bắt đầu ghi âm: ${e.message}")
            releaseRecorder()
            outputFile = null
            return null
        }
    }
    
    /**
     * Dừng ghi âm và trả về file đã ghi
     * @return File đã ghi âm nếu thành công, null nếu thất bại
     */
    fun stopRecording(): File? {
        if (!isRecording) {
            Log.d(TAG, "Không đang ghi âm, bỏ qua yêu cầu dừng")
            return null
        }
        
        try {
            recorder?.apply {
                stop()
                Log.d(TAG, "Dừng ghi âm: ${outputFile?.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi dừng ghi âm: ${e.message}")
        } finally {
            releaseRecorder()
        }
        
        val recordedFile = outputFile
        outputFile = null
        
        return if (recordedFile != null && recordedFile.exists() && recordedFile.length() > 0) {
            recordedFile
        } else {
            Log.e(TAG, "File ghi âm không tồn tại hoặc rỗng")
            null
        }
    }
    
    /**
     * Hủy ghi âm hiện tại
     */
    fun cancelRecording() {
        if (!isRecording) {
            return
        }
        
        try {
            recorder?.apply {
                stop()
                Log.d(TAG, "Hủy ghi âm")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi hủy ghi âm: ${e.message}")
        } finally {
            releaseRecorder()
        }
        
        // Xóa file nếu có
        outputFile?.delete()
        outputFile = null
    }
    
    /**
     * Giải phóng MediaRecorder
     */
    private fun releaseRecorder() {
        try {
            recorder?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi giải phóng MediaRecorder: ${e.message}")
        } finally {
            recorder = null
            isRecording = false
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CustomTextField(
    chatViewModel: ChatViewModel,
    chatState: ChatState,
    focusRequester: FocusRequester,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    imagePicker: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>,
    documentPickerLauncher: ManagedActivityResultLauncher<String, Uri?>,
    photoUri: Uri?,
    takePictureLauncher: ManagedActivityResultLauncher<Uri, Boolean>,
    navController: NavController,
    createImageUriInner: () -> Uri?,
    onPhotoUriChange: (Uri?) -> Unit,
    onImageReceived: (Uri) -> Unit,
    showKeyboardAfterEdit: Boolean = false,
    onKeyboardShown: () -> Unit = {}
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val focusManager = LocalFocusManager.current

    // Trạng thái cho việc hiển thị BottomSheet mở rộng
    var showExpandedInputSheet by remember { mutableStateOf(false) }
    // Thêm biến để theo dõi khi nào nên hiển thị nút mở rộng
    var shouldShowExpandButton by remember { mutableStateOf(false) }
    // Lưu trữ reference đến view để kiểm tra trạng thái cuộn
    var editTextRef by remember { mutableStateOf<AppCompatEditText?>(null) }
    // Sử dụng ID tài nguyên đã định nghĩa làm key
    val textWatcherTagKey = R.id.text_watcher_tag_key
    // Trạng thái cho dropdown menu
    var showSourceMenu by remember { mutableStateOf(false) }
    
    // Biến mới để theo dõi trạng thái đã ghi âm xong và có thể nghe lại
    var hasRecordedAudio by remember { mutableStateOf(false) }
    
    // Biến để lưu Uri của file âm thanh
    var recordedAudioUri by remember { mutableStateOf<Uri?>(null) }
    
    // Kiểm tra xem có văn bản hoặc hình ảnh hoặc file nào không
    val isTextNotEmpty = chatState.prompt.isNotEmpty() || chatState.imageUri != null || chatState.fileUri != null || hasRecordedAudio
    
    // Trạng thái cho voice recording
    var isRecording by remember { mutableStateOf(false) }
    // Thêm trạng thái cho thời gian ghi âm
    var recordingDurationMs by remember { mutableStateOf(0L) }
    
    // Thêm trạng thái cho việc yêu cầu quyền ghi âm
    var showPermissionRequest by remember { mutableStateOf(false) }
    
    // Yêu cầu quyền RECORD_AUDIO
    val audioPermissionState = rememberPermissionState(permission = Manifest.permission.RECORD_AUDIO)
    
    // Thêm trạng thái cho việc xử lý ghi âm
    var isProcessingVoice by remember { mutableStateOf(false) }
    
    // --- Cặp biến trạng thái theo dõi voice recording UI ---
    var isVoiceBarVisible by remember { mutableStateOf(false) } // Ban đầu không hiển thị
    var isClosing by remember { mutableStateOf(false) } // Theo dõi việc đang đóng thanh ghi âm
    var isCancelled by remember { mutableStateOf(false) } // Đánh dấu nếu người dùng đã chủ động hủy
    
    // Biến để theo dõi coroutine cập nhật thời gian
    var timerJob by remember { mutableStateOf<Job?>(null) }
    
    // Khởi tạo AudioRecorderHelper
    val audioRecorderHelper = remember {
        AudioRecorderHelper(context)
    }
    
    // Biến lưu file âm thanh hiện tại
    var currentAudioFile by remember { mutableStateOf<File?>(null) }
    
    // Biến lưu thời điểm bắt đầu ghi âm
    var recordingStartTimeMs by remember { mutableStateOf(0L) }
    
    // Hàm bắt đầu ghi âm và hiển thị thanh trạng thái
    val startRecording = {
        // Reset trạng thái nghe lại âm thanh
        hasRecordedAudio = false
        recordedAudioUri = null
        
        // Không cho bắt đầu ghi âm nếu đang editing hoặc đang chờ phản hồi
        if (!chatState.isEditing && !chatState.isWaitingForResponse && !isProcessingVoice) {
            if (audioRecorderHelper.hasRecordAudioPermission()) {
                Log.d("CustomTextField", "Bắt đầu ghi âm...")
                recordingStartTimeMs = System.currentTimeMillis()
                recordingDurationMs = 0L
                isRecording = true
                isCancelled = false // Reset biến đánh dấu hủy
                isClosing = false // Reset biến đánh dấu đóng
                isVoiceBarVisible = true // Hiển thị thanh ghi âm
                
                // Bắt đầu ghi âm
                currentAudioFile = audioRecorderHelper.startRecording()
                
                // Khởi động coroutine để cập nhật thời gian
                timerJob?.cancel()
                timerJob = scope.launch {
                    try {
                        while (isActive && isRecording) {
                            recordingDurationMs = System.currentTimeMillis() - recordingStartTimeMs
                            delay(100) // Cập nhật mỗi 100ms
                        }
                    } catch (e: Exception) {
                        Log.e("CustomTextField", "Lỗi trong coroutine cập nhật thời gian: ${e.message}")
                    }
                }
            } else {
                Log.d("CustomTextField", "Không có quyền ghi âm")
                showPermissionRequest = true
            }
        } else {
            Log.d("CustomTextField", "Không thể bắt đầu ghi âm - isEditing: ${chatState.isEditing}, isWaiting: ${chatState.isWaitingForResponse}, isProcessing: $isProcessingVoice")
        }
    }
    
    // Hàm dừng ghi âm và xử lý kết quả
    val stopRecording = {
        if (isRecording) {
            Log.d("CustomTextField", "Dừng ghi âm...")
            isRecording = false
            isProcessingVoice = true // Chuyển trạng thái sang đang xử lý
            timerJob?.cancel()
            
            // Dừng ghi âm và lấy file
            val audioFile = audioRecorderHelper.stopRecording()
            currentAudioFile = audioFile
            if (audioFile != null && audioFile.exists()) {
                val audioUri = Uri.fromFile(audioFile)
                recordedAudioUri = audioUri
                
                // Chuyển sang trạng thái nghe lại
                hasRecordedAudio = true
                isVoiceBarVisible = false
                isProcessingVoice = false
                
                // Gửi sự kiện OnAudioRecorded để lưu file âm thanh (nhưng chưa gửi ngay)
                chatViewModel.onEvent(ChatUiEvent.OnAudioRecorded(audioUri, audioFile.name))
            } else {
                // Nếu không có file hợp lệ, reset trạng thái
                isVoiceBarVisible = false
                isProcessingVoice = false
                hasRecordedAudio = false
            }
        }
    }
    
    // Hàm hủy ghi âm
    val cancelRecording = {
        if (isRecording) {
            Log.d("CustomTextField", "Hủy ghi âm...")
            isRecording = false
            isCancelled = true // Đánh dấu là người dùng đã hủy
            isClosing = true // Bắt đầu animation đóng
            timerJob?.cancel()
            audioRecorderHelper.cancelRecording()
            
            // Reset trạng thái nghe lại
            hasRecordedAudio = false
            recordedAudioUri = null
            
            // Animation đóng
            scope.launch {
                delay(300) // Thời gian animation
                isVoiceBarVisible = false
            }
        }
    }
    
    // Cleanup resources khi component bị hủy
    DisposableEffect(Unit) {
        onDispose {
            timerJob?.cancel()
            if (isRecording) {
                audioRecorderHelper.cancelRecording()
            }
        }
    }
    
    // Launcher để yêu cầu quyền ghi âm
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Quyền đã được cấp, bắt đầu ghi âm
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            startRecording()
        } else {
            // Quyền bị từ chối
            scope.launch {
                snackbarHostState.showSnackbar("Ứng dụng cần quyền ghi âm để ghi âm tin nhắn.")
            }
        }
    }
    
    // Document picker callback
    val documentPickerResult = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri ->
            if (uri != null) {
                // Kiểm tra loại file
                val mimeType = context.contentResolver.getType(uri)
                
                // Xử lý nếu là file âm thanh
                if (mimeType != null && mimeType.startsWith("audio/")) {
                    // Đây là file âm thanh
                    recordedAudioUri = uri
                    hasRecordedAudio = true
                    chatViewModel.onEvent(ChatUiEvent.OnAudioFileSelected(uri))
                } 
                // Xử lý nếu là file ảnh
                else if (com.ahmedapps.geminichatbot.utils.FileUtils.isImageFile(context, uri)) {
                    // Đây là file ảnh, xử lý giống như khi chọn ảnh từ thư viện
                    chatViewModel.onEvent(ChatUiEvent.OnImageSelected(uri))
                }
                else {
                    // Đây là file thông thường (không phải âm thanh hoặc ảnh)
                    chatViewModel.onEvent(ChatUiEvent.OnFileSelected(uri))
                }
            }
        }
    )
    
    // Effect khi fileUri trong chatState thay đổi
    LaunchedEffect(chatState.fileUri, chatState.isAudioMessage) {
        if (chatState.fileUri != null && chatState.isAudioMessage && !hasRecordedAudio) {
            // Nếu có file âm thanh trong chatState nhưng chưa hiển thị thanh phát
            recordedAudioUri = chatState.fileUri
            hasRecordedAudio = true
        }
    }
    
    // Giám sát trạng thái đóng thanh ghi âm để đảm bảo thanh ghi âm không bao giờ hiển thị lại sau khi đóng
    LaunchedEffect(isClosing) {
        if (isClosing) {
            Log.d("VoiceRecording", "LaunchedEffect: Phát hiện trạng thái đóng thanh ghi âm")
            // Khi đóng thanh ghi âm, đảm bảo isVoiceBarVisible luôn là false
            isVoiceBarVisible = false
        }
    }
    
    // Hiển thị thông báo nếu cần lý giải tại sao cần quyền ghi âm
    LaunchedEffect(audioPermissionState.status) {
        if (!audioPermissionState.status.isGranted && audioPermissionState.status.shouldShowRationale) {
            snackbarHostState.showSnackbar("Ứng dụng cần quyền ghi âm để ghi âm tin nhắn.")
        }
    }

    // Effect để xử lý việc hiển thị bàn phím khi state showKeyboardAfterEdit thay đổi
    LaunchedEffect(showKeyboardAfterEdit) {
        if (showKeyboardAfterEdit) {
            delay(150) // Đợi một chút để đảm bảo focus đã được thiết lập
            editTextRef?.let { editText ->
                editText.requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(editText, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                onKeyboardShown() // Báo lại đã hiển thị bàn phím
            }
        }
    }
    LaunchedEffect(chatState.isEditing) {
        if (chatState.isEditing) {
            focusRequester.requestFocus()
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Hiển thị audio player nếu đã ghi âm xong và có file âm thanh
        AnimatedVisibility(
            visible = hasRecordedAudio && recordedAudioUri != null,
            enter = fadeIn() + slideInHorizontally(),
            exit = fadeOut() + slideOutHorizontally(),
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            recordedAudioUri?.let { uri ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    AudioPlayerComponent(
                        context = context,
                        audioUri = uri,
                        modifier = Modifier.fillMaxWidth(),
                        isCompact = true,
                        onDelete = {
                            // Hủy bỏ bản ghi âm, reset trạng thái và xóa file
                            hasRecordedAudio = false
                            
                            // Lưu URI tạm thời để tham chiếu đến file cần xóa
                            val fileToDelete = recordedAudioUri
                            recordedAudioUri = null
                            
                            // Reset trạng thái file âm thanh trong ViewModel
                            chatViewModel.onEvent(ChatUiEvent.RemoveFile)
                            
                            // Xóa file âm thanh nếu tồn tại (chạy trong background)
                            scope.launch(Dispatchers.IO) {
                                try {
                                    fileToDelete?.let { audioUri ->
                                        val path = audioUri.path
                                        if (path != null) {
                                            val file = File(path)
                                            if (file.exists()) {
                                                file.delete()
                                                Log.d("CustomTextField", "Đã xóa file âm thanh: $path")
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    Log.e("CustomTextField", "Lỗi khi xóa file âm thanh: ${e.message}")
                                }
                            }
                        }
                    )
                }
            }
        }
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Top)
                .heightIn(min = 40.dp, max = 160.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    if (hasRecordedAudio) 
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f) 
                    else 
                        MaterialTheme.colorScheme.surface
                )
        ) {
            // Đã xóa phần hiển thị ảnh nhỏ ở đây vì đã hiển thị ở phần ChatScreen
            
            AndroidView(
                factory = { ctx ->
                    AppCompatEditText(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        minHeight = with(density) { 40.dp.toPx() }.toInt()
                        maxLines = 8
                        setPadding(
                            with(density) { 16.dp.toPx() }.toInt(), // Đã sửa lại padding vì đã xóa hiển thị ảnh
                            with(density) { 5.dp.toPx() }.toInt(),
                            with(density) { 36.dp.toPx() }.toInt(),
                            with(density) { 5.dp.toPx() }.toInt()
                        )
                        gravity = Gravity.CENTER_VERTICAL or Gravity.START
                        inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES

                        isVerticalScrollBarEnabled = true
                        setVerticalScrollBarEnabled(true)
                        isSingleLine = false
                        setHorizontallyScrolling(false)
                        overScrollMode = android.view.View.OVER_SCROLL_ALWAYS

                        var startY = 0f
                        var isScrolling = false
                        setOnTouchListener { v, event ->
                            when (event.action) {
                                android.view.MotionEvent.ACTION_DOWN -> {
                                    startY = event.y
                                    isScrolling = false
                                    v.parent.requestDisallowInterceptTouchEvent(true)
                                    if (!v.hasFocus()) {
                                        v.requestFocus()
                                    }
                                    false
                                }
                                android.view.MotionEvent.ACTION_MOVE -> {
                                    val moveDistance = Math.abs(event.y - startY)
                                    if (moveDistance > 10f) {
                                        isScrolling = true
                                    }
                                    if ((v as AppCompatEditText).canScrollVertically(1) || (v as AppCompatEditText).canScrollVertically(-1)) {
                                        v.parent.requestDisallowInterceptTouchEvent(true)
                                    } else {
                                        v.parent.requestDisallowInterceptTouchEvent(false)
                                    }
                                    false
                                }
                                android.view.MotionEvent.ACTION_UP -> {
                                    if (!isScrolling) {
                                        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                        imm.showSoftInput(v, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    v.parent.requestDisallowInterceptTouchEvent(false)
                                    false
                                }
                                else -> false
                            }
                        }

                        setTextIsSelectable(true)
                        isFocusable = true
                        isFocusableInTouchMode = true
                        background = null
                        setHintTextColor(Color.Gray.toArgb())
                        setTextColor(textColor.toArgb())
                        hint = "Nhập tin nhắn cho ChatAI"
                        textSize = 16f
                        imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI
                        editTextRef = this

                        viewTreeObserver.addOnScrollChangedListener {
                            val canScrollVertically = canScrollVertically(1) || canScrollVertically(-1)
                            if (shouldShowExpandButton != canScrollVertically) {
                                shouldShowExpandButton = canScrollVertically
                            }
                        }

                        // Tạo TextWatcher
                        val textWatcher = object : TextWatcher {
                            // Đã xóa @Volatile private var isUpdating = false
                            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                            override fun afterTextChanged(s: Editable?) {
                                // Chỉ cập nhật ViewModel nếu text thực sự thay đổi bởi người dùng (không phải từ khối update)
                                // Sử dụng R.id.text_watcher_tag_key
                                if (getTag(textWatcherTagKey) != "UPDATE_IN_PROGRESS") {
                                    // Đã xóa kiểm tra if (!isUpdating)
                                    val newText = s?.toString() ?: ""
                                    // Luôn gửi sự kiện cập nhật. ViewModel/StateFlow sẽ xử lý việc chỉ phát ra giá trị nếu nó thực sự thay đổi.
                                    // Đã xóa kiểm tra if (newText != chatState.prompt)
                                    chatViewModel.onEvent(ChatUiEvent.UpdatePrompt(newText))

                                    post {
                                        shouldShowExpandButton = canScrollVertically(1) || canScrollVertically(-1)
                                    }
                                    // Đã xóa isUpdating = true/false
                                }
                            }
                        }
                        addTextChangedListener(textWatcher) 
                        // Lưu trữ TextWatcher vào tag của View, sử dụng R.id.text_watcher_tag_key
                        setTag(textWatcherTagKey, textWatcher)

                        val mimeTypes = arrayOf("image/*")
                        ViewCompat.setOnReceiveContentListener(this, mimeTypes) { _, payload ->
                            val split = payload.partition { item -> item.uri != null }
                            val uriContent = split.first
                            val remaining = split.second

                            if (uriContent != null) {
                                val clip = uriContent.clip
                                for (i in 0 until clip.itemCount) {
                                    val uri = clip.getItemAt(i).uri
                                    if (uri != null) {
                                        chatViewModel.onEvent(ChatUiEvent.OnImageSelected(uri))
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                        return@setOnReceiveContentListener null
                                    }
                                }
                            }
                            remaining
                        }

                        setOnKeyListener { _, keyCode, event ->
                            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_V && event.isCtrlPressed) {
                                val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                if (clipboardManager.hasPrimaryClip()) {
                                    val description = clipboardManager.primaryClip?.description
                                    if (description != null) {
                                        val hasImageType = (0 until description.mimeTypeCount).any { i ->
                                            val mimeType = description.getMimeType(i)
                                            mimeType.startsWith("image/") || mimeType == "image/*"
                                        }
                                        if (hasImageType) {
                                            handleImageInClipboardForView(clipboardManager) { uri ->
                                                chatViewModel.onEvent(ChatUiEvent.OnImageSelected(uri))
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                                scope.launch { snackbarHostState.showSnackbar("Đã dán ảnh") }
                                            }
                                            return@setOnKeyListener true
                                        }
                                    }
                                }
                            }
                            false
                        }
                    }
                },
                update = { view ->
                    view.setTextColor(textColor.toArgb())

                    // Cập nhật padding khi ảnh thay đổi
                    view.setPadding(
                        with(density) { 16.dp.toPx() }.toInt(), // Đã sửa lại padding vì đã xóa hiển thị ảnh
                        with(density) { 5.dp.toPx() }.toInt(),
                        with(density) { 36.dp.toPx() }.toInt(),
                        with(density) { 5.dp.toPx() }.toInt()
                    )

                    if (view.text.toString() != chatState.prompt) {
                        // Lấy TextWatcher từ tag, sử dụng R.id.text_watcher_tag_key
                        val listener = view.getTag(textWatcherTagKey) as? TextWatcher

                        if (listener != null) {
                            // Đặt cờ, sử dụng R.id.text_watcher_tag_key
                            view.setTag(textWatcherTagKey, "UPDATE_IN_PROGRESS")
                            view.removeTextChangedListener(listener)
                        }

                        val currentSelectionStart = view.selectionStart
                        val currentSelectionEnd = view.selectionEnd

                        view.setText(chatState.prompt)

                        val newLength = chatState.prompt.length
                        try {
                            view.setSelection(
                                currentSelectionStart.coerceAtMost(newLength),
                                currentSelectionEnd.coerceAtMost(newLength)
                            )
                        } catch (e: Exception) {
                            Log.e("ChatScreenUpdate", "Error setting selection: ${e.message}")
                            view.setSelection(newLength)
                        }

                        if (listener != null) {
                            view.addTextChangedListener(listener)
                            // Đặt lại listener vào tag, sử dụng R.id.text_watcher_tag_key
                            view.setTag(textWatcherTagKey, listener)
                        }

                        view.post {
                            shouldShowExpandButton = view.canScrollVertically(1) || view.canScrollVertically(-1)
                        }
                    }
                     if (chatState.isEditing && !view.hasFocus()) {
                         view.requestFocus()
                     }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged { focusState ->
                        Log.d("FocusDebug", "CustomTextField EditText focus changed: ${focusState.isFocused}")
                    }
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = shouldShowExpandButton || chatState.prompt.length > 50,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                IconButton(
                    onClick = {
                        focusManager.clearFocus()
                        scope.launch {
                            showExpandedInputSheet = true
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    },
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(28.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_extendchatinput),
                        contentDescription = "Mở rộng khung nhập tin nhắn",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
        
        // --- Thay đổi Hàng chứa nút Icon để hỗ trợ ghi âm ---
        if (isRecording || isProcessingVoice) {
            // Hiển thị thanh ghi âm với trạng thái hiện tại
            VoiceRecordingBar(
                durationMs = recordingDurationMs,
                isProcessingVoice = isProcessingVoice,
                isVisible = true, // Luôn hiển thị vì điều kiện bên ngoài đã kiểm tra isVoiceBarVisible
                onStopRecording = {
                    if (!isProcessingVoice && isRecording) {
                        // Ngay khi người dùng nhấn nút xử lý, đánh dấu phản hồi xúc giác
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        
                        // Dừng việc ghi âm để bắt đầu xử lý
                        stopRecording()
                        
                        // Hiển thị logging để debug
                        Log.d("VoiceRecording", "Dừng ghi âm để xử lý")
                    }
                },
                onCancelRecording = {
                    // Đánh dấu phản hồi xúc giác
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    
                    // Đánh dấu trạng thái hủy và đóng
                    isCancelled = true
                    isClosing = true
                    
                    // Ẩn thanh ghi âm ngay lập tức bằng cách đặt isVisible = false
                    isVoiceBarVisible = false
                    
                    // Log để debug
                    Log.d("VoiceRecording", "Ẩn thanh ghi âm do người dùng hủy")
                    
                    // Chạy các tác vụ hủy sau khi animation ẩn đã chạy một thời gian
                    scope.launch {
                        try {
                            // Đợi animation có thời gian chạy
                            delay(300)
                            
                            // Dừng và hủy bỏ việc ghi âm
                            Log.d("VoiceRecording", "Bắt đầu hủy ghi âm")
                            cancelRecording()
                            
                            // Reset tất cả các trạng thái
                            isProcessingVoice = false
                            isRecording = false
                            recordingDurationMs = 0
                            
                            // Log để debug
                            Log.d("VoiceRecording", "Đã hoàn thành việc hủy ghi âm và reset trạng thái")
                            
                            // Đợi thêm một thời gian để chắc chắn mọi callback tiềm ẩn đã được xử lý
                            delay(500)
                            
                            // Chỉ đặt lại các trạng thái khi đã chắc chắn mọi thứ đã được dọn dẹp
                            isCancelled = false
                            isClosing = false
                        } catch (e: Exception) {
                            Log.e("VoiceRecording", "Lỗi khi hủy ghi âm: ${e.message}")
                            // Đảm bảo isClosing được reset nếu có lỗi
                            isClosing = false
                            isCancelled = false
                        }
                    }
                }
            )
        } else {
            // Hiển thị hàng chứa nút icon khi không ghi âm
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // --- Nút '+' (Thêm ảnh) ---
                IconButton(
                    onClick = { 
                        showSourceMenu = !showSourceMenu
                        // Thêm phản hồi rung khi mở dropdown chọn ảnh
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    },
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .alpha(if (showSourceMenu) 0.5f else 1f),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_popup),
                        contentDescription = "Thêm ảnh",
                        tint = textColor,
                        modifier = Modifier.alpha(0.6f)
                    )
                }

                // --- DropdownMenu Hiển thị các lựa chọn ---
                DropdownMenu(
                    expanded = showSourceMenu,
                    onDismissRequest = { showSourceMenu = false },
                    properties = PopupProperties(focusable = false),
                    modifier = Modifier
                        .crop(vertical = 8.dp)
                        .background(
                            MaterialTheme.colorScheme.surface,
                            shape = RoundedCornerShape(15.dp)
                        )
                        .width(IntrinsicSize.Max),
                    offset = DpOffset(x = 0.dp, y = (-8).dp)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Gửi tệp tài liệu",
                                style = TextStyle(
                                    color = textColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        },
                        onClick = {
                            // Sử dụng document picker callback đã định nghĩa
                            documentPickerResult.launch("*/*")
                            showSourceMenu = false
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        trailingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_chosefile),
                                contentDescription = "Gửi tệp tài liệu",
                                tint = textColor
                            )
                        }
                    )
                    Divider(color = Color(0x14FFFFFF), thickness = 0.6.dp)
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Chụp ảnh",
                                style = TextStyle(
                                    color = textColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        },
                        onClick = {
                            val uri = createImageUriInner()
                            if (uri != null) {
                                onPhotoUriChange(uri)
                                takePictureLauncher.launch(uri)
                            }
                            showSourceMenu = false
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        trailingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.camera_add),
                                contentDescription = "Chụp ảnh",
                                tint = textColor
                            )
                        }
                    )
                    Divider(color = Color(0x14FFFFFF), thickness = 0.6.dp)
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Thư viện ảnh",
                                style = TextStyle(
                                    color = textColor,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        },
                        onClick = {
                            imagePicker.launch(
                                PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                            showSourceMenu = false
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        trailingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_addpicture),
                                contentDescription = "Thư viện ảnh",
                                tint = textColor
                            )
                        }
                    )
                }
                
                // Spacer để đẩy các nút về phía phải
                Spacer(modifier = Modifier.weight(1f))

                // Nút Microphone - di chuyển sát bên trái nút gửi và điều chỉnh kích thước, màu sắc
                IconButton(
                    onClick = {
                        if (!isRecording && !isProcessingVoice && !isClosing) {
                            when {
                                // Đã có quyền, bắt đầu ghi âm ngay
                                ContextCompat.checkSelfPermission(
                                    context, 
                                    Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED -> {
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    
                                    // Đặt lại trạng thái hiển thị thanh ghi âm nếu cần
                                    if (!isVoiceBarVisible) {
                                        isVoiceBarVisible = true
                                    }
                                    
                                    // Bắt đầu ghi âm
                                    startRecording()
                                }
                                // Chưa có quyền, yêu cầu quyền
                                else -> {
                                    requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_micro),
                        contentDescription = "Bắt đầu ghi âm",
                        tint = if (isRecording) 
                                MaterialTheme.colorScheme.error 
                            else 
                                Color.Gray, // Màu xám thay vì primary
                        modifier = Modifier.size(25.dp) // Tăng kích thước icon để phù hợp với nút lớn hơn
                    )
                }

                // --- Nút gửi tin nhắn ---
                if (chatState.isLoading) {
                    // Bỏ hiệu ứng nhấp nháy
                    val isDarkTheme = isSystemInDarkTheme()
                    Icon(
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                chatViewModel.stopCurrentResponse()
                                // Thêm phản hồi rung khi dừng
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                        painter = painterResource(id = if (isDarkTheme) R.drawable.ic_stopresponse_dark else R.drawable.ic_stopresponse_light),
                        contentDescription = "Dừng",
                        tint = Color.Unspecified
                    )
                } else {
                    IconButton(
                        onClick = {
                            if (isTextNotEmpty) {
                                if (chatState.isEditing) {
                                    // Caso esteja editando uma mensagem existente
                                    val sanitizedPrompt = sanitizeMessage(chatState.prompt)
                                    chatViewModel.onEvent(ChatUiEvent.SaveEditedMessage(sanitizedPrompt))
                                    
                                    // Thêm phản hồi rung khi lưu chỉnh sửa
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                else if ((hasRecordedAudio && recordedAudioUri != null) || 
                                    (chatState.isAudioMessage && chatState.fileUri != null)) {
                                    // Ưu tiên sử dụng recordedAudioUri nếu có (ghi âm trực tiếp)
                                    val audioUriToSend = recordedAudioUri ?: chatState.fileUri!!
                                    
                                    // Gửi prompt với file âm thanh
                                    val sanitizedPrompt = sanitizeMessage(chatState.prompt)
                                    chatViewModel.onEvent(
                                        ChatUiEvent.SendAudioPrompt(
                                            sanitizedPrompt,
                                            audioUriToSend
                                        )
                                    )
                                    // Reset trạng thái nghe lại sau khi gửi
                                    hasRecordedAudio = false
                                    recordedAudioUri = null
                                } else {
                                    // Gửi prompt bình thường
                                    val sanitizedPrompt = sanitizeMessage(chatState.prompt)
                                    chatViewModel.onEvent(
                                        ChatUiEvent.SendPrompt(
                                            sanitizedPrompt,
                                            chatState.imageUri
                                        )
                                    )
                                }
                                // Thêm dòng này để xóa nội dung ô nhập liệu
                                chatViewModel.onEvent(ChatUiEvent.UpdatePrompt(""))
                                // Xóa file sau khi gửi tin nhắn
                                chatViewModel.onEvent(ChatUiEvent.RemoveFile)
                                // Thêm phản hồi rung khi gửi tin nhắn
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        },
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .size(48.dp),
                        enabled = isTextNotEmpty && !chatState.isLoading
                    ) {
                        Icon(
                            painter = painterResource(
                                id = R.drawable.ic_send
                            ),
                            contentDescription = if (chatState.isEditing) "Lưu chỉnh sửa" else "Gửi tin nhắn",
                            tint = if (isTextNotEmpty && !chatState.isLoading) textColor else textColor.copy(alpha = 0.4f),
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        }
    }
    
    if (showExpandedInputSheet) {
        val haptic = LocalHapticFeedback.current
        val navCtrl = navController
        val takePictureLaunch = takePictureLauncher
        val createImgUri = createImageUriInner
        val onPhotoChange = onPhotoUriChange

        ExpandedChatInputBottomSheet(
            onDismiss = { showExpandedInputSheet = false },
            messageText = chatState.prompt,
            onMessageTextChange = { newText -> chatViewModel.onEvent(ChatUiEvent.UpdatePrompt(newText)) },
            onSendMessage = {
                if (chatState.prompt.isNotBlank() || chatState.imageUri != null || chatState.fileUri != null || hasRecordedAudio) {
                    val sanitizedPrompt = sanitizeMessage(chatState.prompt)
                    // Kiểm tra nếu là file âm thanh
                    if (hasRecordedAudio && recordedAudioUri != null || chatState.isAudioMessage && chatState.fileUri != null) {
                        val audioUriToSend = recordedAudioUri ?: chatState.fileUri!!
                        chatViewModel.onEvent(
                            ChatUiEvent.SendAudioPrompt(
                                sanitizedPrompt,
                                audioUriToSend
                            )
                        )
                        // Reset trạng thái âm thanh
                        hasRecordedAudio = false
                        recordedAudioUri = null
                    } else {
                        // Gửi tin nhắn bình thường
                        chatViewModel.onEvent(ChatUiEvent.SendPrompt(sanitizedPrompt, chatState.imageUri))
                    }
                    chatViewModel.onEvent(ChatUiEvent.UpdatePrompt(""))
                    chatViewModel.onEvent(ChatUiEvent.RemoveFile) // Xóa file sau khi gửi
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showExpandedInputSheet = false
                }
            },
            onAttachImage = {
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onAttachFile = {
                documentPickerResult.launch("*/*")
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onTakePicture = {
                val uri = createImgUri()
                if (uri != null) {
                    onPhotoChange(uri)
                    takePictureLaunch.launch(uri)
                }
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onImageReceived = { uri -> chatViewModel.onEvent(ChatUiEvent.OnImageSelected(uri)) },
            imageUri = chatState.imageUri,
            fileUri = chatState.fileUri,
            fileName = chatState.fileName,
            isFileUploading = chatState.isFileUploading,
            isImageProcessing = chatState.isImageProcessing,
            isAudioMessage = chatState.isAudioMessage || hasRecordedAudio,
            onRemoveImage = {
                chatViewModel.onEvent(ChatUiEvent.RemoveImage)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onRemoveFile = {
                chatViewModel.onEvent(ChatUiEvent.RemoveFile)
                // Đồng thời reset trạng thái audio trong component này
                hasRecordedAudio = false
                recordedAudioUri = null
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onImageClick = { uri ->
                val encodedUrl = Base64.encodeToString(uri.toString().toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
                navCtrl.navigate("fullscreen_image/$encodedUrl")
            }
        )
    }
}

private fun handleImageInClipboardForView(
    clipboardManager: android.content.ClipboardManager, 
    onReceiveContent: (Uri) -> Unit
) {
    if (clipboardManager.hasPrimaryClip()) {
        val primaryClip = clipboardManager.primaryClip
        
        if (primaryClip != null) {
            val description = primaryClip.description
            
            if (description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)) {
                val item = primaryClip.getItemAt(0)
                val uri = item.uri
                
                if (uri != null) {
                    val mimeType = clipboardManager.primaryClip?.description?.getMimeType(0)
                    if (mimeType != null && mimeType.startsWith("image/")) {
                        onReceiveContent(uri)
                        return
                    }
                }
            }
            
            val imageMimeTypes = arrayOf(
                "image/*", "image/png", "image/jpeg", "image/gif", "image/webp",
                "image/", "application/octet-stream"
            )
            
            for (mimeType in imageMimeTypes) {
                if (description.hasMimeType(mimeType)) {
                    for (i in 0 until primaryClip.itemCount) {
                        val item = primaryClip.getItemAt(i)
                        val uri = item.uri
                        
                        if (uri != null) {
                            onReceiveContent(uri)
                            return
                        }
                    }
                }
            }
        }
    }
} 