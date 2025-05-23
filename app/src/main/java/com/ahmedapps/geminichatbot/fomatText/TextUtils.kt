package com.ahmedapps.geminichatbot.fomatText

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

/**
 * Enhanced function to parse formatted text with support for combined and nested styles,
 * headings, links, blockquotes, images, tables, and inline code.
 */
fun parseFormattedText(input: String): AnnotatedString {
    val builder = AnnotatedString.Builder()
    val lines = input.trimEnd().lines()

    var listItemNumber = 1
    var wasPreviousLineListItem = false
    var isInCodeBlock = false
    var codeBlockLanguage: String? = null
    var codeBlockContent = StringBuilder()
    var isInTable = false
    var tableHeader: List<String> = emptyList()
    val tableRows: MutableList<List<String>> = mutableListOf()
    
    // For blockquotes
    var isInBlockquote = false
    var blockquoteContent = StringBuilder()


    for ((index, line) in lines.withIndex()) {
        // --- Table Handling ---
        if (line.trim().startsWith("|") && line.trim().endsWith("|")) {
            // Kiểm tra xem dòng này có phải là separator không
            val isSeparatorLine = line.trim().replace("|", "").trim().all { it == '-' || it == ' ' }
            
            if (!isSeparatorLine) {  // Chỉ xử lý nếu không phải dòng separator
                if (!isInTable) {
                    isInTable = true
                }
                val cells = line.split("|").map { it.trim() }.filter { it.isNotEmpty() }

                if (cells.isNotEmpty()) {
                    if (tableHeader.isEmpty()) {
                        tableHeader = cells
                    } else {
                        tableRows.add(cells)
                    }
                }
            }
            
            if (index < lines.lastIndex) {
                continue // Process next line.  Crucial for correct table parsing.
            }
        } else if (isInTable) {
            //End table
            appendTable(builder, tableHeader, tableRows)
            tableHeader = emptyList()
            tableRows.clear()
            isInTable = false
        }
        // --- End Table Handling ---

        // Handle blockquote
        if (line.trim().startsWith(">")) {
            if (!isInBlockquote) {
                isInBlockquote = true
                blockquoteContent = StringBuilder()
            }
            // Extract content without the ">" prefix
            val content = line.trim().substring(1).trim()
            blockquoteContent.appendLine(content)
            
            if (index < lines.lastIndex) {
                continue // Process next line for blockquote
            } else {
                // End blockquote if last line
                appendBlockquote(builder, blockquoteContent.toString().trim())
                isInBlockquote = false
            }
        } else if (isInBlockquote) {
            // End previous blockquote
            appendBlockquote(builder, blockquoteContent.toString().trim())
            isInBlockquote = false
        }

        // Handle code blocks
        if (line.trim().startsWith("```")) {
            if (isInCodeBlock) {
                // End code block
                val codeContent = codeBlockContent.toString().trimEnd()
                val codeContentTrimmed =
                    if (codeContent.endsWith("\n")) codeContent.dropLast(1) else codeContent
                val start = builder.length
                builder.append(codeContentTrimmed)
                val end = builder.length

                val annotationItem =
                    codeBlockLanguage?.let { "$it::$codeContentTrimmed" } ?: "plain::$codeContentTrimmed"
                builder.addStringAnnotation("CODE_BLOCK", annotationItem, start, end)

                codeBlockContent.clear()
                codeBlockLanguage = null
                isInCodeBlock = false
                wasPreviousLineListItem = false
            } else {
                // Start code block
                isInCodeBlock = true
                val parts = line.trim().split("```", limit = 2)
                codeBlockLanguage = parts.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
            }
            continue
        }

        if (isInCodeBlock) {
            codeBlockContent.appendLine(line)
            continue
        }

        var processedLine = line

        // Handle headings
        val headingMatch = """^#+ (.*)$""".toRegex().find(line)
        if (headingMatch != null) {
            val content = headingMatch.groupValues[1]
            val level = line.countLeading('#')
            val fontSize = when (level) {
                1 -> 24.sp
                2 -> 20.sp
                3 -> 18.sp
                else -> 16.sp
            }
            builder.withStyle(SpanStyle(fontSize = fontSize, fontWeight = FontWeight.Bold)) {
                append(content)
            }
            wasPreviousLineListItem = false
        }
        // Handle list items
        else {
            val listMatch = """^(\s*)\*\s+(.*)""".toRegex().find(line)
            if (listMatch != null) {
                val leadingSpaces = listMatch.groupValues[1].length
                val content = listMatch.groupValues[2]
                val level = (leadingSpaces / 4) + 1

                if (level == 1 && !wasPreviousLineListItem) {
                    listItemNumber = 1 // Reset numbering for a new level-1 list
                }

                val marker = when (level) {
                    1 -> "${listItemNumber++}. "
                    2 -> "• "
                    3 -> "◦ "
                    else -> "• "
                }
                val indentation = "    ".repeat(level - 1)
                processedLine = "$indentation$marker$content"
                wasPreviousLineListItem = true
            } else {
                wasPreviousLineListItem = false
            }

            // Handle inline code, images, and styles
            var remainingText = processedLine
            var currentIndex = 0
            val inlineCodePattern = "(?<!\\\\)`([^`]+)(?<!\\\\)`".toRegex()
            val imagePattern = "!\\[([^\\]]*)\\]\\([^)]*\\)".toRegex()

            while (currentIndex < remainingText.length) {
                val inlineCodeMatch = inlineCodePattern.find(remainingText, currentIndex)
                val imageMatch = imagePattern.find(remainingText, currentIndex)

                val earliestMatch = listOfNotNull(inlineCodeMatch, imageMatch)
                    .minByOrNull { it.range.first }

                if (earliestMatch == null) {
                    applyInlineStyles(builder, remainingText.substring(currentIndex))
                    break
                }

                val start = earliestMatch.range.first
                val end = earliestMatch.range.last + 1

                if (start > currentIndex) {
                    val beforeText = remainingText.substring(currentIndex, start)
                    applyInlineStyles(builder, beforeText)
                }

                when (earliestMatch) {
                    inlineCodeMatch -> {
                        val codeText = earliestMatch.groupValues[1]
                        val codeStart = builder.length
                        builder.withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = Color(0xFFE0E0E0),
                                color = Color(0xFF000000)
                            )
                        ) {
                            append(codeText)
                        }
                        val codeEnd = builder.length
                        builder.addStringAnnotation("INLINE_CODE", codeText, codeStart, codeEnd)
                    }
                    imageMatch -> {
                        val altText = earliestMatch.groupValues[1]
                        builder.withStyle(SpanStyle(color = Color.Gray, fontStyle = FontStyle.Italic)) {
                            append(altText)
                        }
                    }
                }

                currentIndex = end
            }
        }

        if (index < lines.lastIndex && !isInCodeBlock && !isInTable && !isInBlockquote) {
            builder.append("\n")
        }
    }

    // Handle unclosed code block
    if (isInCodeBlock && codeBlockContent.isNotEmpty()) {
        val codeContent = codeBlockContent.toString().trimEnd()
        val start = builder.length
        builder.append(codeContent)
        val end = builder.length
        val annotationItem = codeBlockLanguage?.let { "$it::$codeContent" } ?: "plain::$codeContent"
        builder.addStringAnnotation("CODE_BLOCK", annotationItem, start, end)
    }

    // Handle any remaining table
    if (isInTable) {
        appendTable(builder, tableHeader, tableRows)
    }
    
    // Handle any remaining blockquote
    if (isInBlockquote) {
        appendBlockquote(builder, blockquoteContent.toString().trim())
    }

    return builder.toAnnotatedString()
}

