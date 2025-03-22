package fomatText

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmedapps.geminichatbot.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * An enhanced code block view with improved styling and UI
 */
@Composable
fun EnhancedCodeBlockView(
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
    val background = if (isDarkTheme) MarkdownStyles.codeBlockBackground() else MarkdownStyles.codeBlockBackground()
    val borderColor = MarkdownStyles.codeBlockBorder()
    
    // Áp dụng syntax highlighting
    val annotatedCode = remember(code) { syntaxHighlight(code) }
    val coroutineScope = rememberCoroutineScope()
    
    // Copy animation state
    var isCopied by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .background(background, RoundedCornerShape(12.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            // Header with language and copy button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Language display
                if (!language.isNullOrEmpty()) {
                    Text(
                        text = language,
                        style = TextStyle(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 14.sp,
                            color = if (isDarkTheme) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                
                // Copy button
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                        isCopied = true
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Đã sao chép đoạn mã")
                            delay(2000)
                            isCopied = false
                        }
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    AnimatedVisibility(
                        visible = !isCopied,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_copy),
                            contentDescription = "Sao chép mã",
                            tint = if (isDarkTheme) Color.LightGray else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    AnimatedVisibility(
                        visible = isCopied,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Đã sao chép",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Divider
            if (!language.isNullOrEmpty()) {
                Divider(
                    color = borderColor.copy(alpha = 0.5f),
                    thickness = 1.dp
                )
            }

            // Code content
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    if (isAnimated) {
                        AnimatedAnnotatedText(
                            annotatedString = annotatedCode,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                color = textColor,
                                lineHeight = 20.sp
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
                                color = textColor,
                                lineHeight = 20.sp
                            )
                        )
                    }
                }
            }
        }
    }
} 