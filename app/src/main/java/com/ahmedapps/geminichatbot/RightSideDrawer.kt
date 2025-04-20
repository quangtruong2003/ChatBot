package com.ahmedapps.geminichatbot

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.isDigitsOnly

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RightSideDrawer(
    onClose: () -> Unit,
    chatViewModel: ChatViewModel
) {
    val scrollState = rememberScrollState()
    val hapticFeedback = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    
    // Lấy state từ ViewModel
    val apiSettingsState by chatViewModel.apiSettingsState.collectAsState()
    val showSafetyDialog by chatViewModel.showSafetySettingsDialog.collectAsState()
    
    // Advanced Settings luôn mở rộng
    var showAdvancedSettings by remember { mutableStateOf(true) }
    
    // State để theo dõi trường đang focus
    var isFieldFocused by remember { mutableStateOf(false) }
    var focusedFieldName by remember { mutableStateOf("") }
    
    // Ẩn bàn phím khi drawer đóng
    DisposableEffect(Unit) {
        onDispose {
            keyboardController?.hide()
            focusManager.clearFocus()
        }
    }
    
    // Màu sắc nhất quán
    val primaryColor = Color(0xFF2374E1)
    val backgroundColor = Color(0xFF18191B) // Màu nền chính
    val cardBackgroundColor = Color(0xFF1E1F22) // Màu nền card
    val textColorPrimary = Color.White
    val textColorSecondary = Color.LightGray
    val textColorTertiary = Color.Gray
    val dividerColor = Color(0xFF353537)
    
    // Hiệu ứng để tự động scroll đến trường đang focus
    LaunchedEffect(isFieldFocused, focusedFieldName) {
        if (isFieldFocused) {
            when (focusedFieldName) {
                "stopSequence" -> scrollState.animateScrollTo(scrollState.maxValue)
                "outputLength" -> scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
    }
    
    // Xử lý click bên ngoài để đóng bàn phím
    val clearFocus = {
        focusManager.clearFocus()
        isFieldFocused = false
        focusedFieldName = ""
    }
    
    Surface(
        color = backgroundColor,
        modifier = Modifier
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                clearFocus()
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 16.dp)
                .imePadding() // Thêm padding để không bị bàn phím che
                .verticalScroll(scrollState)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = false // Vô hiệu hóa để không chặn sự kiện click của các thành phần con
                ) {}
        ) {
            // Phần tiêu đề
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cài đặt API",
                    style = MaterialTheme.typography.titleLarge,
                    color = textColorPrimary
                )
                
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close, // Sử dụng icon chuẩn
                        contentDescription = "Đóng",
                        tint = textColorTertiary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            // Phần Temperature
            SettingSection(title = "Độ sáng tạo", cardBackgroundColor = cardBackgroundColor, textColorPrimary = textColorPrimary) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = apiSettingsState.temperature,
                        onValueChange = { 
                            chatViewModel.updateTemperature(it)
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        valueRange = 0f..2f, // Tối đa là 2.0
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = primaryColor,
                            activeTrackColor = primaryColor,
                            inactiveTrackColor = dividerColor
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp)) // Tăng khoảng cách
                    Box(
                        modifier = Modifier
                            .size(48.dp) // Tăng kích thước
                            .clip(RoundedCornerShape(8.dp)) // Bo góc rõ hơn
                            .background(Color(0xFF303134)), // Màu nền khác biệt hơn
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = apiSettingsState.temperature.toString().take(3),
                            color = textColorPrimary,
                            fontSize = 14.sp
                        )
                    }
                }
            }
            
            // Phần Thinking
            SettingSection(title = "Tư duy", cardBackgroundColor = cardBackgroundColor, textColorPrimary = textColorPrimary) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingToggleRow(
                        title = "Chế độ tư duy",
                        checked = apiSettingsState.thinkingModeEnabled,
                        onCheckedChange = { 
                            chatViewModel.updateThinkingMode(it)
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        primaryColor = primaryColor,
                        textColorPrimary = textColorPrimary,
                        textColorTertiary = textColorTertiary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    SettingToggleRow(
                        title = "Đặt ngân sách tư duy",
                        checked = apiSettingsState.thinkingBudgetEnabled,
                        onCheckedChange = { 
                            chatViewModel.updateThinkingBudget(it) 
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        primaryColor = primaryColor,
                        textColorPrimary = textColorPrimary,
                        textColorTertiary = textColorTertiary
                    )
                }
            }
            
            // Phần Tools
            SettingSection(title = "Công cụ", cardBackgroundColor = cardBackgroundColor, textColorPrimary = textColorPrimary) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingToggleRowWithEdit(
                        title = "Đầu ra có cấu trúc",
                        checked = apiSettingsState.structuredOutputEnabled,
                        onCheckedChange = { 
                            chatViewModel.updateStructuredOutput(it)
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onEditClick = { /* TODO: Implement edit functionality */ },
                        primaryColor = primaryColor,
                        textColorPrimary = textColorPrimary,
                        textColorTertiary = textColorTertiary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    SettingToggleRow(
                        title = "Thực thi mã",
                        checked = apiSettingsState.codeExecutionEnabled,
                        onCheckedChange = { 
                            chatViewModel.updateCodeExecution(it)
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        primaryColor = primaryColor,
                        textColorPrimary = textColorPrimary,
                        textColorTertiary = textColorTertiary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    SettingToggleRowWithEdit(
                        title = "Gọi hàm",
                        checked = apiSettingsState.functionCallingEnabled,
                        onCheckedChange = { 
                            chatViewModel.updateFunctionCalling(it)
                            hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onEditClick = { /* TODO: Implement edit functionality */ },
                        primaryColor = primaryColor,
                        textColorPrimary = textColorPrimary,
                        textColorTertiary = textColorTertiary
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Grounding with Google Search
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Tìm kiếm với Google",
                                style = MaterialTheme.typography.bodyMedium,
                                color = textColorPrimary
                            )
                            
                            // Chỉ hiển thị dòng "Source: Google Search" khi được bật
                            AnimatedVisibility(visible = apiSettingsState.groundingEnabled) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = "Nguồn: ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textColorTertiary,
                                        fontSize = 12.sp
                                    )
                                    
                                    val density = LocalDensity.current
                                    val iconSize = with(density) { 12.sp.toDp() }
                                    
                                    // Icon Google đã tồn tại trong thư mục drawable
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_google),
                                        contentDescription = "Google Icon",
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(iconSize)
                                    )
                                    
                                    Text(
                                        text = " Google Search",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = textColorTertiary,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        
                        Switch(
                            checked = apiSettingsState.groundingEnabled,
                            onCheckedChange = { 
                                chatViewModel.updateGroundingSearch(it)
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = textColorPrimary,
                                checkedTrackColor = primaryColor,
                                uncheckedThumbColor = textColorPrimary,
                                uncheckedTrackColor = Color.DarkGray
                            )
                        )
                    }
                }
            }
            
            // Advanced settings
            AdvancedSettingsSection(
                isExpanded = showAdvancedSettings,
                onToggle = { 
                    showAdvancedSettings = !showAdvancedSettings
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                },
                outputLength = apiSettingsState.outputLength,
                onOutputLengthChange = { if (it.isDigitsOnly() || it.isEmpty()) chatViewModel.updateOutputLength(it) },
                topP = apiSettingsState.topP,
                onTopPChange = { chatViewModel.updateTopP(it) },
                stopSequence = apiSettingsState.stopSequence,
                onStopSequenceChange = { chatViewModel.updateStopSequence(it) },
                onSafetySettingsClick = { chatViewModel.openSafetySettingsDialog() },
                primaryColor = primaryColor,
                cardBackgroundColor = cardBackgroundColor,
                textColorPrimary = textColorPrimary,
                textColorSecondary = textColorSecondary,
                textColorTertiary = textColorTertiary,
                dividerColor = dividerColor,
                onFieldFocus = { fieldName, isFocused ->
                    isFieldFocused = isFocused
                    focusedFieldName = fieldName
                }
            )
        }
    }

    // Hiển thị Safety Settings Dialog nếu state là true
    if (showSafetyDialog) {
        SafetySettingsDialog(
            apiSettings = apiSettingsState,
            onDismiss = { chatViewModel.closeSafetySettingsDialog() },
            onUpdateSetting = { category, threshold ->
                chatViewModel.updateSafetySetting(category, threshold)
            },
            onResetDefaults = { chatViewModel.resetSafetySettingsToDefaults() }
        )
    }
}

