package fomatText

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.material3.SnackbarHostState // Import SnackbarHostState

/**
 * A composable function that displays a table with typing animation effect.
 */
@Composable
fun AnimatedTableView(
    headers: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    headerTextColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    headerBackgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    rowTextColor: Color = MaterialTheme.colorScheme.onSurface,
    rowBackgroundColor: Color = MaterialTheme.colorScheme.surface,
    alternateRowColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
    typingSpeed: Long = TypingConfig.DEFAULT_TYPING_SPEED,
    onAnimationComplete: () -> Unit = {},
    snackbarHostState: SnackbarHostState
) {
    // Trạng thái cho animation
    var animationState by remember { mutableStateOf(0) }
    
    // Parse the headers and rows
    val parsedHeaders = headers.map { parseFormattedText(it) }
    val parsedRows = rows.map { row -> row.map { parseFormattedText(it) } }
    
    // Tính toán chiều rộng màn hình
    val screenWidth = LocalConfiguration.current.screenWidthDp

    // Tính toán chiều rộng cột thực sự dựa trên nội dung
    val columnInfo = remember(parsedHeaders, parsedRows, screenWidth) {
        calculateOptimizedColumnWidths(parsedHeaders, parsedRows, screenWidth)
    }
    
    // Hiệu ứng cho animation
    LaunchedEffect(headers, rows) {
        animationState = 0
        val delayBetweenSteps = typingSpeed * 50
        
        delay(delayBetweenSteps)
        animationState = 1
        
        for (i in 1..rows.size) {
            delay(delayBetweenSteps)
            animationState = i + 1
        }
        
        onAnimationComplete()
    }
    
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            ),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(vertical = 8.dp)
        ) {
            // Header row
            if (animationState >= 1) {
                Row(
                    modifier = Modifier
                        .background(headerBackgroundColor)
                        .padding(vertical = 16.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    parsedHeaders.forEachIndexed { index, header ->
                        Box(
                            modifier = Modifier
                                .width(columnInfo[index])
                                .padding(horizontal = 8.dp)
                        ) {
                            Text(
                                text = header,
                                style = TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = headerTextColor,
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Divider(color = borderColor, thickness = 1.dp)
            }

            // Table rows
            parsedRows.forEachIndexed { rowIndex, row ->
                if (animationState >= rowIndex + 2) {
                    val isAlternateRow = rowIndex % 2 == 1
                    val rowBackground = if (isAlternateRow) alternateRowColor else rowBackgroundColor

                    Row(
                        modifier = Modifier
                            .background(rowBackground)
                            .padding(vertical = 12.dp, horizontal = 16.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        row.forEachIndexed { colIndex, cell ->
                            Box(
                                modifier = Modifier
                                    .width(if (colIndex < columnInfo.size) columnInfo[colIndex] else 100.dp)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = cell,
                                    style = TextStyle(
                                        fontSize = 15.sp,
                                        color = rowTextColor,
                                        lineHeight = 24.sp
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }

                    if (rowIndex < rows.size - 1) {
                        Divider(
                            color = borderColor.copy(alpha = 0.5f),
                            thickness = 0.5.dp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tính toán chiều rộng cột dựa chính xác theo nội dung, không quá 50% màn hình
 */
private fun calculateOptimizedColumnWidths(
    headers: List<AnnotatedString>,
    rows: List<List<AnnotatedString>>,
    screenWidthDp: Int
): List<androidx.compose.ui.unit.Dp> {
    if (headers.isEmpty()) return emptyList()

    val columnCount = headers.size
    val maxTextLengths = MutableList(columnCount) { 0 }
    val maxColumnWidth = screenWidthDp * 0.5f // 50% màn hình
    
    // Tìm độ dài nội dung tối đa cho mỗi cột
    // 1. Từ tiêu đề
    headers.forEachIndexed { idx, header ->
        maxTextLengths[idx] = header.text.length
    }
    
    // 2. Từ nội dung cell
    rows.forEach { row ->
        row.forEachIndexed { idx, cell ->
            if (idx < columnCount) {
                val lines = cell.text.split("\n")
                val maxLineLength = lines.maxOfOrNull { it.length } ?: cell.text.length
                maxTextLengths[idx] = maxOf(maxTextLengths[idx], maxLineLength)
            }
        }
    }
    
    // Trực tiếp tính toán chiều rộng từng cột dựa trên nội dung
    return maxTextLengths.map { textLength ->
        // Một hệ số tốt cho chiều rộng: mỗi ký tự ~ 8dp + padding cố định
        val characterWidth = 8f
        val fixedPadding = 32f // Padding và margin cho mỗi cột
        
        // Chiều rộng dựa trên nội dung thực tế
        val contentBasedWidth = textLength * characterWidth + fixedPadding
        
        // Đảm bảo chiều rộng tối thiểu và tối đa
        val finalWidth = contentBasedWidth.coerceIn(
            minimumValue = 40f, // Chiều rộng tối thiểu 40dp
            maximumValue = maxColumnWidth // Tối đa 50% màn hình
        )
        
        finalWidth.dp
    }
} 