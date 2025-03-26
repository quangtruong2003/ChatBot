// loginlogout/LoginScreen.kt
package com.ahmedapps.geminichatbot.loginlogout

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahmedapps.geminichatbot.R
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.scale
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.net.URLEncoder
import java.net.URLDecoder
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding

@OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalAnimationApi::class
)
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    onNavigateToForgotPassword: (String) -> Unit, // Lambda nhận email
    viewModel: AuthViewModel = hiltViewModel(),
    sharedViewModel: SharedAuthViewModel = hiltViewModel()
) {
    val authState by viewModel.authState.collectAsState()
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var emailFocused by remember { mutableStateOf(false) }
    var passwordFocused by remember { mutableStateOf(false) }
    var isEmailValid by remember { mutableStateOf(false) }
    var isGoogleSignInLoading by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val defaultWebClientId = stringResource(id = R.string.default_web_client_id)
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val emailFocusRequester = remember { FocusRequester() } // Focus requester cho email
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var isKeyboardVisible by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    // Hàm kiểm tra email hợp lệ
    fun validateEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    // Kiểm tra email khi giá trị thay đổi
    LaunchedEffect(email) {
        isEmailValid = validateEmail(email)
    }

    // Animation states
    var startAnimation by remember { mutableStateOf(false) }

    // Animations (giữ nguyên)
    val logoSize by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.5f,
        animationSpec = tween(500, easing = EaseOutCubic), label = "logoSize"
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(600, delayMillis = 100), label = "titleAlpha"
    )
    val cardOffset by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 50.dp,
        animationSpec = tween(700, delayMillis = 200, easing = EaseOutCubic), label = "cardOffset"
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.8f,
        animationSpec = tween(700, delayMillis = 400, easing = EaseOutCubic), label = "buttonScale"
    )

    // Trigger animations và focus vào email khi màn hình xuất hiện
    LaunchedEffect(Unit) {
        delay(100)
        startAnimation = true
        delay(500) // Chờ animation chạy xong
        // Tự động focus vào trường email khi màn hình mở
        // emailFocusRequester.requestFocus() // Tạm ẩn nếu không muốn tự focus
    }

    // Snackbar host state
    val snackbarHostState = remember { SnackbarHostState() }

    // Google Sign-In Configuration
    val gso = remember(defaultWebClientId) {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(defaultWebClientId)
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    // Google Sign-In Launcher
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isGoogleSignInLoading = false
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                val account = task.getResult(ApiException::class.java)
                account?.idToken?.let { idToken ->
                    viewModel.firebaseAuthWithGoogle(idToken)
                }
            } catch (e: ApiException) {
                Log.e("LoginScreen", "Đăng nhập Google thất bại", e)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Đăng nhập Google thất bại: ${e.message}")
                }
            }
        } else {
            Log.w("LoginScreen", "Đăng nhập Google bị hủy hoặc thất bại với resultCode: ${result.resultCode}")
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Đăng nhập Google bị hủy")
            }
        }
    }

    // Handle authentication state changes
    LaunchedEffect(authState.isSuccess) {
        if (authState.isSuccess) {
            keyboardController?.hide()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(300) // Delay for animation or feedback
            onLoginSuccess()
            viewModel.resetState() // Reset state sau khi thành công
        }
    }

    // Show error message in Snackbar
    LaunchedEffect(authState.errorMessage) {
        authState.errorMessage?.let { message ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            snackbarHostState.showSnackbar(message)
            viewModel.updateError(null) // Reset lỗi sau khi hiển thị
        }
    }

    // Lấy giá trị màu sắc
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val surfaceColor = MaterialTheme.colorScheme.surface
    val secondaryContainerColorAlpha = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
    val primaryColorAlpha = primaryColor.copy(alpha = 0.05f)
    val tertiaryColorAlpha = tertiaryColor.copy(alpha = 0.05f)
    val outlineVariantColor = MaterialTheme.colorScheme.outlineVariant
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val surfaceVariantAlphaColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val outlineColor = MaterialTheme.colorScheme.outline
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val errorColor = MaterialTheme.colorScheme.error

    // Background và Canvas (giữ nguyên)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(surfaceColor, surfaceColor, secondaryContainerColorAlpha)
                )
            )
    ) {
        // Decorative background elements
        Canvas(modifier = Modifier.fillMaxSize()) { /* ... Vẽ background ... */
            val canvasWidth = size.width
            val canvasHeight = size.height
            drawCircle(
                color = primaryColorAlpha,
                center = Offset(canvasWidth * 0.8f, canvasHeight * 0.2f),
                radius = canvasWidth * 0.3f
            )
            drawCircle(
                color = tertiaryColorAlpha,
                center = Offset(canvasWidth * 0.2f, canvasHeight * 0.8f),
                radius = canvasWidth * 0.25f
            )
            val path = Path().apply {
                moveTo(0f, canvasHeight * 0.3f)
                quadraticBezierTo(
                    canvasWidth * 0.5f, canvasHeight * 0.2f,
                    canvasWidth, canvasHeight * 0.4f
                )
            }
            drawPath(path = path, color = primaryColorAlpha, style = Stroke(width = 2.dp.toPx()))
        }

        // Main content
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHostState() }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(24.dp)
                    .imePadding(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(40.dp))

                // App Logo with animation (giữ nguyên)
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(logoSize) // Sử dụng scale thay cho graphicsLayer nếu chỉ cần scale
                        .drawBehind { /* ... vẽ vòng tròn gradient ... */
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(primaryContainerColor, primaryContainerColor.copy(alpha = 0f))
                                ),
                                radius = size.width * 0.7f
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app),
                        contentDescription = "App Logo",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .padding(8.dp) // Có thể giữ lại hoặc điều chỉnh padding tùy ý
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Welcome Text with animation (giữ nguyên)
                Text(
                    text = "Chào mừng trở lại!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .alpha(titleAlpha)
                        .padding(8.dp)
                )
                Text(
                    text = "Đăng nhập để tiếp tục cuộc trò chuyện với ChatAI",
                    style = MaterialTheme.typography.bodyLarge,
                    color = onSurfaceVariantColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .alpha(titleAlpha)
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Login Card with animation
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = cardOffset)
                        .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = surfaceColor)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Email Field
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email", style = MaterialTheme.typography.bodyMedium) },
                            leadingIcon = {
                                AnimatedContent(
                                    targetState = emailFocused,
                                    transitionSpec = { fadeIn(tween(150)) with fadeOut(tween(150)) },
                                    label = "EmailIconAnimation"
                                ) { focused ->
                                    Icon(
                                        imageVector = if (focused) Icons.Filled.AlternateEmail else Icons.Outlined.Email,
                                        contentDescription = null,
                                        tint = if (focused) primaryColor else onSurfaceVariantColor
                                    )
                                }
                            },
                            trailingIcon = {
                                if (email.isNotEmpty()) {
                                    AnimatedVisibility(
                                        visible = isEmailValid,
                                        enter = scaleIn(), exit = scaleOut()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Email hợp lệ",
                                            tint = Color.Green // Hoặc primaryColor
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(emailFocusRequester) // Gắn focus requester
                                .onFocusChanged { emailFocused = it.isFocused }
                                .padding(bottom = 8.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors( // Sử dụng API M3 mới
                                cursorColor = primaryColor,
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = outlineColor,
                                focusedContainerColor = surfaceVariantAlphaColor,
                                unfocusedContainerColor = surfaceVariantAlphaColor,
                                focusedLeadingIconColor = primaryColor,
                                unfocusedLeadingIconColor = onSurfaceVariantColor,
                                // Không cần error colors ở đây trừ khi bạn thêm logic isError
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = if (isEmailValid) ImeAction.Next else ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { if (isEmailValid) focusManager.moveFocus(FocusDirection.Down) },
                                onDone = { keyboardController?.hide() }
                            )
                        )

                        // Password field và các nút bên dưới chỉ hiển thị khi email hợp lệ
                        AnimatedVisibility(
                            visible = isEmailValid,
                            enter = expandVertically(tween(300)) + fadeIn(tween(300)),
                            exit = shrinkVertically(tween(300)) + fadeOut(tween(300))
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { // Đảm bảo Column bao quanh
                                Spacer(modifier = Modifier.height(8.dp))

                                // Password Field
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Mật khẩu", style = MaterialTheme.typography.bodyMedium) },
                                    leadingIcon = {
                                        AnimatedContent(
                                            targetState = passwordFocused,
                                            transitionSpec = { fadeIn(tween(150)) with fadeOut(tween(150)) },
                                            label = "PasswordIconAnimation"
                                        ) { focused ->
                                            Icon(
                                                imageVector = if (focused) Icons.Filled.Lock else Icons.Outlined.Lock,
                                                contentDescription = null,
                                                tint = if (focused) primaryColor else onSurfaceVariantColor
                                            )
                                        }
                                    },
                                    trailingIcon = {
                                        if (password.isNotEmpty()) {
                                            IconButton(onClick = {
                                                isPasswordVisible = !isPasswordVisible
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }) {
                                                AnimatedContent(
                                                    targetState = isPasswordVisible,
                                                    transitionSpec = {
                                                        scaleIn(
                                                            animationSpec = tween(150),
                                                            initialScale = 0.8f
                                                        ) with scaleOut(
                                                            animationSpec = tween(150),
                                                            targetScale = 0.8f
                                                        )
                                                    },
                                                    label = "PasswordVisibilityAnimation"
                                                ) { visible ->
                                                    Icon(
                                                        imageVector = if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                                        contentDescription = if (visible) "Ẩn mật khẩu" else "Hiện mật khẩu",
                                                        tint = if (passwordFocused) primaryColor else onSurfaceVariantColor
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onFocusChanged { state ->
                                            passwordFocused = state.isFocused
                                            if (state.isFocused) {
                                                isKeyboardVisible = true
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                                // Đảm bảo cuộn đến vị trí này khi được focus
                                                coroutineScope.launch {
                                                    delay(100) // Chờ một chút để bàn phím bắt đầu xuất hiện
                                                    scrollState.animateScrollTo((scrollState.maxValue * 0.15f).toInt())
                                                }
                                            }
                                        },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors( // API M3 mới
                                        cursorColor = primaryColor,
                                        focusedBorderColor = primaryColor,
                                        unfocusedBorderColor = outlineColor,
                                        focusedContainerColor = surfaceVariantAlphaColor,
                                        unfocusedContainerColor = surfaceVariantAlphaColor,
                                        focusedLeadingIconColor = primaryColor,
                                        unfocusedLeadingIconColor = onSurfaceVariantColor,
                                        focusedTrailingIconColor = primaryColor,
                                        unfocusedTrailingIconColor = onSurfaceVariantColor
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onDone = {
                                            keyboardController?.hide()
                                            if (email.isNotEmpty() && password.isNotEmpty()) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.login(email.trim(), password)
                                            }
                                        }
                                    )
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // Forgot Password Button
                                TextButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        Log.d("LoginScreen", "User pressed Forgot Password with email='$email'")
                                        
                                        // Lưu email vào EmailDataHolder
                                        EmailDataHolder.setEmail(email.trim())
                                        
                                        // Điều hướng đến màn hình quên mật khẩu
                                        onNavigateToForgotPassword(email.trim())
                                    },
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text(
                                        "Quên Mật Khẩu?",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = primaryColor
                                    )
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Login Button
                                Button(
                                    onClick = {
                                        keyboardController?.hide()
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.login(email.trim(), password)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp)
                                        .scale(buttonScale), // Sử dụng scale thay cho graphicsLayer
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = primaryColor,
                                        contentColor = onPrimaryColor
                                    ),
                                    enabled = !authState.isLoading && email.isNotEmpty() && password.isNotEmpty()
                                ) {
                                    AnimatedContent(
                                        targetState = authState.isLoading,
                                        transitionSpec = { fadeIn(tween(150)) with fadeOut(tween(150)) },
                                        label = "LoginButtonContent"
                                    ) { isLoading ->
                                        if (isLoading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                color = onPrimaryColor,
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Row(
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Đăng nhập", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Icon(Icons.Default.ArrowForward, null, Modifier.size(16.dp))
                                            }
                                        }
                                    }
                                }
                            } // Kết thúc Column cho phần mật khẩu và nút
                        } // Kết thúc AnimatedVisibility
                    } // Kết thúc Column trong Card
                } // Kết thúc Card

                Spacer(modifier = Modifier.height(24.dp))

                // Create Account Button (giữ nguyên)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .alpha(titleAlpha) // Sử dụng alpha từ animation
                        .padding(8.dp)
                ) {
                    Text("Chưa có tài khoản?", color = onSurfaceVariantColor)
                    TextButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToRegister()
                    }) {
                        Text("Đăng ký ngay", color = primaryColor, fontWeight = FontWeight.Bold)
                    }
                }

                // Divider with OR text (giữ nguyên)
                AnimatedVisibility(
                    visible = startAnimation,
                    enter = fadeIn(tween(500, delayMillis = 300))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp, horizontal = 16.dp)
                    ) {
                        Divider(Modifier.weight(1f), color = outlineVariantColor)
                        Text("  HOẶC  ", style = MaterialTheme.typography.bodySmall, color = onSurfaceVariantColor, modifier = Modifier.padding(horizontal = 8.dp))
                        Divider(Modifier.weight(1f), color = outlineVariantColor)
                    }
                }

                // Google Sign In Button (giữ nguyên)
                AnimatedVisibility(
                    visible = startAnimation,
                    enter = fadeIn(tween(500, delayMillis = 400)) + expandVertically(tween(500, easing = EaseOutCubic)),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column { // Cần Column bao quanh OutlinedButton
                        // Shimmer effect (giữ nguyên)
                        val shimmerColors = listOf( /* ... */ Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.5f), Color.White.copy(alpha = 0.3f))
                        val transition = rememberInfiniteTransition(label = "shimmerTransition")
                        val translateAnim = transition.animateFloat( /* ... */ initialValue = 0f, targetValue = 1000f, animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart), label = "shimmerTranslateAnim")

                        OutlinedButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isGoogleSignInLoading = true
                                try {
                                    val signInIntent = googleSignInClient.signInIntent
                                    launcher.launch(signInIntent)
                                } catch (e: Exception) {
                                    // Xử lý nếu có lỗi khi tạo intent (ít gặp)
                                    Log.e("LoginScreen", "Lỗi khi lấy Google Sign In Intent", e)
                                    isGoogleSignInLoading = false
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Không thể bắt đầu đăng nhập Google.")
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(16.dp)), // Clip trước border cho hiệu ứng shimmer đẹp hơn
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(
                                width = 1.dp,
                                brush = Brush.linearGradient(
                                    colors = shimmerColors,
                                    start = Offset(translateAnim.value - 1000f, 0f),
                                    end = Offset(translateAnim.value, 0f)
                                )
                            ),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = onSurfaceColor,
                                containerColor = surfaceColor // Hoặc Color.Transparent nếu muốn nền trong suốt hơn
                            ),
                            enabled = !isGoogleSignInLoading && !authState.isLoading // Disable khi đang loading bất kỳ
                        ) {
                            AnimatedContent(
                                targetState = isGoogleSignInLoading,
                                transitionSpec = {
                                    scaleIn(
                                        animationSpec = tween(150),
                                        initialScale = 0.8f
                                    ) + fadeIn(
                                        animationSpec = tween(150)
                                    ) with scaleOut(
                                        animationSpec = tween(150),
                                        targetScale = 0.8f
                                    ) + fadeOut(
                                        animationSpec = tween(150)
                                    )
                                },
                                label = "GoogleSignInButtonContent"
                            ) { isLoading ->
                                if (isLoading) {
                                    CircularProgressIndicator(Modifier.size(24.dp), color = primaryColor, strokeWidth = 2.dp)
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(painterResource(R.drawable.ic_google), null, tint=Color.Unspecified, modifier = Modifier.size(24.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Text("Đăng nhập bằng Google", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }

                // Spacer cuối cùng để đảm bảo cuộn đủ
                Spacer(modifier = Modifier.height(100.dp))
            } // Kết thúc Column chính
        } // Kết thúc Scaffold
    } // Kết thúc Box background
}

@Composable
fun LoginScreenNavHost(
    onLoginSuccess: () -> Unit,
    onNavigateToRegister: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onLoginSuccess = onLoginSuccess,
                onNavigateToRegister = onNavigateToRegister,
                onNavigateToForgotPassword = { email ->
                    // Không cần làm gì đặc biệt ở đây - email đã được lưu trong EmailDataHolder
                    navController.navigate("forgot_password")
                },
                viewModel = viewModel
            )
        }

        composable("forgot_password") {
            ForgotPasswordScreen(
                onBackToLogin = { 
                    // Xóa email khi quay lại màn hình đăng nhập (tùy chọn)
                    EmailDataHolder.clearEmail()
                    navController.popBackStack() 
                }
            )
        }
    }
}