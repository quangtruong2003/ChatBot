// TextUtils.kt
package fomatText

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import org.intellij.lang.annotations.Language



/**
 * Enhanced function to parse formatted text with support for combined and nested styles.
 */
fun parseFormattedText(input: String): AnnotatedString {
    // Define markdown patterns and their corresponding SpanStyles
    val patterns = listOf(
        "\\*\\*_(.*?)_\\*\\*" to SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
        "\\*_(.*?)_\\*" to SpanStyle(fontStyle = FontStyle.Italic),
        "\\*\\*(.*?)\\*\\*" to SpanStyle(fontWeight = FontWeight.Bold),
        "\\*(.*?)\\*" to SpanStyle(fontStyle = FontStyle.Italic),
        "__([\\s\\S]+?)__" to SpanStyle(textDecoration = TextDecoration.Underline)
    )

    val builder = AnnotatedString.Builder()
    val lines = input.trimEnd().lines()

    var listItemNumber = 1
    var isInCodeBlock = false
    var codeBlockLanguage: String? = null
    var codeBlockContent = StringBuilder()

    for ((index, line) in lines.withIndex()) {
        // Xử lý đoạn mã
        if (line.trim().startsWith("```")) {
            if (isInCodeBlock) {
                // Kết thúc đoạn mã
                val codeContent = codeBlockContent.toString().trimEnd()
                // Loại bỏ xuống dòng cuối cùng để tránh xuống dòng không cần thiết
                val codeContentTrimmed = if (codeContent.endsWith("\n")) {
                    codeContent.dropLast(1)
                } else {
                    codeContent
                }
                val start = builder.length
                builder.append(codeContentTrimmed)
                val end = builder.length

                val annotationItem = if (codeBlockLanguage != null) {
                    "$codeBlockLanguage::$codeContentTrimmed"
                } else {
                    "plain::$codeContentTrimmed"
                }
                builder.addStringAnnotation(
                    tag = "CODE_BLOCK",
                    annotation = annotationItem,
                    start = start,
                    end = end
                )

                codeBlockContent.clear()
                codeBlockLanguage = null
                isInCodeBlock = false
            } else {
                // Bắt đầu đoạn mã
                isInCodeBlock = true
                val parts = line.trim().split("```", limit = 2)
                codeBlockLanguage = if (parts.size > 1 && parts[1].isNotBlank()) {
                    parts[1].trim()
                } else {
                    null
                }
            }
            continue
        }

        if (isInCodeBlock) {
            // Bên trong đoạn mã
            codeBlockContent.appendLine(line)
            continue
        }

        var processedLine = line

        // Xử lý các mục danh sách
        val listMatch = """^(\s*)\*\s+(.*)""".toRegex().find(line)
        if (listMatch != null) {
            val leadingSpaces = listMatch.groupValues[1].length
            val content = listMatch.groupValues[2]
            val level = (leadingSpaces / 4) + 1

            val marker = when (level) {
                1 -> "${listItemNumber++}. "
                2 -> "• "
                3 -> "◦ "
                else -> "• "
            }

            val indentation = "    ".repeat(level - 1)
            processedLine = "$indentation$marker$content"
        }

        // Xử lý các kiểu định dạng nội tuyến
        var remainingText = processedLine
        var currentIndex = 0

        // Xử lý code nội tuyến trước để tránh xung đột với các kiểu khác
        val inlineCodePattern = """`([^`]+)`""".toRegex()
        inlineCodePattern.findAll(remainingText).forEach { matchResult ->
            val start = matchResult.range.first
            val end = matchResult.range.last + 1
            val codeText = matchResult.groupValues[1]

            // Chèn văn bản trước khi gặp code nội tuyến
            if (start > currentIndex) {
                val beforeText = remainingText.substring(currentIndex, start)
                applyInlineStyles(builder, beforeText, patterns)
            }

            // Chèn code nội tuyến với kiểu monospace
            val codeStart = builder.length
            builder.withStyle(
                style = SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color(0xFFE0E0E0),
                    color = Color(0xFF000000)
                )
            ) {
                builder.append(codeText)
            }
            val codeEnd = builder.length
            builder.addStringAnnotation(
                tag = "INLINE_CODE",
                annotation = codeText,
                start = codeStart,
                end = codeEnd
            )

            currentIndex = end
        }

        // Chèn văn bản còn lại sau code nội tuyến
        if (currentIndex < remainingText.length) {
            val afterText = remainingText.substring(currentIndex)
            applyInlineStyles(builder, afterText, patterns)
        }

        // Chèn xuống dòng nếu không phải dòng cuối cùng và không phải ngay sau đoạn mã
        if (index < lines.lastIndex && !isInCodeBlock) {
            builder.append("\n")
        }
    }

    // Xử lý đoạn mã còn lại nếu có
    if (isInCodeBlock && codeBlockContent.isNotEmpty()) {
        val codeContent = codeBlockContent.toString().trimEnd()
        val start = builder.length
        builder.append(codeContent)
        val end = builder.length

        val annotationItem = if (codeBlockLanguage != null) {
            "$codeBlockLanguage::$codeContent"
        } else {
            "plain::$codeContent"
        }
        builder.addStringAnnotation(
            tag = "CODE_BLOCK",
            annotation = annotationItem,
            start = start,
            end = end
        )
    }

    return builder.toAnnotatedString()
}


