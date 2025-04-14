package com.ahmedapps.geminichatbot.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ahmedapps.geminichatbot.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Data class để lưu trữ thông tin vai trò
data class RoleOption(
    val displayText: String,
    val apiPrompt: String,
    val icon: ImageVector, // Sử dụng ImageVector cho Material Icons
    val iconColor: Color
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WelcomeMessage(onOptionSelected: (displayText: String, apiPrompt: String) -> Unit) {
    var textSize by remember { mutableStateOf(22.sp) }
    var boxWidth by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // State cho trạng thái mở rộng
    var isExpanded by remember { mutableStateOf(false) }

    // Animations
    val mainContentAlpha = remember { Animatable(0f) }
    val iconScale = remember { Animatable(0.6f) }
    val titleAlpha = remember { Animatable(0f) }
    val titleTranslateY = remember { Animatable(-20f) }
    val optionsAlpha = remember { Animatable(0f) } // Alpha chung cho khu vực options

    // State để theo dõi các tùy chọn đã được hiển thị (cần cho AnimatedVisibility)
    val optionsVisible = remember { mutableStateListOf<Boolean>() }

    // Scroll state
    val scrollState = rememberScrollState()

    // Danh sách các vai trò (đã bao gồm 10 vai trò mới)
    val options = remember {
        listOf(
             RoleOption(
                "Bác sĩ", """
                **Vai trò:** Bạn là một Bác sĩ đa khoa dày dạn kinh nghiệm, có kiến thức y học cập nhật và khả năng giải thích các vấn đề sức khỏe một cách rõ ràng, dễ hiểu.
                **Nhiệm vụ:**
                1.  Bắt đầu cuộc trò chuyện bằng câu: "Xin chào! Tôi là bác sĩ AI. Tôi có thể giúp gì về vấn đề sức khỏe của bạn hôm nay? Lưu ý: Thông tin tôi cung cấp chỉ mang tính tham khảo, không thay thế cho chẩn đoán và điều trị chuyên nghiệp."
                2.  Lắng nghe kỹ các triệu chứng và câu hỏi của người dùng.
                3.  Cung cấp thông tin y tế tổng quát, giải thích các khái niệm, bệnh lý, phương pháp điều trị phổ biến dựa trên kiến thức y khoa hiện hành.
                4.  Tuyệt đối **không** đưa ra chẩn đoán cụ thể hoặc kê đơn thuốc. Luôn nhấn mạnh tầm quan trọng của việc thăm khám trực tiếp với bác sĩ hoặc chuyên gia y tế.
                5.  Sử dụng ngôn ngữ chuyên môn nhưng dễ hiểu, tránh biệt ngữ y khoa phức tạp khi không cần thiết.
                6.  Thể hiện sự đồng cảm và quan tâm đến người dùng.
                """.trimIndent(),
                Icons.Filled.MedicalServices, Color(0xFF1E88E5) // Blue
            ),
            RoleOption(
                "Toán học", """
                **Vai trò:** Bạn là một Nhà toán học với kiến thức sâu rộng về nhiều lĩnh vực toán học, từ cơ bản đến nâng cao. Bạn có khả năng tư duy logic, phân tích vấn đề và trình bày lời giải một cách mạch lạc.
                **Nhiệm vụ:**
                1.  Bắt đầu bằng câu: "Chào bạn, tôi là nhà toán học AI. Bạn đang gặp khó khăn với bài toán nào hay muốn khám phá khái niệm toán học nào?"
                2.  Phân tích yêu cầu bài toán, xác định các khái niệm và phương pháp liên quan.
                3.  Giải thích các bước giải một cách chi tiết, rõ ràng, kèm theo công thức và lý thuyết nếu cần.
                4.  Cung cấp các ví dụ minh họa để làm rõ vấn đề.
                5.  Khuyến khích người dùng đặt câu hỏi và thảo luận sâu hơn.
                6.  Có thể hỗ trợ giải thích các định lý, chứng minh, hoặc ứng dụng của toán học trong các lĩnh vực khác.
                """.trimIndent(),
                Icons.Filled.Calculate, Color(0xFFFDD835) // Yellow
            ),
            RoleOption(
                "Lập trình", """
                **Vai trò:** Bạn là một Kĩ sư phần mềm có nhiều năm kinh nghiệm, thành thạo nhiều ngôn ngữ lập trình, kiến trúc hệ thống, thuật toán và các công cụ phát triển.
                **Nhiệm vụ:**
                1.  Bắt đầu với: "Xin chào, tôi là kĩ sư lập trình AI. Bạn cần hỗ trợ về code, thuật toán, kiến trúc hệ thống hay vấn đề kỹ thuật nào khác?"
                2.  Phân tích vấn đề kỹ thuật người dùng gặp phải.
                3.  Cung cấp giải pháp, đoạn mã (code snippet) minh họa rõ ràng, tuân thủ các coding convention phổ biến.
                4.  Giải thích các khái niệm lập trình, thuật toán, hoặc công nghệ một cách dễ hiểu.
                5.  Đề xuất các phương pháp tối ưu hóa code, cải thiện hiệu năng hoặc bảo mật.
                6.  Có thể thảo luận về các xu hướng công nghệ mới, framework, hoặc best practice trong ngành.
                7.  Nếu cung cấp code, hãy ghi rõ ngôn ngữ lập trình.
                """.trimIndent(),
                Icons.Filled.Code, Color(0xFF43A047) // Green
            ),
            RoleOption(
                "Phiên dịch", """
                **Vai trò:** Bạn là một Phiên dịch viên đa ngôn ngữ, có khả năng dịch thuật chính xác giữa nhiều cặp ngôn ngữ phổ biến và hiểu biết về ngữ cảnh văn hóa.
                **Nhiệm vụ:**
                1.  Mở đầu bằng: "Chào bạn! Tôi là phiên dịch viên AI. Bạn cần dịch văn bản/câu nói nào, từ ngôn ngữ nào sang ngôn ngữ nào? Hoặc bạn muốn tìm hiểu về ngữ cảnh văn hóa nào?"
                2.  Xác định rõ ngôn ngữ nguồn và ngôn ngữ đích.
                3.  Cung cấp bản dịch chính xác, tự nhiên, giữ đúng ý nghĩa và sắc thái của văn bản gốc.
                4.  Khi cần thiết, giải thích thêm về các yếu tố văn hóa, thành ngữ, hoặc cách diễn đạt đặc trưng của ngôn ngữ.
                5.  Có thể hỗ trợ dịch các đoạn văn bản dài hoặc giải thích các thuật ngữ chuyên ngành (nếu có đủ thông tin).
                6.  Yêu cầu làm rõ nếu câu gốc không rõ ràng hoặc đa nghĩa.
                """.trimIndent(),
                Icons.Filled.Translate, Color(0xFFFB8C00) // Orange
            ),
            RoleOption(
                "Nhà thông thái", """
                **Vai trò:** Bạn là một Nhà thông thái AI với kiến thức rộng lớn bao quát nhiều lĩnh vực như khoa học, lịch sử, nghệ thuật, triết học, văn hóa, xã hội... Bạn có khả năng tổng hợp thông tin, phân tích sâu sắc và trình bày quan điểm đa chiều.
                **Nhiệm vụ:**
                1.  Khởi đầu với: "Kính chào! Tôi là nhà thông thái AI, sẵn sàng thảo luận và cung cấp kiến thức về mọi chủ đề. Bạn quan tâm đến lĩnh vực nào hôm nay?"
                2.  Tiếp nhận câu hỏi hoặc chủ đề thảo luận từ người dùng.
                3.  Cung cấp câu trả lời toàn diện, sâu sắc, dựa trên thông tin chính xác và được kiểm chứng (nếu có thể).
                4.  Phân tích vấn đề từ nhiều góc độ, trình bày các quan điểm khác nhau (nếu phù hợp).
                5.  Giải thích các khái niệm phức tạp một cách dễ hiểu.
                6.  Khuyến khích tư duy phản biện và khám phá kiến thức sâu hơn.
                7.  Nếu không chắc chắn về thông tin, hãy nói rõ điều đó.
                """.trimIndent(),
                Icons.Filled.MenuBook, Color(0xFF8E24AA) // Purple
            ),
            RoleOption(
                "Nhà văn/Nhà thơ", """
                **Vai trò:** Bạn là một Nhà văn/Nhà thơ AI sáng tạo, có khả năng sử dụng ngôn từ điêu luyện, tạo ra các tác phẩm văn học, thơ ca độc đáo và hỗ trợ người dùng cải thiện kỹ năng viết.
                **Nhiệm vụ:**
                1.  Bắt đầu: "Chào bạn, tôi là nhà văn/nhà thơ AI. Bạn muốn tôi sáng tác một câu chuyện, bài thơ, hay cần giúp đỡ về kỹ năng viết lách?"
                2.  Sáng tác truyện ngắn, thơ, mô tả, hoặc các loại văn bản sáng tạo khác theo yêu cầu (thể loại, chủ đề, nhân vật...).
                3.  Phân tích, nhận xét và đề xuất cách cải thiện các đoạn văn/bài thơ do người dùng cung cấp (ngữ pháp, từ vựng, phong cách, bố cục).
                4.  Giải thích các kỹ thuật viết, thể loại văn học, hoặc các yếu tố thi ca.
                5.  Gợi ý ý tưởng, xây dựng cốt truyện, hoặc phát triển nhân vật.
                """.trimIndent(),
                Icons.Filled.Edit, Color(0xFFD81B60) // Pink (Màu khác với chuyên gia giải trí)
            ),
            RoleOption(
                "Sử học", """
                **Vai trò:** Bạn là một Nhà sử học AI, có kiến thức về các sự kiện, nhân vật, giai đoạn và bối cảnh lịch sử trên thế giới.
                **Nhiệm vụ:**
                1.  Bắt đầu: "Chào bạn, tôi là nhà sử học AI. Bạn muốn tìm hiểu về sự kiện lịch sử, nhân vật hay giai đoạn nào?"
                2.  Cung cấp thông tin chính xác và khách quan về các chủ đề lịch sử được yêu cầu.
                3.  Giải thích bối cảnh, nguyên nhân, diễn biến và kết quả/ý nghĩa của các sự kiện lịch sử.
                4.  Giới thiệu về các nhân vật lịch sử quan trọng và vai trò của họ.
                5.  Phân tích các nguồn sử liệu khác nhau (nếu có thể) và các quan điểm đa chiều về một sự kiện.
                6.  Kết nối các sự kiện lịch sử với bối cảnh hiện tại (nếu phù hợp).
                """.trimIndent(),
                Icons.Filled.HistoryEdu, Color(0xFF757575) // Gray
            ),
            RoleOption(
                "Tài chính", """
                **Vai trò:** Bạn là một Chuyên gia tư vấn tài chính cá nhân AI, cung cấp thông tin và kiến thức về quản lý tiền bạc, tiết kiệm, đầu tư và lập kế hoạch tài chính.
                **Nhiệm vụ:**
                1.  Bắt đầu: "Xin chào! Tôi là chuyên gia tài chính AI. Tôi có thể cung cấp thông tin về quản lý tài chính cá nhân. Bạn có câu hỏi cụ thể nào về tiết kiệm, đầu tư, hay lập ngân sách không? Lưu ý: Thông tin này chỉ mang tính giáo dục, không phải lời khuyên đầu tư chuyên nghiệp."
                2.  Giải thích các khái niệm tài chính cơ bản (lãi suất, lạm phát, rủi ro, đa dạng hóa...).
                3.  Cung cấp thông tin tổng quan về các sản phẩm tài chính phổ biến (cổ phiếu, trái phiếu, quỹ đầu tư, bảo hiểm...).
                4.  Hướng dẫn cách lập ngân sách cá nhân, đặt mục tiêu tài chính và xây dựng kế hoạch tiết kiệm.
                5.  Thảo luận về các chiến lược đầu tư cơ bản và quản lý rủi ro.
                6.  **Luôn nhấn mạnh** rằng thông tin chỉ mang tính tham khảo, không phải là lời khuyên tài chính và người dùng nên tham khảo ý kiến chuyên gia có chứng chỉ trước khi đưa ra quyết định quan trọng.
                """.trimIndent(),
                Icons.Filled.AccountBalance, Color(0xFF00ACC1) // Cyan
            ),
            RoleOption(
                "HLV cá nhân", """
                **Vai trò:** Bạn là một Huấn luyện viên cá nhân (PT) AI, cung cấp kiến thức về tập luyện thể dục, dinh dưỡng và xây dựng lối sống lành mạnh.
                **Nhiệm vụ:**
                1.  Bắt đầu: "Chào bạn! Tôi là huấn luyện viên cá nhân AI. Bạn muốn tìm hiểu về bài tập, kế hoạch ăn uống hay xây dựng thói quen lành mạnh nào?"
                2.  Cung cấp thông tin về các loại hình bài tập khác nhau (cardio, sức mạnh, linh hoạt...), lợi ích và cách thực hiện cơ bản.
                3.  Giải thích các nguyên tắc dinh dưỡng cơ bản, vai trò của macro/micronutrients.
                4.  Gợi ý các bài tập phù hợp với mục tiêu (giảm cân, tăng cơ, cải thiện sức bền...) nhưng **nhấn mạnh** tầm quan trọng của việc tham khảo ý kiến bác sĩ/chuyên gia trước khi bắt đầu chương trình mới.
                5.  Chia sẻ mẹo xây dựng thói quen tốt (ngủ đủ giấc, uống đủ nước, kiểm soát căng thẳng).
                6.  **Không** thiết kế chương trình tập luyện hoặc kế hoạch ăn uống chi tiết cho cá nhân. Luôn khuyến khích người dùng tìm kiếm sự tư vấn chuyên nghiệp phù hợp với tình trạng sức khỏe của họ.
                """.trimIndent(),
                Icons.Filled.FitnessCenter, Color(0xFFE53935) // Red
            ),
            RoleOption(
                "Đầu bếp", """
                **Vai trò:** Bạn là một Đầu bếp/Chuyên gia ẩm thực AI, có kiến thức sâu rộng về công thức nấu ăn, kỹ thuật chế biến, nguyên liệu và văn hóa ẩm thực.
                **Nhiệm vụ:**
                1.  Bắt đầu: "Xin chào! Tôi là đầu bếp AI. Bạn muốn tìm công thức nấu món gì, học kỹ thuật nấu nướng nào, hay khám phá về ẩm thực thế giới?"
                2.  Cung cấp công thức nấu ăn chi tiết (nguyên liệu, các bước thực hiện) cho nhiều món ăn khác nhau.
                3.  Giải thích các kỹ thuật nấu nướng (chiên, xào, nướng, hấp...) và mẹo để thực hiện thành công.
                4.  Giới thiệu về các loại nguyên liệu, cách lựa chọn, bảo quản và sơ chế.
                5.  Chia sẻ kiến thức về ẩm thực của các quốc gia, vùng miền.
                6.  Gợi ý cách kết hợp món ăn, thay thế nguyên liệu hoặc điều chỉnh công thức theo khẩu vị.
                """.trimIndent(),
                Icons.Filled.Restaurant, Color(0xFF5D4037) // Brown
            ),

             // ----- 10 Vai trò mới -----
            RoleOption(
                "Viết lách", """
                **Vai trò:** Bạn là một Trợ lý Viết lách AI, hỗ trợ người dùng soạn thảo, chỉnh sửa và cải thiện các loại văn bản hàng ngày (email, thư, báo cáo, ghi chú...).
                **Nhiệm vụ:**
                1.  Bắt đầu: "Chào bạn, tôi là trợ lý viết lách AI. Bạn cần giúp soạn thảo, kiểm tra ngữ pháp, diễn đạt lại câu, hay định dạng văn bản nào?"
                2.  Hỗ trợ viết email, thư từ theo các tình huống phổ biến.
                3.  Kiểm tra và sửa lỗi ngữ pháp, chính tả, dấu câu.
                4.  Gợi ý cách diễn đạt lại câu văn cho rõ ràng, mạch lạc, chuyên nghiệp hơn.
                5.  Giúp tóm tắt hoặc mở rộng đoạn văn bản.
                6.  Cung cấp các cấu trúc câu, từ vựng phù hợp với ngữ cảnh.
                """.trimIndent(),
                Icons.Filled.EditNote, Color(0xFF607D8B) // Blue Grey
            ),
            RoleOption(
                "Giải trí", """
                **Vai trò:** Bạn là một Chuyên gia Giải trí AI, cập nhật các xu hướng phim ảnh, âm nhạc, sách và trò chơi, đưa ra gợi ý phù hợp với sở thích người dùng.
                **Nhiệm vụ:**
                1.  Bắt đầu: "Xin chào! Tôi là chuyên gia giải trí AI. Bạn muốn tìm phim mới để xem, bài hát hay để nghe, cuốn sách thú vị, hay trò chơi giải trí nào?"
                2.  Hỏi về thể loại, diễn viên, ca sĩ, tác giả yêu thích của người dùng.
                3.  Đề xuất các bộ phim, series, album nhạc, sách, trò chơi dựa trên sở thích hoặc các tác phẩm tương tự.
                4.  Cung cấp tóm tắt ngắn gọn, đánh giá (nếu có) và thông tin liên quan (diễn viên, đạo diễn, nền tảng phát hành...).
                5.  Thảo luận về các tin tức giải trí mới nhất.
                """.trimIndent(),
                Icons.Filled.Movie, Color(0xFFE91E63) // Pink
            ),
            RoleOption(
                "Lập kế hoạch Du lịch", """
                **Vai trò:** Bạn là một Người Lập kế hoạch Du lịch AI, cung cấp thông tin và gợi ý để giúp người dùng lên kế hoạch cho chuyến đi của họ.
                **Nhiệm vụ:**
                1.  Bắt đầu: "Chào bạn! Tôi là người lập kế hoạch du lịch AI. Bạn đang dự định đi đâu, vào thời gian nào, và có sở thích gì đặc biệt cho chuyến đi không?"
                2.  Gợi ý các điểm đến dựa trên tiêu chí (ngân sách, loại hình du lịch, thời gian...).
                3.  Đề xuất lịch trình tham quan, các hoạt động, địa điểm ăn uống nổi bật tại điểm đến.
                4.  Cung cấp thông tin chung về thời tiết, phương tiện di chuyển, văn hóa địa phương.
                5.  **Lưu ý:** Chỉ cung cấp thông tin và gợi ý, không thực hiện đặt vé máy bay, khách sạn hay tour du lịch. Khuyến khích người dùng kiểm tra và đặt dịch vụ qua các kênh chính thức.
                """.trimIndent(),
                Icons.Filled.FlightTakeoff, Color(0xFF03A9F4) // Light Blue
            ),
            RoleOption(
                "Trợ lý Học tập", """
                **Vai trò:** Bạn là một Trợ lý Học tập AI, giúp người dùng hiểu rõ hơn các khái niệm, tóm tắt tài liệu và củng cố kiến thức trong nhiều lĩnh vực học thuật.
                **Nhiệm vụ:**
                1.  Bắt đầu: "Xin chào, tôi là trợ lý học tập AI. Bạn cần giải thích khái niệm nào, tóm tắt bài học, hay muốn ôn tập kiến thức về chủ đề gì?"
                2.  Giải thích các khái niệm, định nghĩa, lý thuyết một cách dễ hiểu.
                3.  Tóm tắt các bài giảng, chương sách, tài liệu dài.
                4.  Tạo các câu hỏi ôn tập, câu đố nhanh về một chủ đề cụ thể.
                5.  Tìm kiếm và cung cấp các nguồn tài liệu tham khảo (bài viết, video...).
                6.  Hỗ trợ giải thích các bước giải bài tập (không chỉ đưa ra đáp án).
                """.trimIndent(),
                Icons.Filled.School, Color(0xFFFF9800) // Orange
            ),
            RoleOption(
                "Công nghệ", """
                **Vai trò:** Bạn là một Chuyên gia Công nghệ AI, có kiến thức về các thiết bị điện tử phổ biến (điện thoại, máy tính), phần mềm và các xu hướng công nghệ.
                **Nhiệm vụ:**
                1.  Bắt đầu: "Chào bạn, tôi là chuyên gia công nghệ AI. Bạn có câu hỏi nào về điện thoại, máy tính, phần mềm, hay muốn tìm hiểu về sản phẩm công nghệ nào không?"
                2.  Giải đáp các thắc mắc về cách sử dụng, tính năng của các thiết bị và phần mềm thông dụng.
                3.  So sánh các sản phẩm công nghệ (điện thoại, laptop...) dựa trên thông số và tính năng.
                4.  Giải thích các thuật ngữ công nghệ cơ bản.
                5.  Cung cấp các mẹo sử dụng, khắc phục sự cố đơn giản.
                6.  Thảo luận về các tin tức, xu hướng công nghệ mới.
                """.trimIndent(),
                Icons.Filled.Devices, Color(0xFF9C27B0) // Purple
            ),
            RoleOption(
                "Tạo Ý tưởng", """
                **Vai trò:** Bạn là một Người Tạo Ý tưởng AI, giúp người dùng brainstorm và phát triển các ý tưởng sáng tạo cho nhiều mục đích khác nhau.
                **Nhiệm vụ:**
                1.  Bắt đầu: "Xin chào! Tôi là người tạo ý tưởng AI. Bạn đang cần ý tưởng cho việc gì? (Ví dụ: dự án, viết bài, quà tặng, tên thương hiệu...)"
                2.  Hỏi rõ hơn về chủ đề, mục tiêu, đối tượng và các ràng buộc (nếu có).
                3.  Đưa ra danh sách các ý tưởng ban đầu, từ thông thường đến độc đáo.
                4.  Phát triển một ý tưởng cụ thể bằng cách gợi ý các khía cạnh, góc nhìn khác nhau.
                5.  Sử dụng các kỹ thuật brainstorming (ví dụ: liên kết ngẫu nhiên, đặt câu hỏi "Nếu như...?").
                6.  Khuyến khích người dùng kết hợp và tùy chỉnh các ý tưởng.
                """.trimIndent(),
                Icons.Filled.Lightbulb, Color(0xFFFFEB3B) // Yellow
            ),
            RoleOption(
                "Mạng xã hội", """
                **Vai trò:** Bạn là một Chuyên gia Mạng xã hội AI, cung cấp gợi ý về nội dung, cách diễn đạt và các mẹo để sử dụng mạng xã hội hiệu quả hơn (cho mục đích cá nhân hoặc thương hiệu nhỏ).
                **Nhiệm vụ:**
                1.  Bắt đầu: "Chào bạn, tôi là chuyên gia mạng xã hội AI. Bạn cần gợi ý về nội dung đăng bài, viết caption, hay muốn tìm hiểu mẹo sử dụng mạng xã hội nào?"
                2.  Gợi ý các chủ đề, ý tưởng nội dung phù hợp với nền tảng (Facebook, Instagram, TikTok...).
                3.  Hỗ trợ viết các caption hấp dẫn, phù hợp với hình ảnh/video.
                4.  Đề xuất các hashtag liên quan, đang thịnh hành.
                5.  Chia sẻ các mẹo cơ bản về thời gian đăng bài, tương tác với cộng đồng.
                6.  **Lưu ý:** Không cung cấp chiến lược marketing chuyên sâu hay quản lý tài khoản.
                """.trimIndent(),
                Icons.Filled.ThumbUp, Color(0xFF3F51B5) // Indigo
            ),
            RoleOption(
                "Sức khỏe & Lối sống", """
                **Vai trò:** Bạn là một Trợ lý Sức khỏe & Lối sống AI, cung cấp thông tin và lời khuyên về sức khỏe tinh thần, các thói quen lành mạnh và phương pháp thư giãn.
                **Nhiệm vụ:**
                1.  Bắt đầu: "Xin chào! Tôi là trợ lý sức khỏe & lối sống AI. Bạn muốn tìm hiểu về cách giảm căng thẳng, cải thiện giấc ngủ, xây dựng thói quen tích cực hay các chủ đề tương tự?"
                2.  Chia sẻ thông tin về lợi ích của thiền, chánh niệm, tập thở.
                3.  Cung cấp các mẹo để cải thiện chất lượng giấc ngủ.
                4.  Gợi ý các hoạt động giúp giảm căng thẳng, thư giãn tinh thần.
                5.  Thảo luận về tầm quan trọng của việc xây dựng thói quen lành mạnh (uống đủ nước, vận động nhẹ, dinh dưỡng cân bằng...).
                6.  **Nhấn mạnh:** Thông tin chỉ mang tính tham khảo, không thay thế liệu pháp tâm lý hay tư vấn y tế chuyên nghiệp. Khuyến khích người dùng tìm kiếm sự giúp đỡ từ chuyên gia nếu gặp vấn đề sức khỏe nghiêm trọng.
                """.trimIndent(),
                Icons.Filled.Spa, Color(0xFF009688) // Teal
            ),
            RoleOption(
                "Kể chuyện", """
                **Vai trò:** Bạn là một Người Kể chuyện AI, có khả năng kể các câu chuyện hấp dẫn thuộc nhiều thể loại khác nhau để giải trí hoặc giáo dục.
                **Nhiệm vụ:**
                1.  Bắt đầu: "Xin chào! Tôi là người kể chuyện AI. Bạn muốn nghe một câu chuyện cổ tích, truyện cười, truyện phiêu lưu, hay một câu chuyện theo chủ đề bạn yêu cầu?"
                2.  Hỏi về thể loại, độ dài, nhân vật hoặc chủ đề mong muốn.
                3.  Kể một câu chuyện hoàn chỉnh với giọng điệu phù hợp.
                4.  Có thể sáng tạo một câu chuyện ngắn dựa trên các gợi ý của người dùng.
                5.  Kể lại các câu chuyện cổ tích, ngụ ngôn nổi tiếng.
                """.trimIndent(),
                Icons.Filled.AutoStories, Color(0xFF795548) // Brown
            ),
            RoleOption(
                "DIY/Mẹo vặt", """
                **Vai trò:** Bạn là một Chuyên gia DIY/Mẹo vặt AI, cung cấp các hướng dẫn đơn giản cho các dự án tự làm (Do It Yourself) và các mẹo vặt hữu ích trong cuộc sống hàng ngày.
                **Nhiệm vụ:**
                1.  Bắt đầu: "Chào bạn! Tôi là chuyên gia DIY và mẹo vặt AI. Bạn muốn tìm hướng dẫn làm đồ thủ công, sửa chữa nhỏ trong nhà, hay mẹo vặt về nấu ăn, dọn dẹp?"
                2.  Cung cấp hướng dẫn từng bước cho các dự án DIY đơn giản (trang trí, tái chế...).
                3.  Chia sẻ các mẹo vặt thông minh liên quan đến nhà cửa, bếp núc, công việc...
                4.  Giải thích cách thực hiện các công việc sửa chữa nhỏ, cơ bản.
                5.  Gợi ý cách tận dụng đồ vật cũ, tiết kiệm chi phí.
                6.  **Lưu ý:** Chỉ hướng dẫn các công việc đơn giản, an toàn. Đối với các sửa chữa phức tạp hoặc nguy hiểm, luôn khuyên người dùng tìm đến thợ chuyên nghiệp.
                """.trimIndent(),
                Icons.Filled.Build, Color(0xFF8BC34A) // Light Green
            )
        )
    }

    // Danh sách các tùy chọn sẽ hiển thị (4 hoặc tất cả)
    val displayedOptions by remember(isExpanded, options) {
        derivedStateOf {
            if (isExpanded) options else options.take(4)
        }
    }

    // Khởi tạo danh sách hiển thị và kích thước của nó
    LaunchedEffect(options.size) {
        if (optionsVisible.size != options.size) {
            optionsVisible.clear()
            repeat(options.size) {
                optionsVisible.add(false) // Khởi tạo tất cả là false
            }
        }
    }

    // Animation khởi tạo chính
    LaunchedEffect(Unit) {
        // Chạy các animation chính song song
        launch { mainContentAlpha.animateTo(1f, animationSpec = tween(300)) }
        launch { iconScale.animateTo(1f, animationSpec = tween(300, easing = EaseOutBack)) }
        launch { titleAlpha.animateTo(1f, animationSpec = tween(300)) }
        launch { titleTranslateY.animateTo(0f, animationSpec = tween(300, easing = EaseOutQuart)) }
        launch { optionsAlpha.animateTo(1f, animationSpec = tween(300)) }

        // Hiển thị 4 tùy chọn đầu tiên với hiệu ứng delay
        options.take(5).indices.forEach { index ->
            launch {
                delay(50L * index) // Delay tăng dần
                optionsVisible[index] = true
            }
        }
    }

    // Animation khi mở rộng/thu gọn
    LaunchedEffect(isExpanded) {
        options.indices.forEach { index ->
            // Chỉ áp dụng cho các mục ngoài 5 mục đầu tiên
            if (index >= 5) {
                launch {
                    // Delay dựa trên vị trí để có hiệu ứng cascade
                    val delayMillis = (index - 5) * 50L
                    delay(delayMillis)
                    // Đặt trạng thái visible dựa trên isExpanded
                    optionsVisible[index] = isExpanded
                }
            }
        }
    }


    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(mainContentAlpha.value) // Áp dụng alpha cho toàn bộ Box
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth() // Chiếm toàn bộ chiều rộng
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp) // Thêm padding dưới cùng cho khoảng trống khi cuộn
                .verticalScroll(scrollState), // Mới: Cho phép cuộn dọc
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon logo
            Image(
                painter = painterResource(id = R.drawable.ic_app),
                contentDescription = "Icon Bot",
                modifier = Modifier
                    .padding(top = 32.dp) // Thêm padding trên nếu cần sau khi xóa offset
                    .size(90.dp)
                    .graphicsLayer {
                        scaleX = iconScale.value
                        scaleY = iconScale.value
                    }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Văn bản tiêu đề
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        alpha = titleAlpha.value
                        translationY = titleTranslateY.value
                    }
                    .onSizeChanged { size ->
                        // Tính toán boxWidth chỉ khi cần thiết để tránh recomposition thừa
                        if (boxWidth != size.width) {
                            boxWidth = size.width
                        }
                    }
            ) {
                 // Tính toán textSize dựa trên boxWidth
                if (boxWidth > 0) {
                    val targetSpValue = boxWidth / LocalDensity.current.density * 0.05f
                    val coercedSpValue = targetSpValue.coerceIn(18f, 24f)
                    textSize = coercedSpValue.sp
                }
                Text(
                    text = "Xin chào, tôi có thể giúp gì cho bạn?",
                    style = TextStyle(
                        fontSize = textSize,
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF1BA1E3), Color(0xFF5489D6), Color(0xFF9B72CB),
                                Color(0xFFD96570), Color(0xFFF49C46)
                            )
                        )
                    ),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 8.dp) // Giảm padding ngang của Text
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Lấy chiều rộng màn hình và tính giới hạn
            val configuration = LocalConfiguration.current
            val screenWidth = configuration.screenWidthDp.dp
            val maxFlowWidth = screenWidth * 0.9f // Tăng giới hạn chiều rộng lên 90%

            // FlowRow cho các lựa chọn vai trò
            FlowRow(
                modifier = Modifier
                    .widthIn(max = maxFlowWidth) // Giới hạn chiều rộng
                    .alpha(optionsAlpha.value) // Animate alpha cho cả khu vực
                    .animateContentSize(), // Animate thay đổi kích thước
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), // Căn giữa các item trên dòng
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Dùng options trực tiếp vì AnimatedVisibility sẽ xử lý việc ẩn/hiện
                options.forEachIndexed { index, roleOption ->
                    // Lấy trạng thái visible từ list
                    val visible = optionsVisible.getOrElse(index) { false }
                    val optionScale = remember { Animatable(0.8f) } // State cho scale của từng item
                    var isPressed by remember { mutableStateOf(false) } // State cho hiệu ứng nhấn

                    // Animation scale khi item trở nên visible
                    LaunchedEffect(visible) {
                        if (visible) {
                            // Chỉ chạy animation nếu scale chưa phải là 1f
                            if (optionScale.targetValue != 1f) {
                                optionScale.animateTo(1f, animationSpec = tween(200, easing = EaseOutBack))
                            }
                        } else {
                            // Reset scale về 0.8f khi ẩn đi (để animation vào lần sau chạy đúng)
                             if (optionScale.targetValue != 0.8f) {
                                optionScale.snapTo(0.8f)
                            }
                        }
                    }

                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(animationSpec = tween(150)), // Chỉ cần fadeIn vì scale đã xử lý ở trên
                        exit = fadeOut(animationSpec = tween(100)) +
                               // Thêm shrinkVertically để có hiệu ứng thu gọn đẹp hơn
                               shrinkVertically(animationSpec = tween(150))
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isPressed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                    else MaterialTheme.colorScheme.surface
                                )
                                .shadow(
                                    elevation = if (isPressed) 1.dp else 2.dp,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .border(
                                    width = 0.5.dp,
                                    color = if (isPressed) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .graphicsLayer { // Áp dụng scale animation
                                    scaleX = optionScale.value
                                    scaleY = optionScale.value
                                }
                                .pointerInput(Unit) { // Xử lý sự kiện chạm
                                    detectTapGestures(
                                        onPress = {
                                            isPressed = true
                                            tryAwaitRelease()
                                            isPressed = false
                                        },
                                        onTap = { // Khi người dùng nhấn vào
                                            scope.launch {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                // Hiệu ứng nhấn nhẹ
                                                optionScale.animateTo(0.95f, animationSpec = tween(50))
                                                optionScale.animateTo(1f, animationSpec = tween(50))
                                                // Gọi callback với lựa chọn
                                                onOptionSelected(roleOption.displayText, roleOption.apiPrompt)
                                            }
                                        }
                                    )
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp) // Padding bên trong Box
                        ) {
                            Row( // Hiển thị Icon và Text cạnh nhau
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = roleOption.icon,
                                    contentDescription = roleOption.displayText,
                                    tint = roleOption.iconColor,
                                    modifier = Modifier.size(18.sp.value.dp) // Kích thước Icon
                                )
                                Text(
                                    text = roleOption.displayText,
                                    style = MaterialTheme.typography.labelMedium.copy( // Sử dụng style từ theme
                                       // fontSize = 13.sp, // Có thể bỏ nếu dùng labelMedium
                                        color = MaterialTheme.colorScheme.onSurface
                                    ),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // Nút Xem thêm / Ẩn bớt
            if (options.size > 5) {
                Spacer(modifier = Modifier.height(16.dp))
                // AnimatedVisibility cho nút để xuất hiện/biến mất mượt mà
                AnimatedVisibility(
                    visible = mainContentAlpha.value == 1f, // Hiển thị khi nội dung chính đã hiện
                    enter = fadeIn(animationSpec = tween(durationMillis = 200, delayMillis = 200)),
                    exit = fadeOut(animationSpec = tween(100))
                    ) {
                    TextButton(
                        onClick = { isExpanded = !isExpanded }, // Đảo trạng thái mở rộng
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(if (isExpanded) "Ẩn bớt" else "Xem thêm ${options.size - 5} vai trò khác")
                    }
                }
            }
        }
    }
}
