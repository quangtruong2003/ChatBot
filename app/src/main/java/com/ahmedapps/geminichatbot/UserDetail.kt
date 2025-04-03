package com.ahmedapps.geminichatbot

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.filled.ChevronRight
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.Color

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
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var currentRules by remember { mutableStateOf("") }
    var showFirstDeleteDialog by remember { mutableStateOf(false) }
    var showSecondDeleteDialog by remember { mutableStateOf(false) }
    var showFirstDeleteAccountDialog by remember { mutableStateOf(false) }
    var showSecondDeleteAccountDialog by remember { mutableStateOf(false) }
    
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { 
            // Custom drag handle for the bottom sheet
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 24.dp), // Extra padding at bottom for navigation gestures
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Thông tin tài khoản",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // Avatar
            Card(
                shape = CircleShape,
                modifier = Modifier
                    .size(120.dp)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    if (photoUrl != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(photoUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Ảnh đại diện",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Ảnh đại diện mặc định",
                            modifier = Modifier.size(60.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Email info (with copy function)
            Card(
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Email:",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    IconButton(onClick = {
                        clipboardManager.setText(AnnotatedString(email))
                        Toast.makeText(context, "Đã sao chép email", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(
                            imageVector = Icons.Default.CopyAll,
                            contentDescription = "Sao chép email"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Rules AI Section (Clickable Card)
            Card(
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showRulesDialog = true },
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Rule,
                            contentDescription = "Thiết lập Rules AI",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Thiết lập Rules AI",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Delete All Chats Section (Clickable Card)
            Card(
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showFirstDeleteDialog = true },
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Xóa tất cả lịch sử chat",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Xóa tất cả lịch sử chat",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Delete Account Section (Clickable Card)
            Card(
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showFirstDeleteAccountDialog = true },
                elevation = CardDefaults.cardElevation(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DeleteForever,
                            contentDescription = "Xóa tài khoản",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Xóa tài khoản",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Logout button
            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.error
                )
            ) {
                Icon(
                    imageVector = Icons.Default.ExitToApp,
                    contentDescription = "Đăng xuất",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Đăng xuất", fontWeight = FontWeight.Bold)
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
                    onDeleteAllChats()
                    onDismiss()
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
                    // Xóa tài khoản FirebaseAuth
                    FirebaseAuth.getInstance().currentUser?.delete()
                        ?.addOnSuccessListener {
                            // Đăng xuất sau khi xóa tài khoản
                            onLogout()
                        }
                        ?.addOnFailureListener { error ->
                            Toast.makeText(
                                context,
                                "Lỗi: ${error.message}",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    onDismiss()
                }
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
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Xác nhận đăng xuất",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Bạn có chắc chắn muốn đăng xuất?",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Hủy")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onConfirm) {
                        Text("Đồng ý")
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
    
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { 
            // Custom drag handle cho BottomSheet
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp)
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
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .padding(bottom = 24.dp), // Thêm padding bổ sung ở phía dưới cho cử chỉ điều hướng
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Rule,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Thiết lập Rules AI",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            // Text input area với khả năng cuộn
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 2.dp
            ) {
                OutlinedTextField(
                    value = rulesText,
                    onValueChange = { rulesText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 400.dp)
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    placeholder = { Text("Ví dụ: Luôn trả lời bằng tiếng Việt, đóng vai trò là một trợ lý...") },
                    label = null,
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    )
                )
            }

            // Hàng nút
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Hủy")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = {
                        onSave(rulesText)
                    },
                    shape = RoundedCornerShape(8.dp)
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
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Xác nhận xóa",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Bạn có chắc chắn muốn xóa toàn bộ lịch sử đoạn chat?",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Hủy")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onConfirm) {
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
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Xác nhận lần cuối",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Hành động này không thể hoàn tác. Bạn có thực sự muốn xóa tất cả lịch sử?",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Hủy")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Text("Xóa tất cả")
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
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Xác nhận xóa tài khoản",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Bạn có chắc chắn muốn xóa tài khoản của mình?",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Hủy")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = onConfirm) {
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
    // Biến đếm ngược từ 10 đến 0
    var countdown by remember { mutableStateOf(10) }
    // Biến kiểm soát việc hiển thị nút Delete
    var enableDeleteButton by remember { mutableStateOf(false) }
    
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
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Xác nhận lần cuối",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "CẢNH BÁO: Tài khoản của bạn sẽ bị xóa vĩnh viễn và không thể khôi phục. Bạn có thực sự muốn tiếp tục?",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Hủy")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        enabled = enableDeleteButton,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                            disabledContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            disabledContentColor = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        if (!enableDeleteButton) {
                            // Hiển thị thời gian đếm ngược trong nút
                            Text("${countdown}")
                        } else {
                            // Hiển thị văn bản nút bình thường khi đếm ngược kết thúc
                            Text("Xóa tài khoản")
                        }
                    }
                }
            }
        }
    }
}
