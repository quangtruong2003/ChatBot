// AnnotatedStringSection.kt
package fomatText

import androidx.compose.ui.text.AnnotatedString

data class AnnotatedStringSection(
    val type: String, // "TEXT", "CODE_BLOCK", "INLINE_CODE"
    val content: String,
    val language: String? = null,
    val annotatedString: AnnotatedString = AnnotatedString("")
)
