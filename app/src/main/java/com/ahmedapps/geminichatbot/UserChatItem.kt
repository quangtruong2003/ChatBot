// UserChatItem.kt
package com.ahmedapps.geminichatbot

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import fomatText.FormattedTextDisplay
import fomatText.parseFormattedText
import kotlinx.coroutines.launch
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.ui.platform.LocalFocusManager
import com.ahmedapps.geminichatbot.ui.components.AudioPlayerComponent
import android.util.Log
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import kotlinx.coroutines.delay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.window.PopupProperties
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.content.Context
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Dp




@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserChatItem(
    prompt: String,
    imageUrl: String?,
    isError: Boolean,
    isFileMessage: Boolean = false,
    fileName: String? = null,
    onLongPress: (String) -> Unit,
    onImageClick: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    chatId: String? = null,
    onDeleteClick: (String) -> Unit = {},
    onEditClick: (String) -> Unit = {},
    isBeingEdited: Boolean = false,
    isAudioFile: Boolean = false
) {
    val isDarkTheme = isSystemInDarkTheme()
    val backgroundColor = when {
        isError -> MaterialTheme.colorScheme.error
        isBeingEdited -> MaterialTheme.colorScheme.primaryContainer
        isDarkTheme -> Color(0x43FFFFFF)
        else -> Color(0x97FFFFFF)
    }
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxImageHeight = (screenHeight * 0.3f).coerceAtLeast(175.dp)
    val maxWidth = LocalConfiguration.current.screenWidthDp.dp * 0.7f

    val formattedPrompt = parseFormattedText(prompt)
    val scope = rememberCoroutineScope()
    
    // Context để hiển thị Toast
    val context = LocalContext.current
    
    // Clipboard manager để copy văn bản
    val clipboardManager = LocalClipboardManager.current

    // Thêm biến state để quản lý hiển thị dấu tích khi copy
    var showCopyTick by remember { mutableStateOf(false) }
    
    // Thêm biến state để theo dõi việc hiển thị dropdown menu cho hình ảnh
    var showImageDropdownMenu by remember { mutableStateOf(false) }
    
    // ImageLoader để tải và xử lý hình ảnh
    val imageLoader = ImageLoader.Builder(context).build()
    
    // LaunchedEffect để ẩn dấu tích sau 2 giây
    LaunchedEffect(showCopyTick) {
        if (showCopyTick) {
            delay(2000)
            showCopyTick = false
        }
    }
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

    // Log kiểm tra thông tin
    Log.d("UserChatItem", "Rendering: isFileMessage=$isFileMessage, fileName=$fileName, imageUrl=$imageUrl")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp, bottom = 0.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Column(horizontalAlignment = Alignment.End) {
            // Khai báo biến localIsAudioFile ở ngoài để có phạm vi rộng hơn
            var localIsAudioFile = false
            
            // Xử lý file tin nhắn (nếu có)
            if (isFileMessage && fileName != null) {
                val safeFileName = fileName
                
                // Kiểm tra có URL không (có thể không có nếu vừa gửi đi)
                val safeImageUrl = imageUrl
                
                // Xác định loại file dựa trên phần mở rộng
                localIsAudioFile = safeFileName.endsWith(".ogg") || 
                              safeFileName.endsWith(".m4a") || 
                              safeFileName.endsWith(".mp3") || 
                              safeFileName.endsWith(".wav")
                
                val isTextFile = safeFileName.endsWith(".txt") || 
                              safeFileName.endsWith(".md") || 
                              safeFileName.endsWith(".json") ||
                              safeFileName.endsWith(".xml") ||
                              safeFileName.endsWith(".html") ||
                              safeFileName.endsWith(".htm") ||
                              safeFileName.endsWith(".css") ||
                              safeFileName.endsWith(".js") ||
                              safeFileName.endsWith(".java") ||
                              safeFileName.endsWith(".kt") ||
                              safeFileName.endsWith(".py")
                              
                val isPdfFile = safeFileName.endsWith(".pdf")
                val isDocFile = safeFileName.endsWith(".doc") || safeFileName.endsWith(".docx")
                
                // Xử lý theo loại file
                if (localIsAudioFile && safeImageUrl != null) {
                    // Hiển thị AudioPlayer cho file âm thanh
                    AudioPlayerComponent(
                        context = LocalContext.current,
                        audioSource = safeImageUrl,
                        modifier = Modifier
                            .widthIn(max = maxWidth)
                            .heightIn(max = 80.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        isCompact = true
                    )
                } else {
                    // Có thể hiển thị hình ảnh nếu có URL và không phải file âm thanh
                    if (safeImageUrl != null && !localIsAudioFile && 
                        (safeImageUrl.startsWith("http") || safeImageUrl.startsWith("content"))) {
                        AsyncImage(
                            model = safeImageUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(8.dp)
                                .heightIn(max = 240.dp)
                                .widthIn(max = 240.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                    
                    // LUÔN HIỂN THỊ THÔNG TIN FILE (icon + tên)
                    // Bất kể có URL hay không, hiển thị thông tin file
                    Row(
                        modifier = Modifier
                            .widthIn(max = maxWidth)
                            .padding(start = 8.dp, end = 8.dp, top = 4.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Hiển thị icon trong background tròn
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = when {
                                        isTextFile -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        isPdfFile -> Color(0xFFF44336).copy(alpha = 0.15f)
                                        isDocFile -> Color(0xFF2196F3).copy(alpha = 0.15f)
                                        else -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f)
                                    },
                                    shape = CircleShape
                                )
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                        Icon(
                            imageVector = when {
                                isTextFile -> Icons.Default.InsertDriveFile
                                isPdfFile -> Icons.Default.InsertDriveFile
                                isDocFile -> Icons.Default.InsertDriveFile
                                else -> Icons.Default.Attachment
                            },
                            contentDescription = null,
                            tint = when {
                                isTextFile -> MaterialTheme.colorScheme.primary
                                isPdfFile -> Color(0xFFF44336) // Đỏ cho PDF
                                isDocFile -> Color(0xFF2196F3) // Xanh cho DOC
                                    else -> MaterialTheme.colorScheme.secondary
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        // Thông tin file
                        Column(modifier = Modifier.weight(1f)) {
                            // Tên file
                        Text(
                            text = safeFileName,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            
                            // Thêm loại file (tùy chọn)
                            val fileType = when {
                                isTextFile -> "Text Document"
                                isPdfFile -> "PDF Document"
                                isDocFile -> "Word Document"
                                else -> "File"
                            }
                            
                            Text(
                                text = fileType,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                maxLines = 1
                            )
                        }
                    }
                }
                
                // Sau khi hiển thị thông tin file, hiển thị prompt nếu có
                if (prompt.isNotEmpty() && !localIsAudioFile) { // Sử dụng biến localIsAudioFile
                    Spacer(modifier = Modifier.height(4.dp)) // Thêm khoảng cách nhỏ
                    SelectionContainer {
                        FormattedTextDisplay(
                            annotatedString = formattedPrompt,
                            modifier = Modifier
                                .widthIn(max = maxWidth)
                                .clip(RoundedCornerShape(17.dp))
                                .background(backgroundColor)
                                .padding(12.dp),
                            snackbarHostState = snackbarHostState
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp)) // Thêm khoảng cách trước các nút
                }
                
                // Thêm nút xóa cho file khi không có prompt
                if (prompt.isEmpty() && chatId != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .widthIn(max = maxWidth)
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { 
                                onDeleteClick(chatId)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_bin),
                                contentDescription = "Xóa",
                                tint = textColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            } else if (imageUrl != null) {
                // Hiển thị hình ảnh như bình thường (không phải file)
                Box {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Your Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .heightIn(max = maxImageHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color.Transparent, RoundedCornerShape(12.dp))
                        .combinedClickable(
                            onClick = { onImageClick(imageUrl) },
                                onLongClick = { 
                                    showImageDropdownMenu = true 
                                }
                            )
                    )
                    
                    // Dropdown menu cho hình ảnh khi nhấn giữ
                    DropdownMenu(
                        expanded = showImageDropdownMenu,
                        onDismissRequest = { showImageDropdownMenu = false },
                        properties = PopupProperties(focusable = true),
                        modifier = Modifier
                            .crop(vertical = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Copy hình ảnh") },
                            onClick = {
                                // Copy hình ảnh vào clipboard (việc này yêu cầu tải bitmap)
                                scope.launch {
                                    val bitmap = loadBitmapFromUrl(context, imageUrl, imageLoader)
                                    if (bitmap != null) {
                                        // Chuyển bitmap thành ảnh dạng văn bản và lưu vào clipboard
                                        // Đây là một hành động mô phỏng vì hầu hết clipboard không
                                        // hỗ trợ trực tiếp hình ảnh mà không có plugin
                                        val size = "Width: ${bitmap.width}px, Height: ${bitmap.height}px"
                                        clipboardManager.setText(AnnotatedString("Image copied: $size"))
                                        
                                        // Hiển thị dấu tick hoàn thành
                                        showCopyTick = true
                                        showImageDropdownMenu = false
                                        
                                        // Hiển thị thông báo
                                        Toast.makeText(context, "Đã sao chép hình ảnh", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Không thể sao chép hình ảnh", Toast.LENGTH_SHORT).show()
                                        showImageDropdownMenu = false
                                    }
                                }
                            }
                        )
                        
                        DropdownMenuItem(
                            text = { Text("Copy link hình ảnh") },
                            onClick = {
                                clipboardManager.setText(AnnotatedString(imageUrl))
                                showCopyTick = true
                                showImageDropdownMenu = false
                            }
                        )
                    }
                }
                
                // Thêm nút xóa nếu không kèm tin nhắn (đã xóa nút copy)
                if (prompt.isEmpty() && chatId != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .widthIn(max = maxWidth)
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Chỉ còn nút xóa (đã loại bỏ nút copy)
                        IconButton(
                            onClick = { 
                                onDeleteClick(chatId)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_bin),
                                contentDescription = "Xóa",
                                tint = textColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            if (prompt.isNotEmpty()) {
                // Chỉ hiển thị prompt ở đây nếu KHÔNG phải là tin nhắn file
                // (Vì đã xử lý hiển thị prompt cho file ở trên)
                if (!isFileMessage) {
                    SelectionContainer {
                        FormattedTextDisplay(
                            annotatedString = formattedPrompt,
                            modifier = Modifier
                                .widthIn(max = maxWidth)
                                .clip(RoundedCornerShape(17.dp))
                                .background(backgroundColor)
                                .padding(12.dp),
                            snackbarHostState = snackbarHostState
                        )
                    }
                }
            }
            
            // Thêm các nút hành động (chỉ hiển thị khi có chatId và có tin nhắn văn bản hoặc là tin nhắn file có hình ảnh)
            if (chatId != null && (prompt.isNotEmpty() || (isFileMessage && imageUrl != null))) {
                Row(
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .padding(top = 0.dp, end = 0.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Nút edit (chỉ hiển thị khi tin nhắn có nội dung)
                    if (prompt.isNotEmpty()) {
                        IconButton(
                            onClick = { 
                                onEditClick(chatId)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_editrequest),
                                contentDescription = "Chỉnh sửa",
                                tint = textColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    // Nút copy (chỉ hiển thị khi tin nhắn có nội dung)
                    if (prompt.isNotEmpty()) {
                        Box {
                        IconButton(
                            onClick = { 
                                clipboardManager.setText(formattedPrompt)
                                    showCopyTick = true
                            },
                            modifier = Modifier.size(28.dp)
                            ) {
                                // Hiệu ứng chuyển đổi giữa biểu tượng copy và dấu tích
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = !showCopyTick,
                                    enter = fadeIn(animationSpec = tween(300)),
                                    exit = fadeOut(animationSpec = tween(300))
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_copy),
                                contentDescription = "Sao chép",
                                tint = textColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                                }
                                
                                androidx.compose.animation.AnimatedVisibility(
                                    visible = showCopyTick,
                                    enter = fadeIn(animationSpec = tween(300)),
                                    exit = fadeOut(animationSpec = tween(300))
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_accept),
                                        contentDescription = "Đã sao chép",
                                        tint = Color(0xFF4CAF50), // Màu xanh lá
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    // Nút xóa (luôn hiển thị nếu có chatId)
                    IconButton(
                        onClick = { 
                            onDeleteClick(chatId)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_bin),
                            contentDescription = "Xóa",
                            tint = textColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// Hàm trợ giúp để tải Bitmap từ URL
suspend fun loadBitmapFromUrl(context: Context, url: String, imageLoader: ImageLoader): Bitmap? = withContext(Dispatchers.IO) {
    try {
        val request = ImageRequest.Builder(context)
            .data(url)
            .build()
            
        val result = imageLoader.execute(request)
        if (result is coil.request.SuccessResult) {
            val drawable = result.drawable
            if (drawable is BitmapDrawable) {
                return@withContext drawable.bitmap
            }
        }
        null
    } catch (e: Exception) {
        Log.e("UserChatItem", "Error loading bitmap: ${e.message}")
        null
    }
}