@Composable
fun SettingSection(
    title: String,
    cardBackgroundColor: Color,
    textColorPrimary: Color,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) { // Thêm padding bottom
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = textColorPrimary,
            modifier = Modifier.padding(bottom = 8.dp) // Di chuyển padding vào đây
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = cardBackgroundColor,
            shape = RoundedCornerShape(12.dp) // Tăng bo góc
        ) {
            Box(modifier = Modifier.padding(16.dp)) {
                content()
            }
        }
    }
}

@Composable
fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    primaryColor: Color,
    textColorPrimary: Color,
    textColorTertiary: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = textColorPrimary
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = textColorPrimary,
                checkedTrackColor = primaryColor,
                uncheckedThumbColor = textColorPrimary,
                uncheckedTrackColor = Color.DarkGray
            )
        )
    }
}

@Composable
fun SettingToggleRowWithEdit(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onEditClick: () -> Unit,
    primaryColor: Color,
    textColorPrimary: Color,
    textColorTertiary: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = textColorPrimary
            )
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = onEditClick,
                enabled = checked, // Chỉ bật nút Edit khi Switch bật
                modifier = Modifier.alpha(if (checked) 1f else 0.5f)
            ) {
                Text(
                    text = "Edit",
                    color = primaryColor,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = textColorPrimary,
                checkedTrackColor = primaryColor,
                uncheckedThumbColor = textColorPrimary,
                uncheckedTrackColor = Color.DarkGray
            )
        )
    }
}