/**
 * Appends a formatted table to the AnnotatedString.Builder.
 */
private fun appendTable(
    builder: AnnotatedString.Builder,
    header: List<String>,
    rows: List<List<String>>
) {
    if (header.isEmpty()) return

    // Add a special annotation for the table
    val tableStart = builder.length
    
    // Mark the beginning of a new line if needed
    if (tableStart > 0 && !builder.toString().endsWith("\n")) {
        builder.append("\n")
    }
    
    // Create the table header and rows as a single annotation
    val tableContent = buildTableContent(header, rows)
    
    // Append placeholder text and add the annotation
    builder.append(tableContent)
    val tableEnd = builder.length
    
    // Add a table annotation with all data serialized
    val headerJson = header.joinToString("|||")
    val rowsJson = rows.joinToString(";;;") { row ->
        row.joinToString("|||")
    }
    
    builder.addStringAnnotation(
        tag = "TABLE",
        annotation = "$headerJson::$rowsJson",
        start = tableStart,
        end = tableEnd
    )
    
    // Add a newline after the table if needed
    if (!builder.toString().endsWith("\n")) {
        builder.append("\n")
    }
}

/**
 * Xây dựng nội dung bảng dựa trên header và rows
 */
fun buildTableContent(header: List<String>, rows: List<List<String>>): String {
    val sb = StringBuilder()

    // Header row - giữ nguyên không thay đổi định dạng markdown
    header.forEach { cell ->
        sb.append("| $cell ")
    }
    sb.append("|\n")

    // Data rows - giữ nguyên không thay đổi định dạng markdown
    rows.forEach { row ->
        row.forEach { cell ->
            sb.append("| $cell ")
        }
        sb.append("|\n")
    }

    return sb.toString()
}

