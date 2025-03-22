package fomatText

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Cấu hình màu sắc và kiểu dáng cho Markdown
 */
object MarkdownStyles {
    // Heading styles
    val heading1 = TextStyle(
        fontSize = 28.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 34.sp,
        letterSpacing = 0.25.sp
    )

    val heading2 = TextStyle(
        fontSize = 24.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 30.sp,
        letterSpacing = 0.25.sp
    )

    val heading3 = TextStyle(
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 26.sp
    )

    val heading4 = TextStyle(
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        lineHeight = 24.sp
    )

    // Text styles
    val normalText = TextStyle(
        fontSize = 16.sp,
        lineHeight = 24.sp
    )

    val codeText = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 14.sp,
        lineHeight = 20.sp
    )

    // Blockquote styles
    @Composable
    fun blockquoteStyle() = Modifier
        .fillMaxWidth()
        .padding(start = 16.dp, top = 8.dp, bottom = 8.dp)
        .border(
            width = 3.dp,
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp)
        )
        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 8.dp)
        .background(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
            shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp)
        )

    // List item styles
    val listItemIndent = 24.dp
    val listItemBulletSize = 6.dp
    val listItemNumberSize = 24.dp
    val listItemPadding = 8.dp

    // Table styles
    @Composable
    fun tableHeaderBackgroundColor() = 
        if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceVariant
        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)

    @Composable
    fun tableRowBackgroundColor(isAlternate: Boolean) = 
        if (isSystemInDarkTheme()) {
            if (isAlternate) MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
            else MaterialTheme.colorScheme.surface
        } else {
            if (isAlternate) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        }

    @Composable
    fun tableBorderColor() = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)

    // Code block styles
    @Composable
    fun codeBlockBackground() = 
        if (isSystemInDarkTheme()) Color.Black 
        else MaterialTheme.colorScheme.surfaceVariant

    @Composable
    fun codeBlockBorder() = 
        if (isSystemInDarkTheme()) Color.DarkGray.copy(alpha = 0.5f)
        else MaterialTheme.colorScheme.outline.copy(alpha = 0.7f)

    val linkColor = Color(0xFF1976D2)
    val inlineCodeBackground = Color(0x1F000000)

    // Emphasis styles
    val boldStyle = SpanStyle(fontWeight = FontWeight.Bold)
    val italicStyle = SpanStyle(fontStyle = FontStyle.Italic)
    val boldItalicStyle = SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
    val underlineStyle = SpanStyle(textDecoration = TextDecoration.Underline)
    val strikethroughStyle = SpanStyle(textDecoration = TextDecoration.LineThrough)

    // Create an annotated string with enhanced styling for inline formatting
    fun enhancedInlineStyle(text: String): AnnotatedString {
        return buildAnnotatedString {
            val boldItalicPattern = "\\*\\*\\*([^*]+)\\*\\*\\*".toRegex()
            val boldPattern = "\\*\\*([^*]+)\\*\\*".toRegex()
            val italicPattern = "\\*([^*]+)\\*".toRegex()
            val underlinePattern = "__([^_]+)__".toRegex()
            val strikethroughPattern = "~~([^~]+)~~".toRegex()
            val inlineCodePattern = "`([^`]+)`".toRegex()
            
            var currentIndex = 0
            
            while (currentIndex < text.length) {
                val boldItalicMatch = boldItalicPattern.find(text, currentIndex)
                val boldMatch = boldPattern.find(text, currentIndex)
                val italicMatch = italicPattern.find(text, currentIndex)
                val underlineMatch = underlinePattern.find(text, currentIndex)
                val strikethroughMatch = strikethroughPattern.find(text, currentIndex)
                val inlineCodeMatch = inlineCodePattern.find(text, currentIndex)
                
                val matches = listOfNotNull(
                    boldItalicMatch, boldMatch, italicMatch, 
                    underlineMatch, strikethroughMatch, inlineCodeMatch
                )
                
                if (matches.isEmpty()) {
                    append(text.substring(currentIndex))
                    break
                }
                
                val firstMatch = matches.minByOrNull { it.range.first }!!
                
                // Append text before match
                if (firstMatch.range.first > currentIndex) {
                    append(text.substring(currentIndex, firstMatch.range.first))
                }
                
                // Apply style based on match type
                when (firstMatch) {
                    boldItalicMatch -> withStyle(boldItalicStyle) { 
                        append(firstMatch.groupValues[1]) 
                    }
                    boldMatch -> withStyle(boldStyle) { 
                        append(firstMatch.groupValues[1]) 
                    }
                    italicMatch -> withStyle(italicStyle) { 
                        append(firstMatch.groupValues[1]) 
                    }
                    underlineMatch -> withStyle(underlineStyle) { 
                        append(firstMatch.groupValues[1]) 
                    }
                    strikethroughMatch -> withStyle(strikethroughStyle) { 
                        append(firstMatch.groupValues[1]) 
                    }
                    inlineCodeMatch -> withStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = inlineCodeBackground
                    )) { 
                        append(firstMatch.groupValues[1]) 
                    }
                }
                
                currentIndex = firstMatch.range.last + 1
            }
        }
    }
} 