private val keywordColors = mapOf(
    "all" to listOf(
        // Kotlin keywords
        "fun" to Color(0xFF2B4EDF), // Light Blue
        "val" to Color(0xFF2B4EDF),
        "var" to Color(0xFF2B4EDF),
        "class" to Color(0xFF2B4EDF),
        "interface" to Color(0xFF2B4EDF),
        "object" to Color(0xFF2B4EDF),
        "if" to Color(0xFF2B4EDF),
        "else" to Color(0xFF2B4EDF),
        "when" to Color(0xFF2B4EDF),
        "for" to Color(0xFF2B4EDF),
        "while" to Color(0xFF2B4EDF),
        "do" to Color(0xFF2B4EDF),
        "in" to Color(0xFF2B4EDF),
        "is" to Color(0xFF2B4EDF),
        "as" to Color(0xFF2B4EDF),
        "return" to Color(0xFF2B4EDF),
        "break" to Color(0xFF2B4EDF),
        "continue" to Color(0xFF2B4EDF),
        "throw" to Color(0xFF2B4EDF),
        "try" to Color(0xFF2B4EDF),
        "catch" to Color(0xFF2B4EDF),
        "finally" to Color(0xFF2B4EDF),

        // Access modifiers và các từ khóa khác
        "private" to Color(0xFFAA3A3A), // Light Red
        "protected" to Color(0xFFAA3A3A),
        "public" to Color(0xFFAA3A3A),
        "internal" to Color(0xFFAA3A3A),
        "data" to Color(0xFFAA3A3A),
        "sealed" to Color(0xFFAA3A3A),
        "const" to Color(0xFFAA3A3A),
        "static" to Color(0xFFAA3A3A),
        "final" to Color(0xFFAA3A3A),
        "abstract" to Color(0xFFAA3A3A),
        "native" to Color(0xFFAA3A3A),
        "synchronized" to Color(0xFFAA3A3A),
        "operator" to Color(0xFFAA3A3A),
        "inline" to Color(0xFFAA3A3A),
        "lateinit" to Color(0xFFAA3A3A),
        "tailrec" to Color(0xFFAA3A3A),
        "reified" to Color(0xFFAA3A3A),
        "noinline" to Color(0xFFAA3A3A),
        "crossinline" to Color(0xFFAA3A3A),
        "expect" to Color(0xFFAA3A3A),
        "actual" to Color(0xFFAA3A3A),

        // Các từ khóa liên quan đến async
        "suspend" to Color(0xFF66A16B), // Light Green
        "async" to Color(0xFF66A16B),
        "await" to Color(0xFF66A16B),
        "yield" to Color(0xFF66A16B),
        "import" to Color(0xFF66A16B),
        "package" to Color(0xFF66A16B),
        "extends" to Color(0xFF66A16B),
        "implements" to Color(0xFF66A16B),
        "new" to Color(0xFF66A16B),
        "super" to Color(0xFF66A16B),
        "this" to Color(0xFF66A16B),
        "instanceof" to Color(0xFF66A16B),
        "switch" to Color(0xFF66A16B),
        "case" to Color(0xFF66A16B),
        "default" to Color(0xFF66A16B),
        "void" to Color(0xFF66A16B),
        "boolean" to Color(0xFF2B4EDF), // Cyan
        "byte" to Color(0xFF2B4EDF),
        "short" to Color(0xFF2B4EDF),
        "char" to Color(0xFF2B4EDF),
        "int" to Color(0xFF2B4EDF),
        "long" to Color(0xFF2B4EDF),
        "float" to Color(0xFF2B4EDF),
        "double" to Color(0xFF2B4EDF),
        "String" to Color(0xFF2B4EDF),

        // Các từ khóa giá trị
        "true" to Color(0xFFE6EE9C), // Khaki
        "false" to Color(0xFFE6EE9C),
        "null" to Color(0xFFE6EE9C),
        "undefined" to Color(0xFFE6EE9C),
        "NaN" to Color(0xFFE6EE9C),

        // Các từ khóa khác
        "let" to Color(0xFFCE93D8), // Purple
        "def" to Color(0xFFCE93D8),
        "from" to Color(0xFFCE93D8),
        "global" to Color(0xFFCE93D8),
        "nonlocal" to Color(0xFFCE93D8),
        "lambda" to Color(0xFFCE93D8),
        "pass" to Color(0xFFCE93D8),
        "del" to Color(0xFFCE93D8),
        "assert" to Color(0xFFCE93D8),
        "with" to Color(0xFFCE93D8),
        "raise" to Color(0xFFCE93D8),
        "of" to Color(0xFFCE93D8),
        "elif" to Color(0xFFCE93D8),
    )
)
/**
 * Function to apply syntax highlighting to code based on keywordColors and additional patterns.
 */
