// SideDrawer.kt
package com.ahmedapps.geminichatbot

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmedapps.geminichatbot.data.ChatSegment
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@Composable
fun SideDrawer(
    onClose: () -> Unit,
    chatViewModel: ChatViewModel,
    onLogout: () -> Unit // Thêm tham số này
) {
    val chatState by chatViewModel.chatState.collectAsState()
    val searchQuery = chatState.searchQuery
    val completedSegments = chatState.chatSegments.filter { it.title != "New Chat" }

    // State to manage the segment selected for deletion
    var segmentToDelete by remember { mutableStateOf<ChatSegment?>(null) }

    Surface(
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(16.dp)
        ) {
            // Search bar for searching chat segments
            SearchBar(
                searchQuery = searchQuery,
                onSearchQueryChanged = { query ->
                    chatViewModel.onEvent(ChatUiEvent.SearchSegments(query))
                },
                onClearSearch = {
                    chatViewModel.onEvent(ChatUiEvent.ClearSearch)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // List of completed chat segments
            CompletedChatList(
                segments = completedSegments,
                selectedSegment = chatState.selectedSegment,
                onSegmentSelected = { segment ->
                    chatViewModel.onEvent(ChatUiEvent.SelectSegment(segment))
                    chatViewModel.onEvent(ChatUiEvent.ClearSearch)
                    onClose()
                },
                onSegmentLongPress = { segment ->
                    segmentToDelete = segment // Set the segment to delete
                },
                onDeleteAllChats = {
                    chatViewModel.clearChat()
                    onClose()
                },
                onLogout = onLogout // Truyền callback đăng xuất
            )

            // Show confirmation dialog if a segment is selected for deletion
            segmentToDelete?.let { segment ->
                DeleteConfirmationDialog(
                    segmentTitle = segment.title,
                    onConfirm = {
                        chatViewModel.onEvent(ChatUiEvent.DeleteSegment(segment))
                        segmentToDelete = null
                    },
                    onDismiss = {
                        segmentToDelete = null
                    }
                )
            }
        }
    }
}

@Composable
fun SearchBar(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit
) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChanged,
        label = { Text("Tìm kiếm") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        trailingIcon = {
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = onClearSearch) {
                    Icon(
                        imageVector = Icons.Filled.Clear,
                        contentDescription = "Xóa"
                    )
                }
            }
        }
    )
}

@Composable
fun CompletedChatList(
    segments: List<ChatSegment>,
    selectedSegment: ChatSegment?,
    onSegmentSelected: (ChatSegment) -> Unit,
    onSegmentLongPress: (ChatSegment) -> Unit, // Long press callback for deletion
    onDeleteAllChats: () -> Unit, // Callback for deleting all chats
    onLogout: () -> Unit // Thêm tham số này
) {
    // Biến trạng thái để quản lý hiển thị hộp thoại xác nhận
    var showFirstConfirmationDialog by remember { mutableStateOf(false) }
    var showSecondConfirmationDialog by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Lịch sử",
            style = MaterialTheme.typography.titleMedium
        )
        Row { // Thêm Row chứa các IconButton
            IconButton(onClick = { showFirstConfirmationDialog = true }) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Xóa tất cả lịch sử"
                )
            }
            IconButton(onClick = onLogout) { // Nút Đăng xuất
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "Đăng xuất"
                )
            }
        }
    }

    // Danh sách các đoạn chat hoàn thành
    LazyColumn(
        modifier = Modifier.fillMaxHeight()
    ) {
        items(segments) { segment ->
            ChatSegmentItem(
                segment = segment,
                isSelected = selectedSegment?.id == segment.id,
                onClick = { onSegmentSelected(segment) },
                onLongPress = { onSegmentLongPress(segment) } // Xử lý long press
            )
        }
    }

    // Hộp thoại xác nhận đầu tiên
    if (showFirstConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showFirstConfirmationDialog = false },
            title = { Text(text = "Xác nhận xóa") },
            text = { Text("Bạn có chắc chắn muốn xóa toàn bộ lịch sử đoạn chat?") },
            confirmButton = {
                TextButton(onClick = {
                    showFirstConfirmationDialog = false
                    showSecondConfirmationDialog = true
                }) {
                    Text("Xóa")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFirstConfirmationDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }

    // Hộp thoại xác nhận thứ hai
    if (showSecondConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showSecondConfirmationDialog = false },
            title = { Text(text = "Xác nhận") },
            text = { Text("Hành động này không thể hoàn tác. Bạn có thực sự muốn xóa tất cả lịch sử?") },
            confirmButton = {
                TextButton(onClick = {
                    showSecondConfirmationDialog = false
                    onDeleteAllChats()
                }) {
                    Text("Xóa")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSecondConfirmationDialog = false }) {
                    Text("Hủy")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatSegmentItem(
    segment: ChatSegment,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit // Long press handler
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongPress
            )
            .padding(vertical = 8.dp, horizontal = 16.dp)
    ) {
        Column {
            Text(
                text = segment.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val date = Date(segment.createdAt)
            Text(
                text = sdf.format(date),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    segmentTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Xác nhận xóa") },
        text = {
            val segmentTitleWithoutLineBreaks = segmentTitle.replace("\n", "") // Remove line breaks
            Text(text = "Bạn có chắc chắn muốn xóa đoạn chat \'$segmentTitleWithoutLineBreaks\'?")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Xóa")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Hủy")
            }
        }
    )
}
