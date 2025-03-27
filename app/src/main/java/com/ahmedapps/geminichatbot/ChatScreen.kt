// ChatScreen.kt
package com.ahmedapps.geminichatbot.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ahmedapps.geminichatbot.ChatUiEvent
import com.ahmedapps.geminichatbot.ChatViewModel
import com.ahmedapps.geminichatbot.R
import com.ahmedapps.geminichatbot.SideDrawer
import com.ahmedapps.geminichatbot.ui.components.ModelChatItem
import com.ahmedapps.geminichatbot.ui.components.ThinkingAnimation
import com.ahmedapps.geminichatbot.ui.components.ModelWaitingIndicator
import com.ahmedapps.geminichatbot.ui.components.UserChatItem
import com.ahmedapps.geminichatbot.ui.components.WelcomeMessage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import fomatText.parseFormattedText
import fomatText.TypingConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.window.PopupProperties

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalPermissionsApi::class
)
@Composable
fun ChatScreen(
    navController: NavController,
    chatViewModel: ChatViewModel = hiltViewModel(),
    onShowUserDetail: () -> Unit
) {
    val chatState by chatViewModel.chatState.collectAsState()
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showWelcomeMessage by remember { mutableStateOf(true) }

    // State để quản lý việc hiển thị SideDrawer
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Khởi tạo LazyListState để quản lý cuộn danh sách
    val listState = rememberLazyListState()
    val textFieldHeight = remember { mutableStateOf(0.dp) }
    // Khởi tạo SnackbarHostState
    val snackbarHostState = remember { SnackbarHostState() }

    var showPopup by remember { mutableStateOf(false) }

    // Lấy đối tượng HapticFeedback
    val hapticFeedback = LocalHapticFeedback.current

    val isDarkTheme = isSystemInDarkTheme()
    val backgroundColor = when {
        chatState.chatList.any { it.isError } -> MaterialTheme.colorScheme.error
        isDarkTheme -> Color(0x43FFFFFF)
        else -> Color(0x97FFFFFF)
    }

    val textColor = if (isDarkTheme) Color.White else Color.Black

    // Biến trạng thái để quản lý việc hiển thị DropdownMenu
    var showSourceMenu by remember { mutableStateOf(false) }

    // Yêu cầu quyền CAMERA
    val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)
    LaunchedEffect(cameraPermissionState.status) {
        if (!cameraPermissionState.status.isGranted && cameraPermissionState.status.shouldShowRationale) {
            snackbarHostState.showSnackbar("Ứng dụng cần quyền truy cập máy ảnh để chụp ảnh.")
        }
    }
    val isDrawerOpen = drawerState.currentValue == DrawerValue.Open
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.status.isGranted) {
            cameraPermissionState.launchPermissionRequest()
        }
    }

    val focusManager = LocalFocusManager.current
    // Ẩn bàn phím khi Drawer mở
    LaunchedEffect(drawerState.currentValue) {
        if (drawerState.currentValue == DrawerValue.Open) {
            focusManager.clearFocus()
        }
    }
    val isUserScrolling = remember { mutableStateOf(false) }
    var userScrolled by remember { mutableStateOf(false) }
    var previousChatListSize by remember { mutableStateOf(chatState.chatList.size) }

    // State to track if the user has intentionally scrolled away from the bottom
    var userScrolledAwayFromBottom by remember { mutableStateOf(false) }

    // Effect to detect user scrolling up
    LaunchedEffect(listState) {
        var previousFirstVisibleItemIndex = listState.firstVisibleItemIndex
        var previousFirstVisibleItemScrollOffset = listState.firstVisibleItemScrollOffset

        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (currentIndex, currentOffset) ->
                val isScrollingUp = currentIndex < previousFirstVisibleItemIndex ||
                        (currentIndex == previousFirstVisibleItemIndex && currentOffset < previousFirstVisibleItemScrollOffset)

                if (isScrollingUp) {
                    // Check if we are not already at the very top (index 0, offset 0)
                    // And if the list has content
                    if ((currentIndex > 0 || currentOffset > 0) && listState.layoutInfo.totalItemsCount > 0) {
                        // Only set to true if user scrolls up significantly enough to potentially hide the last item
                        val layoutInfo = listState.layoutInfo
                        val totalItemsCount = layoutInfo.totalItemsCount
                        val lastVisibleItem = layoutInfo.visibleItemsInfo.lastOrNull()
                        // Consider scrolled up if the last item is no longer the last visible one
                        if (lastVisibleItem != null && lastVisibleItem.index < totalItemsCount - 1) {
                            userScrolledAwayFromBottom = true
                        } else if (lastVisibleItem == null && totalItemsCount > 0) {
                            // Handle case where list scrolls very fast and last item info might be briefly unavailable
                            userScrolledAwayFromBottom = true
                        }
                    }
                }

                // Update previous values
                previousFirstVisibleItemIndex = currentIndex
                previousFirstVisibleItemScrollOffset = currentOffset
            }
    }

    // Derived state for button visibility based on user scroll action
    val showScrollToBottomButton by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            val totalItemsCount = layoutInfo.totalItemsCount

            if (visibleItemsInfo.isEmpty() || totalItemsCount == 0) {
                false // No button if list is empty
            } else {
                val lastVisibleItem = visibleItemsInfo.last()
                // Check if the very last item in the list is visible
                val isLastItemVisible = lastVisibleItem.index == totalItemsCount - 1

                // If last item becomes fully visible, reset the scrolled away flag
                if (isLastItemVisible) {
                    // Check if the last item is fully visible (offset is 0 or near 0)
                    // This prevents hiding the button if only a pixel of the last item is visible
                    val lastItemBottomOffset = layoutInfo.viewportSize.height - lastVisibleItem.offset - lastVisibleItem.size
                    if (lastItemBottomOffset <= 10) { // Use a slightly larger threshold
                        userScrolledAwayFromBottom = false
                    }
                }

                // Show button if user has scrolled away
                userScrolledAwayFromBottom
            }
        }
    }

    val canSend = chatState.prompt.isNotEmpty() || chatState.imageUri != null

    var shouldAutoScroll by remember { mutableStateOf(true) }
    // Thêm biến để phát hiện khi danh sách đang hiển thị do LazyColumn được khởi tạo lại
    var isInitialLoad by remember { mutableStateOf(true) }

    // Xử lý cuộn trong LazyColumn
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { isScrolling ->
                if (isScrolling) {
                    shouldAutoScroll = false
                    userScrolled = true
                }
            }
    }

    // Đánh dấu tất cả tin nhắn là đã typed khi chọn đoạn chat mới hoặc màn hình được tạo lần đầu
    LaunchedEffect(chatState.selectedSegment) {
        // Delay nhỏ để đảm bảo state đã được cập nhật sau khi chọn segment
        kotlinx.coroutines.delay(100)
        chatViewModel.markAllCurrentMessagesAsTyped()
        // Reset trạng thái cuộn khi chuyển chat
        userScrolled = false
        shouldAutoScroll = true
        userScrolledAwayFromBottom = false // Reset scroll flag when changing chat
        // Cuộn xuống cuối khi chuyển chat
        if (chatState.chatList.isNotEmpty()) {
            // Use a slightly longer delay to allow layout calculations to potentially settle
            kotlinx.coroutines.delay(100) // Increased delay
            // Scroll to the absolute bottom using Int.MAX_VALUE
            listState.animateScrollToItem(Int.MAX_VALUE)
        }
    }

    // Khi có tin nhắn mới, xử lý cuộn tự động
    LaunchedEffect(chatState.chatList.size) {
        val newMessageAdded = chatState.chatList.size > previousChatListSize
        if (newMessageAdded) {
            // Reset trạng thái cuộn khi có tin nhắn mới
            userScrolled = false
            shouldAutoScroll = true
            previousChatListSize = chatState.chatList.size

            // Chỉ cuộn tự động nếu người dùng không đang cuộn thủ công
            if (shouldAutoScroll) {
                // Reset the scrolled away flag *before* starting the scroll animation
                userScrolledAwayFromBottom = false
                // Delay nhỏ để chờ UI cập nhật trước khi cuộn
                kotlinx.coroutines.delay(50) // Keep this delay for responsiveness
                // Ensure we scroll to the actual last index/bottom
                if (chatState.chatList.isNotEmpty()) {
                    // Scroll to the absolute bottom using Int.MAX_VALUE
                    listState.animateScrollToItem(Int.MAX_VALUE)
                }
            }
        } else if (chatState.chatList.size < previousChatListSize) {
            // Xử lý trường hợp danh sách bị xóa (ví dụ: refresh)
            previousChatListSize = chatState.chatList.size
            userScrolled = false
            shouldAutoScroll = true
            userScrolledAwayFromBottom = false // Also reset if list is cleared/refreshed
        }
    }

    // Image Picker đăng ký bên trong composable để tránh vấn đề ViewModel chưa được khởi tạo
    val context = LocalContext.current
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            uri?.let {
                chatViewModel.onEvent(ChatUiEvent.OnImageSelected(it))
            }
        }
    )

    // Hàm tạo URI tạm thời cho ảnh
    fun createImageUri(): Uri? {
        val imageFile = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "IMG_${System.currentTimeMillis()}.jpg"
        )
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            imageFile
        )
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
    // Tạo một biến để lưu Uri tạm thời cho ảnh chụp
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    // Đăng ký launcher cho máy ảnh
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success && photoUri != null) {
                chatViewModel.onEvent(ChatUiEvent.OnImageSelected(photoUri!!))
            }
        }
    )
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberScrollState()

    var isClicked by remember { mutableStateOf(false) }
    var showModelSelection by remember { mutableStateOf(false) }
    val selectedModel by chatViewModel.selectedModel.collectAsState()
    val robotoFontFamily = FontFamily.Default


    val localView = LocalView.current
    val windowInsetsController = remember(localView) {
        ViewCompat.getWindowInsetsController(localView)
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            // open file don't close slide
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight()
            ) {
                SideDrawer(
                    onClose = {
                        scope.launch {
                            drawerState.close()
                        }
                    },
                    chatViewModel = chatViewModel,
                    onLogout = { showLogoutDialog = true },
                    onShowUserDetail = {
                        // Không đóng drawer khi mở UserDetailBottomSheet
                        onShowUserDetail()
                    }
                )
            }
        },
        scrimColor = Color.Transparent
    ) {

        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "ChatAI",
                                style = TextStyle(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 30.sp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color(0xFF1BA1E3),
                                            Color(0xFF5489D6),
                                            Color(0xFF9B72CB),
                                            Color(0xFFD96570),
                                            Color(0xFFF49C46)
                                        )
                                    ),
                                    fontFamily = robotoFontFamily
                                )
                            )
                            // Spacer between actions if needed
                            Spacer(modifier = Modifier.width(8.dp))

                            // Combined Icon Button for Model Selection
                            Box(
                                modifier = Modifier
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null, // Loại bỏ hiệu ứng hover
                                        onClick = {
                                            isClicked = !isClicked // Chuyển đổi trạng thái đã nhấp
                                            showModelSelection = true
                                            // Thêm phản hồi rung khi mở dropdown model
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    )
                                    .alpha(if (isClicked) 0.5f else 1f)
                                    .padding(0.dp)
                            ) {
                                Row(

                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    // AnimatedContent for Selected Model Icon
                                    AnimatedContent(
                                        targetState = selectedModel,
                                        transitionSpec = {
                                            fadeIn(animationSpec = tween(durationMillis = 300)) togetherWith
                                                    fadeOut(animationSpec = tween(durationMillis = 300))
                                        },
                                        label = "SelectedModelIcon"
                                    ) { targetModel ->
                                        val modelDisplayName = chatViewModel.modelDisplayNameMap[targetModel] ?: targetModel
                                        val iconResourceId = chatViewModel.modelIconMap[modelDisplayName] ?: R.drawable.ic_bot

                                        Icon(
                                            painter = painterResource(id = iconResourceId),
                                            contentDescription = "Selected Model Icon",
                                            tint = textColor,
                                            modifier = Modifier.size(25.dp)
                                        )
                                    }

                                    // AnimatedContent for Dropdown Toggle Icon
                                    AnimatedContent(
                                        targetState = showModelSelection,
                                        transitionSpec = {
                                            fadeIn(animationSpec = tween(durationMillis = 300)) togetherWith
                                                    fadeOut(animationSpec = tween(durationMillis = 300))
                                        },
                                        label = "DropdownToggleIcon"
                                    ) { targetState ->
                                        Icon(
                                            painter = painterResource(
                                                id = if (targetState) {
                                                    R.drawable.ic_closemodel
                                                } else {
                                                    R.drawable.ic_openmodel
                                                }
                                            ),
                                            contentDescription = "Chọn model",
                                            tint = textColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // DropdownMenu associated with the combined icons
                                DropdownMenu(
                                    expanded = showModelSelection,
                                    onDismissRequest = { showModelSelection = false },
                                    properties = PopupProperties(focusable = false),
                                    modifier = Modifier
                                        .wrapContentSize(Alignment.Center)
                                        .crop(vertical = 8.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(15.dp)
                                        )
//                                            .background(
//                                                backgroundColor,
//                                                shape = RoundedCornerShape(15.dp)
//                                            )
                                        .fillMaxWidth(0.5f),
                                    offset = DpOffset(x = 40.dp, y = 8.dp)
                                ) {
                                    chatViewModel.availableModels.forEachIndexed { index, model ->
                                        DropdownMenuItem(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .wrapContentHeight(),
                                            text = {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        val modelDisplayName = chatViewModel.modelDisplayNameMap[model] ?: model
                                                        val iconResourceId = chatViewModel.modelIconMap[modelDisplayName] ?: R.drawable.ic_bot // Default to ic_bot if not found

                                                        Icon(
                                                            painter = painterResource(id = iconResourceId),
                                                            contentDescription = "Model Icon",
                                                            tint = textColor,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text(
                                                            text = modelDisplayName,
                                                            fontSize = 16.sp,
                                                            color = textColor,
                                                            fontWeight = if (model == selectedModel) FontWeight.Bold else FontWeight.Normal
                                                        )
                                                    }
                                                    if (model == selectedModel) {
                                                        Icon(
                                                            painter = painterResource(id = R.drawable.ic_chonmodel),
                                                            contentDescription = "Selected Model",
                                                            tint = textColor,
                                                            modifier = Modifier.size(24.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            onClick = {
                                                chatViewModel.selectModel(model)
                                                showModelSelection = false
                                                // Thêm phản hồi rung khi chọn model
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        )
                                        if (index < chatViewModel.availableModels.size - 1) {
                                            Divider(color = Color(0x14FFFFFF), thickness = 0.6.dp)
                                        }
                                    }
                                    LaunchedEffect(key1 = showModelSelection) {
                                        if (!showModelSelection) {
                                            isClicked = false
                                        }
                                    }
                                }
                            }
                        }

                    },

                    navigationIcon = {
                        IconButton(onClick = { scope.launch {
                            drawerState.open()
                            // Thêm phản hồi rung khi mở drawer
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        } }) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_listhistory),
                                contentDescription = "Menu",
                                tint = textColor,
                                modifier = Modifier.size(35.dp)
                            )
                        }
                    },
                    actions = {
                        // Existing Refresh IconButton
                        IconButton(
                            onClick = {
                                chatViewModel.refreshChats()
                                // Thêm phản hồi rung khi làm mới chat
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            enabled = chatState.chatList.isNotEmpty()
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_newms),
                                contentDescription = "Làm mới",
                                tint = textColor,
                                modifier = Modifier
                                    .size(30.dp)
                                    .alpha(if (chatState.chatList.isNotEmpty()) 1f else 0.5f)
                            )
                        }

                    },
                )
            },
            // Tùy chỉnh SnackbarHost để điều chỉnh vị trí
            snackbarHost = {
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier
                        .fillMaxWidth(),
                    snackbar = { snackbarData ->
                        Snackbar(
                            snackbarData = snackbarData,
                            modifier = Modifier
                                .padding(bottom = 65.dp)
                                .fillMaxWidth()
                        )
                    }
                )
            },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            modifier = Modifier.then(if (!isDrawerOpen) Modifier.imePadding() else Modifier)
        ) { paddingValues ->
            val clipboardManager = LocalClipboardManager.current
            val firstVisibleItemIndex by remember {
                derivedStateOf {
                    listState.firstVisibleItemIndex
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
            ) {
                AnimatedVisibility(
                    visible = showScrollToBottomButton,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 180.dp)
                        .zIndex(1f)
                ) {
                    FloatingActionButton(
                        onClick = {
                            scope.launch {
                                shouldAutoScroll = true // Allow potential future auto-scrolls
                                userScrolledAwayFromBottom = false // Reset flag immediately
                                if (chatState.chatList.isNotEmpty()) {
                                    // Use a slightly longer delay, consistent with chat switching
                                    kotlinx.coroutines.delay(100) // Increased delay
                                    // Scroll to the absolute bottom using Int.MAX_VALUE
                                    listState.animateScrollToItem(Int.MAX_VALUE)
                                }
                            }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .shadow(
                                elevation = 8.dp,
                                shape = CircleShape,
                                clip = false
                            ),
                        shape = CircleShape,
                        containerColor = if (isSystemInDarkTheme()) Color(0xFF1E1F22) else Color(0xFFEAEAEA),
                        contentColor = if (isSystemInDarkTheme()) Color.White else Color.Black,
                        elevation = FloatingActionButtonDefaults.elevation(
                            defaultElevation = 0.dp,
                            pressedElevation = 12.dp,
                            focusedElevation = 12.dp
                        )
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_download),
                            //imageVector = Icons.Filled.ArrowDownward,
                            contentDescription = "Scroll to Bottom"
                        )
                    }
                }



                // Nội dung chính của ChatScreen
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),

                    ){



                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp)
                            // Thêm clickable vào LazyColumn để đóng bàn phím
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null // Không hiển thị hiệu ứng ripple
                            ) {
                                focusManager.clearFocus() // Đóng bàn phím
                            },
                        state = listState,
                        verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.Bottom),
                    ) {
                        items(
                            items = chatState.chatList,
                            key = { chat -> 
                                if (chat.id.isEmpty()) "chat_${chat.hashCode()}" else chat.id 
                            }
                        ) { chat ->
                            if (chat.isFromUser) {
                                UserChatItem(
                                    prompt = chat.prompt,
                                    isError = chat.isError,
                                    imageUrl = chat.imageUrl,
                                    onLongPress = { textToCopy ->
                                        scope.launch {
                                            val plainText = parseFormattedText(textToCopy).text
                                            clipboardManager.setText(AnnotatedString(plainText))
                                            snackbarHostState.showSnackbar("Đã sao chép tin nhắn")
                                        }
                                    },
                                    onImageClick = { imageUrl ->
                                        val encodedUrl = Base64.encodeToString(imageUrl.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
                                        navController.navigate("fullscreen_image/$encodedUrl")
                                    },
                                    snackbarHostState = snackbarHostState
                                )
                            } else {
                                // Đơn giản hóa logic kiểm tra hiệu ứng typing

                                // Một tin nhắn chỉ được hiển thị hiệu ứng typing nếu:
                                // 1. Là tin nhắn mới nhất từ bot
                                // 2. Chưa từng hiển thị hiệu ứng typing
                                val isLatestBotMessage = chatState.chatList
                                    .filter { !it.isFromUser }
                                    .lastOrNull()?.id == chat.id

                                val isMessageAlreadyTyped = chatViewModel.isMessageTyped(chat.id)

                                val shouldShowTypingEffect = isLatestBotMessage && !isMessageAlreadyTyped

                                // Dùng LaunchedEffect để kiểm soát việc đánh dấu tin nhắn cũ
                                LaunchedEffect(chat.id) {
                                    // Đánh dấu các tin nhắn cũ không cần hiệu ứng là đã typed
                                    if (!isLatestBotMessage && !isMessageAlreadyTyped) {
                                        delay(50) // Delay nhỏ để tránh race condition
                                        chatViewModel.markMessageAsTyped(chat.id)
                                    }
                                }

                                ModelChatItem(
                                    response = chat.prompt,
                                    isError = chat.isError,
                                    onLongPress = { textToCopy ->
                                        scope.launch {
                                            val plainText = parseFormattedText(textToCopy).text
                                            clipboardManager.setText(AnnotatedString(plainText))
                                            snackbarHostState.showSnackbar("Đã sao chép tin nhắn")
                                        }
                                    },
                                    onImageClick = { imageUrl ->
                                        val encodedUrl = Base64.encodeToString(imageUrl.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
                                        navController.navigate("fullscreen_image/$encodedUrl")
                                    },
                                    snackbarHostState = snackbarHostState,
                                    chatId = chat.id,
                                    isNewChat = shouldShowTypingEffect,
                                    typingSpeed = TypingConfig.DEFAULT_TYPING_SPEED,
                                    onAnimationComplete = {
                                        chatViewModel.markMessageAsTyped(chat.id)
                                    },
                                    isMessageTyped = isMessageAlreadyTyped
                                )
                            }
                        }

                        // Chỉ báo "Đang suy nghĩ..." với key duy nhất và rõ ràng
                        item(key = "waiting_indicator_unique") {
                            if (chatState.isWaitingForResponse && chatState.imageUri == null) {
                                ModelChatItem(
                                    response = "",
                                    isError = false,
                                    onLongPress = { },
                                    onImageClick = { },
                                    snackbarHostState = snackbarHostState,
                                    isWaitingForResponse = true,
                                    isMessageTyped = true
                                )
                            }
                        }
                    }



                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    ) {
                        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
                        val maxImageHeight = (screenHeight * 0.3f).coerceAtLeast(70.dp)
                        Column {
                            chatState.imageUri?.let { uri ->
                                Box(
                                    modifier = Modifier
                                        .size(70.dp)
                                ) {
                                    // Hình ảnh đã chọn
                                    AsyncImage(
                                        model = ImageRequest.Builder(context)
                                            .data(uri)
                                            .size(coil.size.Size.ORIGINAL)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = "Hình ảnh đã chọn",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .heightIn(max = maxImageHeight)
                                            .clip(RoundedCornerShape(10.dp))
                                            .combinedClickable(
                                                onClick = {
                                                    val encodedUrl = Base64.encodeToString(
                                                        uri
                                                            .toString()
                                                            .toByteArray(Charsets.UTF_8),
                                                        Base64.URL_SAFE or Base64.NO_WRAP
                                                    )
                                                    navController.navigate("fullscreen_image/$encodedUrl")
                                                },
                                                onLongClick = {
                                                    chatViewModel.onEvent(
                                                        ChatUiEvent.OnImageSelected(uri)
                                                    )
                                                }
                                            )
                                    )

                                    // Nút 'X' để xóa ảnh
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .align(Alignment.TopEnd)
                                            .offset(x = 4.dp, y = (-4).dp)
                                            .clip(RoundedCornerShape(50))
                                            .clickable {
                                                chatViewModel.onEvent(ChatUiEvent.RemoveImage)
                                            }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Xóa ảnh",
                                            tint = Color.White,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    color = Color(0xFFAAAAAA),
                                                    shape = RoundedCornerShape(50)
                                                )
                                                .padding(7.dp)
                                                .graphicsLayer {
                                                    scaleX = 3f
                                                    scaleY = 3f
                                                }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp, start = 8.dp, end = 8.dp)
                            .then(if (!isDrawerOpen) Modifier.imePadding() else Modifier)
                            .background(
                                MaterialTheme.colorScheme.surface,
                                shape = RoundedCornerShape(20.dp)
                            ),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            // --- TextField nằm phía trên ---
                            LaunchedEffect(chatState.imageUri, chatState.chatList) {
                                showWelcomeMessage = chatState.chatList.isEmpty()
                            }
                            TextField(
                                modifier = Modifier
                                    .heightIn(min = 50.dp, max = 200.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState)
                                    .focusRequester(focusRequester)
                                    .onFocusChanged {
                                        if (it.isFocused) {
                                            scope.launch {
                                                scrollState.animateScrollTo(scrollState.maxValue)
                                            }
                                        }
                                    },
                                value = chatState.prompt,
                                onValueChange = { newValue ->
                                    chatViewModel.onEvent(ChatUiEvent.UpdatePrompt(newValue))
                                    scope.launch {
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                    }
                                },
                                placeholder = {
                                    Text(
                                        text = "Nhập tin nhắn cho ChatAI",
                                        modifier = Modifier.fillMaxWidth(),
                                        color = Color.Gray
                                    )
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    disabledContainerColor = MaterialTheme.colorScheme.surface,
                                    errorContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent,
                                    errorIndicatorColor = Color.Transparent
                                )
                            )

                            // --- Hàng chứa nút Icon ---
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                // --- Nút '+' (Thêm ảnh) ---
                                IconButton(
                                    onClick = { showSourceMenu = !showSourceMenu
                                        // Thêm phản hồi rung khi mở dropdown chọn ảnh
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    },
                                    modifier = Modifier
                                        .padding(bottom = 8.dp)
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .alpha(if (showSourceMenu) 0.5f else 1f),
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_popup),
                                        contentDescription = "Thêm ảnh",
                                        tint = textColor,
                                        modifier = Modifier.alpha(0.6f)
                                    )
                                }

                                // --- DropdownMenu Hiển thị các lựa chọn ---
                                DropdownMenu(
                                    expanded = showSourceMenu,
                                    onDismissRequest = { showSourceMenu = false },
                                    properties = PopupProperties(focusable = false),
                                    modifier = Modifier
                                        .crop(vertical = 8.dp)
                                        .background(
                                            MaterialTheme.colorScheme.surface,
                                            shape = RoundedCornerShape(15.dp)
                                        )
                                        .width(IntrinsicSize.Max),
                                    offset = DpOffset(x = 0.dp, y = (-8).dp)
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Chụp ảnh",
                                                style = TextStyle(
                                                    color = textColor,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        },
                                        onClick = {
                                            photoUri = createImageUri()
                                            photoUri?.let {
                                                takePictureLauncher.launch(it)
                                            }
                                            showSourceMenu = false
                                            // Thêm phản hồi rung khi chọn chụp ảnh
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        },
                                        trailingIcon = {
                                            Icon(
                                                painter = painterResource(id = R.drawable.camera_add),
                                                contentDescription = "Chụp ảnh",
                                                tint = textColor
                                            )
                                        }
                                    )
                                    Divider(color = Color(0x14FFFFFF), thickness = 0.6.dp)
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Thư viện ảnh",
                                                style = TextStyle(
                                                    color = textColor,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        },
                                        onClick = {
                                            imagePicker.launch(
                                                PickVisualMediaRequest(
                                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                                )
                                            )
                                            showSourceMenu = false
                                            // Thêm phản hồi rung khi chọn thư viện ảnh
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        },
                                        trailingIcon = {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_addpicture),
                                                contentDescription = "Thư viện ảnh",
                                                tint = textColor
                                            )
                                        }
                                    )
                                }

                                // --- Nút gửi tin nhắn ---
                                if (chatState.isLoading) {
                                    Icon(
                                        modifier = Modifier
                                            .padding(bottom = 8.dp)
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                chatViewModel.stopCurrentResponse()
                                                // Thêm phản hồi rung khi dừng
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            },
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dừng",
                                        tint = textColor
                                    )
                                } else {

                                    Icon(
                                        modifier = Modifier
                                            .padding(bottom = 8.dp)
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .alpha(if (canSend) 1f else 0.4f)
                                            .clickable(
                                                enabled = canSend && !chatState.isLoading,
                                                onClick = {
                                                    if (canSend) {
                                                        val sanitizedPrompt =
                                                            sanitizeMessage(chatState.prompt)
                                                        chatViewModel.onEvent(
                                                            ChatUiEvent.SendPrompt(
                                                                sanitizedPrompt,
                                                                chatState.imageUri
                                                            )
                                                        )
                                                        // Thêm phản hồi rung khi gửi tin nhắn
                                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                }
                                            ),
                                            painter = painterResource(id = R.drawable.ic_send),
                                            //imageVector = Icons.Filled.ArrowUpward,
                                        contentDescription = "Send Message",
                                        tint = textColor
                                    )
                                }
                            }
                        }
                    }


                }

                if (showWelcomeMessage && chatState.chatList.isEmpty()) {
                    userScrolled = false
                    WelcomeMessage { displayText, apiPrompt ->
                        // 1) Hiển thị tin nhắn user "Bạn sẽ là XXX" (KHÔNG gọi API)
                        val localUserPrompt = "Bạn sẽ là $displayText"
                        chatViewModel.insertLocalUserChat(localUserPrompt)

                        // 2) Gọi API với apiPrompt
                        scope.launch {
                            chatViewModel.getResponse(apiPrompt, chatState.selectedSegment?.id)
                        }
                    }
                }

                LaunchedEffect(chatState.chatList.isEmpty()) {
                    if (chatState.chatList.isEmpty()) {
                        showWelcomeMessage = true
                    }
                }
            }
            if (showLogoutDialog) {
                AlertDialog(
                    onDismissRequest = { showLogoutDialog = false },
                    title = { Text(text = "Đăng xuất") },
                    text = { Text("Bạn có chắc chắn muốn đăng xuất?") },
                    confirmButton = {
                        TextButton(onClick = {
                            // Thực hiện đăng xuất
                            FirebaseAuth.getInstance().signOut()
                            GoogleSignIn.getClient(
                                context,
                                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                    .build()
                            ).signOut().addOnCompleteListener {
                                navController.navigate("login") {
                                    popUpTo("chat") { inclusive = true }
                                }
                            }
                            showLogoutDialog = false
                        }) {
                            Text("Đồng ý")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showLogoutDialog = false // Đóng Dialog nếu người dùng hủy
                        }) {
                            Text("Hủy")
                        }
                    }
                )
            }
        }
    }
}

fun sanitizeMessage(input: String): String {
    return input
        .lines() // Chia chuỗi thành danh sách các dòng
        .filter { it.isNotBlank() } // Loại bỏ các dòng trống
        .joinToString("\n") // Ghép lại thành một chuỗi với dấu xuống dòng
        .trim() // Loại bỏ khoảng trắng ở đầu và cuối
}

