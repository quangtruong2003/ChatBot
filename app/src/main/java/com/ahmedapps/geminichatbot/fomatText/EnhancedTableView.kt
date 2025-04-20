package com.ahmedapps.geminichatbot.fomatText

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
import androidx.compose.foundation.text.selection.SelectionContainer

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

    // Tính toán chiều rộng cột dựa trên nội dung
    val columnWidths = remember(parsedHeaders, parsedRows, screenWidth) {
        calculateColumnWidthsByContent(parsedHeaders, parsedRows, screenWidth)
    }
    
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            ),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(0.dp)
        ) {
            // Header row
            Row(
                modifier = Modifier
                    .background(headerBackgroundColor)
                    .padding(vertical = 8.dp)
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically
            ) {
                parsedHeaders.forEachIndexed { index, header ->
                    Box(
                        modifier = Modifier
                            .width(columnWidths[index])
                            .padding(horizontal = 12.dp) // Tăng padding ngang
                    ) {
                        Text(
                            text = header,
                            style = TextStyle(
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = headerTextColor,
                                textAlign = TextAlign.Start
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    // Thêm divider dọc sau mỗi cột (trừ cột cuối)
                    if (index < parsedHeaders.size - 1) {
                        Divider(
                            color = borderColor,
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(1.dp)
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
                        .height(IntrinsicSize.Min)
                        .padding(vertical = 6.dp), // Giảm padding dọc
                    verticalAlignment = Alignment.Top
                ) {
                    row.forEachIndexed { colIndex, cell ->
                        // Bọc nội dung trong SelectionContainer để cho phép sao chép
                        SelectionContainer {
                            Box(
                                modifier = Modifier
                                    .width(if (colIndex < columnWidths.size) columnWidths[colIndex] else 100.dp)
                                    .padding(horizontal = 12.dp, vertical = 4.dp) // Tăng padding ngang, giữ padding dọc
                            ) {
                                // Sử dụng trực tiếp cell (AnnotatedString) để giữ định dạng
                                Text(
                                    text = cell,
                                    style = TextStyle(
                                        fontSize = 15.sp,
                                        color = rowTextColor,
                                        lineHeight = 18.sp, // Giảm line height để các dòng gần nhau hơn
                                        textAlign = TextAlign.Start
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                        
                        // Thêm divider dọc giữa các cột (trừ cột cuối cùng)
                        if (colIndex < row.size - 1) {
                            Divider(
                                color = borderColor.copy(alpha = 0.7f), // Làm đậm hơn
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .width(1.dp)
                            )
                        }
                    }
                }

                if (rowIndex < rows.size - 1) {
                    Divider(
                        color = borderColor.copy(alpha = 0.7f), // Làm đậm hơn
                        thickness = 0.5.dp
                    )
                }
            }
        }
    }
}

/**
 * Tính toán chiều rộng cột dựa trên nội dung thực tế
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
                // Tách văn bản thành các dòng
                val lines = cell.text.split("\n")
                
                // Tìm dòng dài nhất
                val maxLineLength = lines.maxOfOrNull { it.length } ?: cell.text.length
                
                // Cập nhật độ dài tối đa cho cột này
                maxTextLengths[idx] = maxOf(maxTextLengths[idx], maxLineLength)
            }
        }
    }
    
    // Tính chiều rộng cho mỗi cột dựa hoàn toàn vào nội dung
    return maxTextLengths.map { textLength ->
        // Điều chỉnh hệ số dựa trên loại nội dung
        val characterWidth = when {
            textLength <= 5 -> 8.0f  // Cột nhỏ, text ngắn: cần nhiều không gian tương đối
            textLength <= 15 -> 7.0f // Cột trung bình
            else -> 6.5f             // Cột lớn, nhiều nội dung: có thể tối ưu không gian
        }
        
        // Tăng padding cố định để tạo khoảng thở cho text
        val fixedPadding = 28f 
        
        // Chiều rộng dựa trên nội dung thực tế
        val contentBasedWidth = textLength * characterWidth + fixedPadding
        
        // Giới hạn tối đa 50% màn hình, tối thiểu 40dp
        contentBasedWidth.coerceIn(40f, maxColumnWidth).dp
    }
} 