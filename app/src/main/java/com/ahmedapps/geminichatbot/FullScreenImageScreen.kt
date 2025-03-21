// FullScreenImageScreen.kt
package com.ahmedapps.geminichatbot.ui.screens

import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ahmedapps.geminichatbot.R

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
    BackHandler {
        onClose()
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
                .background(
                    Color.Gray.copy(alpha = 0.5f),
                    shape = CircleShape
                ) // Use CircleShape for a circular button
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