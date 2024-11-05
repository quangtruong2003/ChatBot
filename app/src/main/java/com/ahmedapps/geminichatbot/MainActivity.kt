// MainActivity.kt
package com.ahmedapps.geminichatbot

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.compose.rememberNavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import com.ahmedapps.geminichatbot.auth.LoginScreen
import com.ahmedapps.geminichatbot.ui.theme.GeminiChatBotTheme
import com.ahmedapps.geminichatbot.ui.theme.Green
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.ahmedapps.geminichatbot.auth.RegistrationScreen
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
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
                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ChatScreen(navController: NavController, chatViewModel: ChatViewModel = hiltViewModel()) {
        val chatState by chatViewModel.chatState.collectAsState()

        var showWelcomeMessage by remember { mutableStateOf(true) }

        // State để quản lý Dialog hiển thị hình ảnh trong khu vực nhập liệu
        var isImageDialogOpen by remember { mutableStateOf(false) }

        // State để quản lý việc hiển thị SideDrawer
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        // Khởi tạo LazyListState để quản lý cuộn danh sách
        val listState = rememberLazyListState()

        // Khởi tạo SnackbarHostState
        val snackbarHostState = remember { SnackbarHostState() }

        // LaunchedEffect để tự động cuộn xuống cuối danh sách khi có tin nhắn mới
        LaunchedEffect(chatState.chatList.size) {
            if (chatState.chatList.isNotEmpty()) {
                listState.animateScrollToItem(chatState.chatList.size - 1)
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

        // Sử dụng ModalNavigationDrawer thay vì ModalDrawer
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                SideDrawer(
                    onClose = { scope.launch { drawerState.close() } },
                    chatViewModel = chatViewModel
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
                            IconButton(onClick = { chatViewModel.refreshChats() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Làm mới")
                            }
                            IconButton(onClick = {
                                FirebaseAuth.getInstance().signOut()
                                GoogleSignIn.getClient(
                                    context,
                                    GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).build()
                                ).signOut().addOnCompleteListener {
                                    navController.navigate("login") {
                                        popUpTo("chat") { inclusive = true }
                                    }
                                }
                            }) {
                                Icon(Icons.Filled.ExitToApp, contentDescription = "Đăng xuất")
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
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = paddingValues.calculateTopPadding())
                ) {
                    // Nội dung chính của ChatScreen
                    Column(
                        modifier = Modifier
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            state = listState,
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
                                        }
                                    )
                                } else {
                                    ModelChatItem(
                                        //response = parseFormattedText(chat.prompt),
                                        response = chat.prompt,
                                        isError = chat.isError,
                                        onLongPress = { textToCopy ->
                                            scope.launch {
                                                clipboardManager.setText(AnnotatedString(textToCopy))
                                                snackbarHostState.showSnackbar("Đã sao chép tin nhắn")
                                            }
                                        }
                                    )
                                }
                            }
                        }

                        if (chatState.isLoading) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp, start = 4.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {

                            Column {
                                chatState.imageUri?.let { uri ->
                                    Image(
                                        painter = rememberAsyncImagePainter(
                                            model = ImageRequest.Builder(context)
                                                .data(uri)
                                                .size(coil.size.Size.ORIGINAL)
                                                .crossfade(true)
                                                .build()
                                        ),
                                        contentDescription = "Hình ảnh đã chọn",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .size(40.dp)
                                            .padding(bottom = 1.dp)
                                            .clickable {
                                                isImageDialogOpen = true
                                            }
                                    )
                                }

                                Icon(
                                    modifier = Modifier
                                        .size(40.dp)
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
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            TextField(
                                modifier = Modifier
                                    .weight(1f),
                                value = chatState.prompt,
                                onValueChange = {
                                    chatViewModel.onEvent(ChatUiEvent.UpdatePrompt(it))
                                    if (it.isNotEmpty()) {
                                        showWelcomeMessage = false
                                    }
                                },
                                placeholder = {
                                    Text(text = "Nhập tin nhắn")
                                }
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            Icon(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clickable {
                                        if (chatState.prompt.isNotEmpty()) {
                                            chatViewModel.onEvent(
                                                ChatUiEvent.SendPrompt(
                                                    chatState.prompt,
                                                    chatState.imageUri
                                                )
                                            )
                                            showWelcomeMessage = false
                                        }
                                    },
                                imageVector = Icons.Rounded.Send,
                                contentDescription = "Gửi tin nhắn",
                                tint = MaterialTheme.colorScheme.primary
                            )
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

                    // Hiển thị Dialog cho hình ảnh trong khu vực nhập liệu
                    if (isImageDialogOpen && chatState.imageUri != null) {
                        ImageDialog(
                            imageUri = chatState.imageUri!!,
                            onDismiss = { isImageDialogOpen = false },
                            onDelete = {
                                chatViewModel.onEvent(ChatUiEvent.OnImageSelected(null))
                                isImageDialogOpen = false
                            }
                        )
                    }
                }
            }
        }
    }


    @Composable
    fun UserChatItem(prompt: String, imageUrl: String?, onLongPress: (String) -> Unit) {
        var isImageDialogOpen by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier
                .padding(start = 100.dp, bottom = 16.dp)
        ) {
            imageUrl?.let { url ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp)
                        .clickable {
                            isImageDialogOpen = true
                        }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(url)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Hình ảnh của bạn",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, Color.Transparent, RoundedCornerShape(12.dp))
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onLongPress = {
                                        onLongPress(prompt)
                                    }
                                )
                            }
                    )
                }
            }

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(16.dp)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                onLongPress(prompt)
                            }
                        )
                    },
                text = prompt,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onPrimary
            )

            // Hiển thị Dialog khi hình ảnh được nhấp
            if (isImageDialogOpen && imageUrl != null) {
                ImageUrlDialog(
                    imageUrl = imageUrl,
                    onDismiss = { isImageDialogOpen = false }
                )
            }
        }
    }




    /**
     * Composable hiển thị phản hồi từ bot
     */
    @Composable
    fun ModelChatItem(response: String, isError: Boolean, onLongPress: (String) -> Unit) {
        val formattedResponse = parseFormattedText(response)
        val backgroundColor = if (isError) MaterialTheme.colorScheme.error else Color(0xFF4CAF50) // Green

        Column(
            modifier = Modifier
                .padding(end = 100.dp, bottom = 16.dp)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onLongPress = {
                            onLongPress(formattedResponse.text)
                        }
                    )
                }
        ) {
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
     * Composable hiển thị Dialog khi hình ảnh được chọn từ thư viện
     */
    @Composable
    fun ImageDialog(
        imageUri: Uri,
        onDismiss: () -> Unit,
        onDelete: () -> Unit
    ) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 8.dp,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    // Nút đóng Dialog
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Đóng",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    // Hiển thị hình ảnh phóng to
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Hình ảnh phóng to",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .align(Alignment.CenterHorizontally)
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Nút xóa hình ảnh
                    Button(
                        onClick = onDelete,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(text = "Xóa", color = Color.White)
                    }
                }
            }
        }
    }

    /**
     * Composable hiển thị Dialog khi hình ảnh được nhấp vào tin nhắn
     */
    @Composable
    fun ImageUrlDialog(
        imageUrl: String,
        onDismiss: () -> Unit
    ) {
        Dialog(onDismissRequest = onDismiss) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 8.dp,
                modifier = Modifier.padding(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    // Nút đóng Dialog
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Đóng",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Hiển thị hình ảnh phóng to
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Hình ảnh phóng to",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .align(Alignment.CenterHorizontally)
                    )
                }
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
        // Các cặp ký tự định dạng và kiểu SpanStyle tương ứng
        val patterns = listOf(
            "**" to SpanStyle(fontWeight = FontWeight.Bold),
            "*" to SpanStyle(fontStyle = FontStyle.Italic),
            "__" to SpanStyle(textDecoration = TextDecoration.Underline)
        )

        val builder = AnnotatedString.Builder()
        val lines = input.lines()

        // Biến để theo dõi số thứ tự của các mục danh sách cấp độ 1
        var listItemNumber = 1

        for ((index, line) in lines.withIndex()) {
            var processedLine = line

            // Kiểm tra xem dòng hiện tại có phải là mục danh sách không
            val listMatch = """^(\s*)\*\s+(.*)""".toRegex().find(line)
            if (listMatch != null) {
                val leadingSpaces = listMatch.groupValues[1].length
                val content = listMatch.groupValues[2]
                val level = (leadingSpaces / 4) + 1 // Giả định 4 khoảng trắng cho mỗi cấp độ lồng nhau

                // Xác định ký hiệu danh sách dựa trên cấp độ
                val marker = when (level) {
                    1 -> "${listItemNumber++}. " // Danh sách cấp độ 1 sử dụng số thứ tự
                    2 -> "• " // Danh sách cấp độ 2 sử dụng dấu chấm đầu dòng
                    3 -> "◦ " // Danh sách cấp độ 3 sử dụng dấu tròn nhỏ hơn
                    else -> "• " // Các cấp độ sâu hơn cũng sử dụng dấu chấm đầu dòng
                }

                // Tạo chuỗi khoảng trắng dựa trên cấp độ để giữ nguyên cấu trúc lồng nhau
                val indentation = "    ".repeat(level - 1)

                // Thay thế dấu * bằng ký hiệu danh sách tương ứng
                processedLine = "$indentation$marker$content"
            }

            // Kiểm tra xem dòng hiện tại có phải là tiêu đề không (dòng có toàn bộ văn bản được bôi đậm)
            val isHeading = """^\*\*(.+)\*\*:$""".toRegex().matches(line.trim())

            if (isHeading) {
                val headingText = """^\*\*(.+)\*\*:$""".toRegex().find(line.trim())?.groupValues?.get(1) ?: line
                builder.withStyle(patterns[0].second) { // Apply Bold style
                    builder.append("$headingText:\n\n")
                }
                continue
            }

            // Xử lý định dạng trong dòng
            var remainingText = processedLine
            while (remainingText.isNotEmpty()) {
                var matched = false
                for ((delimiter, style) in patterns) {
                    // Tạo pattern để tìm các định dạng: **bold**, *italic*, __underline__
                    val pattern = """\Q$delimiter\E(.*?)\Q$delimiter\E""".toRegex()
                    val match = pattern.find(remainingText)
                    if (match != null) {
                        val start = match.range.first
                        if (start > 0) {
                            // Thêm phần text trước pattern nếu có
                            builder.append(remainingText.substring(0, start))
                        }
                        val formattedText = match.groupValues[1]
                        // Áp dụng style cho đoạn text
                        builder.withStyle(style) {
                            append(formattedText)
                        }
                        // Cập nhật remainingText để tiếp tục xử lý phần còn lại
                        remainingText = remainingText.substring(match.range.last + 1)
                        matched = true
                        break
                    }
                }
                if (!matched) {
                    // Nếu không tìm thấy pattern nào, thêm phần còn lại và kết thúc vòng lặp
                    builder.append(remainingText)
                    break
                }
            }

            // Thêm xuống dòng nếu không phải là dòng cuối cùng
            if (index != lines.lastIndex) {
                builder.append("\n")
            }
        }

        return builder.toAnnotatedString()
    }
}
