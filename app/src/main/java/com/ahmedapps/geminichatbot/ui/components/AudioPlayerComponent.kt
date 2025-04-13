package com.ahmedapps.geminichatbot.ui.components

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ahmedapps.geminichatbot.R
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.sin

/**
 * Component để phát âm thanh.
 * 
 * @param audioFilePath Đường dẫn đến file âm thanh
 * @param transcriptionText Văn bản chuyển đổi từ âm thanh (có thể null)
 * @param modifier Modifier cho component
 */
@Composable
fun AudioPlayerComponent(
    audioFilePath: String,
    transcriptionText: String? = null,
    modifier: Modifier = Modifier
) {
    // Kiểm tra xem file âm thanh có tồn tại không
    val fileExists = remember(audioFilePath) {
        File(audioFilePath).exists()
    }
    
    // Nếu file không tồn tại, không hiển thị gì
    if (!fileExists) {
        Log.e("AudioPlayerComponent", "File âm thanh không tồn tại: $audioFilePath")
        return
    }
    
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    val mediaPlayer = remember { MediaPlayer() }
    
    DisposableEffect(audioFilePath) {
        try {
            mediaPlayer.setDataSource(audioFilePath)
            mediaPlayer.prepare()
            duration = mediaPlayer.duration
            
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                progress = 0f
                try {
                    mediaPlayer.seekTo(0)
                } catch (e: Exception) {
                    Log.e("AudioPlayerComponent", "Lỗi khi reset media player: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("AudioPlayerComponent", "Lỗi khi khởi tạo media player: ${e.message}")
        }
        
        onDispose {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.release()
        }
    }
    
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try {
                progress = mediaPlayer.currentPosition.toFloat() / duration.toFloat()
                delay(50)
            } catch (e: Exception) {
                Log.e("AudioPlayerComponent", "Lỗi khi cập nhật tiến trình: ${e.message}")
                isPlaying = false
            }
        }
    }
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        if (isPlaying) {
                            mediaPlayer.pause()
                            isPlaying = false
                        } else {
                            mediaPlayer.start()
                            isPlaying = true
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                        ),
                        contentDescription = if (isPlaying) "Tạm dừng" else "Phát",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = formatTime(if (isPlaying) (progress * duration).toInt() else 0) +
                                " / " + formatTime(duration),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            
            // Hiển thị văn bản chuyển đổi nếu có
            if (!transcriptionText.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = transcriptionText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp)
                )
            }
        }
    }
}

/**
 * Component để phát âm thanh từ Uri.
 * 
 * @param context Context để truy cập file từ Uri
 * @param audioUri Uri đến file âm thanh
 * @param transcriptionText Văn bản chuyển đổi từ âm thanh (có thể null)
 * @param modifier Modifier cho component
 */
@Composable
fun AudioPlayerComponent(
    context: Context,
    audioUri: Uri,
    transcriptionText: String? = null,
    modifier: Modifier = Modifier
) {
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    val mediaPlayer = remember { MediaPlayer() }
    
    DisposableEffect(audioUri) {
        try {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(context, audioUri)
            mediaPlayer.prepare()
            duration = mediaPlayer.duration
            
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                progress = 0f
                try {
                    mediaPlayer.seekTo(0)
                } catch (e: Exception) {
                    Log.e("AudioPlayerComponent", "Lỗi khi reset media player: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("AudioPlayerComponent", "Lỗi khi khởi tạo media player từ Uri: ${e.message}")
        }
        
        onDispose {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.release()
        }
    }
    
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try {
                progress = mediaPlayer.currentPosition.toFloat() / duration.toFloat()
                delay(50)
            } catch (e: Exception) {
                Log.e("AudioPlayerComponent", "Lỗi khi cập nhật tiến trình: ${e.message}")
                isPlaying = false
            }
        }
    }
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(
                    onClick = {
                        if (isPlaying) {
                            mediaPlayer.pause()
                            isPlaying = false
                        } else {
                            mediaPlayer.start()
                            isPlaying = true
                        }
                    },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        painter = painterResource(
                            id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                        ),
                        contentDescription = if (isPlaying) "Tạm dừng" else "Phát",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(8.dp))
                
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = formatTime(if (isPlaying) (progress * duration).toInt() else 0) +
                                " / " + formatTime(duration),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            
            // Hiển thị văn bản chuyển đổi nếu có
            if (!transcriptionText.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = transcriptionText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp)
                )
            }
        }
    }
}

/**
 * Định dạng thời gian từ milliseconds sang chuỗi "mm:ss"
 */
private fun formatTime(timeMs: Int): String {
    val minutes = TimeUnit.MILLISECONDS.toMinutes(timeMs.toLong())
    val seconds = TimeUnit.MILLISECONDS.toSeconds(timeMs.toLong()) % 60
    return String.format("%02d:%02d", minutes, seconds)
}

