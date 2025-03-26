sealed class Screen(val route: String) {
    // ... existing objects ...
    
    object ForgotPassword : Screen("forgot_password/{email}") {
        // Tạo hàm tạo route với tham số email
        fun createRoute(email: String): String {
            return "forgot_password/${Uri.encode(email)}"
        }
    }
} 