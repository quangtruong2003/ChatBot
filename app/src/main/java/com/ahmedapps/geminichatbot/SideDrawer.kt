package com.ahmedapps.geminichatbot

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ahmedapps.geminichatbot.data.ChatSegment
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SideDrawer(
    onClose: () -> Unit,
    chatViewModel: ChatViewModel,
    onLogout: () -> Unit,
    onShowUserDetail: () -> Unit
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
    var segmentToRename by remember { mutableStateOf<ChatSegment?>(null) }
    var showPersonalInfoDialog by remember { mutableStateOf(false) }
    var isSearchVisible by remember { mutableStateOf(false) }

    Surface(
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                CompletedChatList(
                    segments = allSegments,
                    selectedSegment = chatState.selectedSegment,
                    onSegmentSelected = { segment ->
                        chatViewModel.onEvent(ChatUiEvent.SelectSegment(segment))
                        chatViewModel.onEvent(ChatUiEvent.ClearSearch)
                        onClose()
                    },
                    onSegmentRename = { segment ->
                        segmentToRename = segment
                    },
                    onSegmentDelete = { segment ->
                        segmentToDelete = segment
                    },
                    onDeleteAllChats = {
                        chatViewModel.clearChat()
                        onClose()
                    },
                    onToggleSearch = {
                        isSearchVisible = !isSearchVisible
                        if (!isSearchVisible) {
                            chatViewModel.onEvent(ChatUiEvent.ClearSearch)
                        }
                    },
                    isSearchVisible = isSearchVisible,
                    searchQuery = searchQuery,
                    onSearchQueryChanged = { query ->
                        chatViewModel.onEvent(ChatUiEvent.SearchSegments(query))
                    },
                    onClearSearch = {
                        chatViewModel.onEvent(ChatUiEvent.ClearSearch)
                    },
                    onLogout = onLogout,
                    onShowUserDetail = onShowUserDetail,
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
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    )
                }
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Version: ${BuildConfig.VERSION_NAME}",
                        modifier = Modifier.align(Alignment.BottomCenter),
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
        // Dialog xóa
        segmentToDelete?.let { segment ->
            DeleteConfirmationDialog(
                segmentTitle = segment.title,
                onConfirm = {
                    chatViewModel.onEvent(ChatUiEvent.DeleteSegment(segment))
                    segmentToDelete = null
                },
                onDismiss = { segmentToDelete = null }
            )
        }
        // Dialog đổi tên
        segmentToRename?.let { segment ->
            RenameSegmentDialog(
                segment = segment,
                onConfirm = { newTitle ->
                    chatViewModel.onEvent(ChatUiEvent.RenameSegment(segment, newTitle))
                    segmentToRename = null
                },
                onDismiss = { segmentToRename = null }
            )
        }
        // Dialog thông tin cá nhân
        if (showPersonalInfoDialog) {
            PersonalInfoDialog(
                onDismiss = { showPersonalInfoDialog = false }
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun AnimatedSearchBar(
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    isVisible: Boolean
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { -it }) + expandVertically() + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + shrinkVertically() + fadeOut()
    ) {
        SearchBar(
            searchQuery = searchQuery,
            onSearchQueryChanged = onSearchQueryChanged,
            onClearSearch = onClearSearch
        )
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
    val now = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val todayAtMidnight = now.timeInMillis
    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val yesterdayAtMidnight = yesterday.timeInMillis
    val twoDaysAgo = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -2) }
    val twoDaysAgoAtMidnight = twoDaysAgo.timeInMillis
    val sevenDaysAgo = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }
    val sevenDaysAgoAtMidnight = sevenDaysAgo.timeInMillis
    val tomorrow = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }
    val tomorrowAtMidnight = tomorrow.timeInMillis

    return when {
        date in todayAtMidnight until tomorrowAtMidnight -> "Hôm nay"
        date in yesterdayAtMidnight until todayAtMidnight -> "Hôm qua"
        date in twoDaysAgoAtMidnight until yesterdayAtMidnight -> "Hôm kia"
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
    onSegmentDelete: (ChatSegment) -> Unit,
    onSegmentRename: (ChatSegment) -> Unit,
    onDeleteAllChats: () -> Unit,
    onToggleSearch: () -> Unit,
    isSearchVisible: Boolean,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onLogout: () -> Unit,
    onShowUserDetail: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showFirstConfirmationDialog by remember { mutableStateOf(false) }
    var showSecondConfirmationDialog by remember { mutableStateOf(false) }
    
    val currentUser = FirebaseAuth.getInstance().currentUser
    val photoUrl = currentUser?.photoUrl
    val email = currentUser?.email

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = if (!isSearchVisible) 8.dp else 0.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Lịch sử",
                style = MaterialTheme.typography.titleMedium
            )
            Row {
                IconButton(onClick = onToggleSearch) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Tìm kiếm",
                        tint = if (isSearchVisible) MaterialTheme.colorScheme.primary else LocalContentColor.current
                    )
                }
                IconButton(onClick = { showFirstConfirmationDialog = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_bin),
                        contentDescription = "Xóa tất cả lịch sử"
                    )
                }
                IconButton(onClick = onShowUserDetail) {
                    if (photoUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(photoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Ảnh đại diện",
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Thông tin người dùng"
                        )
                    }
                }
            }
        }
        
        AnimatedSearchBar(
            searchQuery = searchQuery,
            onSearchQueryChanged = onSearchQueryChanged,
            onClearSearch = onClearSearch,
            isVisible = isSearchVisible
        )
        
        if (isSearchVisible) {
            Spacer(modifier = Modifier.height(8.dp))
        }

        val groupedByLabel = segments.groupBy { segment ->
            val calendar = Calendar.getInstance().apply { timeInMillis = segment.createdAt }
            getGroupLabel(segment.createdAt)
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            groupedByLabel.forEach { (label, segsInLabel) ->
                item {
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
                        onRename = { onSegmentRename(segment) },
                        onDelete = { onSegmentDelete(segment) }
                    )
                }
            }
        }

        if (showFirstConfirmationDialog) {
            Dialog(onDismissRequest = { showFirstConfirmationDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Xác nhận xóa",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "Bạn có chắc chắn muốn xóa toàn bộ lịch sử đoạn chat?",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showFirstConfirmationDialog = false }) {
                                Text("Hủy")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = {
                                showFirstConfirmationDialog = false
                                showSecondConfirmationDialog = true
                            }) {
                                Text("Xóa")
                            }
                        }
                    }
                }
            }
        }
        if (showSecondConfirmationDialog) {
            Dialog(onDismissRequest = { showSecondConfirmationDialog = false }) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Xác nhận",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Text(
                            text = "Hành động này không thể hoàn tác. Bạn có thực sự muốn xóa tất cả lịch sử?",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showSecondConfirmationDialog = false }) {
                                Text("Hủy")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(onClick = {
                                showSecondConfirmationDialog = false
                                onDeleteAllChats()
                            }) {
                                Text("Xóa")
                            }
                        }
                    }
                }
            }
        }
    }
}

