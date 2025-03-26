package com.ahmedapps.geminichatbot.ui.screens

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ahmedapps.geminichatbot.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FullScreenImageScreen(
    imageUrl: String?,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val imageLoader = ImageLoader.Builder(context).build()
    
    // Trạng thái zoom và pan
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var showControls by remember { mutableStateOf(true) }
    var showResetButton by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }

    // Khởi tạo trình xin quyền lưu ảnh
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            coroutineScope.launch {
                saveImage(context, imageUrl, imageLoader)
            }
        } else {
            Toast.makeText(context, "Cần quyền lưu trữ để lưu ảnh", Toast.LENGTH_SHORT).show()
        }
    }

    // Cải thiện trải nghiệm zoom với spring animation
    val transformableState = rememberTransformableState(
        onTransformation = { zoomChange, panChange, _ ->
            scale = (scale * zoomChange).coerceIn(0.5f, 5f)

            // Giới hạn khu vực pan dựa trên mức zoom
            // Sử dụng abs() để đảm bảo giới hạn luôn dương
            val limitX = kotlin.math.abs((scale - 1) * 500) // Giới hạn theo chiều rộng ảnh
            val limitY = kotlin.math.abs((scale - 1) * 500) // Giới hạn theo chiều cao ảnh

            offset = Offset(
                // Đảm bảo phạm vi của coerceIn luôn hợp lệ: [-limit, limit]
                x = (offset.x + panChange.x).coerceIn(-limitX, limitX),
                y = (offset.y + panChange.y).coerceIn(-limitY, limitY)
            )

            showResetButton = scale != 1f || offset != Offset.Zero
        }
    )
    
    BackHandler {
        onClose()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .navigationBarsPadding()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        showControls = !showControls
                    },
                    onDoubleTap = { tapOffset ->
                        if (scale > 2f) {
                            // Reset về 1x nếu đang zoom lớn
                            scale = 1f
                            offset = Offset.Zero
                        } else {
                            // Zoom tới vị trí tap với mức 3x
                            scale = 3f
                            // Tính toán offset dựa trên vị trí tap
                            val centerX = size.width / 2
                            val centerY = size.height / 2
                            offset = Offset(
                                (centerX - tapOffset.x) * (scale - 1),
                                (centerY - tapOffset.y) * (scale - 1)
                            )
                        }
                        showResetButton = scale != 1f || offset != Offset.Zero
                    }
                )
            }
    ) {
        if (imageUrl != null) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
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
                    .transformable(
                        state = transformableState,
                        lockRotationOnZoomPan = true
                    ),
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Đang tải...", color = Color.White)
                    }
                },
                error = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Lỗi tải hình ảnh", color = Color.White)
                    }
                }
            )
        } else {
            Text("Không tìm thấy hình ảnh", color = Color.White)
        }

        // UI Controls overlay
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Nút đóng
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .statusBarsPadding()
                        .background(
                            Color.Gray.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Đóng",
                        tint = Color.White
                    )
                }

                // Nút lưu hình ảnh
                IconButton(
                    onClick = {
                        if (!isSaving) {
                            isSaving = true
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                // Android 10+ không cần quyền lưu trữ
                                coroutineScope.launch {
                                    saveImage(context, imageUrl, imageLoader)
                                    isSaving = false
                                }
                            } else {
                                // Kiểm tra và yêu cầu quyền lưu trữ
                                when (PackageManager.PERMISSION_GRANTED) {
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    ) -> {
                                        coroutineScope.launch {
                                            saveImage(context, imageUrl, imageLoader)
                                            isSaving = false
                                        }
                                    }
                                    else -> {
                                        permissionLauncher.launch(
                                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                                        )
                                        isSaving = false
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .background(
                            Color.Gray.copy(alpha = 0.5f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowDownward,
                        contentDescription = "Lưu hình ảnh",
                        tint = Color.White
                    )
                }

                // Nút reset zoom/pan
                AnimatedVisibility(
                    visible = showResetButton,
                    enter = fadeIn() + scaleIn(),
                    exit = fadeOut() + scaleOut(),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .statusBarsPadding()
                ) {
                    IconButton(
                        onClick = {
                            // Sử dụng spring animation khi reset
                            coroutineScope.launch {
                                val springSpec = spring<Float>(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessLow
                                )

                                // Reset scale với animation
                                launch {
                                    animate(scale, 1f, animationSpec = springSpec) { value, _ ->
                                        scale = value
                                    }
                                }
                                launch {
                                    animate(offset.x, 0f, animationSpec = springSpec) { value, _ ->
                                        offset = offset.copy(x = value)
                                    }
                                }
                                launch {
                                    animate(offset.y, 0f, animationSpec = springSpec) { value, _ ->
                                        offset = offset.copy(y = value)
                                    }
                                }

                                // Đợi các animation hoàn thành trước khi ẩn nút (tùy chọn)
                                // Hoặc có thể ẩn ngay lập tức nếu muốn
                                showResetButton = false
                            }
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
}

// Hàm lưu hình ảnh
private suspend fun saveImage(context: Context, imageUrl: String?, imageLoader: ImageLoader) {
    if (imageUrl == null) {
        Toast.makeText(context, "Không thể lưu hình ảnh", Toast.LENGTH_SHORT).show()
        return
    }

    try {
        // Tải hình ảnh từ URL
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .build()

        val result = withContext(Dispatchers.IO) {
            (imageLoader.execute(request) as? SuccessResult)?.drawable
        }

        val bitmap = (result as? BitmapDrawable)?.bitmap

        if (bitmap != null) {
            // Lưu hình ảnh vào thư viện
            val saved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveImageOnAndroid10AndAbove(context, bitmap)
            } else {
                saveImageOnAndroid9AndBelow(context, bitmap)
            }

            withContext(Dispatchers.Main) {
                if (saved) {
                    Toast.makeText(context, "Đã lưu hình ảnh vào thư viện", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Không thể lưu hình ảnh", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Không thể tải hình ảnh", Toast.LENGTH_SHORT).show()
            }
        }
    } catch (e: Exception) {
        Log.e("FullScreenImageScreen", "Error saving image: ${e.message}")
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Lỗi: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

// Phương thức lưu hình ảnh cho Android 10 trở lên
@Suppress("DEPRECATION")
private suspend fun saveImageOnAndroid10AndAbove(context: Context, bitmap: Bitmap): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${timeStamp}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }

            val uri: Uri? = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues
            )

            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                }
                true
            } ?: false
        } catch (e: Exception) {
            Log.e("SaveImage", "Error saving image (Android 10+): ${e.message}")
            false
        }
    }
}

// Phương thức lưu hình ảnh cho Android 9 trở xuống
@Suppress("DEPRECATION")
private suspend fun saveImageOnAndroid9AndBelow(context: Context, bitmap: Bitmap): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, "IMG_${timeStamp}.jpg")
            
            FileOutputStream(image).use { outputStream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
            }
            
            // Thông báo cho gallery về hình ảnh mới
            context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(image)))
            true
        } catch (e: Exception) {
            Log.e("SaveImage", "Error saving image (Android 9-): ${e.message}")
            false
        }
    }
} 