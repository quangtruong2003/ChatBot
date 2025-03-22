// FormattedTextDisplay.kt
package fomatText

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmedapps.geminichatbot.R
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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

    // Lấy các màu từ theme để sử dụng trong enhancedTextProcessing
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground

    val sections = remember(annotatedString) {
        splitAnnotatedString(annotatedString)
    }
    
    LaunchedEffect(animationCompleted) {
        if (animationCompleted && isNewMessage) {
            onAnimationComplete()
        }
    }

    Column(modifier = modifier.padding(horizontal = 8.dp)) {
        for (i in sections.indices) {
            val section = sections[i]

            when (section.type) {
                "CODE_BLOCK" -> {
                    // Sử dụng EnhancedCodeBlockView cải tiến với cuộn ngang
                    EnhancedCodeBlockView(
                        code = section.content,
                        language = section.language ?: "",
                        isDarkTheme = isSystemInDarkTheme(),
                        isAnimated = isNewMessage && !animationCompleted,
                        typingSpeed = typingSpeed,
                        onAnimationComplete = { if (i == sections.size - 1) animationCompleted = true }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
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
                "TABLE" -> {
                    val (headers, tableRows) = parseTableData(section.content)
                    
                    if (isNewMessage && !animationCompleted) {
                        AnimatedTableView(
                            headers = headers,
                            rows = tableRows,
                            typingSpeed = typingSpeed,
                            onAnimationComplete = { if (i == sections.size - 1) animationCompleted = true },
                            snackbarHostState = snackbarHostState
                        )
                    } else {
                        EnhancedTableView(
                            headers = headers,
                            rows = tableRows,
                            snackbarHostState = snackbarHostState
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                "BLOCKQUOTE" -> {
                    val blockquoteContent = parseFormattedText(section.content)
                    
                    if (isNewMessage && !animationCompleted) {
                        EnhancedBlockquoteView(
                            content = blockquoteContent,
                            isAnimated = true,
                            typingSpeed = typingSpeed,
                            onAnimationComplete = { if (i == sections.size - 1) animationCompleted = true }
                        )
                    } else {
                        EnhancedBlockquoteView(
                            content = blockquoteContent
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
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
            
            if (i < sections.size - 1 && section.type != "INLINE_CODE") {
                Spacer(modifier = Modifier.height(2.dp))
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
        val tableAnnotation = annotatedString.getStringAnnotations(tag = "TABLE", start = currentIndex, end = annotatedString.length).firstOrNull()
        val blockquoteAnnotation = annotatedString.getStringAnnotations(tag = "BLOCKQUOTE", start = currentIndex, end = annotatedString.length).firstOrNull()

        val nextAnnotation = listOfNotNull(codeBlockAnnotation, inlineCodeAnnotation, tableAnnotation, blockquoteAnnotation).minByOrNull { it.start }

        if (nextAnnotation != null) {
            if (nextAnnotation.start > currentIndex) {
                val textBefore = annotatedString.subSequence(currentIndex, nextAnnotation.start)
                sections.add(
                    AnnotatedStringSection(
                        type = "TEXT",
                        content = textBefore.text,
                        annotatedString = textBefore
                    )
                )
            }

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
                "TABLE" -> {
                    sections.add(
                        AnnotatedStringSection(
                            type = "TABLE",
                            content = nextAnnotation.item
                        )
                    )
                }
                "BLOCKQUOTE" -> {
                    sections.add(
                        AnnotatedStringSection(
                            type = "BLOCKQUOTE",
                            content = nextAnnotation.item
                        )
                    )
                }
            }

            currentIndex = nextAnnotation.end

        } else {
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
 * Parse the table annotation data into headers and rows.
 */
private fun parseTableData(tableData: String): Pair<List<String>, List<List<String>>> {
    val parts = tableData.split("::", limit = 2)
    if (parts.size != 2) return Pair(emptyList(), emptyList())
    
    val headers = parts[0].split("|||")
    val rows = parts[1].split(";;;").map { row -> 
        row.split("|||") 
    }
    
    val filteredRows = rows.filter { rowCells ->
        !rowCells.all { cell -> 
            cell.trim().all { char -> char == '-' || char == ' ' } 
        }
    }
    
    return Pair(headers, filteredRows)
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
    val backgroundColor = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)
    val borderColor = if (isDarkTheme) Color(0xFF3E3E3E) else Color(0xFFDDDDDD)
    val scrollIndicatorColor = if (isDarkTheme) Color(0xFF6E6E6E) else Color(0xFFAAAAAA)
    
    val horizontalScrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    
    // Check if scrolling is needed
    val needsScrolling by remember {
        derivedStateOf {
            horizontalScrollState.maxValue > 0
        }
    }
    
    var showCopyConfirmation by remember { mutableStateOf(false) }
    var displayedText by remember { mutableStateOf(if (isAnimated) "" else code) }
    
    // Animation effect for typing
    LaunchedEffect(code, isAnimated) {
        if (isAnimated) {
            displayedText = ""
            var currentText = ""
            
            for (char in code) {
                currentText += char
                displayedText = currentText
                delay(typingSpeed)
            }
            
            onAnimationComplete()
        } else {
            displayedText = code
        }
    }
    
    // Hide copy confirmation after a delay
    LaunchedEffect(showCopyConfirmation) {
        if (showCopyConfirmation) {
            delay(2000)
            showCopyConfirmation = false
        }
    }

    Surface(
        modifier = Modifier
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
                    text = language ?: "Code",
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
                        clipboardManager.setText(AnnotatedString(code))
                        showCopyConfirmation = true
                        coroutineScope.launch {
                            snackbarHostState.showSnackbar("Đã sao chép đoạn mã")
                        }
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
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = textColor
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

    SelectionContainer {
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

/**
 * Composable để hiển thị giải thích code một cách đẹp mắt
 */
@Composable
fun CodeExplanation(
    explanation: AnnotatedString,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp)
    ) {
        SelectionContainer {
            Text(
                text = explanation,
                style = TextStyle(
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                    lineHeight = 24.sp,
                    letterSpacing = 0.5.sp
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * Hàm xử lý text không gây ra xuống dòng khi có mã code
 */
private fun enhancedTextProcessing(
    text: String,
    primaryColor: Color,
    surfaceColor: Color
): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val codeStyle = SpanStyle(
        fontFamily = FontFamily.Monospace,
        color = primaryColor,
        background = surfaceColor.copy(alpha = 0.15f)
    )
    
    // Tạo một bản sao của text để xử lý
    val processedText = text
    
    // Xác định các vùng cần highlight
    val regions = mutableListOf<Pair<IntRange, Boolean>>() // IntRange: phạm vi, Boolean: có phải code không
    
    // Tìm tất cả các vùng code cần highlight
    val codePatterns = listOf(
        // C++ directives/includes
        "#include\\s*<[^>]*>",
        // Keywords
        "\\b(int|float|double|char|void|bool|class|struct|return|if|else|for|while|switch|case|break|continue|namespace|using|std|auto|const|static)\\b",
        // I/O operations
        "\\b(cout|cin|endl)\\b",
        // Operators
        "(<<|>>|::|->)",
        // Specific C++ elements
        "\\bmain\\(\\)",
        "std::[a-zA-Z0-9_]+",
        // Braces and punctuation in code context
        "(\\{|\\}|\\(|\\)|\\[|\\])"
    )
    
    // Combine tất cả pattern thành một regex
    val codeRegex = codePatterns.joinToString("|").toRegex()
    
    // Tìm tất cả các vùng code
    codeRegex.findAll(processedText).forEach { matchResult ->
        regions.add(Pair(matchResult.range, true))
    }
    
    // Tìm các đánh số đầu mục
    val numberPattern = "\\b\\d+\\.".toRegex()
    numberPattern.findAll(processedText).forEach { matchResult ->
        // Kiểm tra nếu không nằm trong một vùng code đã xác định
        if (regions.none { (range, _) -> range.contains(matchResult.range.first) }) {
            regions.add(Pair(matchResult.range, false))
        }
    }
    
    // Sắp xếp các vùng theo thứ tự xuất hiện
    val sortedRegions = regions.sortedBy { it.first.first }
    
    // Xử lý text theo từng vùng đã xác định
    var lastPos = 0
    for ((range, isCode) in sortedRegions) {
        // Thêm text thường trước vùng hiện tại
        if (range.first > lastPos) {
            builder.append(processedText.substring(lastPos, range.first))
        }
        
        // Thêm phần highlight với style tương ứng
        val highlightedText = processedText.substring(range.first, range.last + 1)
        if (isCode) {
            builder.pushStyle(codeStyle)
            builder.append(highlightedText)
            builder.pop()
        } else {
            // Đánh số đầu mục được giữ nguyên style văn bản
            builder.append(highlightedText)
        }
        
        lastPos = range.last + 1
    }
    
    // Thêm phần còn lại của text
    if (lastPos < processedText.length) {
        builder.append(processedText.substring(lastPos))
    }
    
    return builder.toAnnotatedString()
}

/**
 * AnimatedText Composable để hiển thị animation đánh máy
 */
@Composable
private fun AnimatedText(
    text: AnnotatedString,
    style: TextStyle,
    delayMillis: Long,
    onAnimationComplete: () -> Unit = {}
) {
    var displayedText by remember { mutableStateOf(AnnotatedString("")) }
    var isAnimationComplete by remember { mutableStateOf(false) }

    LaunchedEffect(text) {
        displayedText = AnnotatedString("")
        
        // Animation đánh máy cho văn bản với format được giữ nguyên
        for (i in 1..text.length) {
            delay(delayMillis)
            displayedText = text.subSequence(0, i)
        }
        
        isAnimationComplete = true
        onAnimationComplete()
    }

    Text(
        text = displayedText,
        style = style
    )
}

