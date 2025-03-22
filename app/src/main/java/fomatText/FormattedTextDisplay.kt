// FormattedTextDisplay.kt
package fomatText

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmedapps.geminichatbot.R
import kotlinx.coroutines.launch

/**
 * Composable function to display formatted text based on AnnotatedString.
 */
@Composable
fun FormattedTextDisplay(
    annotatedString: AnnotatedString,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState,
    isNewMessage: Boolean = false,
    typingSpeed: Long = TypingConfig.DEFAULT_TYPING_SPEED,
    onAnimationComplete: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var animationCompleted by remember(annotatedString) { mutableStateOf(!isNewMessage) }

    val sections = remember(annotatedString) {
        splitAnnotatedString(annotatedString)
    }
    
    // Thêm LaunchedEffect để gọi callback khi animation hoàn tất
    LaunchedEffect(animationCompleted) {
        if (animationCompleted && isNewMessage) {
            onAnimationComplete()
        }
    }

    Column(modifier = modifier) {
        for (i in sections.indices) {
            val section = sections[i]

            when (section.type) {
                "CODE_BLOCK" -> {
                    CodeBlockView(
                        code = section.content,
                        clipboardManager = clipboardManager,
                        context = context,
                        language = section.language,
                        snackbarHostState = snackbarHostState,
                        isAnimated = isNewMessage && !animationCompleted,
                        typingSpeed = typingSpeed,
                        onAnimationComplete = { if (i == sections.size - 1) animationCompleted = true }
                    )
                }
                "INLINE_CODE" -> {
                    if (isNewMessage && !animationCompleted) {
                        SelectionContainer {
                            AnimatedAnnotatedText(
                                annotatedString = syntaxHighlight(section.content),
                                style = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    background = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface,
                                    color = if (isSystemInDarkTheme()) Color.White else Color.Black,
                                    fontSize = 17.sp
                                ),
                                delayMillis = typingSpeed,
                                onAnimationComplete = { if (i == sections.size - 1) animationCompleted = true }
                            )
                        }
                    } else {
                        InlineCodeText(text = section.content, isError = false)
                    }
                }
                else -> {
                    SelectionContainer {
                        if (isNewMessage && !animationCompleted) {
                            AnimatedAnnotatedText(
                                annotatedString = section.annotatedString,
                                style = TextStyle(
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                ),
                                delayMillis = typingSpeed,
                                onAnimationComplete = { if (i == sections.size - 1) animationCompleted = true }
                            )
                        } else {
                            Text(
                                text = section.annotatedString,
                                style = TextStyle(
                                    fontSize = 17.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Function to split AnnotatedString into sections based on annotations.
 */
fun splitAnnotatedString(annotatedString: AnnotatedString): List<AnnotatedStringSection> {
    val sections = mutableListOf<AnnotatedStringSection>()
    var currentIndex = 0


    while (currentIndex < annotatedString.length) {
        val codeBlockAnnotation = annotatedString.getStringAnnotations(tag = "CODE_BLOCK", start = currentIndex, end = annotatedString.length).firstOrNull()
        val inlineCodeAnnotation = annotatedString.getStringAnnotations(tag = "INLINE_CODE", start = currentIndex, end = annotatedString.length).firstOrNull()

        val nextAnnotation = listOfNotNull(codeBlockAnnotation, inlineCodeAnnotation).minByOrNull { it.start }

        if (nextAnnotation != null) {
            if (nextAnnotation.start > currentIndex) {
                // Thêm văn bản trước chú thích
                val textBefore = annotatedString.subSequence(currentIndex, nextAnnotation.start)
                sections.add(
                    AnnotatedStringSection(
                        type = "TEXT",
                        content = textBefore.text,
                        annotatedString = textBefore
                    )
                )
            }

            // Thêm nội dung chú thích
            when (nextAnnotation.tag) {
                "CODE_BLOCK" -> {
                    val parts = nextAnnotation.item.split("::", limit = 2)
                    val language = if (parts.size > 1) parts[0] else null
                    val codeContent = if (parts.size > 1) parts[1] else parts[0]
                    sections.add(
                        AnnotatedStringSection(
                            type = "CODE_BLOCK",
                            content = codeContent,
                            language = language
                        )
                    )
                }
                "INLINE_CODE" -> {
                    sections.add(
                        AnnotatedStringSection(
                            type = "INLINE_CODE",
                            content = nextAnnotation.item
                        )
                    )
                }
            }

            currentIndex = nextAnnotation.end

        } else {
            // Không còn chú thích nào nữa; thêm văn bản còn lại
            val remainingText = annotatedString.subSequence(currentIndex, annotatedString.length)
            sections.add(
                AnnotatedStringSection(
                    type = "TEXT",
                    content = remainingText.text,
                    annotatedString = remainingText
                )
            )
            break
        }
    }

    return sections
}

/**
 * Composable function to display code blocks with optional language.
 */

@Composable
fun CodeBlockView(
    code: String,
    clipboardManager: ClipboardManager,
    context: android.content.Context,
    language: String?,
    snackbarHostState: SnackbarHostState,
    isAnimated: Boolean = false,
    typingSpeed: Long = TypingConfig.DEFAULT_TYPING_SPEED,
    onAnimationComplete: () -> Unit = {}
) {
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val background = if (isDarkTheme) Color.Black else Color(0x22FFFFFF)

    // Áp dụng syntax highlighting
    val annotatedCode = remember(code) { syntaxHighlight(code) }
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(0.7.dp, Color.LightGray, RoundedCornerShape(12.dp))
            .background(background, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
        ) {

            // Tách phần header thành một Row riêng biệt
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Phần tên ngôn ngữ (có thể custom riêng)
                if (!language.isNullOrEmpty()) {
                    Box(modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp)
                    ) {
                        Text(
                            text = language,
                            style = TextStyle(
                                fontFamily = FontFamily.SansSerif,
                                fontSize = 17.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // Phần icon sao chép (có thể custom riêng)
                Box(
                    modifier = Modifier
                        .wrapContentSize()
                        .padding(end = 10.dp)
                ) {
                    IconButton(
                        onClick = {
                            clipboardManager.setText(AnnotatedString(code))
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar("Đã sao chép đoạn mã")
                            }
                        },
                        modifier = Modifier
                            .size(25.dp),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_copy),
                            contentDescription = "Copy Code",
                            tint = Color.Gray,
                        )
                    }
                }
            }

            // Add horizontal line
            if (!language.isNullOrEmpty()) {
                Divider(
                    color = Color.LightGray,
                    thickness = 1.dp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // Phần code (có thể custom riêng)
            SelectionContainer {
                if (isAnimated) {
                    AnimatedAnnotatedText(
                        annotatedString = annotatedCode,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = textColor
                        ),
                        delayMillis = typingSpeed,
                        onAnimationComplete = onAnimationComplete
                    )
                } else {
                    Text(
                        text = annotatedCode,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = textColor
                        ),
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Composable function to display inline code.
 */
@Composable
fun InlineCodeText(
    text: String,
    isError: Boolean,
) {
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val backgroundColor = when {
        isError -> MaterialTheme.colorScheme.error
        isDarkTheme -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surface
    }

    // Áp dụng syntax highlighting
    val annotatedText = remember(text) { syntaxHighlight(text) }

    SelectionContainer { // Add SelectionContainer
        Text(
            text = annotatedText,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                background = backgroundColor,
                color = textColor,
                fontSize = 17.sp
            )
        )
    }
}