fun Modifier.crop(
    horizontal: Dp = 0.dp,
    vertical: Dp = 0.dp,
): Modifier = this.layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    fun Dp.toPxInt(): Int = this.toPx().toInt()
    layout(
        placeable.width - (horizontal * 2).toPxInt(),
        placeable.height - (vertical * 2).toPxInt()
    ) {
        placeable.placeRelative(-horizontal.toPx().toInt(), -vertical.toPx().toInt())
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatSegmentItem(
    segment: ChatSegment,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                else Color.Transparent
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = { expanded = true }
            )
            .padding(vertical = 8.dp, horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
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
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .wrapContentSize(Alignment.Center)
                .crop(vertical = 8.dp)
                .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(15.dp))
                .fillMaxWidth(0.45f),
            offset = DpOffset(x = 40.dp, y = 8.dp)
        ) {
            DropdownMenuItem(
                text = { Text("Đổi tên đoạn chat") },
                onClick = {
                    expanded = false
                    onRename()
                }
            )
            Divider(color = Color(0x14FFFFFF), thickness = 0.6.dp)
            DropdownMenuItem(
                text = { Text("Xóa đoạn chat") },
                onClick = {
                    expanded = false
                    onDelete()
                }
            )
        }
    }
}

@Composable
fun RenameSegmentDialog(
    segment: ChatSegment,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newTitle by remember { mutableStateOf(segment.title) }
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Đổi tên đoạn chat",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    label = { Text("Tiêu đề mới") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Hủy")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { onConfirm(newTitle) }) {
                        Text("Lưu")
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteConfirmationDialog(
    segmentTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Xác nhận xóa",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Bạn có chắc chắn muốn xóa đoạn chat?",//'${segmentTitle.replace("\n", "")}'?",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Hủy")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onConfirm) {
                        Text("Xóa")
                    }
                }
            }
        }
    }
}

@Composable
fun PersonalInfoDialog(
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Thông Tin",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "ChatAI là một ứng dụng mạnh mẽ cho phép tích hợp dữ liệu thời gian thực và thực hiện các tác vụ phức tạp thông qua giao diện lập trình dễ sử dụng. Ứng dụng hỗ trợ kết nối nhanh với các hệ thống nội bộ hoặc dịch vụ bên ngoài, đảm bảo bảo mật cao và hiệu suất ổn định. Với ChatAI Pro, người dùng có thể tối ưu hóa quy trình tự động hóa và nâng cao khả năng phân tích dữ liệu.",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Đóng")
                    }
                }
            }
        }
    }
}
