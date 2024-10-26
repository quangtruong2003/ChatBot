// MainActivity.kt
package com.ahmedapps.geminichatbot

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment // Thêm import này

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
                        verticalAlignment = Alignment.CenterVertically // Sử dụng Alignment từ Compose
                    ) {

                        Column {
                            chatState.imageUri?.let { uri ->
                                Image(
                                    painter = rememberAsyncImagePainter(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(uri)
                                            .size(coil.size.Size.ORIGINAL) // Sử dụng ImageRequest để đặt size
                                            .build()
                                    ),
                                    contentDescription = "Hình ảnh đã chọn",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(40.dp)
                                        .padding(bottom = 2.dp)
                                        .clip(RoundedCornerShape(6.dp))
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
            }
        }
    }

    @Composable
    fun UserChatItem(prompt: String, imageUrl: String?) {
        Column(
            modifier = Modifier.padding(start = 100.dp, bottom = 16.dp)
        ) {
            imageUrl?.let { url ->
                Image(
                    painter = rememberAsyncImagePainter(
                        model = url
                    ),
                    contentDescription = "Hình ảnh của bạn",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .padding(bottom = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
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
        }
    }

    @Composable
    fun ModelChatItem(response: String, isError: Boolean) {
        val backgroundColor = if (isError) MaterialTheme.colorScheme.error else Green
        Column(
            modifier = Modifier.padding(end = 100.dp, bottom = 16.dp)
        ) {
            Text(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(backgroundColor)
                    .padding(16.dp),
                text = response,
                fontSize = 17.sp,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}
