// SideDrawer.kt
package com.ahmedapps.geminichatbot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmedapps.geminichatbot.data.ChatSegment
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SideDrawer(onClose: () -> Unit, chatViewModel: ChatViewModel) {
    val chatState = chatViewModel.chatState.collectAsState().value
    val searchQuery = chatState.searchQuery
    val completedSegments = chatState.chatSegments.filter { it.title != "New Chat" }

    Surface(
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(16.dp)
        ) {
            // Nút đóng
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Đóng"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Ô tìm kiếm
            SearchBar(searchQuery) {
                chatViewModel.onEvent(ChatUiEvent.SearchSegments(it))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Danh sách các đoạn chat đã hoàn thành
            CompletedChatList(
                completedSegments,
                chatState.selectedSegment,
                onSegmentSelected = { segment ->
                    chatViewModel.onEvent(ChatUiEvent.SelectSegment(segment))
                    onClose()
                }
            )
        }
    }
}

@Composable
fun SearchBar(searchQuery: String, onSearchQueryChanged: (String) -> Unit) {
    OutlinedTextField(
        value = searchQuery,
        onValueChange = onSearchQueryChanged,
        label = { Text("Tìm kiếm") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
fun CompletedChatList(
    segments: List<ChatSegment>,
    selectedSegment: ChatSegment?,
    onSegmentSelected: (ChatSegment) -> Unit
) {
    Text(
        text = "Đoạn Chat Đã Hoàn Thành",
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
                onClick = { onSegmentSelected(segment) }
            )
        }
    }
}

@Composable
fun ChatSegmentItem(
    segment: ChatSegment,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 16.dp)
    ) {
        Column {
            Text(
                text = segment.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp)) // Thêm khoảng cách giữa tiêu đề và ngày
            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            val date = Date(segment.createdAt)
            Text(
                text = sdf.format(date),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
