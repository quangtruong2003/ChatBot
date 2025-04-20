package com.ahmedapps.geminichatbot.drawer.right

// Enum để đại diện cho các ngưỡng chặn an toàn
enum class SafetyThreshold(val level: Int, val displayName: String) {
    BLOCK_NONE(0, "Tắt"), // Tắt
    BLOCK_ONLY_HIGH(1, "Thấp"), // Chặn ít
    BLOCK_MEDIUM_AND_ABOVE(2, "Trung bình"), // Chặn vừa
    BLOCK_LOW_AND_ABOVE(3, "Cao") // Chặn nhiều
}

data class ApiSettingsState(
    val temperature: Float = 1f,
    val thinkingModeEnabled: Boolean = true,
    val thinkingBudgetEnabled: Boolean = false,
    val structuredOutputEnabled: Boolean = false,
    val codeExecutionEnabled: Boolean = false,
    val functionCallingEnabled: Boolean = false,
    val groundingEnabled: Boolean = true,
    val outputLength: String = "65536",
    val topP: Float = 0.95f,
    val stopSequence: String = "",
    // Thêm cài đặt an toàn - tất cả mặc định là OFF
    val safetyHarassment: SafetyThreshold = SafetyThreshold.BLOCK_NONE,
    val safetyHate: SafetyThreshold = SafetyThreshold.BLOCK_NONE,
    val safetySexuallyExplicit: SafetyThreshold = SafetyThreshold.BLOCK_NONE,
    val safetyDangerous: SafetyThreshold = SafetyThreshold.BLOCK_NONE
) 