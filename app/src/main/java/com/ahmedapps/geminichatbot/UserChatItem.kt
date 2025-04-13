// UserChatItem.kt
package com.ahmedapps.geminichatbot

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.ui.platform.LocalFocusManager
import com.ahmedapps.geminichatbot.ui.components.AudioPlayerComponent

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserChatItem(
    prompt: String,
    imageUrl: String?,
    isError: Boolean,
    isFileMessage: Boolean = false,
    fileName: String? = null,
    onLongPress: (String) -> Unit,
    onImageClick: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
    chatId: String? = null,
    onDeleteClick: (String) -> Unit = {},
    onEditClick: (String) -> Unit = {},
    isBeingEdited: Boolean = false
) {
    val isDarkTheme = isSystemInDarkTheme()
    val backgroundColor = when {
        isError -> MaterialTheme.colorScheme.error
        isBeingEdited -> MaterialTheme.colorScheme.primaryContainer
        isDarkTheme -> Color(0x43FFFFFF)
        else -> Color(0x97FFFFFF)
    }
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val maxImageHeight = (screenHeight * 0.3f).coerceAtLeast(175.dp)
    val maxWidth = LocalConfiguration.current.screenWidthDp.dp * 0.7f

    val formattedPrompt = parseFormattedText(prompt)
    val scope = rememberCoroutineScope()
    
    // Context để hiển thị Toast
    val context = LocalContext.current
    
    // Clipboard manager để copy văn bản
    val clipboardManager = LocalClipboardManager.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.End
    ) {
        Column(horizontalAlignment = Alignment.End) {
            imageUrl?.let { url ->
                // Kiểm tra nếu là tin nhắn file
                if (isFileMessage && fileName != null) {
                    val safeFileName = fileName
                    val safeImageUrl = url

                    // Kiểm tra nếu là file âm thanh
                    if (safeFileName != null && safeImageUrl != null) {
                        if (safeFileName.endsWith(".ogg") || safeFileName.endsWith(".m4a") || 
                            safeFileName.endsWith(".mp3") || safeFileName.endsWith(".wav")) {
                            AudioPlayerComponent(
                                context = LocalContext.current,
                                audioSource = safeImageUrl,
                                modifier = Modifier
                                    .widthIn(max = maxWidth)
                                    .heightIn(max = 80.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                isCompact = true
                            )
                        } else {
                            // Hiển thị hình ảnh nếu không phải file âm thanh
                            AsyncImage(
                                model = safeImageUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(8.dp)
                                    .heightIn(max = 240.dp)
                                    .widthIn(max = 240.dp),
                                contentScale = ContentScale.Fit
                            )

                            // Hiển thị thông tin file
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, end = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Attachment,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = Color.Gray
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = safeFileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                } else {
                    // Hiển thị hình ảnh như bình thường
                    AsyncImage(
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
            }
            
            Spacer(modifier = Modifier.height(8.dp))

            if (prompt.isNotEmpty()) {
                SelectionContainer {
                    FormattedTextDisplay(
                        annotatedString = formattedPrompt,
                        modifier = Modifier
                            .widthIn(max = maxWidth)
                            .clip(RoundedCornerShape(17.dp))
                            .background(backgroundColor)
                            .padding(12.dp),
                        snackbarHostState = snackbarHostState
                    )
                }
            }
            
            // Thêm các nút hành động (chỉ hiển thị khi có chatId)
            if (chatId != null && (prompt.isNotEmpty() || (isFileMessage && imageUrl != null))) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 0.dp, end = 0.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Nút edit (chỉ hiển thị khi tin nhắn có nội dung)
                    if (prompt.isNotEmpty()) {
                        IconButton(
                            onClick = { 
                                onEditClick(chatId)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_editrequest),
                                contentDescription = "Chỉnh sửa",
                                tint = textColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    // Nút copy (chỉ hiển thị khi tin nhắn có nội dung)
                    if (prompt.isNotEmpty()) {
                        IconButton(
                            onClick = { 
                                clipboardManager.setText(formattedPrompt)
                                Toast.makeText(context, "Đã sao chép vào bộ nhớ tạm", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_copy),
                                contentDescription = "Sao chép",
                                tint = textColor.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    
                    // Nút xóa (luôn hiển thị nếu có chatId)
                    IconButton(
                        onClick = { 
                            onDeleteClick(chatId)
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_bin),
                            contentDescription = "Xóa",
                            tint = textColor.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}