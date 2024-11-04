// SideDrawer.kt
package com.ahmedapps.geminichatbot

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ahmedapps.geminichatbot.data.ChatSegment
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SideDrawer(onClose: () -> Unit, chatViewModel: ChatViewModel) {
    // Implement SideDrawer UI here
    // Bao gồm: ô tìm kiếm và danh sách các đoạn chat đã hoàn thành
    val chatState = chatViewModel.chatState.collectAsState().value
    val searchQuery = chatState.searchQuery
    val completedSegments = chatState.completedSegments
    Surface(
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(

            modifier = Modifier
                .fillMaxWidth(0.85f) // Chiếm 3/4 màn hình
                .padding(16.dp)

        ) {
            // Nút đóng
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Ô tìm kiếm
            OutlinedTextField(
                value = searchQuery,
                onValueChange = {
                    chatViewModel.onEvent(ChatUiEvent.SearchSegments(it))
                },
                label = { Text("Tìm kiếm") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Danh sách các đoạn chat đã hoàn thành
            Text(
                text = "Đoạn Chat Đã Hoàn Thành",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier.fillMaxHeight()
            ) {
                items(completedSegments) { segment ->
                    ListItem(
                        headlineContent = { Text(segment.title) },
                        supportingContent = {
                            val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                            val date = Date(segment.createdAt)
                            Text("Tạo vào: ${sdf.format(date)}")
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                chatViewModel.onEvent(ChatUiEvent.SelectSegment(segment))
                                onClose()
                            }
                            .background(
                                if (chatState.selectedSegment?.id == segment.id)
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                else
                                    Color.Transparent
                            )
                            .padding(vertical = 4.dp)
                    )
                }
            }
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
        Text(
            text = segment.title,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
