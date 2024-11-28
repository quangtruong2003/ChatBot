// ForgotPasswordScreen.kt
package com.ahmedapps.geminichatbot.loginlogout

import android.util.Patterns
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

// Data class để quản lý trạng thái
data class ForgotPasswordState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

// ViewModel xử lý logic cho Quên Mật Khẩu
@HiltViewModel
class ForgotPasswordViewModel @Inject constructor(
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _state = MutableStateFlow(ForgotPasswordState())
    val state: StateFlow<ForgotPasswordState> = _state.asStateFlow()

    fun resetPassword(email: String) {
        if (email.isEmpty()) {
            _state.value = ForgotPasswordState(errorMessage = "Email không được để trống")
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _state.value = ForgotPasswordState(errorMessage = "Email không hợp lệ")
            return
        }

        _state.value = ForgotPasswordState(isLoading = true)

        viewModelScope.launch {
            try {
                auth.sendPasswordResetEmail(email).await()
                _state.value = ForgotPasswordState(isSuccess = true)
            } catch (e: Exception) {
                val error = when (e) {
                    is FirebaseAuthInvalidUserException -> "Email không tồn tại trong hệ thống"
                    else -> e.localizedMessage ?: "Đã xảy ra lỗi, vui lòng thử lại sau"
                }
                _state.value = ForgotPasswordState(errorMessage = error)
            }
        }
    }

    fun resetState() {
        _state.value = ForgotPasswordState()
    }
}

// Composable function cho màn hình Quên Mật Khẩu
@Composable
fun ForgotPasswordScreen(
    onBackToLogin: () -> Unit,
    viewModel: ForgotPasswordViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var email by remember { mutableStateOf("") }

    // Hiển thị thông báo thành công
    if (state.isSuccess) {
        AlertDialog(
            onDismissRequest = {
                viewModel.resetState()
                onBackToLogin()
            },
            title = { Text("Thành công") },
            text = { Text("Email đặt lại mật khẩu đã được gửi. Vui lòng kiểm tra hộp thư của bạn.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetState()
                    onBackToLogin()
                }) {
                    Text("OK")
                }
            }
        )
    }

    // Hiển thị thông báo lỗi
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message)
            }
            viewModel.resetState()
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Quên Mật Khẩu",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Email TextField
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Reset Password Button
                    Button(
                        onClick = { viewModel.resetPassword(email.trim()) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !state.isLoading
                    ) {
                        Text("Đặt lại mật khẩu")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Loading Indicator
                    if (state.isLoading) {
                        CircularProgressIndicator()
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Back to Login
                    TextButton(onClick = onBackToLogin) {
                        Text("Quay lại đăng nhập")
                    }
                }
            }
        }
    }
}
