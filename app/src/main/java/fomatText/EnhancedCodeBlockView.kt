package fomatText

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Enhanced code block view with syntax highlighting and copy button
 */
@Composable
fun EnhancedCodeBlockView(
    code: String,
    language: String,
    modifier: Modifier = Modifier,
    isDarkTheme: Boolean = isSystemInDarkTheme()
) {
    val scrollState = rememberScrollState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var showCopyConfirmation by remember { mutableStateOf(false) }
    
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
    
    // Hide copy confirmation after a delay
    LaunchedEffect(showCopyConfirmation) {
        if (showCopyConfirmation) {
            delay(2000)
            showCopyConfirmation = false
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .background(backgroundColor)
    ) {
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
            
            // Copy button with animation
            IconButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString(code))
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
                .horizontalScroll(scrollState)
                .padding(16.dp)
        ) {
            SelectionContainer {
                Text(
                    text = code,
                    style = TextStyle(
                        color = textColor,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 20.sp
                    )
                )
            }
        }
    }
} 