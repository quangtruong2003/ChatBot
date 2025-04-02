// ChatScreen.kt
package com.ahmedapps.geminichatbot

import android.Manifest
import android.content.ClipDescription
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Base64
import android.view.KeyEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Send
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
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
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.window.PopupProperties
import com.ahmedapps.geminichatbot.services.PDFProcessingService
import com.ahmedapps.geminichatbot.UserChatItem
import android.content.Context
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.isCtrlPressed
import android.util.Log
import java.io.FileOutputStream
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.ScrollState
import androidx.compose.ui.graphics.SolidColor
import kotlinx.coroutines.CoroutineScope
import androidx.appcompat.widget.AppCompatEditText
import android.view.ViewGroup
import android.view.Gravity
import android.text.InputType
import android.text.TextWatcher
import android.text.Editable
import android.view.View
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.toArgb
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.platform.LocalDensity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout


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
    val context = LocalContext.current
    
    // Tạo một biến để lưu Uri tạm thời cho ảnh chụp
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    
    // Hàm tạo URI tạm thời cho ảnh
    fun createImageUriInner(): Uri? {
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
    
    // Đăng ký launcher cho máy ảnh
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture(),
        onResult = { success ->
            if (success && photoUri != null) {
                chatViewModel.onEvent(ChatUiEvent.OnImageSelected(photoUri!!))
            }
        }
    )
    
    // Hàm cấp cao hơn để xử lý chụp ảnh
    fun handleTakePicture() {
        val uri = createImageUriInner()
        if (uri != null) {
            photoUri = uri
            takePictureLauncher.launch(uri)
        }
        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
    
    // Hàm cấp cao hơn để mở ảnh toàn màn hình
    fun navigateToFullScreenImage(uri: Uri) {
        val encodedUrl = Base64.encodeToString(uri.toString().toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
        navController.navigate("fullscreen_image/$encodedUrl")
    }

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

    val isTextNotEmpty = chatState.prompt.isNotEmpty() || chatState.imageUri != null || chatState.fileUri != null

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
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri: Uri? ->
            uri?.let {
                chatViewModel.onEvent(ChatUiEvent.OnImageSelected(it))
            }
        }
    )

    // Hàm tạo URI từ Bitmap
    fun createImageUriFromBitmap(context: Context, bitmap: Bitmap): Uri? {
        try {
            // Tạo tệp tạm thời để lưu bitmap
            val timeStamp = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault()).format(java.util.Date())
            val imageFile = File(
                context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
                "IMG_${timeStamp}.jpg"
            )
            
            // Ghi bitmap vào file
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }
            
            // Tạo URI từ file sử dụng FileProvider
            return FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                imageFile
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    // Comment hàm này lại để tránh xung đột với hàm cùng tên trong SideDrawer.kt
    /*
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
    */

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

    // Cập nhật launcher cho document picker để loại trừ file ảnh
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val mimeType = context.contentResolver.getType(it)
            // Kiểm tra xem có phải file ảnh không
            if (mimeType?.startsWith("image/") == true) {
                // Hiển thị thông báo lỗi nếu là file ảnh
                scope.launch {
                    snackbarHostState.showSnackbar("Không hỗ trợ upload file ảnh qua tính năng này.")
                }
            } else {
                // Nếu không phải file ảnh, xử lý file
                chatViewModel.onEvent(ChatUiEvent.OnFileSelected(it))
            }
        }
    }

    // Định nghĩa launcher để chọn file
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            chatViewModel.onEvent(ChatUiEvent.OnFileSelected(it))
        }
    }

    // Hàm xử lý paste từ clipboard
    fun handlePasteFromClipboard() {
        // Lấy ClipboardManager từ context
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        
        try {
            // Khởi tạo biến để theo dõi việc xử lý đã thành công chưa
            var imageProcessed = false
            
            // Trường hợp 1: Kiểm tra xem clipboard có chứa hình ảnh không
            if (clipboardManager.hasPrimaryClip()) {
                val primaryClip = clipboardManager.primaryClip
                
                if (primaryClip != null) {
                    val description = primaryClip.description
                    
                    // Ghi log để debug
                    Log.d("ChatScreen", "Clipboard MIME types: ${description.mimeTypeCount} types")
                    for (i in 0 until description.mimeTypeCount) {
                        Log.d("ChatScreen", "MIME type ${i}: ${description.getMimeType(i)}")
                    }
                    
                    // Trường hợp A: Direct URI khi description có MIMETYPE_TEXT_URILIST
                    if (description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)) {
                        val item = primaryClip.getItemAt(0)
                        val uri = item.uri
                        
                        if (uri != null) {
                            try {
                                // Kiểm tra xem URI có phải là hình ảnh không
                                val mimeType = context.contentResolver.getType(uri)
                                if (mimeType != null && mimeType.startsWith("image/")) {
                                    chatViewModel.onEvent(ChatUiEvent.OnImageSelected(uri))
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Đã dán ảnh từ clipboard")
                                    }
                                    imageProcessed = true
                                    return
                                } else {
                                    Log.d("ChatScreen", "URI không phải là hình ảnh: $mimeType")
                                }
                            } catch (e: Exception) {
                                Log.e("ChatScreen", "Lỗi khi kiểm tra URI: ${e.message}")
                            }
                        }
                    }
                    
                    // Trường hợp B: Image MIME Types
                    val imageMimeTypes = arrayOf(
                        "image/*", "image/png", "image/jpeg", "image/gif", "image/webp",
                        "image/", // Một số thiết bị có thể dùng prefix này
                        "application/octet-stream" // Đôi khi ảnh được lưu dưới dạng này
                    )
                    
                    for (mimeType in imageMimeTypes) {
                        if (description.hasMimeType(mimeType)) {
                            Log.d("ChatScreen", "Tìm thấy MIME type ảnh: $mimeType")
                            val item = primaryClip.getItemAt(0)
                            
                            try {
                                // Với Android 12+, có thể lấy URI trực tiếp
                                val uri = item.uri
                                
                                if (uri != null) {
                                    Log.d("ChatScreen", "Lấy được URI ảnh: $uri")
                                    chatViewModel.onEvent(ChatUiEvent.OnImageSelected(uri))
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Đã dán ảnh từ clipboard")
                                    }
                                    imageProcessed = true
                                    return
                                }
                            } catch (e: Exception) {
                                Log.e("ChatScreen", "Lỗi khi lấy URI: ${e.message}")
                            }
                        }
                    }

                    // Trường hợp C: Các định dạng đặc biệt
                    try {
                        for (i in 0 until primaryClip.itemCount) {
                            val item = primaryClip.getItemAt(i)
                            
                            // Kiểm tra MIME type đặc biệt
                            if (item.uri != null && !imageProcessed) {
                                val uri = item.uri
                                try {
                                    val mimeType = context.contentResolver.getType(uri)
                                    if (mimeType != null && mimeType.startsWith("image/")) {
                                        chatViewModel.onEvent(ChatUiEvent.OnImageSelected(uri))
                                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Đã dán ảnh từ clipboard")
                                        }
                                        imageProcessed = true
                                        return
                                    }
                                } catch (e: Exception) {
                                    Log.e("ChatScreen", "Lỗi khi kiểm tra MIME của URI: ${e.message}")
                                }
                            }
                            
                            // Kiểm tra nếu có text là đường dẫn
                            if (item.text != null && !imageProcessed) {
                                val text = item.text.toString()
                                
                                // Kiểm tra xem text có phải là URI hình ảnh không
                                if (text.startsWith("content://") || text.startsWith("file://")) {
                                    try {
                                        val uri = Uri.parse(text)
                                        val mimeType = context.contentResolver.getType(uri)
                                        if (mimeType != null && mimeType.startsWith("image/")) {
                                            chatViewModel.onEvent(ChatUiEvent.OnImageSelected(uri))
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Đã dán ảnh từ đường dẫn")
                                            }
                                            imageProcessed = true
                                            return
                                        }
                                    } catch (e: Exception) {
                                        Log.e("ChatScreen", "Lỗi khi xử lý text URI: ${e.message}")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ChatScreen", "Lỗi khi duyệt các item: ${e.message}")
                    }
                    
                    // Trường hợp D: Ngăn chặn dán HTML image
                    if (description.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) && !imageProcessed) {
                        val htmlText = primaryClip.getItemAt(0).htmlText
                        if (htmlText?.contains("<img") == true) {
                            scope.launch {
                                snackbarHostState.showSnackbar("Không thể dán hình ảnh từ HTML. Hãy lưu ảnh và chọn từ thư viện.")
                            }
                            imageProcessed = true
                            return
                        }
                    }
                }
            }
            
            // Không tìm thấy hình ảnh để dán, có thể đây là dán text thông thường
            if (!imageProcessed) {
                Log.d("ChatScreen", "Không phải nội dung hình ảnh, xử lý như dán văn bản thông thường")
                // Không cần hiển thị thông báo lỗi vì có thể người dùng đang muốn dán text
            }
        } catch (e: Exception) {
            // Xử lý lỗi tổng thể nếu có
            Log.e("ChatScreen", "Paste error: ${e.message}", e)
            scope.launch {
                snackbarHostState.showSnackbar("Không thể dán: ${e.message}")
            }
        }
    }

    // Hàm để theo dõi sự kiện dán vào TextField
    fun setupClipboardListener() {
        val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        
        // Thêm listener để theo dõi sự kiện clipboard thay đổi
        clipboardManager.addPrimaryClipChangedListener {
            if (clipboardManager.hasPrimaryClip() && clipboardManager.primaryClip != null) {
                val description = clipboardManager.primaryClip?.description
                
                // Log ra để debug
                Log.d("ClipboardListener", "Clipboard changed. MIME types: ${description?.mimeTypeCount}")
                
                // Nếu có MIME type là hình ảnh, xử lý như dán ảnh
                val hasImageType = (0 until (description?.mimeTypeCount ?: 0)).any { i ->
                    val mimeType = description?.getMimeType(i) ?: ""
                    mimeType.startsWith("image/") || mimeType == "image/*"
                }
                
                if (hasImageType) {
                    // Xử lý như dán ảnh bằng hàm đã có
                    handlePasteFromClipboard()
                }
            }
        }
    }
    
    // Gọi hàm thiết lập listener
    LaunchedEffect(Unit) {
        setupClipboardListener()
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
                            //painter = painterResource(id = R.drawable.ic_download),
                            imageVector = Icons.Filled.ArrowDownward,
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
                                    imageUrl = chat.imageUrl,
                                    isError = chat.isError,
                                    isFileMessage = chat.isFileMessage,
                                    fileName = chat.fileName,
                                    onLongPress = { message ->
                                        scope.launch {
                                            val plainText = parseFormattedText(message).text
                                            clipboardManager.setText(AnnotatedString(plainText))
                                            snackbarHostState.showSnackbar("Đã sao chép tin nhắn")
                                        }
                                    },
                                    onImageClick = { url ->
                                        val encodedUrl = Base64.encodeToString(url.toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
                                        navController.navigate("fullscreen_image/$encodedUrl")
                                    },
                                    snackbarHostState = snackbarHostState
                                )
                            } else {
                                // Cần kiểm tra xem tin nhắn đã được hiển thị hiệu ứng typing chưa
                                val isMessageAlreadyTyped = chatViewModel.isMessageTyped(chat.id)
                                val shouldShowTypingEffect = !isMessageAlreadyTyped
                                
                                // Tìm prompt của user trước response này để sử dụng cho regenerate
                                val userPromptForThisResponse = if (!chat.isFromUser) {
                                    val chatList = chatState.chatList
                                    val chatIndex = chatList.indexOf(chat)
                                    if (chatIndex > 0 && chatList[chatIndex - 1].isFromUser) {
                                        chatList[chatIndex - 1].prompt
                                    } else ""
                                } else ""
                                
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
                                    isMessageTyped = isMessageAlreadyTyped,
                                    onDeleteClick = { chatId ->
                                        chatViewModel.onEvent(ChatUiEvent.DeleteChat(chatId))
                                    },
                                    onRegenerateClick = { prompt, responseId ->
                                        chatViewModel.onEvent(ChatUiEvent.RegenerateResponse(prompt, responseId))
                                    },
                                    currentUserPrompt = userPromptForThisResponse,
                                    availableModels = chatViewModel.availableModels,
                                    modelDisplayNameMap = chatViewModel.modelDisplayNameMap,
                                    modelIconMap = chatViewModel.modelIconMap,
                                    selectedModel = selectedModel
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

                            // Thêm phần hiển thị file vào đây
                            chatState.fileUri?.let { uri ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth(0.5f)
                                        .padding(vertical = 4.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Icon file
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .padding(start = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(
                                                id = if (chatState.isFileUploading) R.drawable.ic_fileuploaderror else R.drawable.ic_fileuploaded
                                            ),
                                            contentDescription = "File đã chọn",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    
                                    // Tên file nằm bên phải icon
                                    Text(
                                        text = chatState.fileName ?: "File không xác định",
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(horizontal = 8.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    
                                    // Nút 'X' để xóa file (Đã sửa lại)
                                    Box(
                                        modifier = Modifier
                                            .padding(end = 8.dp)
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFAAAAAA))
                                            .clickable {
                                                chatViewModel.onEvent(ChatUiEvent.RemoveFile)
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Xóa file",
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
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
                            
                            // CustomTextField với các tham số cần thiết
                            CustomTextField(
                                chatViewModel = chatViewModel,
                                chatState = chatState,
                                focusRequester = focusRequester,
                                scope = scope,
                                snackbarHostState = snackbarHostState,
                                imagePicker = imagePicker,
                                documentPickerLauncher = documentPickerLauncher,
                                photoUri = photoUri,
                                takePictureLauncher = takePictureLauncher,
                                navController = navController,
                                createImageUriInner = { createImageUriInner() },
                                onPhotoUriChange = { newUri -> 
                                    photoUri = newUri 
                                },
                                onImageReceived = { uri -> 
                                    chatViewModel.onEvent(ChatUiEvent.OnImageSelected(uri))
                                }
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
                                                text = "Gửi tệp tài liệu",
                                                style = TextStyle(
                                                    color = textColor,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            )
                                        },
                                        onClick = {
                                            // Tạm thời quay lại dùng "*/*" để kiểm tra
                                            documentPickerLauncher.launch("*/*")
                                            showSourceMenu = false
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        },
                                        trailingIcon = {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_chosefile),
                                                contentDescription = "Gửi tệp tài liệu",
                                                tint = textColor
                                            )
                                        }
                                    )
                                    Divider(color = Color(0x14FFFFFF), thickness = 0.6.dp)
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
                                            photoUri = createImageUriInner()
                                            photoUri?.let {
                                                takePictureLauncher.launch(it)
                                            }
                                            showSourceMenu = false
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
                                    // Bỏ hiệu ứng nhấp nháy
                                    val isDarkTheme = isSystemInDarkTheme()
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
                                        painter = painterResource(id = if (isDarkTheme) R.drawable.ic_stopresponse_dark else R.drawable.ic_stopresponse_light),
                                        contentDescription = "Dừng",
                                        tint = Color.Unspecified
                                    )
                                } else {
                                    IconButton(
                                        onClick = {
                                            if (isTextNotEmpty) {
                                                val sanitizedPrompt = sanitizeMessage(chatState.prompt)
                                                chatViewModel.onEvent(
                                                    ChatUiEvent.SendPrompt(
                                                        sanitizedPrompt,
                                                        chatState.imageUri
                                                    )
                                                )
                                                // Thêm phản hồi rung khi gửi tin nhắn
                                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        },
                                        modifier = Modifier
                                            .padding(bottom = 8.dp)
                                            .size(48.dp),
                                        enabled = isTextNotEmpty && !chatState.isLoading
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_send),
                                            contentDescription = "Send Message",
                                            tint = if (isTextNotEmpty && !chatState.isLoading) textColor else textColor.copy(alpha = 0.4f),
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
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

                // Thêm hiệu ứng loading khi đang xử lý file PDF
                if (chatViewModel.isProcessingFile.value) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Đang xử lý file PDF...",
                                color = Color.White
                            )
                        }
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

@Composable
fun CustomTextField(
    chatViewModel: ChatViewModel,
    chatState: ChatState,
    focusRequester: FocusRequester,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    imagePicker: ManagedActivityResultLauncher<PickVisualMediaRequest, Uri?>,
    documentPickerLauncher: ManagedActivityResultLauncher<String, Uri?>,
    photoUri: Uri?,
    takePictureLauncher: ManagedActivityResultLauncher<Uri, Boolean>,
    navController: NavController,
    createImageUriInner: () -> Uri?,
    onPhotoUriChange: (Uri?) -> Unit,
    onImageReceived: (Uri) -> Unit
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current
    val density = LocalDensity.current
    val isDarkTheme = isSystemInDarkTheme()
    val textColor = if (isDarkTheme) Color.White else Color.Black
    val focusManager = LocalFocusManager.current

    // Trạng thái cho việc hiển thị BottomSheet mở rộng
    var showExpandedInputSheet by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .wrapContentHeight(align = Alignment.Top)
            .heightIn(min = 40.dp, max = 160.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        AndroidView(
            factory = { ctx ->
                AppCompatEditText(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                    minHeight = with(density) { 40.dp.toPx() }.toInt()
                    maxLines = 8
                    setPadding(
                        with(density) { 16.dp.toPx() }.toInt(),
                        with(density) { 5.dp.toPx() }.toInt(),
                        with(density) { 36.dp.toPx() }.toInt(),
                        with(density) { 5.dp.toPx() }.toInt()
                    )
                    gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                    isVerticalScrollBarEnabled = true
                    setTextIsSelectable(true)
                    isFocusable = true
                    isFocusableInTouchMode = true
                    background = null
                    setHintTextColor(Color.Gray.toArgb())
                    setTextColor(textColor.toArgb())
                    hint = "Nhập tin nhắn cho ChatAI"
                    textSize = 16f

                    val mimeTypes = arrayOf("image/*")
                    ViewCompat.setOnReceiveContentListener(this, mimeTypes) { _, payload ->
                        val split = payload.partition { item -> item.uri != null }
                        val uriContent = split.first
                        val remaining = split.second

                        if (uriContent != null) {
                            val clip = uriContent.clip
                            for (i in 0 until clip.itemCount) {
                                val uri = clip.getItemAt(i).uri
                                if (uri != null) {
                                    chatViewModel.onEvent(ChatUiEvent.OnImageSelected(uri))
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Đã dán ảnh")
                                    }
                                    return@setOnReceiveContentListener null
                                }
                            }
                        }
                        remaining
                    }
                    
                    addTextChangedListener(object : TextWatcher {
                        @Volatile private var isUpdating = false
                        
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: Editable?) {
                            if (!isUpdating) {
                                val newText = s?.toString() ?: ""
                                if (chatState.prompt != newText) {
                                    isUpdating = true
                                    chatViewModel.onEvent(ChatUiEvent.UpdatePrompt(newText))
                                    post { isUpdating = false }
                                }
                            }
                        }
                    })
                    
                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_V && event.isCtrlPressed) {
                            val clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            if (clipboardManager.hasPrimaryClip()) {
                                val description = clipboardManager.primaryClip?.description
                                if (description != null) {
                                    val hasImageType = (0 until description.mimeTypeCount).any { i ->
                                        val mimeType = description.getMimeType(i)
                                        mimeType.startsWith("image/") || mimeType == "image/*"
                                    }
                                    
                                    if (hasImageType) {
                                        handleImageInClipboardForView(clipboardManager) { uri ->
                                            chatViewModel.onEvent(ChatUiEvent.OnImageSelected(uri))
                                            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Đã dán ảnh")
                                            }
                                        }
                                        return@setOnKeyListener true
                                    }
                                }
                            }
                        }
                        false
                    }
                }
            },
            update = { view ->
                // Luôn cập nhật màu chữ dựa trên theme hiện tại
                view.setTextColor(textColor.toArgb())

                // Nếu nội dung trong EditText khác với state
                if (view.text.toString() != chatState.prompt) {
                    // Cập nhật EditText để phản ánh state
                    view.setText(chatState.prompt)
                    // Chỉ đặt lại con trỏ về cuối nếu EditText không có focus
                    // để tránh xung đột với vị trí con trỏ do người dùng đặt.
                    if (!view.hasFocus()) {
                        view.setSelection(chatState.prompt.length)
                    }
                }
                // Optional: Nếu text giống nhau nhưng selection sai khi không focus? (Tạm thời bỏ qua)
            },
            modifier = Modifier.fillMaxWidth()
                               .focusRequester(focusRequester)
                               .onFocusChanged { focusState ->
                                   Log.d("FocusDebug", "CustomTextField EditText focus changed: ${focusState.isFocused}")
                               }
        )
        
        IconButton(
            onClick = {
                // 1. Xóa focus khỏi EditText chính
                focusManager.clearFocus()
                // 2. Mở BottomSheet (trong coroutine để đảm bảo focus đã được xử lý)
                scope.launch {
                    // delay(1) // Có thể thêm delay cực nhỏ nếu cần
                    showExpandedInputSheet = true
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 5.dp, end = 4.dp)
                .size(28.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_extendchatinput),
                contentDescription = "Mở rộng khung nhập tin nhắn",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
    
    if (showExpandedInputSheet) {
        val haptic = LocalHapticFeedback.current
        val navCtrl = navController
        val takePictureLaunch = takePictureLauncher
        val createImgUri = createImageUriInner
        val onPhotoChange = onPhotoUriChange

        ExpandedChatInputBottomSheet(
            onDismiss = { showExpandedInputSheet = false },
            messageText = chatState.prompt, // Luôn đọc từ state
            onMessageTextChange = { newText ->
                chatViewModel.onEvent(ChatUiEvent.UpdatePrompt(newText))
            },
            onSendMessage = {
                if (chatState.prompt.isNotBlank() || chatState.imageUri != null || chatState.fileUri != null) {
                    val sanitizedPrompt = sanitizeMessage(chatState.prompt)
                    chatViewModel.onEvent(
                        ChatUiEvent.SendPrompt(
                            sanitizedPrompt,
                            chatState.imageUri
                        )
                    )
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showExpandedInputSheet = false
                }
            },
            onAttachImage = {
                imagePicker.launch(
                    PickVisualMediaRequest(
                        ActivityResultContracts.PickVisualMedia.ImageOnly
                    )
                )
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onAttachFile = {
                documentPickerLauncher.launch("*/*")
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onTakePicture = {
                val uri = createImgUri()
                if (uri != null) {
                    onPhotoChange(uri)
                    takePictureLaunch.launch(uri)
                }
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onImageReceived = { uri ->
                chatViewModel.onEvent(ChatUiEvent.OnImageSelected(uri))
            },
            imageUri = chatState.imageUri,
            fileUri = chatState.fileUri,
            fileName = chatState.fileName,
            isFileUploading = chatState.isFileUploading,
            onRemoveImage = {
                chatViewModel.onEvent(ChatUiEvent.RemoveImage)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onRemoveFile = {
                chatViewModel.onEvent(ChatUiEvent.RemoveFile)
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            },
            onImageClick = { uri ->
                val encodedUrl = Base64.encodeToString(uri.toString().toByteArray(Charsets.UTF_8), Base64.URL_SAFE or Base64.NO_WRAP)
                navCtrl.navigate("fullscreen_image/$encodedUrl")
            }
        )
    }
}

private fun handleImageInClipboardForView(
    clipboardManager: android.content.ClipboardManager, 
    onReceiveContent: (Uri) -> Unit
) {
    if (clipboardManager.hasPrimaryClip()) {
        val primaryClip = clipboardManager.primaryClip
        
        if (primaryClip != null) {
            val description = primaryClip.description
            
            if (description.hasMimeType(ClipDescription.MIMETYPE_TEXT_URILIST)) {
                val item = primaryClip.getItemAt(0)
                val uri = item.uri
                
                if (uri != null) {
                    val mimeType = clipboardManager.primaryClip?.description?.getMimeType(0)
                    if (mimeType != null && mimeType.startsWith("image/")) {
                        onReceiveContent(uri)
                        return
                    }
                }
            }
            
            val imageMimeTypes = arrayOf(
                "image/*", "image/png", "image/jpeg", "image/gif", "image/webp",
                "image/", "application/octet-stream"
            )
            
            for (mimeType in imageMimeTypes) {
                if (description.hasMimeType(mimeType)) {
                    for (i in 0 until primaryClip.itemCount) {
                        val item = primaryClip.getItemAt(i)
                        val uri = item.uri
                        
                        if (uri != null) {
                            onReceiveContent(uri)
                            return
                        }
                    }
                }
            }
        }
    }
}

