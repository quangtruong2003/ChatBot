package com.ahmedapps.geminichatbot.fomatText

import androidx.compose.runtime.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay

@Composable
fun AnimatedAnnotatedText(
    annotatedString: AnnotatedString,
    style: TextStyle,
    delayMillis: Long = TypingConfig.DEFAULT_TYPING_SPEED,
    minTypingSpeed: Long = TypingConfig.MIN_TYPING_SPEED,
    maxTypingSpeed: Long = TypingConfig.MAX_TYPING_SPEED, 
    smartTypingSpeed: Boolean = TypingConfig.USE_SMART_TYPING,
    onAnimationComplete: () -> Unit = {},
    stopTypingMessageId: String? = null,
    messageId: String? = null
) {
    var currentLength by remember { mutableStateOf(0) }
    val textLength = annotatedString.text.length
    
    // Tính độ tối ưu tốc độ typing dựa trên độ dài văn bản
    val optimizedDelay = remember(textLength, delayMillis) {
        if (smartTypingSpeed) {
            when {
                textLength > TypingConfig.VERY_LONG_MESSAGE_THRESHOLD -> minTypingSpeed
                textLength > TypingConfig.LONG_MESSAGE_THRESHOLD -> delayMillis / 2
                else -> delayMillis
            }
        } else {
            delayMillis
        }
    }

    // Tính độ trễ thông minh dựa trên ký tự
    fun getSmartDelay(currentChar: Char): Long {
        if (!smartTypingSpeed) return optimizedDelay
        
        return when {
            currentChar == ' ' -> optimizedDelay * TypingConfig.SPACE_SPEED_FACTOR 
            currentChar == '.' || currentChar == ',' || 
            currentChar == '!' || currentChar == '?' -> optimizedDelay * TypingConfig.PUNCTUATION_SPEED_FACTOR
            currentChar == '\n' -> optimizedDelay * TypingConfig.NEWLINE_SPEED_FACTOR
            else -> optimizedDelay
        }
    }

    // Hiệu ứng để kiểm tra nếu cần dừng ngay lập tức
    LaunchedEffect(stopTypingMessageId) {
        if (stopTypingMessageId != null && messageId != null && stopTypingMessageId == messageId && currentLength < textLength) {
            currentLength = textLength
            onAnimationComplete()
        }
    }

    // Khi annotatedString thay đổi, bắt đầu lại animation
    LaunchedEffect(annotatedString) {
        currentLength = 0
        while (currentLength < annotatedString.text.length) {
            if (stopTypingMessageId != null && messageId != null && stopTypingMessageId == messageId) {
                currentLength = textLength
                break
            }
            
            val nextChar = if (currentLength < annotatedString.text.length) 
                            annotatedString.text[currentLength] else ' '
            val smartDelay = getSmartDelay(nextChar)
            
            currentLength++
            delay(smartDelay)
        }
        onAnimationComplete();
    }

    // Lấy ra phần sub-sequence của AnnotatedString
    Text(
        text = if (currentLength > 0) annotatedString.subSequence(0, currentLength) else AnnotatedString(""),
        style = style
    )
}