@Composable
fun AdvancedSettingsSection(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    outputLength: String,
    onOutputLengthChange: (String) -> Unit,
    topP: Float,
    onTopPChange: (Float) -> Unit,
    stopSequence: String,
    onStopSequenceChange: (String) -> Unit,
    onSafetySettingsClick: () -> Unit,
    primaryColor: Color,
    cardBackgroundColor: Color,
    textColorPrimary: Color,
    textColorSecondary: Color,
    textColorTertiary: Color,
    dividerColor: Color,
    onFieldFocus: (String, Boolean) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        // Tiêu đề Advanced settings
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Cài đặt nâng cao",
                style = MaterialTheme.typography.titleMedium,
                color = textColorPrimary
            )
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = "Chuyển đổi cài đặt nâng cao",
                    tint = textColorTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Nội dung Advanced settings (luôn hiển thị, bỏ qua biến isExpanded)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = cardBackgroundColor,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // --- Safety settings --- 
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Cài đặt an toàn",
                        style = MaterialTheme.typography.bodyMedium,
                        color = textColorPrimary
                    )
                    TextButton(onClick = onSafetySettingsClick) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Edit",
                                color = primaryColor,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Chỉnh sửa cài đặt an toàn",
                                tint = primaryColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Divider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = dividerColor
                )

                // --- Add stop sequence --- 
                Text(
                    text = "Thêm chuỗi dừng",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColorPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ModernTextField(
                    value = stopSequence,
                    onValueChange = onStopSequenceChange,
                    placeholder = "Thêm chuỗi dừng...",
                    primaryColor = primaryColor,
                    containerColor = Color(0xFF303134),
                    textColor = textColorPrimary,
                    placeholderColor = textColorTertiary,
                    onFocusChange = { focused -> onFieldFocus("stopSequence", focused) }
                )
                Spacer(modifier = Modifier.height(16.dp))

                // --- Output length --- 
                Text(
                    text = "Độ dài đầu ra",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColorPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                ModernTextField(
                    value = outputLength,
                    onValueChange = onOutputLengthChange,
                    placeholder = "Số token tối đa (vd: 8192)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    primaryColor = primaryColor,
                    containerColor = Color(0xFF303134),
                    textColor = textColorPrimary,
                    placeholderColor = textColorTertiary,
                    onFocusChange = { focused -> onFieldFocus("outputLength", focused) }
                )
                Spacer(modifier = Modifier.height(16.dp))

                // --- Top P --- 
                Text(
                    text = "Top P",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColorPrimary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Slider(
                        value = topP,
                        onValueChange = onTopPChange,
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = primaryColor,
                            activeTrackColor = primaryColor,
                            inactiveTrackColor = dividerColor
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = topP.toString().take(4),
                        color = textColorPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(40.dp)
                    )
                }
            }
        }
    }
}

// Composable TextField hiện đại hơn
@Composable
fun ModernTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    primaryColor: Color,
    containerColor: Color,
    textColor: Color,
    placeholderColor: Color,
    onFocusChange: ((Boolean) -> Unit)? = null
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = placeholderColor, fontSize = 14.sp) },
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .onFocusEvent { focusState ->
                onFocusChange?.invoke(focusState.isFocused)
            },
        shape = RoundedCornerShape(8.dp),
        colors = TextFieldDefaults.colors(
            focusedTextColor = textColor,
            unfocusedTextColor = textColor,
            focusedContainerColor = containerColor,
            unfocusedContainerColor = containerColor,
            cursorColor = primaryColor,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        ),
        keyboardOptions = keyboardOptions,
        singleLine = true
    )
} 