// UserChatItem.kt
package com.ahmedapps.geminichatbot.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import fomatText.FormattedTextDisplay
import fomatText.parseFormattedText
import kotlinx.coroutines.launch
import androidx.compose.runtime.*



@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserChatItem(
    prompt: String,
    imageUrl: String?,
    isError: Boolean,
    onLongPress: (String) -> Unit,
    onImageClick: (String) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val isDarkTheme = isSystemInDarkTheme()
    val backgroundColor = when {
        isError -> MaterialTheme.colorScheme.error
        isDarkTheme -> Color(0x43FFFFFF)
        else -> Color(0x97FFFFFF)
    }
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxImageHeight = (screenHeight * 0.3f).coerceAtLeast(175.dp)
    val maxWidth = LocalConfiguration.current.screenWidthDp.dp * 0.7f

    val formattedPrompt = parseFormattedText(prompt)
    val scope = rememberCoroutineScope()


    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Column(horizontalAlignment = Alignment.End) {
            imageUrl?.let { url ->
                AsyncImage( // Giữ combinedClickable ở đây
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Your Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .heightIn(max = maxImageHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color.Transparent, RoundedCornerShape(12.dp))
                        .combinedClickable(
                            onClick = { onImageClick(url) },
                            onLongClick = { onLongPress(prompt) }
                        )
                )
            }
            Spacer(modifier = Modifier.height(8.dp))


            if (prompt.isNotEmpty()) {
                SelectionContainer { // Giữ SelectionContainer
                    FormattedTextDisplay(
                        annotatedString = formattedPrompt,
                        modifier = Modifier  // Loại bỏ combinedClickable
                            .widthIn(max = maxWidth)
                            .clip(RoundedCornerShape(17.dp))
                            .background(backgroundColor)
                            .padding(12.dp),
//                            .combinedClickable(
//                                onClick = {},
//                                onLongClick = { onLongPress(prompt) }
//                            ),
                        snackbarHostState = snackbarHostState
                    )
                }
            }
        }
    }
}