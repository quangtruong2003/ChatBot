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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.SnackbarHostState

/**
 * A composable function that displays a table.
 */
@Composable
fun EnhancedTableView(
    headers: List<String>,
    rows: List<List<String>>,
    modifier: Modifier = Modifier,
    headerTextColor: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    headerBackgroundColor: Color = MaterialTheme.colorScheme.primaryContainer,
    rowTextColor: Color = MaterialTheme.colorScheme.onSurface,
    rowBackgroundColor: Color = MaterialTheme.colorScheme.surface,
    alternateRowColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
    snackbarHostState: SnackbarHostState
) {
    // Parse the headers and rows
    val parsedHeaders = headers.map { parseFormattedText(it) }
    val parsedRows = rows.map { row -> row.map { parseFormattedText(it) } }
    
    // Lấy độ rộng màn hình
    val screenWidth = LocalConfiguration.current.screenWidthDp

    // Sử dụng cùng hàm tính toán chiều rộng như trong AnimatedTableView
    val columnWidths = remember(parsedHeaders, parsedRows, screenWidth) {
        calculateColumnWidthsByContent(parsedHeaders, parsedRows, screenWidth)
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
            Row(
                modifier = Modifier
                    .background(headerBackgroundColor)
                    .padding(vertical = 16.dp, horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                parsedHeaders.forEachIndexed { index, header ->
                    Box(
                        modifier = Modifier
                            .width(columnWidths[index])
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

            // Table rows
            parsedRows.forEachIndexed { rowIndex, row ->
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
                                .width(if (colIndex < columnWidths.size) columnWidths[colIndex] else 100.dp)
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

/**
 * Tính toán chiều rộng cột chỉ dựa vào nội dung, 
 * không làm cân đối các cột với nhau
 */
private fun calculateColumnWidthsByContent(
    headers: List<AnnotatedString>,
    rows: List<List<AnnotatedString>>,
    screenWidthDp: Int
): List<androidx.compose.ui.unit.Dp> {
    if (headers.isEmpty()) return emptyList()

    val columnCount = headers.size
    val maxTextLengths = MutableList(columnCount) { 0 }
    val maxColumnWidth = screenWidthDp * 0.5f // 50% màn hình
    
    // Xác định độ dài text lớn nhất của mỗi cột
    headers.forEachIndexed { idx, header ->
        maxTextLengths[idx] = header.text.length
    }
    
    // Xem xét nội dung của các cell
    rows.forEach { row ->
        row.forEachIndexed { idx, cell ->
            if (idx < columnCount) {
                val lines = cell.text.split("\n")
                val maxLineLength = lines.maxOfOrNull { it.length } ?: cell.text.length
                maxTextLengths[idx] = maxOf(maxTextLengths[idx], maxLineLength)
            }
        }
    }
    
    // Tính chiều rộng cho mỗi cột dựa hoàn toàn vào nội dung
    return maxTextLengths.map { textLength ->
        val characterWidth = 7.5f // Chữ ở giữa bảng 7.5dp/ký tự
        val fixedPadding = 32f // Đệm cố định
        
        // Chiều rộng dựa trên nội dung thực tế
        val contentBasedWidth = textLength * characterWidth + fixedPadding
        
        // Giới hạn tối đa 50% màn hình, tối thiểu 40dp
        contentBasedWidth.coerceIn(40f, maxColumnWidth).dp
    }
} 