/**
 * Xác định chiều rộng thích hợp cho mỗi cột dựa trên nội dung
 */
private fun determineColumnWidths(header: List<String>, rows: List<List<String>>): List<Int> {
    val columnCount = header.size
    val widths = MutableList(columnCount) { 0 }

    // Check header lengths
    header.forEachIndexed { index, cell ->
        widths[index] = cell.length.coerceAtLeast(widths[index])
    }

    // Check all row cell lengths
    rows.forEach { row ->
        row.forEachIndexed { index, cell ->
            if (index < columnCount) {
                widths[index] = cell.length.coerceAtLeast(widths[index])
            }
        }
    }

    // Ensure minimum width for better readability
    return widths.map { it.coerceAtLeast(3) }
}

/**
 * Xử lý markdown trong các ô của bảng (trả về plain text, chuẩn bị cho hiển thị)
 */
fun processTableCellMarkdown(cellContent: String): String {
    // Loại bỏ các định dạng markdown để xác định chiều rộng cột chính xác
    var content = cellContent

    // Xử lý bold (**text**)
    content = content.replace("""(\*\*)(.*?)(\*\*)""".toRegex()) { it.groupValues[2] }

    // Xử lý italic (*text*)
    content = content.replace("""(?<!\*)\*(?!\*)(.*?)(?<!\*)\*(?!\*)""".toRegex()) { it.groupValues[1] }

    // Xử lý bold-italic (***text***)
    content = content.replace("""(\*\*\*)(.*?)(\*\*\*)""".toRegex()) { it.groupValues[2] }

    // Xử lý inline code (`text`)
    content = content.replace("""`(.*?)`""".toRegex()) { it.groupValues[1] }

    return content
}

/**
 * Appends a blockquote to the AnnotatedString.Builder.
 */
private fun appendBlockquote(
    builder: AnnotatedString.Builder,
    content: String
) {
    if (content.isEmpty()) return
    
    // Add a special annotation for the blockquote
    val blockquoteStart = builder.length
    
    // Mark the beginning of a new line if needed
    if (blockquoteStart > 0 && !builder.toString().endsWith("\n")) {
        builder.append("\n")
    }
    
    builder.append(content)
    val blockquoteEnd = builder.length
    
    builder.addStringAnnotation(
        tag = "BLOCKQUOTE",
        annotation = content,
        start = blockquoteStart,
        end = blockquoteEnd
    )
    
    // Add a newline after the blockquote
    if (!builder.toString().endsWith("\n")) {
        builder.append("\n")
    }
}

/**
 * Updated keyword colors map for Kotlin-specific syntax highlighting.
 */
