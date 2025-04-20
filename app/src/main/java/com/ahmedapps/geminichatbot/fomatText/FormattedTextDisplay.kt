// FormattedTextDisplay.kt
package com.ahmedapps.geminichatbot.fomatText

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
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
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
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.fillMaxHeight

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
    onAnimationComplete: () -> Unit = {},
    stopTypingMessageId: String? = null,
    messageId: String? = null
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var animationCompleted by remember(annotatedString) { mutableStateOf(!isNewMessage) }
    val haptic = LocalHapticFeedback.current

    // Lấy các màu từ theme để sử dụng trong enhancedTextProcessing
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val onBackgroundColor = MaterialTheme.colorScheme.onBackground

    val sections = remember(annotatedString) {
        splitAnnotatedString(annotatedString)
    }
    
    LaunchedEffect(stopTypingMessageId) {
        if (stopTypingMessageId != null && messageId != null && stopTypingMessageId == messageId && !animationCompleted) {
            animationCompleted = true
            onAnimationComplete()
        }
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
                    EnhancedCodeBlockView(
                        code = section.content,
                        language = section.language ?: "",
                        isDarkTheme = isSystemInDarkTheme(),
                        isAnimated = isNewMessage && !animationCompleted,
                        typingSpeed = typingSpeed,
                        onAnimationComplete = { if (i == sections.size - 1) animationCompleted = true },
                        hapticFeedback = haptic,
                        stopTypingMessageId = stopTypingMessageId,
                        messageId = messageId
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
                                onAnimationComplete = { if (i == sections.size - 1) animationCompleted = true },
                                hapticFeedback = haptic,
                                stopTypingMessageId = stopTypingMessageId,
                                messageId = messageId
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
                            snackbarHostState = snackbarHostState,
                            hapticFeedback = haptic,
                            stopTypingMessageId = stopTypingMessageId,
                            messageId = messageId
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
                        EnhancedBlockquoteViewWithHaptic(
                            content = blockquoteContent,
                            isAnimated = true,
                            typingSpeed = typingSpeed,
                            onAnimationComplete = { if (i == sections.size - 1) animationCompleted = true },
                            hapticFeedback = haptic,
                            stopTypingMessageId = stopTypingMessageId,
                            messageId = messageId
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
                                onAnimationComplete = { if (i == sections.size - 1) animationCompleted = true },
                                hapticFeedback = haptic,
                                stopTypingMessageId = stopTypingMessageId,
                                messageId = messageId
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
fun EnhancedCodeBlockView(
    code: String,
    language: String?,
    isDarkTheme: Boolean,
    isAnimated: Boolean = false,
    typingSpeed: Long = TypingConfig.DEFAULT_TYPING_SPEED,
    onAnimationComplete: () -> Unit = {},
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
    stopTypingMessageId: String?,
    messageId: String?
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var showCopyConfirmation by remember { mutableStateOf(false) }
    var displayedCode by remember { mutableStateOf(if (isAnimated) AnnotatedString("") else syntaxHighlight(code)) }

    LaunchedEffect(code, isAnimated) {
        if (isAnimated) {
            displayedCode = AnnotatedString("")
            val builder = AnnotatedString.Builder()
            val fullStyledCode = syntaxHighlight(code)

            for (i in code.indices) {
                val char = code[i]
                val styles = fullStyledCode.spanStyles.filter { it.start <= i && i < it.end }
                val paragraphStyles = fullStyledCode.paragraphStyles.filter { it.start <= i && i < it.end }

                val charDelay = calculateCharDelay(char, typingSpeed)
                delay(charDelay)

                builder.withStyle(SpanStyle()) {
                    paragraphStyles.forEach { pushStyle(it.item) }
                    styles.forEach { pushStyle(it.item) }
                    append(char)
                    styles.forEach { pop() }
                    paragraphStyles.forEach { pop() }
                }
                displayedCode = builder.toAnnotatedString()

                try {
                    if (char == '\n') {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    } else if (!char.isWhitespace()) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                } catch (e: Exception) {
                    // Log error if needed
                }
            }
            onAnimationComplete()
        } else {
            displayedCode = syntaxHighlight(code)
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isDarkTheme) Color(0xFF1E1E1E) else Color(0xFFF5F5F5),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isDarkTheme) Color(0xFF3E3E3E) else Color(0xFFDDDDDD)
        ),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = language ?: "Code",
                    style = TextStyle(
                        color = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(code))
                        showCopyConfirmation = true
                        coroutineScope.launch {
                            Toast.makeText(context, "Đã sao chép đoạn mã", Toast.LENGTH_SHORT).show()
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
                            tint = if (isDarkTheme) Color.White.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.7f),
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
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
            
            Divider(color = if (isDarkTheme) Color(0xFF3E3E3E) else Color(0xFFDDDDDD))
            
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = displayedCode,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            color = if (isDarkTheme) Color(0xFFD4D4D4) else Color(0xFF333333)
                        ),
                        softWrap = false
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

/**
 * AnimatedAnnotatedText Composable để hiển thị animation đánh máy
 * Cập nhật để nhận và sử dụng HapticFeedback
 */
@Composable
private fun AnimatedAnnotatedText(
    annotatedString: AnnotatedString,
    style: TextStyle,
    delayMillis: Long,
    onAnimationComplete: () -> Unit = {},
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
    stopTypingMessageId: String?,
    messageId: String?
) {
    var displayedText by remember { mutableStateOf(AnnotatedString("")) }

    LaunchedEffect(annotatedString) {
        displayedText = AnnotatedString("")
        val builder = AnnotatedString.Builder()

        for (i in annotatedString.indices) {
            val char = annotatedString[i]
            val charDelay = calculateCharDelay(char, delayMillis)
            delay(charDelay)

            val styles = annotatedString.spanStyles.filter { it.start <= i && i < it.end }
            val paragraphStyles = annotatedString.paragraphStyles.filter { it.start <= i && i < it.end }

            builder.withStyle(SpanStyle()) {
                paragraphStyles.forEach { pushStyle(it.item) }
                styles.forEach { pushStyle(it.item) }
                append(char)
                styles.forEach { pop() }
                paragraphStyles.forEach { pop() }
            }
            displayedText = builder.toAnnotatedString()

            try {
                if (char == '\n') {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                } else if (!char.isWhitespace()) {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            } catch (e: Exception) {
                // Ghi log hoặc xử lý lỗi nếu cần thiết
            }
        }
        onAnimationComplete()
    }

    Text(
        text = displayedText,
        style = style
    )
}

/**
 * Hàm tính toán delay dựa trên ký tự (có thể tái sử dụng từ TypingConfig)
 */
private fun calculateCharDelay(char: Char, baseDelay: Long): Long {
    val punctuationFactor = try { TypingConfig.PUNCTUATION_SPEED_FACTOR } catch (e: NoSuchFieldError) { 3L }
    val spaceFactor = try { TypingConfig.SPACE_SPEED_FACTOR } catch (e: NoSuchFieldError) { 2L }
    val newlineFactor = try { TypingConfig.NEWLINE_SPEED_FACTOR } catch (e: NoSuchFieldError) { 4L }
    val minSpeed = try { TypingConfig.MIN_TYPING_SPEED } catch (e: NoSuchFieldError) { 2L }
    val maxSpeed = try { TypingConfig.MAX_TYPING_SPEED } catch (e: NoSuchFieldError) { 15L }

    return when {
        char == '\n' -> baseDelay * newlineFactor
        char.isWhitespace() -> baseDelay * spaceFactor
        ",.?!:;".contains(char) -> baseDelay * punctuationFactor
        else -> baseDelay
    }.coerceIn(minSpeed, maxSpeed)
}

/**
 * Cập nhật AnimatedTableView để nhận và sử dụng HapticFeedback
 * Đảm bảo định nghĩa hàm này có tham số hapticFeedback
 */
@Composable
fun AnimatedTableView(
    headers: List<String>,
    rows: List<List<String>>,
    typingSpeed: Long,
    onAnimationComplete: () -> Unit,
    snackbarHostState: SnackbarHostState,
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
    stopTypingMessageId: String?,
    messageId: String?
) {
    var displayedRowCount by remember { mutableStateOf(0) }
    var displayedCellCount by remember { mutableStateOf(List(rows.size) { 0 }) }
    val isDarkTheme = isSystemInDarkTheme()
    val borderColor = if (isDarkTheme) Color.DarkGray else Color.LightGray

    LaunchedEffect(rows) {
        // Animate headers first (optional, có thể hiển thị ngay)
        // ...

        // Animate rows and cells
        for (rowIndex in rows.indices) {
            val newCellCount = displayedCellCount.toMutableList()
            for (colIndex in rows[rowIndex].indices) {
                val cellText = rows[rowIndex][colIndex]
                for (charIndex in cellText.indices) {
                     val char = cellText[charIndex]
                     val charDelay = calculateCharDelay(char, typingSpeed)
                     delay(charDelay) // Delay cho từng ký tự trong cell

                    // Kích hoạt rung phản hồi cho từng ký tự
                    try {
                        if (char == '\n') { // Hiếm khi có trong table cell nhưng vẫn check
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        } else if (!char.isWhitespace()) {
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    } catch (e: Exception) {
                        // Log error if needed
                    }
                }
                // Cập nhật số lượng cell đã hiển thị hoàn chỉnh trong row hiện tại
                newCellCount[rowIndex] = colIndex + 1
                displayedCellCount = newCellCount

                // Có thể thêm delay nhỏ giữa các cell nếu muốn
                 delay(typingSpeed * 3)
            }
            // Cập nhật số lượng row đã hiển thị hoàn chỉnh
            displayedRowCount = rowIndex + 1
             // Có thể thêm delay nhỏ giữa các row nếu muốn
             delay(typingSpeed * 5)
        }
        onAnimationComplete()
    }

    // Phần UI của TableView, sử dụng displayedRowCount và displayedCellCount để hiển thị dần
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        // Headers
        Row(Modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
            headers.forEach { header ->
                TableCell(text = header, isHeader = true, weight = 1f / headers.size)
            }
        }
        Divider(color = borderColor)

        // Rows (hiển thị dựa trên displayedRowCount)
        rows.take(displayedRowCount).forEachIndexed { rowIndex, row ->
            Row(Modifier.fillMaxWidth()) {
                row.take(displayedCellCount[rowIndex]).forEachIndexed { colIndex, cell ->
                    // Lấy toàn bộ text của cell để hiển thị, vì animation ký tự đã xong
                    val fullCellText = rows[rowIndex][colIndex]
                    TableCell(text = fullCellText, weight = 1f / headers.size)
                }
                // Hiển thị các cell còn lại trong row dưới dạng trống hoặc placeholder nếu cần
                 repeat(headers.size - displayedCellCount[rowIndex]) {
                     TableCell(text = "...", weight = 1f / headers.size, alpha = 0.5f) // Placeholder
                 }
            }
             if (rowIndex < displayedRowCount - 1) {
                 Divider(color = borderColor.copy(alpha = 0.5f))
             }
        }
         // Hiển thị các row còn lại dưới dạng trống hoặc placeholder nếu cần
         repeat(rows.size - displayedRowCount) {
             Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                 repeat(headers.size) {
                     TableCell(text = "...", weight = 1f / headers.size, alpha = 0.5f) // Placeholder
                 }
             }
             if (it < rows.size - displayedRowCount -1) {
                 Divider(color = borderColor.copy(alpha = 0.5f))
             }
         }
    }
}

/**
 * Cập nhật EnhancedBlockquoteView để nhận và sử dụng HapticFeedback
 * Đảm bảo định nghĩa hàm này có tham số hapticFeedback
 */
@Composable
private fun EnhancedBlockquoteViewWithHaptic(
    content: AnnotatedString,
    isAnimated: Boolean = false,
    typingSpeed: Long = TypingConfig.DEFAULT_TYPING_SPEED,
    onAnimationComplete: () -> Unit = {},
    hapticFeedback: androidx.compose.ui.hapticfeedback.HapticFeedback,
    stopTypingMessageId: String?,
    messageId: String?
) {
    val isDarkTheme = isSystemInDarkTheme()
    val blockquoteColor = if (isDarkTheme) Color(0xFF4A4A4A) else Color(0xFFE0E0E0)
    val textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)

    var displayedText by remember { mutableStateOf(if (isAnimated) AnnotatedString("") else content) }

    LaunchedEffect(content, isAnimated) {
        if (isAnimated && hapticFeedback != null) { // Kiểm tra null cho hapticFeedback
            displayedText = AnnotatedString("")
            val builder = AnnotatedString.Builder()

            for (i in content.indices) {
                val char = content[i]
                val charDelay = calculateCharDelay(char, typingSpeed)
                delay(charDelay)

                // Append ký tự với style gốc
                val styles = content.spanStyles.filter { it.start <= i && i < it.end }
                val paragraphStyles = content.paragraphStyles.filter { it.start <= i && i < it.end }

                builder.withStyle(SpanStyle()) {
                    paragraphStyles.forEach { pushStyle(it.item) }
                    styles.forEach { pushStyle(it.item) }
                    append(char)
                    styles.forEach { pop() }
                    paragraphStyles.forEach { pop() }
                }
                displayedText = builder.toAnnotatedString()

                // Kích hoạt rung phản hồi
                try {
                    if (char == '\n') {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    } else if (!char.isWhitespace()) {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                } catch (e: Exception) {
                    // Log error if needed
                }
            }
            onAnimationComplete()
        } else {
            displayedText = content
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Spacer(
            modifier = Modifier
                .width(4.dp)
                .height(IntrinsicSize.Min)
                .background(blockquoteColor, RoundedCornerShape(2.dp))
                .fillMaxHeight()
        )
        Spacer(modifier = Modifier.width(8.dp))
        SelectionContainer(modifier = Modifier.weight(1f)) {
            Text(
                text = displayedText,
                style = TextStyle(
                    fontSize = 16.sp,
                    color = textColor,
                    fontStyle = FontStyle.Italic,
                    lineHeight = 22.sp
                )
            )
        }
    }
}

@Composable
fun RowScope.TableCell(
    text: String, 
    weight: Float,
    isHeader: Boolean = false,
    alpha: Float = 1.0f
) {
    Text(
        text = text,
        modifier = Modifier
            .weight(weight)
            .padding(8.dp)
            .alpha(alpha),
        fontWeight = if (isHeader) FontWeight.Bold else FontWeight.Normal,
        textAlign = TextAlign.Center,
        fontSize = 14.sp,
        maxLines = 3,
        overflow = TextOverflow.Ellipsis
    )
}

