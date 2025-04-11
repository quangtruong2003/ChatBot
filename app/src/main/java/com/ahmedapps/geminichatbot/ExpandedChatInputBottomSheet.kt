package com.ahmedapps.geminichatbot

import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import android.content.ClipDescription
import android.util.Log
import android.widget.Toast
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import android.view.KeyEvent
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatEditText
import androidx.compose.foundation.isSystemInDarkTheme
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.ViewCompat
import android.content.Context
import android.view.inputmethod.InputMethodManager

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExpandedChatInputBottomSheet(
    onDismiss: () -> Unit,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSendMessage: () -> Unit,
    onAttachImage: () -> Unit,
    onAttachFile: () -> Unit,
    onTakePicture: () -> Unit,
    onImageReceived: (Uri) -> Unit,
    imageUri: Uri? = null,
    fileUri: Uri? = null,
    fileName: String? = null,
    isFileUploading: Boolean = false,
    onRemoveImage: () -> Unit = {},
    onRemoveFile: () -> Unit = {},
    onImageClick: (Uri) -> Unit = {}
) {
    val context = LocalContext.current
    var showSourceMenu by remember { mutableStateOf(false) }
    val hapticFeedback = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    // Xác định theme và màu chữ ở phạm vi Composable
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { 
            // Custom drag handle cho BottomSheet
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Divider(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                //.padding(bottom = 12.dp), // Thêm padding bổ sung ở phía dưới cho cử chỉ điều hướng
        ) {
            // Hiển thị ảnh đã chọn (nếu có)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = if (imageUri != null) 8.dp else 0.dp)
            ) {
                Column {
                    imageUri?.let { uri ->
                        Box(
                            modifier = Modifier
                                .size(70.dp)
                        ) {
                            // Hình ảnh đã chọn
                            AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(uri)
                                    .size(coil.size.Size.ORIGINAL)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Hình ảnh đã chọn",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(10.dp))
                                    .combinedClickable(
                                        onClick = {
                                            onImageClick(uri)
                                        },
                                        onLongClick = { }
                                    )
                            )

                            // Nút 'X' để xóa ảnh
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .align(Alignment.TopEnd)
                                    .offset(x = 4.dp, y = (-4).dp)
                                    .clip(RoundedCornerShape(50))
                                    .clickable {
                                        onRemoveImage()
                                    }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Xóa ảnh",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            color = Color(0xFFAAAAAA),
                                            shape = RoundedCornerShape(50)
                                        )
                                        .padding(4.dp)
                                )
                            }
                        }
                    }

                    // Thêm phần hiển thị file vào đây
                    fileUri?.let { uri ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth(0.5f)
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icon file
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .padding(start = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(
                                        id = if (isFileUploading) R.drawable.ic_fileuploaderror else R.drawable.ic_fileuploaded
                                    ),
                                    contentDescription = "File đã chọn",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            
                            // Tên file nằm bên phải icon
                            Text(
                                text = fileName ?: "File không xác định",
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            // Nút 'X' để xóa file
                            Box(
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .size(20.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFAAAAAA))
                                    .clickable {
                                        onRemoveFile()
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Xóa file",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }
            
            // Text input area với khả năng cuộn
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 2.dp
            ) {
                // Sử dụng Box để bọc AndroidView và hỗ trợ cuộn tốt hơn
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    AndroidView(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        factory = { ctx ->
                            AppCompatEditText(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                gravity = android.view.Gravity.TOP or android.view.Gravity.START
                                inputType = android.text.InputType.TYPE_CLASS_TEXT or 
                                            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or 
                                            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                                
                                // Cải thiện khả năng cuộn văn bản
                                isVerticalScrollBarEnabled = true
                                setVerticalScrollBarEnabled(true)
                                isSingleLine = false
                                maxLines = Integer.MAX_VALUE // Cho phép hiển thị nhiều dòng không giới hạn
                                setHorizontallyScrolling(false) // Tắt cuộn ngang để tự động ngắt dòng
                                
                                // Đặt overScrollMode để cải thiện trải nghiệm cuộn
                                overScrollMode = android.view.View.OVER_SCROLL_ALWAYS
                                
                                // Cài đặt OnTouchListener để xử lý việc cuộn và click
                                var startY = 0f
                                var isScrolling = false
                                
                                setOnTouchListener { v, event ->
                                    when (event.action) {
                                        android.view.MotionEvent.ACTION_DOWN -> {
                                            // Lưu vị trí bắt đầu chạm
                                            startY = event.y
                                            isScrolling = false
                                            
                                            // Ngăn parent chặn touch events
                                            v.parent.requestDisallowInterceptTouchEvent(true)
                                            
                                            // Đảm bảo view có focus
                                            if (!v.hasFocus()) {
                                                v.requestFocus()
                                            }
                                            
                                            false // Không tiêu thụ sự kiện để EditText vẫn xử lý mặc định
                                        }
                                        android.view.MotionEvent.ACTION_MOVE -> {
                                            // Xác định cử chỉ cuộn nếu di chuyển đủ xa
                                            val moveDistance = Math.abs(event.y - startY)
                                            if (moveDistance > 10f) {
                                                isScrolling = true
                                            }
                                            
                                            // Nếu có thể cuộn, điều khiển sự can thiệp của parent
                                            if ((v as AppCompatEditText).canScrollVertically(1) || 
                                                (v as AppCompatEditText).canScrollVertically(-1)) {
                                                // Giữ touch events cho EditText để xử lý cuộn
                                                v.parent.requestDisallowInterceptTouchEvent(true)
                                            } else {
                                                // Cho phép parent xử lý nếu không thể cuộn
                                                v.parent.requestDisallowInterceptTouchEvent(false)
                                            }
                                            false // Không tiêu thụ sự kiện
                                        }
                                        android.view.MotionEvent.ACTION_UP -> {
                                            // Chỉ mở bàn phím và phản hồi xúc giác khi là click thật sự (không cuộn)
                                            if (!isScrolling && v.hasFocus()) {
                                                // Hiển thị bàn phím khi thực sự là click
                                                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                                                imm.showSoftInput(v, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
                                                
                                                // Phản hồi xúc giác nhẹ
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                            
                                            v.parent.requestDisallowInterceptTouchEvent(false)
                                            false // Không tiêu thụ sự kiện để EditText xử lý mặc định (vị trí con trỏ)
                                        }
                                        else -> false
                                    }
                                }
                                
                                setTextIsSelectable(true)
                                isFocusable = true
                                isFocusableInTouchMode = true
                                background = null // Bỏ background mặc định
                                setHintTextColor(Color.Gray.toArgb()) // Màu hint
                                setTextColor(Color.Black.toArgb()) // Màu chữ (sẽ được cập nhật)
                                hint = "Nhập tin nhắn cho ChatAI" // Hint text
                                textSize = 16f // Cỡ chữ

                                // Xử lý dán ảnh bằng setOnReceiveContentListener
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
                                                // Gọi callback để xử lý ảnh đã chọn
                                                onImageReceived(uri)
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
//                                                scope.launch {
//                                                    Toast.makeText(context, "Đã dán ảnh", Toast.LENGTH_SHORT).show()
//                                                }
                                                return@setOnReceiveContentListener null // Đã xử lý URI
                                            }
                                        }
                                    }
                                    remaining // Trả về nội dung không được xử lý (nếu có)
                                }

                                // Listener để cập nhật state khi text thay đổi
                                addTextChangedListener(object : android.text.TextWatcher {
                                    private var isUpdating = false
                                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                                    override fun afterTextChanged(s: android.text.Editable?) {
                                        if (isUpdating) return
                                        val newText = s?.toString() ?: ""
                                        if (messageText != newText) {
                                            isUpdating = true
                                            onMessageTextChange(newText) // Gọi callback cập nhật state
                                            isUpdating = false
                                        }
                                    }
                                })
                            }
                        },
                        update = { view ->
                            // Sử dụng textColor đã được xác định ở phạm vi ngoài
                            view.setTextColor(textColor.toArgb())

                            // Chỉ cập nhật text nếu nó khác và view không focus
                            // để tránh mất vị trí con trỏ hoặc reset text khi xóa
                            if (view.text.toString() != messageText && !view.hasFocus()) {
                                view.setText(messageText)
                                // Di chuyển con trỏ về cuối sau khi cập nhật text (nếu cần)
                                // view.setSelection(messageText.length) 
                            }
                            // Không cần requestFocus ở đây vì bottom sheet thường tự focus
                        }
                    )
                }
            }
            
            // Phần nút gửi và tùy chọn đính kèm
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Nút '+' (Thêm ảnh)
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
                        tint = LocalContentColor.current,
                        modifier = Modifier.alpha(0.6f)
                    )
                }

                // --- DropdownMenu Hiển thị các lựa chọn ---
                DropdownMenu(
                    expanded = showSourceMenu,
                    onDismissRequest = { showSourceMenu = false },
                    properties = PopupProperties(focusable = false),
                    modifier = Modifier
//                        .background(
//                            MaterialTheme.colorScheme.surface,
//                            shape = RoundedCornerShape(15.dp)
//                        )
                        .crop(vertical = 8.dp)
                        .width(IntrinsicSize.Max),
                    offset = DpOffset(x = 0.dp, y = (-8).dp)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Gửi tệp tài liệu",
                                style = TextStyle(
                                    color = LocalContentColor.current,
                                    fontSize = 16.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            )
                        },
                        onClick = {
                            onAttachFile()
                            showSourceMenu = false
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        trailingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_chosefile),
                                contentDescription = "Gửi tệp tài liệu",
                                tint = LocalContentColor.current
                            )
                        }
                    )
                    Divider(color = Color(0x14FFFFFF), thickness = 0.6.dp)
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Chụp ảnh",
                                style = TextStyle(
                                    color = LocalContentColor.current,
                                    fontSize = 16.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            )
                        },
                        onClick = {
                            // Gọi callback để bật máy ảnh mà không đóng BottomSheet
                            onTakePicture()
                            showSourceMenu = false
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        trailingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.camera_add),
                                contentDescription = "Chụp ảnh",
                                tint = LocalContentColor.current
                            )
                        }
                    )
                    Divider(color = Color(0x14FFFFFF), thickness = 0.6.dp)
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = "Thư viện ảnh",
                                style = TextStyle(
                                    color = LocalContentColor.current,
                                    fontSize = 16.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                                )
                            )
                        },
                        onClick = {
                            onAttachImage()
                            showSourceMenu = false
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        trailingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_addpicture),
                                contentDescription = "Thư viện ảnh",
                                tint = LocalContentColor.current
                            )
                        }
                    )
                }
                
                // Nút gửi tin nhắn
                IconButton(
                    onClick = onSendMessage,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .size(48.dp),
                    enabled = messageText.isNotBlank() || imageUri != null || fileUri != null
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_send),
                        contentDescription = "Gửi",
                        tint = if (messageText.isNotBlank() || imageUri != null || fileUri != null) 
                            LocalContentColor.current 
                        else 
                            LocalContentColor.current.copy(alpha = 0.4f),
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }
    }
}

// Hàm để xử lý ảnh trong clipboard
private fun handleImageInClipboard(
    clipboardManager: android.content.ClipboardManager, 
    onReceiveContent: (Uri) -> Unit
) {
    Log.d("ExpandedInput", "handleImageInClipboard executing") // Logging
    if (clipboardManager.hasPrimaryClip()) {
        val primaryClip = clipboardManager.primaryClip
        
        if (primaryClip != null) {
            val description = primaryClip.description
            
            if (description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)) {
                val item = primaryClip.getItemAt(0)
                val uri = item.uri
                
                if (uri != null) {
                    Log.d("ExpandedInput", "Found image URI in clipboard (MIMETYPE_TEXT_URILIST): $uri") // Logging
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
                            Log.d("ExpandedInput", "Found image URI in clipboard (imageMimeTypes loop): $uri") // Logging
                            onReceiveContent(uri)
                            return
                        }
                    }
                }
            }
        }
    }
    else {
        Log.d("ExpandedInput", "handleImageInClipboard: No primary clip") // Logging
    }
} 