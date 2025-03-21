package fomatText

import androidx.compose.runtime.*
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.material3.Text
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay

@Composable
fun AnimatedAnnotatedText(
    annotatedString: AnnotatedString,
    style: TextStyle,
    delayMillis: Long = 5L,
    onAnimationComplete: () -> Unit = {}
) {
    var currentLength by remember { mutableStateOf(0) }

    // Khi annotatedString thay đổi, bắt đầu lại animation
    LaunchedEffect(annotatedString) {
        currentLength = 0
        while (currentLength < annotatedString.text.length) {
            currentLength++
            delay(delayMillis)
        }
        onAnimationComplete();
    }

    // Lấy ra phần sub-sequence của AnnotatedString
    Text(
        text = annotatedString.subSequence(0, currentLength),
        style = style
    )
}
