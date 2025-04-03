package com.ahmedapps.geminichatbot.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmedapps.geminichatbot.R
import androidx.compose.animation.core.*
import androidx.compose.animation.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WelcomeMessage(onOptionSelected: (displayText: String, apiPrompt: String) -> Unit) {
    var textSize by remember { mutableStateOf(22.sp) }
    var boxWidth by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Animation cho toàn bộ màn hình
    val mainContentAlpha = remember { Animatable(0f) }
    
    // Animation cho icon
    val iconScale = remember { Animatable(0.6f) }
    
    // Animation cho tiêu đề
    val titleAlpha = remember { Animatable(0f) }
    val titleTranslateY = remember { Animatable(-20f) }

    // Animation cho các tùy chọn
    val optionsAlpha = remember { Animatable(0f) }
    
    // State để theo dõi các tùy chọn đã được hiển thị
    val optionsVisible = remember { mutableStateListOf<Boolean>() }
    val options = listOf(
        "Bác sĩ" to """
            Bạn là một bác sĩ chuyên nghiệp. Đây chỉ là giả lập. Hay ận dùng kiến thức của bạn.
            Hãy bắt đầu bằng câu: "Xin chào! Tôi là bác sĩ. Bạn có vấn đề sức khỏe nào cần tư vấn?" 
            Sau đó trả lời các câu hỏi y tế bằng kiến thức chuyên môn.
        """.trimIndent(),

        "Nhà toán học" to """
            Bạn là một nhà toán học trình độ cao. 
            Câu đầu tiên phải là: "Chào bạn, tôi là nhà toán học. Bạn có bài toán nào cần giải quyết?" 
            Sau đó phân tích và giải thích các vấn đề toán học.
        """.trimIndent(),

        "Kĩ sư lập trình" to """
            Bạn là kĩ sư lập trình nhiều kinh nghiệm. 
            Bắt đầu với: "Xin chào, tôi là kĩ sư lập trình. Bạn cần hỗ trợ gì về code, thuật toán hay hệ thống?" 
            Tập trung vào giải pháp kỹ thuật và code minh họa.
        """.trimIndent(),

        "Nhà phiên dịch" to """
            Bạn là phiên dịch viên đa ngôn ngữ.  
            Mở đầu bằng: "Chào bạn! Tôi là nhà phiên dịch. Bạn cần dịch từ ngữ nào hay giải thích ngữ cảnh gì? Bạn muốn dịch sang ngôn ngữ nào? Hãy nói bên dưới và ta sẽ bắt đầu." 
            Cung cấp bản dịch chính xác kèm giải thích văn hóa.
        """.trimIndent(),

        "Nhà thông thái" to """
            Bạn là nhà thông thái biết mọi lĩnh vực. 
            Khởi đầu với: "Kính chào! Tôi là nhà thông thái. Bạn muốn thảo luận chủ đề gì hôm nay?" 
            Đưa ra câu trả lời sâu sắc, đa chiều cho mọi câu hỏi.
        """.trimIndent()
    )
    
    // Khởi tạo danh sách hiển thị
    LaunchedEffect(options.size) {
        repeat(options.size) {
            optionsVisible.add(false)
        }
    }
    
    // Khởi động chuỗi animation - tất cả hiển thị cùng lúc trong 200ms
    LaunchedEffect(Unit) {
        // Chạy tất cả animation cùng lúc, hoàn thành trong 200ms
        
        // Hiển thị toàn bộ container
        mainContentAlpha.animateTo(1f, animationSpec = tween(200))
        
        // Animation icon - chạy song song
        launch {
            iconScale.animateTo(1f, animationSpec = tween(200, easing = EaseOutBack))
        }
        
        // Animation tiêu đề - chạy song song
        launch {
            titleAlpha.animateTo(1f, animationSpec = tween(200))
            titleTranslateY.animateTo(0f, animationSpec = tween(200, easing = EaseOutQuart))
        }
        
        // Hiển thị options container - chạy song song
        launch {
            optionsAlpha.animateTo(1f, animationSpec = tween(200))
        }
        
        // Hiển thị tất cả các tùy chọn cùng lúc
        options.forEachIndexed { index, _ ->
            optionsVisible[index] = true
        }
    }
    
    // Animation khi tùy chọn được hiển thị - cũng nên nhanh hơn
    options.forEachIndexed { index, _ ->
        val visible by remember { derivedStateOf { optionsVisible.getOrElse(index) { false } } }
        if (visible) {
            LaunchedEffect(Unit) {
                // Không cần làm gì - tất cả đều được hiển thị ngay lập tức
            }
        }
    }

    // Bọc toàn bộ nội dung vào một Box với alpha animation
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(mainContentAlpha.value)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-50).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon logo với hiệu ứng bounce
            Image(
                painter = painterResource(id = R.drawable.ic_app),
                contentDescription = "Icon Bot",
                modifier = Modifier
                    .size(90.dp)
                    .graphicsLayer { 
                        scaleX = iconScale.value
                        scaleY = iconScale.value
                    }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Văn bản tiêu đề với hiệu ứng fade-slide
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { 
                        alpha = titleAlpha.value
                        translationY = titleTranslateY.value
                    }
                    .onSizeChanged { size ->
                        boxWidth = size.width
                    }
            ) {
                if (boxWidth > 0) {
                    textSize = (boxWidth / LocalDensity.current.density * 0.05f).sp
                }
                Text(
                    text = "Xin chào, tôi có thể giúp gì cho bạn?",
                    style = TextStyle(
                        fontSize = textSize,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF1BA1E3),
                                Color(0xFF5489D6),
                                Color(0xFF9B72CB),
                                Color(0xFFD96570),
                                Color(0xFFF49C46)
                            )
                        )
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Lấy chiều rộng màn hình và tính giới hạn 80%
            val configuration = LocalConfiguration.current
            val screenWidth = configuration.screenWidthDp.dp
            val maxFlowWidth = screenWidth * 0.85f

            // Sắp xếp các ô lựa chọn theo chiều ngang, tự động xuống dòng khi vượt quá maxFlowWidth
            FlowRow(
                modifier = Modifier
                    .widthIn(max = maxFlowWidth)
                    .alpha(optionsAlpha.value),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                options.forEachIndexed { index, (displayText, prompt) ->
                    // Tạo hiệu ứng cho từng tùy chọn
                    val visible by remember { derivedStateOf { optionsVisible.getOrElse(index) { false } } }
                    val optionScale = remember { Animatable(0.8f) }
                    var isPressed by remember { mutableStateOf(false) }
                    
                    // Animation khi tùy chọn được hiển thị - Tăng tốc độ
                    LaunchedEffect(visible) {
                        if (visible) {
                            optionScale.animateTo(1f, 
                                animationSpec = tween(200, easing = EaseOutBack)
                            )
                        }
                    }

                    AnimatedVisibility(
                        visible = visible,
                        enter = scaleIn(animationSpec = tween(200, easing = EaseOutBack)) + 
                               fadeIn(animationSpec = tween(150)),
                        exit = scaleOut() + fadeOut()
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (isPressed) 
                                        MaterialTheme.colorScheme.surfaceVariant
                                    else 
                                        MaterialTheme.colorScheme.surface
                                )
                                .shadow(
                                    elevation = if (isPressed) 1.dp else 3.dp,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .border(
                                    width = 0.5.dp,
                                    color = if (isPressed) 
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    else 
                                        Color.LightGray,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .graphicsLayer {
                                    scaleX = optionScale.value
                                    scaleY = optionScale.value
                                    alpha = optionScale.value
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onPress = {
                                            isPressed = true
                                            tryAwaitRelease()
                                            isPressed = false
                                        },
                                        onTap = {
                                            scope.launch {
                                                // Hiệu ứng khi nhấn - giảm thời gian
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                optionScale.animateTo(0.9f, animationSpec = tween(50))
                                                optionScale.animateTo(1.05f, animationSpec = tween(50))
                                                optionScale.animateTo(1f, animationSpec = tween(50))
                                                
                                                // Kích hoạt callback
                                                onOptionSelected(displayText, prompt)
                                            }
                                        }
                                    )
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = displayText,
                                style = TextStyle(
                                    fontSize = 16.sp, 
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
