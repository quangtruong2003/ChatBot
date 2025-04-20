package com.ahmedapps.geminichatbot

import android.os.Bundle
import android.util.Base64
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ahmedapps.geminichatbot.data.ChatViewModel
import com.ahmedapps.geminichatbot.drawer.left.UserDetailBottomSheet
import com.ahmedapps.geminichatbot.loginlogout.LoginScreen
import com.ahmedapps.geminichatbot.loginlogout.ForgotPasswordScreen
import com.ahmedapps.geminichatbot.loginlogout.RegistrationScreen
import com.ahmedapps.geminichatbot.ui.screens.FullScreenImageScreen
import com.ahmedapps.geminichatbot.ui.theme.GeminiChatBotTheme
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint


@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var lastBackPressed: Long = 0
    private var currentDestination: String = ""

    override fun onBackPressed() {
        if (currentDestination == "chat") {
            if (System.currentTimeMillis() - lastBackPressed < 2000) {
                super.onBackPressed()
            } else {
                Toast.makeText(this, "Nhấn back lần nữa để thoát", Toast.LENGTH_SHORT).show()
                lastBackPressed = System.currentTimeMillis()
            }
        } else {
            super.onBackPressed()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            GeminiChatBotTheme(dynamicColor = true) {
                val navController = rememberNavController()
                val currentUser = FirebaseAuth.getInstance().currentUser
                val startDestination = if (currentUser != null) "chat" else "login"
                
                // Thiết lập giá trị ban đầu cho currentDestination
                currentDestination = startDestination
                
                // Theo dõi điểm đến hiện tại
                DisposableEffect(navController) {
                    val listener = NavController.OnDestinationChangedListener { _, destination, _ ->
                        currentDestination = destination.route?.split('/')?.firstOrNull() ?: ""
                    }
                    navController.addOnDestinationChangedListener(listener)
                    onDispose {
                        navController.removeOnDestinationChangedListener(listener)
                    }
                }
                
                // State to control user detail bottom sheet
                var showUserDetail by remember { mutableStateOf(false) }

                NavHost(navController, startDestination = startDestination) {
                    composable("login") {
                        LoginScreen(
                            onLoginSuccess = {
                                navController.navigate("chat") {
                                    popUpTo("login") { inclusive = true }
                                }
                            },
                            onNavigateToRegister = { navController.navigate("register") },
                            onNavigateToForgotPassword = { navController.navigate("forgot_password") }
                        )
                    }
                    composable("forgot_password") {
                        ForgotPasswordScreen(
                            onBackToLogin = { navController.popBackStack() }
                        )
                    }
                    composable("register") {
                        RegistrationScreen(
                            onRegistrationSuccess = {
                                navController.navigate("chat") {
                                    popUpTo("register") { inclusive = true }
                                }
                            },
                            onNavigateToLogin = {
                                navController.navigate("login") {
                                    popUpTo("register") { inclusive = true }
                                }
                            }
                        )
                    }
                    composable("chat") {
                        val chatViewModel = hiltViewModel<ChatViewModel>()
                        ChatScreen(
                            navController = navController,
                            onShowUserDetail = { showUserDetail = true }
                        )
                        
                        // Show user detail as bottom sheet when requested
                        if (showUserDetail) {
                            UserDetailBottomSheet(
                                onDismiss = { showUserDetail = false },
                                onLogout = {
                                    FirebaseAuth.getInstance().signOut()
                                    navController.navigate("login") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                    showUserDetail = false
                                },
                                onDeleteAllChats = {
                                    chatViewModel.clearChat()
                                }
                            )
                        }
                    }
                    composable(
                        "fullscreen_image/{encodedImageUrl}",
                        arguments = listOf(navArgument("encodedImageUrl") { type = NavType.StringType })
                    ) { backStackEntry ->
                        val encodedImageUrl = backStackEntry.arguments?.getString("encodedImageUrl") ?: ""
                        val imageUrl = String(Base64.decode(encodedImageUrl, Base64.URL_SAFE or Base64.NO_WRAP))
                        FullScreenImageScreen(
                            imageUrl = imageUrl,
                            onClose = { navController.popBackStack() }
                        )
                    }
                    // Removed separate user_detail screen as it's now a bottom sheet
                }
            }
        }
    }
}
