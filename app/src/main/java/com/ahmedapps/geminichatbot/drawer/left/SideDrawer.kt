package com.ahmedapps.geminichatbot.drawer.left

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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.ahmedapps.geminichatbot.BuildConfig
import com.ahmedapps.geminichatbot.data.ChatUiEvent
import com.ahmedapps.geminichatbot.data.ChatViewModel

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
    var expandedSegmentId by remember { mutableStateOf<String?>(null) }
    val searchFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Ẩn bàn phím khi drawer đóng
    DisposableEffect(Unit) {
        onDispose {
            keyboardController?.hide()
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        ) {
            Column(
                modifier = Modifier
                .fillMaxSize()
                    .padding(16.dp)
            ) {
                CompletedChatList(
                    segments = allSegments,
                    selectedSegment = chatState.selectedSegment,
                expandedSegmentId = expandedSegmentId,
                onSegmentExpand = { segmentId -> expandedSegmentId = segmentId },
                onSegmentDismiss = { expandedSegmentId = null },
                onSegmentSelected = { segment ->
                    chatViewModel.onEvent(ChatUiEvent.SelectSegment(segment))
                    chatViewModel.onEvent(ChatUiEvent.ClearSearch)
                    expandedSegmentId = null
                    keyboardController?.hide()
                    onClose()
                },
                onSegmentRename = { segment ->
                    segmentToRename = segment
                },
                onSegmentDelete = { segment ->
                    segmentToDelete = segment
                    expandedSegmentId = null
                },
                onToggleSearch = {
                    isSearchVisible = !isSearchVisible
                    if (isSearchVisible) {
                        // Tập trung vào TextField và hiển thị bàn phím
                        // LaunchedEffect để đảm bảo hệ thống có đủ thời gian render TextField trước khi focus
                        chatViewModel.onEvent(ChatUiEvent.ClearSearch)
                    } else {
                        chatViewModel.onEvent(ChatUiEvent.ClearSearch)
                        keyboardController?.hide()
                    }
                    expandedSegmentId = null
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
                searchFocusRequester = searchFocusRequester,
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
                    text = "CHATAI BY JOHNESS",
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
    isVisible: Boolean,
    focusRequester: FocusRequester
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { -it }) + expandVertically() + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + shrinkVertically() + fadeOut()
    ) {
        SearchBar(
            searchQuery = searchQuery,
            onSearchQueryChanged = onSearchQueryChanged,
            onClearSearch = onClearSearch,
            focusRequester = focusRequester
        )
        
        // Khi SearchBar xuất hiện, tự động focus và hiển thị bàn phím
        LaunchedEffect(isVisible) {
            if (isVisible) {
                kotlinx.coroutines.delay(100) // Đợi một chút để UI render
                focusRequester.requestFocus()
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
    onClearSearch: () -> Unit,
    focusRequester: FocusRequester
) {
    TextField(
        value = searchQuery,
        onValueChange = onSearchQueryChanged,
        placeholder = { Text("Tìm kiếm đoạn chat") },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(48.dp)
            .focusRequester(focusRequester),
        singleLine = true,
        shape = RoundedCornerShape(50),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Tìm kiếm"
            )
        },
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

/**
 * Hàm định dạng thời gian tương đối cho timestamp
 */
private fun formatRelativeTime(createdAt: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - createdAt
    val seconds = diff / 1000
    val minutes = seconds / 60
    val hours = minutes / 60

    return when {
        seconds < 60 -> "Vừa xong"
        minutes < 60 -> "$minutes phút trước"
        hours < 24 -> "$hours giờ trước"
        else -> {
            val sdf = SimpleDateFormat("HH:mm dd/MM/yyyy", Locale.getDefault())
            sdf.format(Date(createdAt))
        }
    }
}

@Composable
fun CompletedChatList(
    segments: List<ChatSegment>,
    selectedSegment: ChatSegment?,
    expandedSegmentId: String?,
    onSegmentExpand: (String) -> Unit,
    onSegmentDismiss: () -> Unit,
    onSegmentSelected: (ChatSegment) -> Unit,
    onSegmentDelete: (ChatSegment) -> Unit,
    onSegmentRename: (ChatSegment) -> Unit,
    onToggleSearch: () -> Unit,
    isSearchVisible: Boolean,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onClearSearch: () -> Unit,
    onLogout: () -> Unit,
    onShowUserDetail: () -> Unit,
    searchFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    val currentUser = FirebaseAuth.getInstance().currentUser
    val photoUrl = currentUser?.photoUrl
    val email = currentUser?.email

    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 0.dp),
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
        
        if (isSearchVisible) {
            Spacer(modifier = Modifier.height(8.dp))
        }

        AnimatedSearchBar(
            searchQuery = searchQuery,
            onSearchQueryChanged = onSearchQueryChanged,
            onClearSearch = onClearSearch,
            isVisible = isSearchVisible,
            focusRequester = searchFocusRequester
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
                items(segsInLabel, key = { it.id }) { segment ->
                    ChatSegmentItem(
                        segment = segment,
                        isSelected = selectedSegment?.id == segment.id,
                        isExpanded = expandedSegmentId == segment.id,
                        expandedSegmentId = expandedSegmentId,
                        onExpand = { onSegmentExpand(segment.id) },
                        onDismiss = onSegmentDismiss,
                        onClick = { onSegmentSelected(segment) },
                        onRename = { onSegmentRename(segment) },
                        onDelete = { onSegmentDelete(segment) }
                    )
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
    isExpanded: Boolean,
    expandedSegmentId: String?,
    onExpand: () -> Unit,
    onDismiss: () -> Unit,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    val hapticFeedback = LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isExpanded) 1.05f else 1f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
    )
    val elevation by animateFloatAsState(
        targetValue = if (isExpanded) 8f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
    )
    
    val alpha = if (expandedSegmentId != null && !isExpanded) {
        0.2f
    } else {
        1f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .alpha(alpha)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .scale(scale)
                .shadow(
                    elevation = elevation.dp,
                    shape = RoundedCornerShape(10.dp)
                )
                .clip(RoundedCornerShape(10.dp))
                .background(
                    when {
                        isExpanded -> MaterialTheme.colorScheme.surfaceVariant
                        isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        else -> Color.Transparent
                    }
                )
                .combinedClickable(
                    onClick = {
                        if (isExpanded) {
                            onDismiss()
                        }
                        onClick()
                    },
                    onLongClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        onExpand()
                    }
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
                Text(
                    text = formatRelativeTime(segment.createdAt),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.alpha(0.5f)
                )
            }
        }
        
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = onDismiss,
            modifier = Modifier
                .wrapContentSize(Alignment.Center)
                .crop(vertical = 8.dp)
                .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(15.dp))
                .fillMaxWidth(0.45f),
            offset = DpOffset(x = 40.dp, y = (-8).dp)
        ) {
            DropdownMenuItem(
                text = { Text("Đổi tên đoạn chat") },
                onClick = {
                    onDismiss()
                    onRename()
                }
            )
            Divider(color = Color(0x14FFFFFF), thickness = 0.6.dp)
            DropdownMenuItem(
                text = { Text("Xóa đoạn chat") },
                onClick = {
                    onDismiss()
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
