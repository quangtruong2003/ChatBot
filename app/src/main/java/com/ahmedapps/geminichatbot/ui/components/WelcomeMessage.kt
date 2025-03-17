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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WelcomeMessage(onOptionSelected: (displayText: String, apiPrompt: String) -> Unit) {
    var textSize by remember { mutableStateOf(22.sp) }
    var boxWidth by remember { mutableStateOf(0) }

    // Bọc toàn bộ nội dung vào một Box để hỗ trợ Modifier.align
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-70).dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_app),
                contentDescription = "Icon Bot",
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
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
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            // Lấy chiều rộng màn hình và tính giới hạn 80%
            val configuration = LocalConfiguration.current
            val screenWidth = configuration.screenWidthDp.dp
            val maxFlowWidth = screenWidth * 0.8f

            // Sắp xếp các ô lựa chọn theo chiều ngang, tự động xuống dòng khi vượt quá maxFlowWidth
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
            FlowRow(
                modifier = Modifier.widthIn(max = maxFlowWidth),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                options.forEach { (displayText, prompt) ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.background)
                            .border(0.5.dp, Color.LightGray, shape = RoundedCornerShape(12.dp))
                            .clickable { onOptionSelected(displayText, prompt) }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = displayText,
                            style = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
