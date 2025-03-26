// Tạo hàm helper để tạo route quên mật khẩu với email
fun createForgotPasswordRoute(email: String): String {
    return "forgot_password/${Uri.encode(email)}"
}

@Composable
fun AppNavHost(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        // ... existing code ...

        // Màn hình đăng nhập
        composable(
            route = Screen.Login.route
        ) {
            val viewModel = hiltViewModel<LoginViewModel>()
            val state by viewModel.state.collectAsState()

            LoginScreen(
                state = state,
                onSignInClick = { email, password ->
                    viewModel.signInUser(email, password)
                },
                onNavigateToRegister = {
                    navController.navigate(Screen.Register.route)
                },
                onNavigateToForgotPassword = { emailFromLogin ->
                    // Truyền email từ màn hình đăng nhập sang quên mật khẩu
                    navController.navigate(createForgotPasswordRoute(emailFromLogin))
                }
            )
        }

        // Màn hình quên mật khẩu - sử dụng tham số route
        composable(
            route = "forgot_password/{email}",
            arguments = listOf(
                navArgument("email") {
                    type = NavType.StringType
                    defaultValue = ""
                    nullable = true
                }
            )
        ) { backStackEntry ->
            val emailArg = backStackEntry.arguments?.getString("email") ?: ""
            
            ForgotPasswordScreen(
                onBackToLogin = {
                    navController.navigate(Screen.Login.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                },
                email = emailArg
            )
        }

        // ... existing code ...
    }
} 