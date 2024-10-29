// MainActivity.kt
package com.ahmedapps.geminichatbot

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val imagePicker =
        registerForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            uri?.let {
                chatViewModel.onEvent(ChatUiEvent.OnImageSelected(it))
            }
        }

    private lateinit var chatViewModel: ChatViewModel

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
                        LoginScreen(onLoginSuccess = {
                            navController.navigate("chat") {
                                popUpTo("login") { inclusive = true }
                            }
                        })
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
    fun ChatScreen(navController: NavController) {
        val chatViewModel: ChatViewModel = hiltViewModel()
        this.chatViewModel = chatViewModel
        val chatState by chatViewModel.chatState.collectAsState()

        var showWelcomeMessage by remember { mutableStateOf(true) }

        // State để quản lý Dialog hiển thị hình ảnh trong khu vực nhập liệu
        var isImageDialogOpen by remember { mutableStateOf(false) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(stringResource(id = R.string.app_name))
                    },
                    actions = {
                        IconButton(onClick = { chatViewModel.clearChat() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Làm mới")
                        }
                        IconButton(onClick = {
                            FirebaseAuth.getInstance().signOut()
                            navController.navigate("login") {
                                popUpTo("chat") { inclusive = true }
                            }
                        }) {
                            Icon(Icons.Filled.ExitToApp, contentDescription = "Đăng xuất")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding())
            ) {
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
                        reverseLayout = true
                    ) {
                        items(chatState.chatList) { chat ->
                            if (chat.isFromUser) {
                                UserChatItem(
                                    prompt = chat.prompt,
                                    imageUrl = chat.imageUrl
                                )
                            } else {
                                ModelChatItem(response = chat.prompt, isError = chat.isError)
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
                                        model = ImageRequest.Builder(LocalContext.current)
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
                                    chatViewModel.onEvent(
                                        ChatUiEvent.SendPrompt(
                                            chatState.prompt,
                                            chatState.imageUri
                                        )
                                    )
                                    showWelcomeMessage = false
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
                    chatState.imageUri?.let { uri ->
                        ImageDialog(
                            imageUri = uri,
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
    fun UserChatItem(prompt: String, imageUrl: String?) {

        var isImageDialogOpen by remember { mutableStateOf(false) }

        Column(
            modifier = Modifier.padding(start = 100.dp, bottom = 16.dp)
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
                    )
                }
            }

            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(16.dp),
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





    @Composable
    fun ModelChatItem(response: String, isError: Boolean) {
        val backgroundColor = if (isError) MaterialTheme.colorScheme.error else Green
        val annotatedResponse = parseFormattedText(response)

        Column(
            modifier = Modifier.padding(end = 100.dp, bottom = 16.dp)
        ) {
            Text(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .fillMaxWidth()

                    .background(backgroundColor)
                    .padding(16.dp),
                text = annotatedResponse,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
    fun parseBoldText(input: String): AnnotatedString {
        val boldPattern = "\\*\\*(.*?)\\*\\*".toRegex()
        val matches = boldPattern.findAll(input)

        val builder = AnnotatedString.Builder()
        var lastIndex = 0

        for (match in matches) {
            val range = match.range
            val textBefore = input.substring(lastIndex, range.first)
            if (textBefore.isNotEmpty()) {
                builder.append(textBefore)
            }

            val boldText = match.groupValues[1]
            builder.withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                append(boldText)
            }
            lastIndex = range.last + 1
        }

        val remainingText = input.substring(lastIndex)
        if (remainingText.isNotEmpty()) {
            builder.append(remainingText)
        }

        return builder.toAnnotatedString()
    }
    fun parseFormattedText(input: String): AnnotatedString {
        val patterns = listOf(
            "**" to SpanStyle(fontWeight = FontWeight.Bold),
            "*" to SpanStyle(fontStyle = FontStyle.Italic),
            "__" to SpanStyle(textDecoration = TextDecoration.Underline)
        )

        val builder = AnnotatedString.Builder()
        var remainingText = input

        while (remainingText.isNotEmpty()) {
            var matched = false
            for ((delimiter, style) in patterns) {
                val pattern = Regex.escape(delimiter) + "(.*?)" + Regex.escape(delimiter)
                val regex = pattern.toRegex()
                val match = regex.find(remainingText)
                if (match != null) {
                    val textBefore = remainingText.substring(0, match.range.first)
                    if (textBefore.isNotEmpty()) {
                        builder.append(textBefore)
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

        return builder.toAnnotatedString()
    }

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



}