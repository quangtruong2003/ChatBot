// SideDrawer.kt
package com.ahmedapps.geminichatbot

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ahmedapps.geminichatbot.data.ChatSegment
import java.text.SimpleDateFormat
import java.util.*
import kotlinx.coroutines.launch

@Composable
fun SideDrawer(
    onClose: () -> Unit,
    chatViewModel: ChatViewModel,
    onLogout: () -> Unit
) {
    val gradientBrush = Brush.linearGradient(
        colors = listOf(
            Color(0xFF1BA1E3),
            Color(0xFF5489D6),
            Color(0xFF9B72CB),
            Color(0xFFD96570),
            Color(0xFFF49C46)
        )
    )
    val chatState by chatViewModel.chatState.collectAsState()
    val searchQuery = chatState.searchQuery
    val allSegments by chatViewModel.getSortedChatSegments().collectAsState()
    var segmentToDelete by remember { mutableStateOf<ChatSegment?>(null) }
    var showPersonalInfoDialog by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .padding(16.dp)
        ) {
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

            CompletedChatList(
                segments = allSegments,
                selectedSegment = chatState.selectedSegment,
                onSegmentSelected = { segment ->
                    chatViewModel.onEvent(ChatUiEvent.SelectSegment(segment))
                    chatViewModel.onEvent(ChatUiEvent.ClearSearch)
                    onClose()
                },
                onSegmentLongPress = { segment ->
                    segmentToDelete = segment
                },
                onDeleteAllChats = {
                    chatViewModel.clearChat()
                    onClose()
                },
                onLogout = onLogout,
                modifier = Modifier.weight(1f)
            )

            GradientButton(
                onClick = { showPersonalInfoDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                gradient = gradientBrush
            ) {
                Text(
                    text = "Nguyễn Quang Trường - D21_TH12",
                    color = Color.White,
                    style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold)
                )
            }

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

            if (showPersonalInfoDialog) {
                PersonalInfoDialog(
                    onDismiss = { showPersonalInfoDialog = false }
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Version: ${BuildConfig.VERSION_NAME}",
                    modifier = Modifier
                        .align(Alignment.BottomCenter),
                    style = TextStyle(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF1BA1E3),
                                Color(0xFF5489D6),
                                Color(0xFF9B72CB),
                                Color(0xFFD96570),
                                Color(0xFFF49C46)
                            )
                        )
                    )
                )
            }
        }
    }
}

@Composable
fun GradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    gradient: Brush,
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(gradient)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            content = content
        )
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
        label = { Text("Tìm kiếm đoạn chat") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
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

/**
 * Hàm lấy nhãn nhóm ngày cho một timestamp
 */
fun getGroupLabel(date: Long): String {
    val now = Calendar.getInstance()
    now.set(Calendar.HOUR_OF_DAY, 0)
    now.set(Calendar.MINUTE, 0)
    now.set(Calendar.SECOND, 0)
    now.set(Calendar.MILLISECOND, 0)

    val todayAtMidnight = now.timeInMillis

    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val yesterdayAtMidnight = yesterday.timeInMillis

    val twoDaysAgo = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -2) }
    val twoDaysAgoAtMidnight = twoDaysAgo.timeInMillis

    val sevenDaysAgo = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }
    val sevenDaysAgoAtMidnight = sevenDaysAgo.timeInMillis

    // tomorrowAtMidnight để so sánh cho hôm nay
    val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    val tomorrowAtMidnight = tomorrow.timeInMillis

    return when {
        // date trong hôm nay: [todayAtMidnight, tomorrowAtMidnight)
        date in todayAtMidnight until tomorrowAtMidnight -> "Hôm nay"

        // date trong hôm qua: [yesterdayAtMidnight, todayAtMidnight)
        date in yesterdayAtMidnight until todayAtMidnight -> "Hôm qua"

        // date trong hôm kia: [twoDaysAgoAtMidnight, yesterdayAtMidnight)
        date in twoDaysAgoAtMidnight until yesterdayAtMidnight -> "Hôm kia"

        // date trong khoảng 3 -> 7 ngày trước: [sevenDaysAgoAtMidnight, twoDaysAgoAtMidnight)
        date in sevenDaysAgoAtMidnight until twoDaysAgoAtMidnight -> "7 ngày trước"

        else -> {
            val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
            sdf.format(Date(date))
        }
    }
}


@Composable
fun CompletedChatList(
    segments: List<ChatSegment>,
    selectedSegment: ChatSegment?,
    onSegmentSelected: (ChatSegment) -> Unit,
    onSegmentLongPress: (ChatSegment) -> Unit,
    onDeleteAllChats: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showFirstConfirmationDialog by remember { mutableStateOf(false) }
    var showSecondConfirmationDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
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
            Row {
                IconButton(onClick = { showFirstConfirmationDialog = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_bin),
                        contentDescription = "Xóa tất cả lịch sử"
                    )
                }
                IconButton(onClick = onLogout) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = "Đăng xuất"
                    )
                }
            }
        }

        // Thay vì groupBy ngày chính xác, ta groupBy nhãn từ hàm getGroupLabel
        val groupedByLabel = segments.groupBy { segment ->
            // Chuyển timestamp thành label
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = segment.createdAt
            getGroupLabel(segment.createdAt)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            groupedByLabel.forEach { (label, segsInLabel) ->
                item {
                    // Chỉ in tiêu đề nếu label không rỗng
                    Text(
                        text = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End
                    )
                }
                items(segsInLabel) { segment ->
                    ChatSegmentItem(
                        segment = segment,
                        isSelected = selectedSegment?.id == segment.id,
                        onClick = { onSegmentSelected(segment) },
                        onLongPress = { onSegmentLongPress(segment) }
                    )
                }
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatSegmentItem(
    segment: ChatSegment,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
    } else {
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Transparent)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(backgroundColor)
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
            val sdf = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault())
            val date = Date(segment.createdAt)
            Text(
                text = sdf.format(date),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.alpha(0.5f)
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
            val segmentTitleWithoutLineBreaks = segmentTitle.replace("\n", "")
            Text(text = "Bạn có chắc chắn muốn xóa đoạn chat '$segmentTitleWithoutLineBreaks'?")
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

@Composable
fun PersonalInfoDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1BA1E3),
                            Color(0xFF5489D6),
                            Color(0xFF9B72CB),
                            Color(0xFFD96570),
                            Color(0xFFF49C46)
                        )
                    )
                )
                .padding(24.dp)
        ) {
            Column(
                modifier = Modifier.wrapContentHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Thông Tin",
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Thông Tin",
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "ChatAI là một ứng dụng mạnh mẽ cho phép tích hợp dữ liệu thời gian thực và thực hiện các tác vụ phức tạp thông qua giao diện lập trình dễ sử dụng. Ứng dụng hỗ trợ kết nối nhanh chóng với các hệ thống nội bộ hoặc dịch vụ bên ngoài, đảm bảo bảo mật cao và hiệu suất ổn định. Với ChatAI Pro, người dùng có thể tối ưu hóa quy trình tự động hóa và nâng cao khả năng phân tích dữ liệu.",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.White
                        ),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(
                        text = "Đóng",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}
