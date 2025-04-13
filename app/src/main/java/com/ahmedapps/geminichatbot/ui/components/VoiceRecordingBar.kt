package com.ahmedapps.geminichatbot.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahmedapps.geminichatbot.R
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun VoiceRecordingBar(
    durationMs: Long,
    onStopRecording: () -> Unit,
    onCancelRecording: () -> Unit,
    isProcessingVoice: Boolean = false,
    isVisible: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Chuyển đổi thời gian từ mili giây sang định dạng "mm:ss"
    val formattedTime = formatRecordingTime(durationMs)
    
    // Hiệu ứng pulsating cho đèn thu âm
    val infiniteTransition = rememberInfiniteTransition(label = "recording_pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )
    
    // Hiệu ứng thanh sóng âm
    val waveScale1 by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave1"
    )
    
    val waveScale2 by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave2"
    )
    
    val waveScale3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "wave3"
    )
    
    // Xác định màu đèn báo trạng thái
    val indicatorColor = if (isProcessingVoice) Color.Gray else Color.Red
    
    // Xác định thông báo trạng thái
    val statusMessage = if (isProcessingVoice) "Đang xử lý..." else formattedTime
    
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + slideInHorizontally(),
        exit = fadeOut() + slideOutHorizontally()
    ) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(8.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            // Nút hủy ghi âm (bên trái)
            IconButton(
                onClick = onCancelRecording,
                modifier = Modifier
                    .size(42.dp)
                    .border(1.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f), CircleShape)
                    .clip(CircleShape)
                    .padding(8.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_close), 
                    contentDescription = "Hủy ghi âm",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Đèn ghi âm (đang nhấp nháy) - chỉ hiển thị khi không trong quá trình xử lý
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .scale(if (isProcessingVoice) 1f else scale)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Thời gian ghi âm hoặc thông báo đang xử lý
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Thanh sóng âm - hiển thị khi không trong quá trình xử lý
            if (!isProcessingVoice) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    repeat(12) { index ->
                        val waveHeight = when {
                            index % 3 == 0 -> waveScale1
                            index % 3 == 1 -> waveScale2
                            else -> waveScale3
                        }
                        
                        Box(
                            modifier = Modifier
                                .height(20.dp * waveHeight)
                                .width(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    MaterialTheme.colorScheme.primary.copy(
                                        alpha = 0.5f + (waveHeight * 0.5f)
                                    )
                                )
                        )
                    }
                }
            } else {
                // Khi đang xử lý, hiển thị khoảng trống thay vì thanh sóng
                Spacer(modifier = Modifier.weight(1f))
            }
            
            // Nút dừng và xử lý ghi âm (bên phải) hoặc biểu tượng loading
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                if (isProcessingVoice) {
                    // Hiển thị biểu tượng loading khi đang xử lý
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                        strokeCap = StrokeCap.Round
                    )
                } else {
                    // Hiển thị nút dừng để xử lý khi không trong quá trình xử lý
                    IconButton(
                        onClick = onStopRecording,
                        modifier = Modifier
                            .size(42.dp)
                            .padding(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_accept), 
                            contentDescription = "Dừng và xử lý ghi âm",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatRecordingTime(timeMs: Long): String {
    val duration = timeMs.milliseconds
    val seconds = duration.inWholeSeconds
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
} 