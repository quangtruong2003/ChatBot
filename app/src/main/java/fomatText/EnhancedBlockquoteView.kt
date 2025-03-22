package fomatText

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * An enhanced blockquote view with modern styling.
 */
@Composable
fun EnhancedBlockquoteView(
    content: AnnotatedString,
    modifier: Modifier = Modifier,
    isAnimated: Boolean = false,
    typingSpeed: Long = TypingConfig.DEFAULT_TYPING_SPEED,
    onAnimationComplete: () -> Unit = {}
) {
    val borderColor = MaterialTheme.colorScheme.primaryContainer
    val backgroundColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
    
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .border(
                width = 3.dp,
                color = borderColor,
                shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp)
            )
            .background(
                color = backgroundColor,
                shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp)
            )
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 12.dp)
            .animateContentSize()
    ) {
        if (isAnimated) {
            AnimatedAnnotatedText(
                annotatedString = content,
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontStyle = FontStyle.Italic,
                ),
                delayMillis = typingSpeed,
                onAnimationComplete = onAnimationComplete
            )
        } else {
            Text(
                text = content,
                style = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                    fontStyle = FontStyle.Italic,
                )
            )
        }
    }
}

/**
 * A simple blockquote indicator (vertical line) with text content.
 */
@Composable
fun SimpleBlockquoteView(
    content: AnnotatedString,
    modifier: Modifier = Modifier,
    isAnimated: Boolean = false,
    typingSpeed: Long = TypingConfig.DEFAULT_TYPING_SPEED,
    onAnimationComplete: () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp)
                )
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            if (isAnimated) {
                AnimatedAnnotatedText(
                    annotatedString = content,
                    style = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        fontSize = 16.sp,
                        fontStyle = FontStyle.Italic,
                    ),
                    delayMillis = typingSpeed,
                    onAnimationComplete = onAnimationComplete
                )
            } else {
                Text(
                    text = content,
                    style = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                        fontSize = 16.sp,
                        fontStyle = FontStyle.Italic,
                    )
                )
            }
        }
    }
} 