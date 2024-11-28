// RegistrationScreen.kt
package com.ahmedapps.geminichatbot.loginlogout

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahmedapps.geminichatbot.R
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.delay

@Composable
fun RegistrationScreen(
    onRegistrationSuccess: () -> Unit,
    onNavigateToLogin: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
    auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    val authState by viewModel.authState.collectAsState()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isPasswordVisible1 by rememberSaveable { mutableStateOf(false) }
    var isPasswordVisible2 by rememberSaveable { mutableStateOf(false) }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current
    val defaultWebClientId = stringResource(id = R.string.default_web_client_id)

    val snackbarHostState = remember { SnackbarHostState() }
    var showLoadingDialog by remember { mutableStateOf(false) } // Trạng thái hiển thị hộp thoại
    var countdownTime by remember { mutableStateOf(5) } // Biến đếm ngược
    val coroutineScope = rememberCoroutineScope()

    // Cấu hình Đăng nhập Google
    val gso = remember(defaultWebClientId) {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(defaultWebClientId)
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    // Khởi tạo launcher cho Đăng nhập Google
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
        onResult = { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                try {
                    val account = task.getResult(ApiException::class.java)
                    account?.idToken?.let { idToken ->
                        viewModel.firebaseAuthWithGoogle(idToken)
                    }
                } catch (e: ApiException) {
                    Log.e("RegistrationScreen", "Google sign-in failed", e)
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar("Đăng nhập Google thất bại: ${e.message}")
                    }
                }
            } else {
                Log.e("RegistrationScreen", "Google sign-in canceled or failed")
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Đăng nhập Google đã bị hủy")
                }
            }
        }
    )

    LaunchedEffect(authState.isSuccess) {
        if (authState.isSuccess) {
            // Gửi email xác minh
            try {
                auth.currentUser?.sendEmailVerification()?.await()
                Log.d("RegistrationScreen", "Verification email sent to ${auth.currentUser?.email}")
            } catch (e: Exception) {
                Log.e("RegistrationScreen", "Failed to send verification email", e)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Không thể gửi email xác minh: ${e.message}")
                }
            }
            // Hiển thị hộp thoại đếm ngược
            showLoadingDialog = true
        }
    }

    // Logic đếm ngược
    LaunchedEffect(showLoadingDialog) {
        if (showLoadingDialog) {
            for (i in 5 downTo 1) {
                countdownTime = i
                delay(1000)
            }
            showLoadingDialog = false
            onNavigateToLogin() // Quay lại màn hình đăng nhập
            viewModel.resetState() // Đặt lại trạng thái sau khi đăng ký thành công
        }
    }

    // Hiển thị thông báo lỗi
    LaunchedEffect(authState.errorMessage) {
        authState.errorMessage?.let { message ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(message)
            }
            viewModel.updateError(null)
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
                    Image(
                        painter = painterResource(id = R.drawable.app),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Tạo tài khoản",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Đăng ký để bắt đầu",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("Email") },
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Mật khẩu") },
                        visualTransformation = if (isPasswordVisible1 && password.isNotEmpty()) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.lock),
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            if (password.isNotEmpty()) {
                                IconButton(onClick = { isPasswordVisible1 = !isPasswordVisible1 }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible1) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isPasswordVisible1) "Ẩn mật khẩu" else "Hiện mật khẩu"
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        label = { Text("Xác nhận mật khẩu") },
                        visualTransformation = if (isPasswordVisible2 && confirmPassword.isNotEmpty()) VisualTransformation.None else PasswordVisualTransformation(),
                        leadingIcon = {
                            Icon(
                                painter = painterResource(id = R.drawable.lock),
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            if (confirmPassword.isNotEmpty()) {
                                IconButton(onClick = { isPasswordVisible2 = !isPasswordVisible2 }) {
                                    Icon(
                                        imageVector = if (isPasswordVisible2) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (isPasswordVisible2) "Ẩn mật khẩu" else "Hiện mật khẩu"
                                    )
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            if (password == confirmPassword) {
                                viewModel.register(email.trim(), password)
                            } else {
                                viewModel.updateError("Mật khẩu không khớp")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = !authState.isLoading
                    ) {
                        Text("Đăng ký")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Đã có tài khoản?")
                        TextButton(onClick = onNavigateToLogin) {
                            Text("Đăng nhập")
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Divider với "HOẶC"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Divider(modifier = Modifier.weight(1f))
                        Text("  HOẶC  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Divider(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Nút Đăng ký với Google
                    OutlinedButton(
                        onClick = {
                            val signInIntent = googleSignInClient.signInIntent
                            launcher.launch(signInIntent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_google),
                            contentDescription = null,
                            tint = Color.Unspecified,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Đăng nhập với Google")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Hiển thị tiến trình tải
                    if (authState.isLoading && !showLoadingDialog) {
                        CircularProgressIndicator()
                    }
                }
            }

            // Hộp thoại thông báo đếm ngược
            if (showLoadingDialog) {
                AlertDialog(
                    onDismissRequest = { /* Không cho phép đóng hộp thoại */ },
                    title = { Text("Vui lòng kiểm tra email để xác thực tài khoản.") },
                    text = {
                        Text("Hộp thoại này sẽ đóng sau $countdownTime giây.")
                    },
                    confirmButton = {},
                    dismissButton = {}
                )
            }
        }
    }
}