fun syntaxHighlight(code: String): AnnotatedString {
    val builder = AnnotatedString.Builder(code)

    // Lấy danh sách các từ khóa và màu sắc từ keywordColors
    val keywords = keywordColors["all"] ?: emptyList()

    // Sắp xếp các từ khóa theo độ dài giảm dần để tránh xung đột (ví dụ: "public" trước "pub")
    val sortedKeywords = keywords.sortedByDescending { it.first.length }

    // Áp dụng màu cho các từ khóa
    for ((keyword, color) in sortedKeywords) {
        // Sử dụng regex với word boundaries để đảm bảo chỉ tìm đúng từ khóa
        val regex = Regex("\\b${Regex.escape(keyword)}\\b")
        regex.findAll(code).forEach { matchResult ->
            builder.addStyle(
                style = SpanStyle(color = color),
                start = matchResult.range.first,
                end = matchResult.range.last + 1
            )
        }
    }

    // Áp dụng màu cho chú thích (Comments)
    val singleLineCommentRegex = Regex("//.*")
    singleLineCommentRegex.findAll(code).forEach { matchResult ->
        builder.addStyle(
            style = SpanStyle(color = Color.Gray, fontStyle = FontStyle.Italic),
            start = matchResult.range.first,
            end = matchResult.range.last + 1
        )
    }

    val multiLineCommentRegex = Regex("/\\*(.|\\n)*?\\*/")
    multiLineCommentRegex.findAll(code).forEach { matchResult ->
        builder.addStyle(
            style = SpanStyle(color = Color.Gray, fontStyle = FontStyle.Italic),
            start = matchResult.range.first,
            end = matchResult.range.last + 1
        )
    }

    // Áp dụng màu cho chuỗi văn bản (Strings)
    val stringRegex = Regex("\"([^\"\\\\]|\\\\.)*\"")
    stringRegex.findAll(code).forEach { matchResult ->
        builder.addStyle(
            style = SpanStyle(color = Color(0xFFFFA500)), // Orange
            start = matchResult.range.first,
            end = matchResult.range.last + 1
        )
    }

    // Áp dụng màu cho số (Numbers)
    val numberRegex = Regex("\\b\\d+\\b")
    numberRegex.findAll(code).forEach { matchResult ->
        builder.addStyle(
            style = SpanStyle(color = Color(0xFF006400)), // Dark Green
            start = matchResult.range.first,
            end = matchResult.range.last + 1
        )
    }

    // Áp dụng màu cho annotations
    val annotationRegex = Regex("@\\w+")
    annotationRegex.findAll(code).forEach { matchResult ->
        builder.addStyle(
            style = SpanStyle(color = Color(0xFFFF1493)), // Deep Pink
            start = matchResult.range.first,
            end = matchResult.range.last + 1
        )
    }

    // Áp dụng màu cho toán tử (Operators)
    val operators = listOf("+", "-", "*", "/", "=", "==", ">", "<", ">=", "<=", "&&", "||", "!", "!", "?")
    val operatorRegex = Regex("(${operators.joinToString("|") { Regex.escape(it) }})")
    operatorRegex.findAll(code).forEach { matchResult ->
        builder.addStyle(
            style = SpanStyle(color = Color(0xFFFF0000)), // Red
            start = matchResult.range.first,
            end = matchResult.range.last + 1
        )
    }

    // Áp dụng màu cho các kiểu dữ liệu (Types)
    val types = listOf("Int", "String", "Float", "Double", "Boolean", "Long", "Short", "Byte", "Char", "Unit", "Any")
    val typeRegex = Regex("\\b(${types.joinToString("|") { Regex.escape(it) }})\\b")
    typeRegex.findAll(code).forEach { matchResult ->
        builder.addStyle(
            style = SpanStyle(color = Color(0xFF0000FF), fontWeight = FontWeight.Bold), // Blue
            start = matchResult.range.first,
            end = matchResult.range.last + 1
        )
    }

    return builder.toAnnotatedString()
}
/**
 * Helper function to apply inline styles based on markdown patterns.
 */
