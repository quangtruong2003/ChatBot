@Composable
fun LoginScreen(
    state: LoginState,
    onSignInClick: (String, String) -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: (String) -> Unit
) {
    // ... existing code ...
    
    // Thêm state để lưu trữ email đang nhập
    var emailState by remember { mutableStateOf("") }
    
    // ... existing code ...
    
    // Email Field
    OutlinedTextField(
        value = emailState,
        onValueChange = { emailState = it },
        // ... existing code ...
    )
    
    // ... existing code ...
    
    // Quên mật khẩu Text Button
    TextButton(
        onClick = {
            // Truyền email hiện tại khi chuyển đến màn hình quên mật khẩu
            onNavigateToForgotPassword(emailState.trim())
        }
    ) {
        Text("Quên mật khẩu?")
    }
    
    // ... existing code ...
} 