private val keywordColors = mapOf(
    "kotlin" to listOf(
        // Control flow and declarations (Light Blue)
        "as" to Color(0xFF2B4EDF),
        "break" to Color(0xFF2B4EDF),
        "class" to Color(0xFF2B4EDF),
        "continue" to Color(0xFF2B4EDF),
        "do" to Color(0xFF2B4EDF),
        "else" to Color(0xFF2B4EDF),
        "for" to Color(0xFF2B4EDF),
        "fun" to Color(0xFF2B4EDF),
        "if" to Color(0xFF2B4EDF),
        "in" to Color(0xFF2B4EDF),
        "interface" to Color(0xFF2B4EDF),
        "is" to Color(0xFF2B4EDF),
        "object" to Color(0xFF2B4EDF),
        "return" to Color(0xFF2B4EDF),
        "throw" to Color(0xFF2B4EDF),
        "try" to Color(0xFF2B4EDF),
        "catch" to Color(0xFF2B4EDF),
        "finally" to Color(0xFF2B4EDF),
        "typealias" to Color(0xFF2B4EDF),
        "val" to Color(0xFF2B4EDF),
        "var" to Color(0xFF2B4EDF),
        "when" to Color(0xFF2B4EDF),
        "while" to Color(0xFF2B4EDF),
        "package" to Color(0xFF2B4EDF),
        "import" to Color(0xFF2B4EDF),

        // Modifiers and other keywords (Light Red)
        "private" to Color(0xFFAA3A3A),
        "protected" to Color(0xFFAA3A3A),
        "public" to Color(0xFFAA3A3A),
        "internal" to Color(0xFFAA3A3A),
        "data" to Color(0xFFAA3A3A),
        "sealed" to Color(0xFFAA3A3A),
        "const" to Color(0xFFAA3A3A),
        "lateinit" to Color(0xFFAA3A3A),
        "vararg" to Color(0xFFAA3A3A),
        "override" to Color(0xFFAA3A3A),
        "open" to Color(0xFFAA3A3A),
        "abstract" to Color(0xFFAA3A3A),
        "enum" to Color(0xFFAA3A3A),
        "annotation" to Color(0xFFAA3A3A),
        "inner" to Color(0xFFAA3A3A),
        "tailrec" to Color(0xFFAA3A3A),
        "operator" to Color(0xFFAA3A3A),
        "infix" to Color(0xFFAA3A3A),
        "inline" to Color(0xFFAA3A3A),
        "noinline" to Color(0xFFAA3A3A),
        "crossinline" to Color(0xFFAA3A3A),
        "external" to Color(0xFFAA3A3A),
        "actual" to Color(0xFFAA3A3A),
        "expect" to Color(0xFFAA3A3A),
        "reified" to Color(0xFFAA3A3A),

        // Async-related (Light Green)
        "suspend" to Color(0xFF66A16B),

        // Literals (Khaki)
        "true" to Color(0xFFE6EE9C),
        "false" to Color(0xFFE6EE9C),
        "null" to Color(0xFFE6EE9C)
    )
)

/**
 * Function to apply syntax highlighting to code based on keywordColors and additional patterns.
 */
fun syntaxHighlight(code: String): AnnotatedString {
    val builder = AnnotatedString.Builder(code)

    // Handle multi-line strings first
    val multiLineStringRegex = """\"\"\"[\s\S]*?\"\"\"""".toRegex()
    val multiLineMatches = multiLineStringRegex.findAll(code).toList().sortedBy { it.range.first }

    var currentIndex = 0
    for (match in multiLineMatches) {
        val start = match.range.first
        val end = match.range.last + 1

        if (start > currentIndex) {
            val beforeText = code.substring(currentIndex, start)
            applySyntaxHighlighting(builder, beforeText)
        }

        builder.addStyle(
            style = SpanStyle(color = Color(0xFFFFA500)), // Orange for strings
            start = start,
            end = end
        )
        currentIndex = end
    }

    if (currentIndex < code.length) {
        val remainingText = code.substring(currentIndex)
        applySyntaxHighlighting(builder, remainingText)
    }

    return builder.toAnnotatedString()
}

/**
 * Helper function to apply syntax highlighting to a segment of code.
 */
