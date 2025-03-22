// ModelChatItem.kt
package com.ahmedapps.geminichatbot.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmedapps.geminichatbot.R
import fomatText.FormattedTextDisplay
import fomatText.parseFormattedText
import fomatText.TypingConfig
import androidx.compose.runtime.*
import com.ahmedapps.geminichatbot.data.Chat
import kotlinx.coroutines.delay



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
    isMessageTyped: Boolean = false
) {
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val backgroundColor = when {
        isError -> MaterialTheme.colorScheme.error
        isSystemInDarkTheme() -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surface
    }


    val maxWidth = LocalConfiguration.current.screenWidthDp.dp * 0.9f
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val scope = rememberCoroutineScope()

    // Theo dõi trạng thái suy nghĩ dựa vào trạng thái isWaitingForResponse 
    // thay vì chỉ dựa vào response.isEmpty()
    var isThinking by remember(isWaitingForResponse) { mutableStateOf(isWaitingForResponse) }
    
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
        // Icon (Avatar)
        Image(
            painter = painterResource(id = R.drawable.ic_bot),
            contentDescription = "Chatbot Avatar",
            modifier = Modifier
                .padding(top = 7.dp)
                .size(30.dp)
                .clip(CircleShape)
                .align(Alignment.Top)
        )

        Column(horizontalAlignment = Alignment.Start) {
            // Hiển thị "Thinking..." hoặc nội dung tin nhắn
            if (isThinking) {
                Box(
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .clip(RoundedCornerShape(15.dp))
                        .background(backgroundColor)
                        .padding(12.dp)
                ) {
                    ThinkingAnimation()
                }
            } else {
                val formattedResponse = parseFormattedText(response)
                
                FormattedTextDisplay(
                    annotatedString = formattedResponse,
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .clip(RoundedCornerShape(15.dp))
                        .background(backgroundColor)
                        .padding(12.dp),
                    snackbarHostState = snackbarHostState,
                    isNewMessage = showTypingEffect,
                    typingSpeed = typingSpeed,
                    onAnimationComplete = {
                        isAnimationCompleted = true
                    }
                )
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