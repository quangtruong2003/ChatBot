package com.ahmedapps.geminichatbot.fomatText

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * Enhanced code block view with syntax highlighting, copy button, horizontal scrolling and animation
 */
@Composable
fun EnhancedCodeBlockView(
    code: String,
    language: String,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    isAnimated: Boolean = false,
    typingSpeed: Long = 20L,
    onAnimationComplete: () -> Unit = {}
) {
    val horizontalScrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var showCopyConfirmation by remember { mutableStateOf(false) }
    
    // Áp dụng syntax highlighting cho code
    val highlightedCode = remember(code) {
        syntaxHighlight(code)
    }
    
    // Chuyển từ AnnotatedString sang displayText để animation
    var displayedText by remember { mutableStateOf(if (isAnimated) AnnotatedString("") else highlightedCode) }
    
    // Animation effect for typing with syntax highlighting
    LaunchedEffect(code, isAnimated) {
        if (isAnimated) {
            // Tạo animation đánh máy với syntax highlighting
            val plainText = code
            displayedText = AnnotatedString("")
            
            for (i in plainText.indices) {
                delay(typingSpeed)
                // Tạo một phần của text với highlighting
                val currentText = plainText.substring(0, i + 1)
                displayedText = syntaxHighlight(currentText)
            }
            
            onAnimationComplete()
        } else {
            displayedText = highlightedCode
        }
    }
    
    // Check if scrolling is needed
    val needsScrolling by remember {
        derivedStateOf {
            horizontalScrollState.maxValue > 0
        }
    }
    
    // Colors for the code block
    val backgroundColor = if (isDarkTheme) {
        Color(0xFF1E1E1E) // Darker background for dark theme
    } else {
        Color(0xFFF5F5F5) // Light grey for light theme
    }
    
    val textColor = if (isDarkTheme) {
        Color(0xFFE0E0E0) // Light grey text for dark theme
    } else {
        Color(0xFF212121) // Dark grey text for light theme
    }
    
    val borderColor = if (isDarkTheme) {
        Color(0xFF3E3E3E) // Darker border for dark theme
    } else {
        Color(0xFFDDDDDD) // Light grey border for light theme
    }
    
    val scrollIndicatorColor = if (isDarkTheme) {
        Color(0xFF6E6E6E) // Scroll indicator for dark theme
    } else {
        Color(0xFFAAAAAA) // Scroll indicator for light theme
    }
    
    // Hide copy confirmation after a delay
    LaunchedEffect(showCopyConfirmation) {
        if (showCopyConfirmation) {
            delay(2000)
            showCopyConfirmation = false
        }
    }
    
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = borderColor
        ),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header with language name and copy button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language.ifEmpty { "Code" },
                    style = TextStyle(
                        color = textColor.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Scroll indicator (only shown if scrolling is needed)
                if (needsScrolling) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowRight,
                        contentDescription = "Swipe to see more",
                        tint = scrollIndicatorColor,
                        modifier = Modifier
                            .size(20.dp)
                            .alpha(0.7f)
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Copy button with animation
                IconButton(
                    onClick = {
                        clipboardManager.setText(displayedText)
                        showCopyConfirmation = true
                        Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    AnimatedVisibility(
                        visible = !showCopyConfirmation,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy code",
                            tint = textColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    AnimatedVisibility(
                        visible = showCopyConfirmation,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Copied",
                            tint = Color(0xFF4CAF50), // Green color for confirmation
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            Divider(color = borderColor)
            
            // Code content with horizontal scroll
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(horizontalScrollState)
                    .padding(16.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = displayedText,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 20.sp
                        ),
                        softWrap = false, // Prevent wrapping to enable horizontal scrolling
                        overflow = TextOverflow.Visible
                    )
                }
            }
            
            // Scroll indicator at bottom (only shown if scrolling is needed and scrolled)
            if (needsScrolling && horizontalScrollState.value > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = "◄ Scroll for more",
                        style = TextStyle(
                            color = scrollIndicatorColor,
                            fontSize = 12.sp,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                        )
                    )
                }
            }
        }
    }
} 