private fun applySyntaxHighlighting(builder: AnnotatedString.Builder, code: String) {
    val keywords = keywordColors["kotlin"] ?: emptyList()
    val sortedKeywords = keywords.sortedByDescending { it.first.length }

    // Apply keyword colors
    for ((keyword, color) in sortedKeywords) {
        val regex = Regex("\\b${Regex.escape(keyword)}\\b")
        regex.findAll(code).forEach { matchResult ->
            builder.addStyle(
                style = SpanStyle(color = color),
                start = matchResult.range.first,
                end = matchResult.range.last + 1
            )
        }
    }

    // Comments
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

    // Single-line strings
    val stringRegex = Regex("\"([^\"\\\\]|\\\\.)*\"")
    stringRegex.findAll(code).forEach { matchResult ->
        builder.addStyle(
            style = SpanStyle(color = Color(0xFFFFA500)), // Orange
            start = matchResult.range.first,
            end = matchResult.range.last + 1
        )
    }

    // Numbers (including decimals and exponents)
    val numberRegex = Regex("(\\b\\d+(\\.\\d*)?|\\.\\d+)([eE][+-]?\\d+)?\\b")
    numberRegex.findAll(code).forEach { matchResult ->
        builder.addStyle(
            style = SpanStyle(color = Color(0xFF006400)), // Dark Green
            start = matchResult.range.first,
            end = matchResult.range.last + 1
        )
    }

    // Annotations
    val annotationRegex = Regex("@\\w+")
    annotationRegex.findAll(code).forEach { matchResult ->
        builder.addStyle(
            style = SpanStyle(color = Color(0xFFFF1493)), // Deep Pink
            start = matchResult.range.first,
            end = matchResult.range.last + 1
        )
    }

    // Operators
    val operators = listOf("+", "-", "*", "/", "=", "==", ">", "<", ">=", "<=", "&&", "||", "!", "?", ":")
    val operatorRegex = Regex("(${operators.joinToString("|") { Regex.escape(it) }})")
    operatorRegex.findAll(code).forEach { matchResult ->
        builder.addStyle(
            style = SpanStyle(color = Color(0xFFFF0000)), // Red
            start = matchResult.range.first,
            end = matchResult.range.last + 1
        )
    }

    // Types
    val types =
        listOf("Int", "String", "Float", "Double", "Boolean", "Long", "Short", "Byte", "Char", "Unit", "Any")
    val typeRegex = Regex("\\b(${types.joinToString("|") { Regex.escape(it) }})\\b")
    typeRegex.findAll(code).forEach { matchResult ->
        builder.addStyle(
            style = SpanStyle(color = Color(0xFF0000FF), fontWeight = FontWeight.Bold), // Blue
            start = matchResult.range.first,
            end = matchResult.range.last + 1
        )
    }
}

/**
 * Helper function to apply inline styles based on markdown patterns.
 */
private fun applyInlineStyles(
    builder: AnnotatedString.Builder,
    text: String
) {
    val patterns = listOf(
        "(?<!\\\\)\\*\\*_(.*?)_(?<!\\\\)\\*\\*" to SpanStyle(
            fontWeight = FontWeight.Bold,
            fontStyle = FontStyle.Italic
        ),
        "(?<!\\\\)\\*_(.*?)_(?<!\\\\)\\*" to SpanStyle(fontStyle = FontStyle.Italic),
        "(?<!\\\\)\\*\\*(.*?)(?<!\\\\)\\*\\*" to SpanStyle(fontWeight = FontWeight.Bold),
        "(?<!\\\\)\\*(.*?)(?<!\\\\)\\*" to SpanStyle(fontStyle = FontStyle.Italic),
        "(?<!\\\\)__([\\s\\S]+?)(?<!\\\\)__" to SpanStyle(textDecoration = TextDecoration.Underline),
        "\\[([^\\]]*)\\]\\(([^)]*)\\)" to SpanStyle(
            color = Color.Blue,
            textDecoration = TextDecoration.Underline
        )
    )
    var currentIndex = 0

    while (currentIndex < text.length) {
        var earliestMatch: MatchResult? = null
        var selectedPattern: Pair<String, SpanStyle>? = null

        for ((pattern, style) in patterns) {
            val regex = pattern.toRegex()
            val match = regex.find(text, currentIndex)
            if (match != null && (earliestMatch == null || match.range.first < earliestMatch.range.first)) {
                earliestMatch = match
                selectedPattern = Pair(pattern, style)
            }
        }

        if (earliestMatch != null && selectedPattern != null) {
            val start = earliestMatch.range.first
            val end = earliestMatch.range.last + 1
            val content = earliestMatch.groupValues[1]

            if (start > currentIndex) {
                val beforeText = text.substring(currentIndex, start)
                builder.append(beforeText)
            }

            // Link
            if (selectedPattern.first.contains("\\[([^\\]]*)\\]\\(([^)]*)\\)")) {
                val url = earliestMatch.groupValues[2]
                val linkStart = builder.length
                builder.withStyle(selectedPattern.second) {
                    applyInlineStyles(builder, content) // nested
                }
                val linkEnd = builder.length
                builder.addStringAnnotation("URL", url, linkStart, linkEnd)
            } else {
                builder.withStyle(selectedPattern.second) {
                    applyInlineStyles(builder, content)
                }
            }

            currentIndex = end
        } else {
            val remainingText = text.substring(currentIndex)
            builder.append(remainingText)
            break
        }
    }
}

/**
 * Helper function to count leading '#' characters in a string.
 */
private fun String.countLeading(char: Char): Int {
    return takeWhile { it == char }.length
}