/**
 * Class dữ liệu đại diện cho một tin nhắn âm thanh
 */
data class AudioMessage(
    val id: String,
    val audioFilePath: String,
    val transcriptionText: String? = null,
    val durationMs: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val isFromUser: Boolean = true
)

/**
 * Ghi chú về triển khai Firebase Storage trong dự án:
 * 
 * 1. Tải lên File:
 *    - Firebase Storage được cấu hình trong AppModule.kt
 *    - ChatRepository.kt chứa phương thức uploadImage() để tải ảnh lên Firebase Storage
 *    - Khi ghi âm, ứng dụng sử dụng MediaRecorder để ghi âm vào bộ nhớ cục bộ
 *    - Sau khi ghi âm, file được lưu trữ và đường dẫn được sử dụng trong AudioPlayerComponent
 * 
 * 2. Nhận dạng Giọng nói:
 *    - ImprovedVoiceRecognitionHelper sử dụng kết hợp cả nhận dạng online (VoiceRecognitionHelper)
 *      và offline (VoskRecognitionHelper)
 *    - VoskRecognitionHelper sử dụng AudioRecord để ghi âm và xử lý dữ liệu âm thanh theo thời gian thực
 *    - MediaRecorder.AudioSource.VOICE_RECOGNITION được sử dụng để tối ưu hóa chất lượng âm thanh cho nhận dạng giọng nói
 * 
 * 3. Chat với File Âm thanh:
 *    - Khi người dùng ghi âm và gửi, nội dung được chuyển đổi thành văn bản sử dụng API nhận dạng giọng nói
 *    - File âm thanh được lưu trữ cục bộ và đường dẫn được lưu trong đối tượng Chat
 *    - AudioPlayerComponent được sử dụng để phát lại âm thanh từ đường dẫn lưu trữ
 */

/**
 * Phân tích đầu vào để xác định nếu nó là Uri cục bộ hoặc URL từ server
 * @param input Chuỗi đầu vào có thể là URL hoặc Uri
 * @return Uri nếu có thể phân tích được, null nếu không
 */
private fun parseUriOrUrl(context: Context, input: String): Uri? {
    return try {
        // Kiểm tra nếu đầu vào là URL (http/https)
        if (input.startsWith("http://") || input.startsWith("https://")) {
            // Nếu là URL, trả về Uri
            Uri.parse(input)
        } else {
            // Thử phân tích như Uri cục bộ
            val uri = Uri.parse(input)
            
            // Kiểm tra xem Uri có hợp lệ không bằng cách thử truy cập
            context.contentResolver.openInputStream(uri)?.close()
            
            // Nếu không ném ngoại lệ, Uri hợp lệ
            uri
        }
    } catch (e: Exception) {
        Log.e("AudioPlayerComponent", "Không thể phân tích Uri từ: $input, lỗi: ${e.message}")
        null
    }
}

/**
 * Component để phát âm thanh từ chuỗi có thể là URL hoặc Uri cục bộ.
 * 
 * @param context Context để truy cập file từ Uri
 * @param audioSource Chuỗi đại diện cho URL hoặc Uri cục bộ đến file âm thanh
 * @param transcriptionText Văn bản chuyển đổi từ âm thanh (có thể null)
 * @param modifier Modifier cho component
 * @param isCompact Nếu true, sẽ hiển thị phiên bản nhỏ gọn hơn (mặc định: false)
 */
@Composable
fun AudioPlayerComponent(
    context: Context,
    audioSource: String,
    transcriptionText: String? = null,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false
) {
    // Phân tích Uri từ chuỗi đầu vào (có thể là URL hoặc Uri cục bộ)
    val uri = remember(audioSource) {
        parseUriOrUrl(context, audioSource)
    }
    
    // Nếu không thể phân tích Uri, hiển thị lỗi
    if (uri == null) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = modifier.fillMaxWidth()
        ) {
            Text(
                text = "Không thể phát âm thanh. File không tồn tại hoặc bị lỗi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp)
            )
        }
        return
    }
    
    // Sử dụng component phát âm thanh từ Uri
    AudioPlayerComponent(
        context = context,
        audioUri = uri,
        transcriptionText = transcriptionText,
        modifier = modifier,
        isCompact = isCompact
    )
}

/**
 * Component để phát âm thanh.
 * 
 * @param context Context để truy cập file từ Uri
 * @param audioUri Uri đến file âm thanh
 * @param transcriptionText Văn bản chuyển đổi từ âm thanh (có thể null)
 * @param modifier Modifier cho component
 * @param isCompact Nếu true, sẽ hiển thị phiên bản nhỏ gọn hơn (mặc định: false)
 */
