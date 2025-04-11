package com.ahmedapps.geminichatbot.ui.components

import android.content.ClipDescription
import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.Base64
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import android.net.Uri

fun sanitizeMessage(input: String): String {
    return input
        .lines() // Chia chuỗi thành danh sách các dòng
        .filter { it.isNotBlank() } // Loại bỏ các dòng trống
        .joinToString("\n") // Ghép lại thành một chuỗi với dấu xuống dòng
        .trim() // Loại bỏ khoảng trắng ở đầu và cuối
}

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

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(align = Alignment.Top)
            .heightIn(min = 40.dp, max = 160.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
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
                        @Volatile private var isUpdating = false
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: Editable?) {
                            // Chỉ cập nhật ViewModel nếu text thực sự thay đổi bởi người dùng (không phải từ khối update)
                            // Sử dụng R.id.text_watcher_tag_key
                            if (getTag(textWatcherTagKey) != "UPDATE_IN_PROGRESS") {
                                if (!isUpdating) {
                                    val newText = s?.toString() ?: ""
                                    if (newText != chatState.prompt) {
                                        isUpdating = true
                                        chatViewModel.onEvent(ChatUiEvent.UpdatePrompt(newText))
                                        isUpdating = false
                                    }
                                    post {
                                        shouldShowExpandButton = canScrollVertically(1) || canScrollVertically(-1)
                                    }
                                }
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
        
        AnimatedVisibility(
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
                if (chatState.prompt.isNotBlank() || chatState.imageUri != null || chatState.fileUri != null) {
                    val sanitizedPrompt = sanitizeMessage(chatState.prompt)
                    chatViewModel.onEvent(ChatUiEvent.SendPrompt(sanitizedPrompt, chatState.imageUri))
                    chatViewModel.onEvent(ChatUiEvent.UpdatePrompt(""))
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showExpandedInputSheet = false
                }
            },
            onAttachImage = {
                imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onAttachFile = {
                documentPickerLauncher.launch("*/*")
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
            onRemoveImage = {
                chatViewModel.onEvent(ChatUiEvent.RemoveImage)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onRemoveFile = {
                chatViewModel.onEvent(ChatUiEvent.RemoveFile)
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