// ModelChatItem.kt
package com.ahmedapps.geminichatbot

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmedapps.geminichatbot.R
import fomatText.FormattedTextDisplay
import fomatText.parseFormattedText
import fomatText.TypingConfig
import androidx.compose.runtime.*
import com.ahmedapps.geminichatbot.data.Chat
import kotlinx.coroutines.delay
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.window.PopupProperties



@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ModelChatItem(
    response: String,
    isError: Boolean,
    onLongPress: (String) -> Unit,
    onImageClick: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    chatId: String? = null,
    isNewChat: Boolean = false,
    typingSpeed: Long = TypingConfig.DEFAULT_TYPING_SPEED,
    onAnimationComplete: () -> Unit = {},
    isWaitingForResponse: Boolean = false,
    isMessageTyped: Boolean = false,
    onDeleteClick: (String) -> Unit = {},
    onRegenerateClick: (String, String, String?) -> Unit = { _, _, _ -> },
    currentUserPrompt: String = "",
    availableModels: List<String> = emptyList(),
    modelDisplayNameMap: Map<String, String> = emptyMap(),
    modelIconMap: Map<String, Int> = emptyMap(),
    selectedModel: String = "",
    chat: Chat? = null
) {
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val backgroundColor = when {
        isError -> MaterialTheme.colorScheme.error
        isSystemInDarkTheme() -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surface
    }
    
    // Context for Toast
    val context = LocalContext.current
    
    // Clipboard manager for copy function
    val clipboardManager = LocalClipboardManager.current

    // Sử dụng toàn màn hình thay vì 0.9f
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val scope = rememberCoroutineScope()

    // Theo dõi trạng thái suy nghĩ dựa vào trạng thái isWaitingForResponse 
    // thay vì chỉ dựa vào response.isEmpty()
    var isThinking by remember(isWaitingForResponse) { mutableStateOf(isWaitingForResponse) }
    
    // Dropdown cho model selection
    var showModelSelection by remember { mutableStateOf(false) }
    
    // Xác định xem tin nhắn này có phải là tin nhắn mới chưa hiển thị hiệu ứng không
    // Đảm bảo chỉ hiển thị hiệu ứng khi:
    // 1. Tin nhắn là mới (isNewChat = true)
    // 2. Có nội dung (response không rỗng)
    // 3. Chưa được đánh dấu là đã hiển thị hiệu ứng (isMessageTyped = false)
    var showTypingEffect by remember(chatId, response, isNewChat, isMessageTyped) { 
        mutableStateOf(isNewChat && response.isNotEmpty() && !isMessageTyped) 
    }
    
    // Khi trạng thái thay đổi, cập nhật
    LaunchedEffect(response, isWaitingForResponse, isMessageTyped) {
        if (response.isNotEmpty()) {
            // Cập nhật trạng thái suy nghĩ
            isThinking = isWaitingForResponse
            
            // Cập nhật hiệu ứng typing - chỉ hiển thị nếu là tin nhắn mới và chưa được đánh dấu
            showTypingEffect = isNewChat && !isMessageTyped
        }
    }
    
    // Đánh dấu khi hiệu ứng typing đã hoàn tất
    var isAnimationCompleted by remember { mutableStateOf(false) }
    
    // LaunchedEffect để gọi callback khi animation hoàn tất
    LaunchedEffect(isAnimationCompleted) {
        if (isAnimationCompleted && showTypingEffect) {
            onAnimationComplete()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        // Đã ẩn Icon (Avatar)
        
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.Start
        ) {
            // Hiển thị "Thinking..." hoặc nội dung tin nhắn
            if (isThinking) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(15.dp))
                        .background(backgroundColor)
                        .padding(start = 12.dp, top = 12.dp, end = 0.dp, bottom = 12.dp)
                ) {
                    ThinkingAnimation()
                }
            } else {
                val formattedResponse = parseFormattedText(response)
                
                Column {
                    // Message content
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(15.dp))
                            .background(backgroundColor)
                            .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 12.dp)
                    ) {
                        FormattedTextDisplay(
                            annotatedString = formattedResponse,
                            modifier = Modifier.fillMaxWidth(),
                            snackbarHostState = snackbarHostState,
                            isNewMessage = showTypingEffect,
                            typingSpeed = typingSpeed,
                            onAnimationComplete = {
                                isAnimationCompleted = true
                            }
                        )
                    }

                    if (chatId != null && !isThinking) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 0.dp, start = 12.dp),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Regenerate button
                            Box {
                                IconButton(
                                    onClick = { 
                                        showModelSelection = true
                                    },
                                    modifier = Modifier.size(30.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_callback),
                                        contentDescription = "Tạo lại",
                                        tint = textColor.copy(alpha = 0.7f),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                
                                // Model selection dropdown
                                DropdownMenu(
                                    expanded = showModelSelection,
                                    onDismissRequest = { showModelSelection = false },
                                    properties = PopupProperties(focusable = false),
                                    modifier = Modifier
                                        .crop(vertical = 8.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(15.dp)
                                        )
                                ) {
                                    availableModels.forEach { model ->
                                        val displayName = modelDisplayNameMap[model] ?: model
                                        val iconResourceId = modelIconMap[displayName] ?: R.drawable.ic_bot
                                        
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        painter = painterResource(id = iconResourceId),
                                                        contentDescription = "Model Icon",
                                                        tint = textColor,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = displayName,
                                                        color = textColor,
                                                        fontWeight = if (model == selectedModel) FontWeight.Bold else FontWeight.Normal
                                                    )
                                                }
                                            },
                                            onClick = {
                                                // Kiểm tra và tạo prompt phù hợp để regenerate
                                                var canRegenerate = false
                                                var effectivePrompt = ""
                                                
                                                // Option 1: Sử dụng currentUserPrompt được truyền vào từ tham số
                                                if (currentUserPrompt.isNotEmpty()) {
                                                    canRegenerate = true
                                                    effectivePrompt = currentUserPrompt
                                                } 
                                                // Option 2: Kiểm tra thông tin từ đối tượng Chat (tin nhắn người dùng) được truyền vào
                                                else if (chat != null) {
                                                    // Nếu là tin nhắn chứa hình ảnh
                                                    if (chat.imageUrl != null) {
                                                        canRegenerate = true
                                                        effectivePrompt = "Hãy mô tả hình ảnh này"
                                                    } 
                                                    // Nếu là tin nhắn chứa file
                                                    else if (chat.isFileMessage) {
                                                        canRegenerate = true
                                                        val fileName = chat.fileName ?: "không rõ tên"
                                                        effectivePrompt = "Hãy tóm tắt nội dung file $fileName"
                                                    }
                                                }
                                                
                                                if (canRegenerate) {
                                                    // Xử lý regenerate với thông tin phù hợp
                                                    onRegenerateClick(
                                                        effectivePrompt,
                                                        chatId,
                                                        chat?.imageUrl // Truyền URL hình ảnh nếu có
                                                    )
                                                    showModelSelection = false
                                                } else {
                                                    // Thông báo khi không thể regenerate
                                                    Toast.makeText(
                                                        context, 
                                                        "Không thể tạo lại vì không tìm thấy prompt gốc", 
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            
                            // Copy button
                            IconButton(
                                onClick = { 
                                    clipboardManager.setText(formattedResponse)
                                    Toast.makeText(context, "Đã sao chép vào bộ nhớ tạm", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_copy),
                                    contentDescription = "Sao chép",
                                    tint = textColor.copy(alpha = 0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            
                            // Delete button
                            IconButton(
                                onClick = { 
                                    onDeleteClick(chatId)
                                },
                                modifier = Modifier.size(30.dp)
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
    }
}

// Composable cho animation "Thinking..."
@Composable
fun ThinkingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ), label = ""
    )

    Text(
        text = "Đang suy nghĩ...",
        modifier = Modifier
            .padding(start = 8.dp, top = 4.dp)
            .alpha(alpha),
        fontSize = 16.sp,
        color = if (isSystemInDarkTheme()) Color.White else Color.DarkGray
    )
}