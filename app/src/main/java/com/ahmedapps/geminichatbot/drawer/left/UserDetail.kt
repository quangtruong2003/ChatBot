package com.ahmedapps.geminichatbot

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.SheetValue
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.clickable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.viewModelScope
import com.ahmedapps.geminichatbot.ChatViewModel
import com.ahmedapps.geminichatbot.data.ChatRepository
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserDetailBottomSheet(
    onDismiss: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAllChats: () -> Unit
) {
    val currentUser = FirebaseAuth.getInstance().currentUser
    val photoUrl = currentUser?.photoUrl
    val email = currentUser?.email ?: "Không có email"
    val displayName = currentUser?.displayName ?: "Người dùng"
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val chatViewModel = hiltViewModel<ChatViewModel>()
    val chatRepository = chatViewModel.getChatRepository()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var currentRules by remember { mutableStateOf("") }
    var showFirstDeleteDialog by remember { mutableStateOf(false) }
    var showSecondDeleteDialog by remember { mutableStateOf(false) }
    var showFirstDeleteAccountDialog by remember { mutableStateOf(false) }
    var showSecondDeleteAccountDialog by remember { mutableStateOf(false) }
    var showDeleteAllChatsLoading by remember { mutableStateOf(false) }
    
    // Animation states
    val headerAlpha = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }
    
    LaunchedEffect(key1 = Unit) {
        currentRules = chatRepository.rulesAI
        // Animation sequence
        headerAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 500)
        )
        contentAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 800, delayMillis = 300)
        )
    }

    val sheetState = rememberModalBottomSheetState(
        // Cấu hình để không bỏ qua trạng thái partially expanded
        skipPartiallyExpanded = false
    )
    
    // Auto-expand khi hiển thị
    LaunchedEffect(Unit) {
        sheetState.expand()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        dragHandle = { 
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Divider(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)  // Giới hạn chiều cao tối đa là 90% màn hình
                .padding(start = 20.dp, end = 20.dp, top = 8.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Animated Header Section
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = headerAlpha.value }
            ) {
                // Profile Avatar and User Info
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
//                        modifier = Modifier
//                            .size(120.dp)
//                            .shadow(8.dp, CircleShape)
//                            .clip(CircleShape)
//                            .background(
//                                brush = Brush.radialGradient(
//                                    colors = listOf(
//                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
//                                        MaterialTheme.colorScheme.primary
//                                    )
//                                )
//                            ),
//                        contentAlignment = Alignment.Center
                    ) {
                        if (photoUrl != null) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(photoUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Ảnh đại diện",
                                modifier = Modifier
                                    .size(112.dp)
                                    .clip(CircleShape)
                                    .border(3.dp, MaterialTheme.colorScheme.background, CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "Ảnh đại diện mặc định",
                                modifier = Modifier
                                    .size(70.dp)
                                    .padding(8.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // User name with animated scale
                    val scaleAnim by animateFloatAsState(
                        targetValue = if (headerAlpha.value > 0.5f) 1f else 0.8f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "nameScale"
                    )
                    
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.graphicsLayer {
                            scaleX = scaleAnim
                            scaleY = scaleAnim
                        }
                    )
                    
                    // Email with copy button
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(email))
                                Toast.makeText(context, "Đã sao chép email", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = "Sao chép email",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            
            // Animated Content Sections
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = contentAlpha.value }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Danh mục: Cài đặt ứng dụng
                    CategoryHeader(title = "Cài đặt ứng dụng", icon = Icons.Outlined.Settings)

                    // Rules AI Card
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + expandVertically()
                    ) {
                        SettingsCard(
                            title = "Thiết lập Rules AI",
                            description = "Cấu hình quy tắc cho AI trợ lý của bạn",
                            icon = Icons.Outlined.Rule,
                            iconTint = MaterialTheme.colorScheme.primary,
                            onClick = { showRulesDialog = true }
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Danh mục: Quản lý dữ liệu
                    CategoryHeader(title = "Quản lý dữ liệu", icon = Icons.Outlined.Storage)

                    // Delete All Chats Card
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + expandVertically(
                            animationSpec = tween(durationMillis = 300, delayMillis = 100)
                        )
                    ) {
                        SettingsCard(
                            title = "Xóa tất cả lịch sử chat",
                            description = "Xóa toàn bộ cuộc trò chuyện và dữ liệu liên quan",
                            icon = Icons.Outlined.DeleteSweep,
                            iconTint = MaterialTheme.colorScheme.error,
                            onClick = { if (!showDeleteAllChatsLoading) { showFirstDeleteDialog = true } },
                            isLoading = showDeleteAllChatsLoading
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Danh mục: Bảo mật
                    CategoryHeader(title = "Bảo mật", icon = Icons.Outlined.Security)

                    // Delete Account Card
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + expandVertically(
                            animationSpec = tween(durationMillis = 300, delayMillis = 200)
                        )
                    ) {
                        SettingsCard(
                            title = "Xóa tài khoản",
                            description = "Xóa vĩnh viễn tài khoản và tất cả dữ liệu liên quan",
                            icon = Icons.Outlined.PersonRemove,
                            iconTint = MaterialTheme.colorScheme.error,
                            onClick = { showFirstDeleteAccountDialog = true }
                        )
                    }

                    // Logout Button
                    Spacer(modifier = Modifier.height(36.dp))
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn(
                            animationSpec = tween(durationMillis = 500, delayMillis = 300)
                        ) + slideInVertically(
                            animationSpec = tween(durationMillis = 400, delayMillis = 300),
                            initialOffsetY = { it / 2 }
                        )
                    ) {
                        Button(
                            onClick = { showLogoutDialog = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.error
                            ),
                            shape = RoundedCornerShape(12.dp),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 4.dp,
                                pressedElevation = 1.dp
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = "Đăng xuất",
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Đăng xuất",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
        
        // Show logout confirmation dialog if needed
        if (showLogoutDialog) {
            LogoutConfirmationDialog(
                onDismiss = { showLogoutDialog = false },
                onConfirm = {
                    showLogoutDialog = false
                    onLogout()
                }
            )
        }

        // Show Rules AI dialog if needed
        if (showRulesDialog) {
            RulesAIDialog(
                initialRules = currentRules,
                onDismiss = { showRulesDialog = false },
                onSave = { newRules ->
                    currentRules = newRules
                    showRulesDialog = false
                    Toast.makeText(context, "Đã lưu Rules AI", Toast.LENGTH_SHORT).show()
                }
            )
        }

        // Show Delete All Chats confirmation dialogs if needed
        if (showFirstDeleteDialog) {
            DeleteAllChatsFirstConfirmationDialog(
                onDismiss = { showFirstDeleteDialog = false },
                onConfirm = {
                    showFirstDeleteDialog = false
                    showSecondDeleteDialog = true
                }
            )
        }

        if (showSecondDeleteDialog) {
            DeleteAllChatsSecondConfirmationDialog(
                onDismiss = { showSecondDeleteDialog = false },
                onConfirm = {
                    showSecondDeleteDialog = false
                    showDeleteAllChatsLoading = true
                    
                    // Sử dụng viewModelScope để xóa tất cả lịch sử chat
                    chatViewModel.viewModelScope.launch {
                        try {
                            onDeleteAllChats()
                        } finally {
                            showDeleteAllChatsLoading = false
                            onDismiss()
                        }
                    }
                }
            )
        }

        // Show Delete Account confirmation dialogs if needed
        if (showFirstDeleteAccountDialog) {
            DeleteAccountFirstConfirmationDialog(
                onDismiss = { showFirstDeleteAccountDialog = false },
                onConfirm = {
                    showFirstDeleteAccountDialog = false
                    showSecondDeleteAccountDialog = true
                }
            )
        }

        if (showSecondDeleteAccountDialog) {
            DeleteAccountSecondConfirmationDialog(
                onDismiss = { showSecondDeleteAccountDialog = false },
                onConfirm = {
                    showSecondDeleteAccountDialog = false
                    
                    // Xóa tài khoản Firebase thực sự
                    val user = FirebaseAuth.getInstance().currentUser
                    if (user != null) {
                        // Thực hiện xác thực lại trước khi xóa tài khoản
                        user.delete()
                            .addOnSuccessListener {
                                // Hiển thị thông báo thành công
                                Toast.makeText(
                                    context,
                                    "Đã xóa tài khoản thành công",
                                    Toast.LENGTH_LONG
                                ).show()
                                
                                // Đăng xuất sau khi xóa
                                FirebaseAuth.getInstance().signOut()
                                onLogout()
                            }
                            .addOnFailureListener { error ->
                                if (error.message?.contains("requires recent authentication") == true) {
                                    // Xác thực lại trước khi xóa
                                    FirebaseAuth.getInstance().currentUser?.let { currentUser ->
                                        // Lấy provider ID để biết người dùng đã đăng nhập bằng phương thức nào
                                        val providers = currentUser.providerData
                                        val isGoogleUser = providers.any { it.providerId == "google.com" }
                                        
                                        if (isGoogleUser) {
                                            Toast.makeText(
                                                context,
                                                "Vui lòng đăng xuất và đăng nhập lại để xóa tài khoản Google",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            Toast.makeText(
                                                context,
                                                "Vui lòng đăng nhập lại để xóa tài khoản: ${error.message}",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                        
                                        // Đăng xuất để người dùng đăng nhập lại
                                        FirebaseAuth.getInstance().signOut()
                                        onLogout()
                                    }
                                } else {
                                    // Các lỗi khác
                                    Toast.makeText(
                                        context,
                                        "Lỗi khi xóa tài khoản: ${error.message}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            }
                    }
                    onDismiss()
                }
            )
        }
    }
}

@Composable
fun CategoryHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Divider(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp)
                .align(Alignment.CenterVertically),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        )
    }
}

@Composable
fun SettingsCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    onClick: () -> Unit,
    isLoading: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val elevation by animateDpAsState(
        targetValue = if (isPressed) 1.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "elevation"
    )
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = tween(150),
        label = "scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = elevation
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = iconTint
                    )
                } else {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun LogoutConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Xác nhận đăng xuất",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Bạn có chắc chắn muốn đăng xuất?",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text("Hủy")
                    }
                    
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Đăng xuất")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RulesAIDialog(
    initialRules: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var rulesText by remember { mutableStateOf(initialRules) }
    val context = LocalContext.current
    val chatViewModel = hiltViewModel<ChatViewModel>()
    val chatRepository = chatViewModel.getChatRepository()
    
    // Trạng thái bật/tắt rules
    var isRulesEnabled by remember { mutableStateOf(chatRepository.isRulesEnabled) }
    
    // Sử dụng skipPartiallyExpanded = true để mở full màn hình ngay lập tức
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { 
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Divider(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .padding(bottom = 24.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Rule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Thiết lập Rules AI",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Switch control
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRulesEnabled) 
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    else 
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Áp dụng Rules",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = if (isRulesEnabled) 
                                "AI sẽ áp dụng quy tắc này khi trả lời"
                            else 
                                "AI sẽ không áp dụng quy tắc này khi trả lời",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isRulesEnabled) 
                                MaterialTheme.colorScheme.primary
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Switch(
                        checked = isRulesEnabled,
                        onCheckedChange = { isRulesEnabled = it },
                        thumbContent = {
                            Icon(
                                imageVector = if (isRulesEnabled) Icons.Filled.Check else Icons.Filled.Close,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }
            }

            // Text input area
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 2.dp,
                shadowElevation = 2.dp
            ) {
                OutlinedTextField(
                    value = rulesText,
                    onValueChange = { rulesText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 400.dp)
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    placeholder = { 
                        Text(
                            "Ví dụ: Luôn trả lời bằng tiếng Việt, đóng vai trò là một trợ lý...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        ) 
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    enabled = isRulesEnabled,
                    textStyle = MaterialTheme.typography.bodyLarge
                )
            }

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Hủy")
                }
                
                Button(
                    onClick = {
                        chatViewModel.viewModelScope.launch {
                            chatRepository.updateRulesAI(rulesText, isRulesEnabled)
                            onSave(rulesText)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    elevation = ButtonDefaults.buttonElevation(
                        defaultElevation = 4.dp,
                        pressedElevation = 1.dp
                    )
                ) {
                    Text("Lưu")
                }
            }
        }
    }
}

@Composable
fun DeleteAllChatsFirstConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Xác nhận xóa",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Bạn có chắc chắn muốn xóa toàn bộ lịch sử đoạn chat?",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text("Hủy")
                    }
                    
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Xóa")
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteAllChatsSecondConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    // Biến đếm ngược từ 10 đến 0
    var countdown by remember { mutableStateOf(10) }
    // Biến kiểm soát việc hiển thị nút Delete
    var enableDeleteButton by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = 1f - (countdown / 10f),
        animationSpec = tween(1000),
        label = "countdown"
    )
    
    // Sử dụng LaunchedEffect để bắt đầu đếm ngược khi dialog hiển thị
    LaunchedEffect(key1 = true) {
        // Đếm ngược từ 10 về 0
        while (countdown > 0) {
            delay(1000) // Đợi 1 giây
            countdown--
        }
        // Kích hoạt nút sau khi đếm ngược xong
        enableDeleteButton = true
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                // Tiêu đề cảnh báo
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    if (!enableDeleteButton) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.error,
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = countdown.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Xác nhận lần cuối",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Hành động này không thể hoàn tác. Bạn có thực sự muốn xóa tất cả lịch sử?",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text("Hủy")
                    }
                    
                    Button(
                        onClick = onConfirm,
                        enabled = enableDeleteButton,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            disabledContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            disabledContentColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(if (!enableDeleteButton) countdown.toString() else "Xóa tất cả")
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteAccountFirstConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PersonRemove,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Xác nhận xóa tài khoản",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "Bạn có chắc chắn muốn xóa tài khoản của mình?",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text("Hủy")
                    }
                    
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text("Xóa")
                    }
                }
            }
        }
    }
}

