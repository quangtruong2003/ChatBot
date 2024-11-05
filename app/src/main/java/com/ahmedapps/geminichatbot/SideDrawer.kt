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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ahmedapps.geminichatbot.data.ChatSegment
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@Composable
fun SideDrawer(onClose: () -> Unit, chatViewModel: ChatViewModel) {
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
                }
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
    onSegmentLongPress: (ChatSegment) -> Unit // Long press callback for deletion
) {
    Text(
        text = "Lịch sử",
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    LazyColumn(
        modifier = Modifier.fillMaxHeight()
    ) {
        items(segments) { segment ->
            ChatSegmentItem(
                segment = segment,
                isSelected = selectedSegment?.id == segment.id,
                onClick = { onSegmentSelected(segment) },
                onLongPress = { onSegmentLongPress(segment) } // Handle long press
            )
        }
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
        text = { Text(text = "Bạn có chắc chắn muốn xóa đoạn chat \"$segmentTitle\"?") },
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
