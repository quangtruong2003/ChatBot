// MainActivity.kt
package com.ahmedapps.geminichatbot

import android.graphics.Bitmap
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size
import com.ahmedapps.geminichatbot.data.Chat
import com.ahmedapps.geminichatbot.ui.theme.GeminiChatBotTheme
import com.ahmedapps.geminichatbot.ui.theme.Green
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update


@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val uriState = MutableStateFlow("")

    private val imagePicker =
        registerForActivityResult<PickVisualMediaRequest, Uri?>(
            ActivityResultContracts.PickVisualMedia()
        ) { uri ->
            uri?.let {
                uriState.update { uri.toString() }
            }
        }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GeminiChatBotTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {

                    Scaffold(

                    ) {
                        ChatScreen(paddingValues = it)
                    }

                }
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun ChatScreen(paddingValues: PaddingValues) {
        val chatViewModel: ChatViewModel = hiltViewModel()
        val chatState by chatViewModel.chatState.collectAsState()

        var showWelcomeMessage by remember { mutableStateOf(true) }

        val bitmap = getBitmap()

        Scaffold(
            //modifier= Modifier.systemBarsPadding(),
            //or
            //android:windowSoftInputMode="adjustResize"

            topBar = {
                CenterAlignedTopAppBar(

                    title = {
                        Text(
                            text = stringResource(id = R.string.app_name),
                            style = MaterialTheme.typography.titleLarge
                        )
                    },
                    actions = {
                        IconButton(onClick = { chatViewModel.clearChat() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(

                    )
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
                                    prompt = chat.prompt, bitmap = chat.bitmap
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
                            bitmap?.let {
                                Image(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .padding(bottom = 2.dp)
                                        .clip(RoundedCornerShape(6.dp)),
                                    contentDescription = "Chọn ảnh",
                                    contentScale = ContentScale.Crop,
                                    bitmap = it.asImageBitmap()
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
                                            bitmap
                                        )
                                    )
                                    uriState.update { "" }
                                    showWelcomeMessage = false
                                },
                            imageVector = Icons.Rounded.Send,
                            contentDescription = "Gửi form",
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
    fun UserChatItem(prompt: String, bitmap: Bitmap?) {
        Column(
            modifier = Modifier.padding(start = 100.dp, bottom = 16.dp)
        ) {

            bitmap?.let {
                Image(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .padding(bottom = 2.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentDescription = "image",
                    contentScale = ContentScale.Crop,
                    bitmap = it.asImageBitmap()
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

    @Composable
    private fun getBitmap(): Bitmap? {
        val uri = uriState.collectAsState().value

        val imageState = rememberAsyncImagePainter(
            model = ImageRequest.Builder(LocalContext.current)
                .data(uri)
                .size(Size.ORIGINAL)
                .build()
        ).state

        if (imageState is AsyncImagePainter.State.Success) {
            return imageState.result.drawable.toBitmap()
        }

        return null
    }
}