@Composable
fun AudioPlayerComponent(
    context: Context,
    audioUri: Uri,
    transcriptionText: String? = null,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    onDelete: (() -> Unit)? = null
) {
    var isPlaying by remember { mutableStateOf(false) }
    var duration by remember { mutableStateOf(0) }
    var progress by remember { mutableFloatStateOf(0f) }
    val mediaPlayer = remember { MediaPlayer() }
    
    DisposableEffect(audioUri) {
        try {
            mediaPlayer.reset()
            mediaPlayer.setDataSource(context, audioUri)
            mediaPlayer.prepare()
            duration = mediaPlayer.duration
            
            mediaPlayer.setOnCompletionListener {
                isPlaying = false
                progress = 0f
                try {
                    mediaPlayer.seekTo(0)
                } catch (e: Exception) {
                    Log.e("AudioPlayerComponent", "Lỗi khi reset media player: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("AudioPlayerComponent", "Lỗi khi khởi tạo media player từ Uri: ${e.message}")
        }
        
        onDispose {
            if (mediaPlayer.isPlaying) {
                mediaPlayer.stop()
            }
            mediaPlayer.release()
        }
    }
    
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            try {
                progress = mediaPlayer.currentPosition.toFloat() / duration.toFloat()
                delay(50)
            } catch (e: Exception) {
                Log.e("AudioPlayerComponent", "Lỗi khi cập nhật tiến trình: ${e.message}")
                isPlaying = false
            }
        }
    }
    
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = modifier,
        shadowElevation = 1.dp
    ) {
        if (isCompact) {
            // Phiên bản hiện đại cho UserChatItem
            Box(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Nút phát/dừng với animation
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .clickable {
                                if (isPlaying) {
                                    mediaPlayer.pause()
                                } else {
                                    mediaPlayer.start()
                                }
                                isPlaying = !isPlaying
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                            ),
                            contentDescription = if (isPlaying) "Tạm dừng" else "Phát",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(12.dp))
                    
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        // Hiệu ứng sóng âm
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(24.dp)
                        ) {
                            // Đường gợn sóng (đơn giản hóa)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Tạo 27 bar cho hiệu ứng sóng
                                repeat(27) { index ->
                                    val maxHeight = 24.dp
                                    
                                    // Tính chiều cao mỗi thanh dựa trên index
                                    val heightPercent = when {
                                        index % 3 == 0 -> 0.9f
                                        index % 2 == 0 -> 0.7f
                                        else -> 0.4f
                                    }
                                    
                                    // Điều chỉnh alpha dựa trên progress
                                    val barAlpha = if (progress * 27 > index) 0.9f else 0.3f
                                    
                                    // Nếu đang phát, điều chỉnh chiều cao dựa trên index
                                    val heightModifier = if (isPlaying) {
                                        // Mod giá trị để tạo hiệu ứng
                                        val phase = (System.currentTimeMillis() / 100 + index * 15) % 360
                                        val sineValue = sin(Math.toRadians(phase.toDouble())).toFloat() * 0.2f + 0.8f
                                        heightPercent * sineValue
                                    } else {
                                        heightPercent
                                    }
                                    
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(maxHeight * heightModifier)
                                            .alpha(barAlpha)
                                            .background(
                                                MaterialTheme.colorScheme.primary,
                                                RoundedCornerShape(2.dp)
                                            )
                                    )
                                }
                            }
                        }
                        
                        // Hiển thị thời gian và thanh tiến trình
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(if (isPlaying) (progress * duration).toInt() else 0),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Text(
                                text = formatTime(duration),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    
                    // Thêm nút xóa nếu có callback onDelete
                    if (onDelete != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                // Dừng phát nếu đang phát
                                if (isPlaying) {
                                    mediaPlayer.pause()
                                    isPlaying = false
                                }
                                onDelete()
                            },
                            modifier = Modifier
                                .size(32.dp)
                                .border(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f), CircleShape)
                                .clip(CircleShape)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_close),
                                contentDescription = "Xóa bản ghi âm",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        } else {
            // Phiên bản đầy đủ (giữ nguyên code hiện tại)
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = {
                            if (isPlaying) {
                                mediaPlayer.pause()
                                isPlaying = false
                            } else {
                                mediaPlayer.start()
                                isPlaying = true
                            }
                        },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            painter = painterResource(
                                id = if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
                            ),
                            contentDescription = if (isPlaying) "Tạm dừng" else "Phát",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = formatTime(if (isPlaying) (progress * duration).toInt() else 0) +
                                    " / " + formatTime(duration),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    }
                }
                
                // Hiển thị văn bản chuyển đổi nếu có
                if (!transcriptionText.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = transcriptionText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp, end = 8.dp)
                    )
                }
            }
        }
    }
} 