private fun applyInlineStyles(
    builder: AnnotatedString.Builder,
    text: String,
    patterns: List<Pair<String, SpanStyle>>
) {
    var remainingText = text
    var currentIndex = 0

    while (currentIndex < remainingText.length) {
        var earliestMatch: MatchResult? = null
        var selectedPattern: Pair<String, SpanStyle>? = null

        // Tìm kiếm mẫu phù hợp sớm nhất trong văn bản
        for ((pattern, style) in patterns) {
            val regex = pattern.toRegex()
            val match = regex.find(remainingText, currentIndex)
            if (match != null && (earliestMatch == null || match.range.first < earliestMatch.range.first)) {
                earliestMatch = match
                selectedPattern = Pair(pattern, style)
            }
        }

        if (earliestMatch != null && selectedPattern != null) {
            if (earliestMatch.range.first > currentIndex) {
                // Chèn văn bản trước khi gặp định dạng đặc biệt
                val beforeText = remainingText.substring(currentIndex, earliestMatch.range.first)
                builder.append(beforeText)
            }

            // Áp dụng kiểu định dạng cho đoạn văn bản đã chọn
            val content = earliestMatch.groupValues[1]
            builder.withStyle(
                style = selectedPattern.second
            ) {
                builder.append(content)
            }

            // Cập nhật currentIndex để tiếp tục xử lý phần còn lại
            currentIndex = earliestMatch.range.last + 1
        } else {
            // Nếu không còn mẫu nào phù hợp, chèn phần còn lại của văn bản
            builder.append(remainingText.substring(currentIndex))
            break
        }
    }
}

