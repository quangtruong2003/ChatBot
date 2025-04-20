package com.ahmedapps.geminichatbot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.google.ai.client.generativeai.type.HarmCategory
import kotlin.math.roundToInt

@Composable
fun SafetySettingsDialog(
    apiSettings: ApiSettingsState,
    onDismiss: () -> Unit,
    onUpdateSetting: (HarmCategory, SafetyThreshold) -> Unit,
    onResetDefaults: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF2C2C2E), // Màu nền tối hơn một chút
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Tiêu đề và nút đóng
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cài đặt an toàn",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Đóng",
                            tint = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Mô tả
                Text(
                    text = "Điều chỉnh mức độ hiển thị nội dung có khả năng gây hại. Nội dung sẽ bị chặn dựa trên xác suất gây hại của nó.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.LightGray
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Bạn có trách nhiệm đảm bảo rằng cài đặt an toàn phù hợp với mục đích sử dụng của bạn và tuân thủ Điều khoản và Chính sách Sử dụng.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Các cài đặt
                SafetySliderSetting(
                    label = "Quấy rối",
                    currentThreshold = apiSettings.safetyHarassment,
                    onValueChange = { threshold ->
                        onUpdateSetting(HarmCategory.HARASSMENT, threshold)
                    }
                )
                SafetySliderSetting(
                    label = "Phát ngôn thù địch",
                    currentThreshold = apiSettings.safetyHate,
                    onValueChange = { threshold ->
                        onUpdateSetting(HarmCategory.HATE_SPEECH, threshold)
                    }
                )
                SafetySliderSetting(
                    label = "Nội dung khiêu dâm",
                    currentThreshold = apiSettings.safetySexuallyExplicit,
                    onValueChange = { threshold ->
                        onUpdateSetting(HarmCategory.SEXUALLY_EXPLICIT, threshold)
                    }
                )
                SafetySliderSetting(
                    label = "Nội dung nguy hiểm",
                    currentThreshold = apiSettings.safetyDangerous,
                    onValueChange = { threshold ->
                        onUpdateSetting(HarmCategory.DANGEROUS_CONTENT, threshold)
                    }
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Nút Reset
                TextButton(onClick = onResetDefaults) {
                    Text(
                        text = "Khôi phục mặc định",
                        color = Color(0xFF2374E1)
                    )
                }
            }
        }
    }
}

@Composable
private fun SafetySliderSetting(
    label: String,
    currentThreshold: SafetyThreshold,
    onValueChange: (SafetyThreshold) -> Unit
) {
    // Lấy danh sách các ngưỡng có thể chọn
    val thresholds = SafetyThreshold.values()

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
                modifier = Modifier.weight(1f) // Đảm bảo label không bị đẩy đi
            )
            Text(
                text = currentThreshold.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.padding(start = 16.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = currentThreshold.level.toFloat(),
            onValueChange = {
                // Tìm threshold tương ứng với giá trị float gần nhất
                val selectedLevel = it.roundToInt()
                val newThreshold = thresholds.find { t -> t.level == selectedLevel } ?: SafetyThreshold.BLOCK_NONE
                onValueChange(newThreshold)
            },
            steps = thresholds.size - 2, // Số bước = số mức - 1 - 1 (vì slider có 4 mức)
            valueRange = 0f.. (thresholds.size - 1).toFloat(),
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFF2374E1),
                activeTrackColor = Color(0xFF2374E1).copy(alpha = 0.7f),
                inactiveTrackColor = Color.DarkGray,
                activeTickColor = Color(0xFF2374E1).copy(alpha = 0.5f),
                inactiveTickColor = Color.Gray
            )
        )
    }
} 