@Composable
fun DeleteAccountSecondConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    // Tương tự như DeleteAllChatsSecondConfirmationDialog
    var countdown by remember { mutableStateOf(10) }
    var enableDeleteButton by remember { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = 1f - (countdown / 10f),
        animationSpec = tween(1000),
        label = "countdown"
    )
    
    LaunchedEffect(key1 = true) {
        while (countdown > 0) {
            delay(1000)
            countdown--
        }
        enableDeleteButton = true
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background,
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.errorContainer)
                        .align(Alignment.CenterHorizontally),
                    contentAlignment = Alignment.Center
                ) {
                    if (!enableDeleteButton) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            CircularProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.fillMaxSize(),
                                color = MaterialTheme.colorScheme.error,
                                strokeWidth = 3.dp
                            )
                            Text(
                                text = countdown.toString(),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Xác nhận lần cuối",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = "CẢNH BÁO: Tài khoản của bạn sẽ bị xóa vĩnh viễn và không thể khôi phục. Bạn có thực sự muốn tiếp tục?",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Text("Hủy")
                    }
                    
                    Button(
                        onClick = onConfirm,
                        enabled = enableDeleteButton,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            disabledContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            disabledContentColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Text(if (!enableDeleteButton) countdown.toString() else "Xóa tài khoản")
                    }
                }
            }
        }
    }
}
