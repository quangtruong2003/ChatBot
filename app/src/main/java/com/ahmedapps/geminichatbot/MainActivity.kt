// MainActivity.kt
package com.ahmedapps.geminichatbot

import android.Manifest
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.compose.foundation.Image
import android.util.Base64
import android.util.Log
import android.view.ViewTreeObserver
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ahmedapps.geminichatbot.auth.LoginScreen
import com.ahmedapps.geminichatbot.loginlogout.ForgotPasswordScreen
import com.ahmedapps.geminichatbot.loginlogout.RegistrationScreen
import fomatText.FormattedTextDisplay
import com.ahmedapps.geminichatbot.ui.theme.GeminiChatBotTheme
import fomatText.parseFormattedText
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File




@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalPermissionsApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            GeminiChatBotTheme {
                val navController = rememberNavController()
                val currentUser = FirebaseAuth.getInstance().currentUser
                val startDestination = if (currentUser != null) "chat" else "login"

                NavHost(navController, startDestination = startDestination) {
                    composable("login") {
                        LoginScreen(
                            onLoginSuccess = {
                                navController.navigate("chat") {
                                    // Xóa tất cả các màn hình trước đó trong ngăn xếp và giữ lại màn hình chat
                                    popUpTo("login") { inclusive = true }
                                }
                            },
                            onNavigateToRegister = {
                                navController.navigate("register")
                            },
                            onNavigateToForgotPassword = {
                                navController.navigate("forgot_password")
                            }
                        )
                    }
                    composable("forgot_password") {
                        ForgotPasswordScreen(
                            onBackToLogin = {
                                navController.popBackStack()
                            }
                        )
                    }
                    composable("register") {
                        RegistrationScreen(
                            onRegistrationSuccess = {
                                navController.navigate("chat") {
                                    // Xóa màn hình đăng ký trước đó
                                    popUpTo("register") { inclusive = true }
                                }
                            },
                            onNavigateToLogin = {
                                navController.navigate("login") {
                                    // Xóa màn hình đăng ký trước đó khi chuyển đến đăng nhập
                                    popUpTo("register") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("chat") {
                        ChatScreen(navController)
                    }
                    composable(
                        "fullscreen_image/{encodedImageUrl}",
                        arguments = listOf(navArgument("encodedImageUrl") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val encodedImageUrl = backStackEntry.arguments?.getString("encodedImageUrl") ?: ""
                        val imageUrl = String(Base64.decode(encodedImageUrl, Base64.URL_SAFE or Base64.NO_WRAP))
                        FullScreenImageScreen(
                            imageUrl = imageUrl,
                            onClose = { navController.popBackStack() }
                        )
                    }
                }

            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun ChatScreen(navController: NavController, chatViewModel: ChatViewModel = hiltViewModel()) {val isError: Boolean = false

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

        val isDarkTheme = isSystemInDarkTheme()
        val backgroundColor = when {
            isError -> MaterialTheme.colorScheme.error
            isDarkTheme -> Color(0x43FFFFFF)
            else -> Color(0x97FFFFFF)
        }
        val textColor = if (isDarkTheme) Color.White else Color.Black

        // Biến trạng thái để quản lý việc hiển thị DropdownMenu
        var showSourceMenu by remember { mutableStateOf(false) }

        // Yêu cầu quyền CAMERA
        val cameraPermissionState = rememberPermissionState(permission = Manifest.permission.CAMERA)

        LaunchedEffect(Unit) {
            if (!cameraPermissionState.status.isGranted) {
                cameraPermissionState.launchPermissionRequest()
            }
        }

        // Kiểm tra quyền và hiển thị thông báo nếu chưa cấp quyền
        if (!cameraPermissionState.status.isGranted && cameraPermissionState.status.shouldShowRationale) {
            LaunchedEffect(Unit) {
                snackbarHostState.showSnackbar("Ứng dụng cần quyền truy cập máy ảnh để chụp ảnh.")
            }
        }

        val isUserScrolling = remember { mutableStateOf(false) }
        var userScrolled by remember { mutableStateOf(false) }
        var previousChatListSize by remember { mutableStateOf(chatState.chatList.size) }
        val showScrollToBottomButton by remember {
            derivedStateOf {
                val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                if (lastVisibleItem != null) {
                    // Kiểm tra nếu chỉ số của mục cuối cùng hiển thị nhỏ hơn chỉ số của mục cuối cùng trong danh sách
                    lastVisibleItem.index < chatState.chatList.lastIndex
                } else {
                    false
                }
            }
        }
        val canSend = chatState.prompt.isNotEmpty() || chatState.imageUri != null

        var shouldAutoScroll by remember { mutableStateOf(true) }
        // Xử lý cuộn trong LazyColumn
        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }
                .collect { isScrolling ->
                    if (isScrolling) {
                        // Người dùng đang cuộn, tắt tự động cuộn
                        shouldAutoScroll = false
                        userScrolled = true
                    }
                }
        }

        LaunchedEffect(chatState.chatList.size) {
            val newMessageAdded = chatState.chatList.size > previousChatListSize
            previousChatListSize = chatState.chatList.size

            if (newMessageAdded) {
                // Có tin nhắn mới, bật tự động cuộn
                shouldAutoScroll = true

                if (shouldAutoScroll) {
                    // Chỉ tự động cuộn nếu đang bật tự động cuộn
                    scope.launch {
                        listState.animateScrollToItem(chatState.chatList.size - 1)
                    }
                }
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

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
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
                        onLogout = { showLogoutDialog = true }
                    )
                }
            },
            scrimColor = Color.Transparent // Làm cho lớp phủ trong suốt
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
                                        modifier = Modifier
                                            .wrapContentSize(Alignment.Center)
                                            .crop(vertical = 8.dp)
                                            .background(
                                                backgroundColor,
                                                shape = RoundedCornerShape(15.dp)
                                            )
                                            .fillMaxWidth(0.45f),
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
                                                }
                                            )
                                            if (index < chatViewModel.availableModels.size - 1) {
                                                Divider(color = Color.LightGray, thickness = 0.8.dp)
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
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
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
                contentWindowInsets = WindowInsets(0, 0, 0, 0), // Loại bỏ các insets mặc định
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
                        .imePadding()
                ) {
                    AnimatedVisibility(
                        visible = showScrollToBottomButton,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter) // Align at the bottom center within the Box
                            .padding(bottom = 180.dp)
                            .zIndex(1f)// Add padding to adjust above the input bar
                    ) {
                        FloatingActionButton(
                            onClick = {
                                scope.launch {
                                    shouldAutoScroll = true
                                    listState.animateScrollToItem(chatState.chatList.size)

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
                                defaultElevation = 0.dp, // Tăng giá trị defaultElevation
                                pressedElevation = 12.dp, // Tăng giá trị pressedElevation (tùy chọn)
                                focusedElevation = 12.dp // Tăng giá trị focusedElevation (tùy chọn)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.ArrowDownward,
                                contentDescription = "Scroll to Bottom"
                            )
                        }
                    }

                    // Nội dung chính của ChatScreen
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                    ){



                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            state = listState,
                            verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.Bottom),
                        ) {
                            items(chatState.chatList) { chat ->
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
                                        snackbarHostState = snackbarHostState
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
                                .imePadding()
                            ,

                            ) {

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth(),
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    // Nút '+' để hiển thị DropdownMenu
                                    IconButton(
                                        onClick = { showSourceMenu = !showSourceMenu },
                                        modifier = Modifier
                                            .padding(bottom = 8.dp)
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .alpha(if (showSourceMenu) 0.5f else 1f),
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_popup),
                                            contentDescription = "Thêm ảnh",
                                            tint = textColor
                                        )
                                    }
                                    // DropdownMenu hiển thị các lựa chọn
                                    DropdownMenu(
                                        expanded = showSourceMenu,
                                        onDismissRequest = { showSourceMenu = false },
                                        modifier = Modifier
                                            .crop(vertical = 8.dp)
                                            .background(
                                                backgroundColor,
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
                                                // Tạo URI tạm thời và mở máy ảnh
                                                photoUri = createImageUri()
                                                photoUri?.let {
                                                    takePictureLauncher.launch(it)
                                                }
                                                showSourceMenu = false
                                            },
                                            trailingIcon = {
                                                Icon(
                                                    painter = painterResource(id = R.drawable.camera_add),
                                                    contentDescription = "Chụp ảnh",
                                                    tint = textColor
                                                )
                                            }
                                        )
                                        Divider(color = Color.LightGray, thickness = 0.8.dp)
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
//                                    Icon(
//                                        modifier = Modifier
//                                            .padding(bottom = 8.dp)
//                                            .size(40.dp)
//                                            .clip(RoundedCornerShape(8.dp))
//                                            .clickable {
//                                                imagePicker.launch(
//                                                    PickVisualMediaRequest
//                                                        .Builder()
//                                                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
//                                                        .build()
//                                                )
//                                            },
//                                        imageVector = Icons.Rounded.AddPhotoAlternate,
//                                        contentDescription = "Thêm ảnh",
//                                        tint = MaterialTheme.colorScheme.primary
//                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    TextField(
                                        modifier = Modifier
                                            .heightIn(min = 50.dp, max = 200.dp)
                                            .clip(RoundedCornerShape(20.dp))
                                            .weight(1f)
                                            .verticalScroll(scrollState)
                                            .border(
                                                width = 1.dp,
                                                color = Color.LightGray,
                                                shape = RoundedCornerShape(20.dp)
                                            )
                                            .focusRequester(focusRequester)
                                            .onFocusChanged {
                                                if (it.isFocused) {
                                                    scope.launch {
                                                        scrollState.animateScrollTo(scrollState.maxValue)
                                                    }
                                                }
                                            },
                                        value = chatState.prompt,
                                        onValueChange = {
                                            chatViewModel.onEvent(ChatUiEvent.UpdatePrompt(it))
                                            if (it.isNotEmpty()) {
                                                showWelcomeMessage = false
                                            }
                                            scope.launch {
                                                scrollState.animateScrollTo(scrollState.maxValue)
                                            }
                                        },
                                        placeholder = {
                                            Text(
                                                text = "Nhập tin nhắn",
                                                modifier = Modifier.fillMaxWidth(),
                                                style = TextStyle(
                                                    fontSize = 17.sp,
                                                    brush = Brush.linearGradient(
                                                        colors = listOf(
                                                            Color(0xFF1BA1E3),
                                                            Color(0xFF5489D6),
                                                            Color(0xFF9B72CB),
                                                            Color(0xFFD96570),
                                                            Color(0xFFF49C46)
                                                        )
                                                    ),
                                                )
                                            )
                                        },
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (chatState.isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .padding(bottom = 8.dp)
                                                .size(40.dp),
                                            color = textColor,
                                            strokeWidth = 5.dp
                                        )
                                    } else {
                                        Icon(
                                            modifier = Modifier
                                                .padding(bottom = 8.dp)
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .alpha(if (canSend) 1f else 0.4f) // Adjust opacity based on canSend
                                                .clickable(
                                                    enabled = canSend && !chatState.isLoading, // Enable or disable based on canSend
                                                    onClick = {
                                                        if (canSend) { // Additional safety check
                                                            val sanitizedPrompt =
                                                                sanitizeMessage(chatState.prompt) // Xử lý chuỗi tin nhắn
                                                            chatViewModel.onEvent(
                                                                ChatUiEvent.SendPrompt(
                                                                    sanitizedPrompt,
                                                                    chatState.imageUri
                                                                )
                                                            )
                                                            showWelcomeMessage = false
                                                        }
                                                    }
                                                ),
                                            painter = painterResource(id = R.drawable.ic_send),
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

                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(y = (-70).dp)
                            ,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_app),
                                contentDescription = "Icon Bot",
                                modifier = Modifier.size(80.dp),
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            var textSize by remember { mutableStateOf(22.sp) }
                            var boxWidth by remember { mutableStateOf(0) }

                            Box(modifier = Modifier
                                .fillMaxWidth(1f)
                                .onSizeChanged { size ->
                                    // Cập nhật kích thước của Box
                                    boxWidth = size.width
                                }
                            ) {
                                // Tính toán fontSize dựa trên chiều rộng của Box
                                if (boxWidth > 0) {
                                    // điều chỉnh công thức này dựa trên thử nghiệm để có kết quả tốt nhất
                                    textSize = (boxWidth / LocalDensity.current.density * 0.05f).sp
                                }

                                Text(
                                    text = "Xin chào, tôi có thể giúp gì cho bạn?",
                                    style = TextStyle(
                                        fontSize = textSize, // Sử dụng fontSize đã được tính toán
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                Color(0xFF1BA1E3),
                                                Color(0xFF5489D6),
                                                Color(0xFF9B72CB),
                                                Color(0xFFD96570),
                                                Color(0xFFF49C46)
                                            )
                                        ),
                                    ),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.align(Alignment.Center)
                                )
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Column(horizontalAlignment = Alignment.End) {
                imageUrl?.let { url ->
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
                Spacer(modifier = Modifier.height(8.dp))
//                if (prompt.isNotEmpty()) {
//                    Text(
//                        modifier = Modifier
//                            .widthIn(max = maxWidth)
//                            .clip(RoundedCornerShape(17.dp))
//                            .background(backgroundColor)
//                            .padding(12.dp)
//                            .combinedClickable(
//                                onClick = {},
//                                onLongClick = { onLongPress(prompt) }
//                            ),
//                        text = prompt,
//                        style = TextStyle(
//                            fontSize = 17.sp,
//                            color = textColor
//                        )
//                    )
//                }
                if (prompt.isNotEmpty()) {
                    FormattedTextDisplay(
                        annotatedString = formattedPrompt,
                        modifier = Modifier
                            .widthIn(max = maxWidth)
                            .clip(RoundedCornerShape(17.dp))
                            .background(backgroundColor)
                            .padding(12.dp)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { onLongPress(prompt) }
                            ),
                        snackbarHostState = snackbarHostState
                    )
                }
            }
        }
    }



    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun ModelChatItem(
        response: String,
        //imageUrl: String?, // Add imageUrl parameter
        isError: Boolean,
        onLongPress: (String) -> Unit,
        onImageClick: (String) -> Unit,
        snackbarHostState: SnackbarHostState
    ) {
        val isDarkTheme = isSystemInDarkTheme()
        val textColor = if (isDarkTheme) Color.White else Color.Black
        val backgroundColor = when {
            isError -> MaterialTheme.colorScheme.error
            isSystemInDarkTheme() -> MaterialTheme.colorScheme.surface // Sử dụng surface cho màu nền dark
            else -> MaterialTheme.colorScheme.surface // Sử dụng surface cho màu nền white
        }
        val formattedResponse = parseFormattedText(response)

        val maxWidth = LocalConfiguration.current.screenWidthDp.dp * 0.9f
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        //val maxImageHeight = (screenHeight * 0.3f).coerceAtLeast(175.dp)


        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            // Icon (Avatar)
            Image( // or AsyncImage if using a URL
                painter = painterResource(id = R.drawable.ic_bot),
                contentDescription = "Chatbot Avatar",
                modifier = Modifier
                    .padding(top = 7.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    //.border(1.dp, Color.LightGray, CircleShape)
                    .align(Alignment.Top) // Align to the top of the text
            )

            Column(horizontalAlignment = Alignment.Start) {
//                imageUrl?.let { url ->  // Display image if URL is provided
//                    AsyncImage(
//                        model = ImageRequest.Builder(LocalContext.current)
//                            .data(url)
//                            .crossfade(true)
//                            .build(),
//                        contentDescription = "Model Image",
//                        contentScale = ContentScale.Crop, // Or ContentScale.Fit as needed
//                        modifier = Modifier
//                            .fillMaxWidth()
//                            .heightIn(max = maxImageHeight)
//                            .clip(RoundedCornerShape(12.dp))
//                            .combinedClickable(
//                                onClick = { onImageClick(url) },
//                                onLongClick = {onLongPress(response)} // Or handle image long-click differently
//                            )
//                    )
//                    Spacer(Modifier.height(8.dp)) // Add space between image and text
//                }

                FormattedTextDisplay(
                    annotatedString = formattedResponse,
                    modifier = Modifier
                        .widthIn(max = maxWidth)
                        .clip(RoundedCornerShape(15.dp))
                        .background(backgroundColor)
                        .padding(12.dp)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { onLongPress(response) }
                        ),
                    snackbarHostState = snackbarHostState
                )
            }
        }
    }


    /**
     * Composable hiển thị toàn màn hình hình ảnh với khả năng zoom và kéo.
     */
    @Composable
    fun FullScreenImageScreen(
        imageUrl: String?,
        onClose: () -> Unit
    ) {
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        var showResetButton by remember { mutableStateOf(false) }

        val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, 5f)
            offset += panChange
            showResetButton = scale != 1f || offset != Offset.Zero
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .navigationBarsPadding()

        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Hình ảnh toàn màn hình",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(
                            scaleX = scale,
                            scaleY = scale,
                            translationX = offset.x,
                            translationY = offset.y
                        )
                        .transformable(state = transformableState)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    scale = if (scale < 3f) scale * 2 else 1f
                                    offset = Offset.Zero
                                    showResetButton = false
                                }
                            )
                        },
                    onError = {
                        Log.e("FullScreenImageScreen", "Error loading image: ${it.result.throwable}")
                    }
                )
            } else {
                Text("Không tìm thấy hình ảnh", color = Color.White)
            }

            // Nút đóng
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .statusBarsPadding() // Apply statusBarsPadding before background
                    .background(Color.Gray.copy(alpha = 0.5f), shape = CircleShape) // Use CircleShape for a circular button
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Đóng",
                    tint = Color.White
                )
            }

            // Nút reset zoom/pan
            AnimatedVisibility(
                visible = showResetButton,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .statusBarsPadding()
            ) {
                IconButton(
                    onClick = {
                        scale = 1f
                        offset = Offset.Zero
                        showResetButton = false
                    },
                    modifier = Modifier
                        .background(Color.Gray.copy(alpha = 0.5f), shape = RoundedCornerShape(50))
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_reset),
                        contentDescription = "Reset",
                        tint = Color.White,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }
        }
    }

}
