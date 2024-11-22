// MainActivity.kt
package com.ahmedapps.geminichatbot

import android.net.Uri
import android.os.Bundle
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import coil.compose.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.ahmedapps.geminichatbot.auth.LoginScreen
import com.ahmedapps.geminichatbot.auth.RegistrationScreen
import com.ahmedapps.geminichatbot.data.ChatSegment
import com.ahmedapps.geminichatbot.ui.theme.GeminiChatBotTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
                                    popUpTo("login") { inclusive = true }
                                }
                            },
                            onNavigateToRegister = {
                                navController.navigate("register")
                            }
                        )
                    }
                    composable("register") {
                        RegistrationScreen(
                            onRegistrationSuccess = {
                                navController.navigate("chat") {
                                    popUpTo("register") { inclusive = true }
                                }
                            },
                            onNavigateToLogin = {
                                navController.navigate("login") {
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

    @OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
    @Composable
    fun ChatScreen(navController: NavController, chatViewModel: ChatViewModel = hiltViewModel()) {
        val chatState by chatViewModel.chatState.collectAsState()
        var showLogoutDialog by remember { mutableStateOf(false) }
        var showWelcomeMessage by remember { mutableStateOf(true) }

        // State để quản lý việc hiển thị SideDrawer
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        // Khởi tạo LazyListState để quản lý cuộn danh sách
        val listState = rememberLazyListState()

        // Khởi tạo SnackbarHostState
        val snackbarHostState = remember { SnackbarHostState() }

        val isUserScrolling = remember { mutableStateOf(false) }
        var userScrolled by remember { mutableStateOf(false) }
        var previousChatListSize by remember { mutableStateOf(chatState.chatList.size) }
        val showScrollToBottomButton by remember {
            derivedStateOf {
                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index != chatState.chatList.lastIndex
            }
        }
        val canSend = chatState.prompt.isNotEmpty() || chatState.imageUri != null

        LaunchedEffect(listState) {
            snapshotFlow { listState.isScrollInProgress }
                .collect { isScrolling ->
                    if (isScrolling) {
                        userScrolled = true
                    }
                }
        }

        LaunchedEffect(chatState.chatList.size,isUserScrolling.value) {
            val newMessageAdded = chatState.chatList.size > previousChatListSize
            previousChatListSize = chatState.chatList.size

            if (newMessageAdded) {
                // Có tin nhắn mới, đặt lại userScrolled để tự động cuộn
                userScrolled = false
            }

            if (!userScrolled && chatState.chatList.isNotEmpty()) {
                scope.launch {
                    listState.animateScrollToItem(chatState.chatList.size - 1)
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

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                SideDrawer(
                    onClose = { scope.launch { drawerState.close() } },
                    chatViewModel = chatViewModel,
                    onLogout = { showLogoutDialog = true }
                )
            }
        ) {
            Scaffold(
                topBar = {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = "ChatAI",
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            IconButton(
                                onClick = { chatViewModel.refreshChats() },
                                enabled = chatState.chatList.isNotEmpty() // Vô hiệu hóa nếu danh sách trống
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Làm mới")
                            }
                        }
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
                }
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
                        enter = fadeIn(), // Hiệu ứng mờ dần khi xuất hiện
                        exit = fadeOut(), // Hiệu ứng mờ dần khi ẩn đi
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 85.dp)
                            .zIndex(9f)
                    ) {
                        FloatingActionButton(
                            onClick = {
                                scope.launch {
                                    listState.animateScrollToItem(chatState.chatList.size - 1)
                                }
                            },
                            modifier = Modifier.size(40.dp),
                            shape = CircleShape,
                            containerColor = if (isSystemInDarkTheme()) Color.DarkGray else Color.LightGray,
                            contentColor = if (isSystemInDarkTheme()) Color.White else Color.Black,
                            elevation = FloatingActionButtonDefaults.elevation(0.dp) // Loại bỏ đổ bóng
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
                    ) {

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
                                        imageUrl = chat.imageUrl,
                                        onLongPress = { textToCopy ->
                                            scope.launch {
                                                clipboardManager.setText(AnnotatedString(textToCopy))
                                                snackbarHostState.showSnackbar("Đã sao chép tin nhắn")
                                            }
                                        },
                                        onImageClick = { imageUrl ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Image clicked: $imageUrl")
                                            }
                                            val encodedUrl = Base64.encodeToString(imageUrl.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
                                            navController.navigate("fullscreen_image/$encodedUrl")
                                        }
                                    )
                                } else {
                                    ModelChatItem(
                                        response = chat.prompt,
                                        isError = chat.isError,
                                        onLongPress = { textToCopy ->
                                            scope.launch {
                                                clipboardManager.setText(AnnotatedString(textToCopy))
                                                snackbarHostState.showSnackbar("Đã sao chép tin nhắn")
                                            }
                                        },
                                        onImageClick = { imageUrl ->
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Image clicked: $imageUrl")
                                            }
                                            val encodedUrl = Base64.encodeToString(imageUrl.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
                                            navController.navigate("fullscreen_image/$encodedUrl")
                                        }
                                    )
                                }

                            }


                        }
                        Box(
                            modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 8.dp, start = 8.dp, end = 8.dp),
                            ){
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
                                                            ChatUiEvent.OnImageSelected(
                                                                uri
                                                            )
                                                        )
                                                    }
                                                )
                                        )

                                        // Nút 'X' để xóa ảnh
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Xóa ảnh",
                                            tint = Color.White,
                                            modifier = Modifier
                                                .size(17.dp)
                                                .align(Alignment.TopEnd)
                                                .offset(x = 4.dp, y = (-4).dp)
                                                .background(
                                                    color = Color.LightGray,
                                                    shape = RoundedCornerShape(50)
                                                )
                                                .padding(5.dp)
                                                .clickable {
                                                    chatViewModel.onEvent(ChatUiEvent.RemoveImage)
                                                }
                                                // Apply the style for bolding
                                                .graphicsLayer {
                                                    scaleX = 2.2f
                                                    scaleY = 2.2f
                                                }
                                        )

                                    }

                                }

                            }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp, bottom = 16.dp, start = 8.dp, end = 8.dp),
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
                                    Icon(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                imagePicker.launch(
                                                    PickVisualMediaRequest
                                                        .Builder()
                                                        .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                        .build()
                                                )
                                            },
                                        imageVector = Icons.Rounded.AddPhotoAlternate,
                                        contentDescription = "Thêm ảnh",
                                        tint = MaterialTheme.colorScheme.primary
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    TextField(
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(
                                                min = 50.dp,
                                                max = 200.dp
                                            ) // Adjust height for multiline input
                                            .verticalScroll(rememberScrollState()), // Enable scrolling for text
                                        value = chatState.prompt,
                                        onValueChange = {
                                            chatViewModel.onEvent(ChatUiEvent.UpdatePrompt(it))
                                            if (it.isNotEmpty()) {
                                                showWelcomeMessage = false
                                            }
                                        },
                                        placeholder = {
                                            Text(text = "Nhập tin nhắn")
                                        },
                                    )

                                    Spacer(modifier = Modifier.width(8.dp))

                                    if (chatState.isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(40.dp),
                                            color = MaterialTheme.colorScheme.primary,
                                            strokeWidth = 7.dp
                                        )
                                    } else {
                                        Icon(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .alpha(if (canSend) 1f else 0.4f) // Adjust opacity based on canSend
                                                .clickable(
                                                    enabled = canSend, // Enable or disable based on canSend
                                                    onClick = {
                                                        if (canSend) { // Additional safety check
                                                            chatViewModel.onEvent(
                                                                ChatUiEvent.SendPrompt(
                                                                    chatState.prompt,
                                                                    chatState.imageUri
                                                                )
                                                            )
                                                            showWelcomeMessage = false
                                                        }
                                                    }
                                                ),
                                            imageVector = Icons.Rounded.Send,
                                            contentDescription = "Send Message",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (showWelcomeMessage && chatState.chatList.isEmpty()) {
                        Text(
                            modifier = Modifier.align(Alignment.Center),
                            text = "Xin chào, tôi có thể giúp gì cho bạn?",
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
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

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun UserChatItem(
        prompt: String,
        imageUrl: String?,
        onLongPress: (String) -> Unit,
        onImageClick: (String) -> Unit
    ) {
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        val maxImageHeight = (screenHeight * 0.3f).coerceAtLeast(175.dp)

        Column(
            modifier = Modifier
                .padding(start = 55.dp, bottom = 16.dp)
        ) {
            imageUrl?.let { url ->
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(url)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Your Image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxImageHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, Color.Transparent, RoundedCornerShape(12.dp))
                        .combinedClickable(
                            onClick = { onImageClick(url) },
                            onLongClick = { onLongPress(prompt) }
                        )
                )
            }

            if (prompt.isNotEmpty()) {
                Text(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF3F6D7C))
                        .padding(16.dp)
                        .combinedClickable(
                            onClick = {},
                            onLongClick = { onLongPress(prompt) }
                        ),
                    text = prompt,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }




    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    fun ModelChatItem(
        response: String,
        isError: Boolean,
        onLongPress: (String) -> Unit,
        onImageClick: (String) -> Unit
    ) {
        val formattedResponse = parseFormattedText(response)
        val backgroundColor = if (isError) MaterialTheme.colorScheme.error else Color(0xFF838483)

        Column(
            modifier = Modifier
                .padding(end = 55.dp, bottom = 16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            onLongPress(formattedResponse.text)
                        }
                    )
                }
        ) {
            // If your model can send images, include this block
//            val imageUrl = extractImageUrlFromResponse(response) // Implement this function as needed
//            imageUrl?.let { url ->
//                AsyncImage(
//                    model = ImageRequest.Builder(LocalContext.current)
//                        .data(url)
//                        .crossfade(true)
//                        .build(),
//                    contentDescription = "Model Image",
//                    contentScale = ContentScale.Crop,
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .heightIn(max = 200.dp) // Set maximum height
//                        .clip(RoundedCornerShape(12.dp))
//                        .border(1.dp, Color.Transparent, RoundedCornerShape(12.dp))
//                        .combinedClickable(
//                            onClick = { onImageClick(url) },
//                            onLongClick = { onLongPress(formattedResponse.text) }
//                        )
//                )
//                Spacer(modifier = Modifier.height(8.dp))
//            }

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(backgroundColor)
                    .padding(16.dp),
                text = formattedResponse,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }


    /**
     * Composable hiển thị toàn màn hình hình ảnh với khả năng zoom và kéo.
     */
    @Composable
    fun FullScreenImageScreen(
        imageUrl: String,
        onClose: () -> Unit
    ) {
        // Biến trạng thái để quản lý các phép biến đổi (scale, translation)
        var scale by remember { mutableStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }

        // GestureDetector để xử lý các cử chỉ zoom và kéo
        val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(1f, 5f) // Giới hạn zoom từ 1x đến 5x
            offset += panChange
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
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
                    .transformable(state = transformableState) // Xử lý các phép biến đổi
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = {
                                // Tăng hoặc giảm zoom khi double tap
                                scale = if (scale < 3f) scale * 2 else 1f
                            }
                        )
                    }
            )

            // Nút đóng góc trên bên trái
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Gray.copy(alpha = 0.5f), shape = RoundedCornerShape(50))
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Đóng",
                    tint = Color.White
                )
            }
        }
    }


    /**
     * Hàm phân tích và định dạng văn bản dựa trên các định dạng Markdown phức tạp, bao gồm danh sách lồng nhau với các ký hiệu đặc biệt.
     *
     * @param input Chuỗi văn bản đầu vào chứa các định dạng Markdown như **bold**, *italic*, __underline__, và danh sách bắt đầu bằng *.
     * @return AnnotatedString đã được định dạng theo các quy tắc Markdown được hỗ trợ.
     */
    fun parseFormattedText(input: String): AnnotatedString {
        // Patterns for formatting
        val patterns = listOf(
            "**" to SpanStyle(fontWeight = FontWeight.Bold),
            "*" to SpanStyle(fontStyle = FontStyle.Italic),
            "__" to SpanStyle(textDecoration = TextDecoration.Underline)
        )

        val builder = AnnotatedString.Builder()
        val lines = input.trimEnd().lines()

        // Variable to track list item numbers
        var listItemNumber = 1

        for ((index, line) in lines.withIndex()) {
            var processedLine = line

            // Check for list items
            val listMatch = """^(\s*)\*\s+(.*)""".toRegex().find(line)
            if (listMatch != null) {
                val leadingSpaces = listMatch.groupValues[1].length
                val content = listMatch.groupValues[2]
                val level = (leadingSpaces / 4) + 1

                val marker = when (level) {
                    1 -> "${listItemNumber++}. "
                    2 -> "• "
                    3 -> "◦ "
                    else -> "• "
                }

                val indentation = "    ".repeat(level - 1)
                processedLine = "$indentation$marker$content"
            }

            // Check for headings
            val isHeading = """^\*\*(.+)\*\*:$""".toRegex().matches(line.trim())
            if (isHeading) {
                val headingText = """^\*\*(.+)\*\*:$""".toRegex().find(line.trim())?.groupValues?.get(1) ?: line
                builder.withStyle(patterns[0].second) {
                    append("$headingText:")
                }
                if (index != lines.lastIndex) {
                    builder.append("\n\n")
                }
                continue
            }

            // Process inline formatting
            var remainingText = processedLine
            while (remainingText.isNotEmpty()) {
                var matched = false
                for ((delimiter, style) in patterns) {
                    val pattern = """\Q$delimiter\E(.*?)\Q$delimiter\E""".toRegex()
                    val match = pattern.find(remainingText)
                    if (match != null) {
                        val start = match.range.first
                        if (start > 0) {
                            builder.append(remainingText.substring(0, start))
                        }
                        val formattedText = match.groupValues[1]
                        builder.withStyle(style) {
                            append(formattedText)
                        }
                        remainingText = remainingText.substring(match.range.last + 1)
                        matched = true
                        break
                    }
                }
                if (!matched) {
                    builder.append(remainingText)
                    break
                }
            }

            // Add newline if it's not the last line
            if (index != lines.lastIndex) {
                builder.append("\n")
            }
        }

        return builder.toAnnotatedString()
    }


}
