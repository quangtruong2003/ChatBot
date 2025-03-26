package com.ahmedapps.geminichatbot.loginlogout

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.util.Patterns
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
import androidx.compose.ui.draw.*
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ahmedapps.geminichatbot.R
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
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
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    var isPasswordVisible1 by rememberSaveable { mutableStateOf(false) }
    var isPasswordVisible2 by rememberSaveable { mutableStateOf(false) }
    var emailFocused by remember { mutableStateOf(false) }
    var passwordFocused by remember { mutableStateOf(false) }
    var confirmPasswordFocused by remember { mutableStateOf(false) }
    var isGoogleSignInLoading by remember { mutableStateOf(false) }
    
    // Thêm biến để theo dõi trạng thái bàn phím
    var isKeyboardVisible by remember { mutableStateOf(false) }
    
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val defaultWebClientId = stringResource(id = R.string.default_web_client_id)
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    
    // Focus requesters for form fields
    val emailFocusRequester = remember { FocusRequester() }
    val passwordFocusRequester = remember { FocusRequester() }
    val confirmPasswordFocusRequester = remember { FocusRequester() }

    // Animation states
    var startAnimation by remember { mutableStateOf(false) }
    
    // Animations
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
    
    // Password strength indicator animation
    val passwordStrength = remember(password) {
        calculatePasswordStrength(password)
    }
    
    val strengthColor = when (passwordStrength) {
        0 -> MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
        1 -> MaterialTheme.colorScheme.error
        2 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    
    val strengthText = remember(passwordStrength) {
        when (passwordStrength) {
            0 -> "Yếu"
            1 -> "Trung bình"
            2 -> "Khá"
            else -> "Mạnh"
        }
    }
    
    val strengthWidth by animateFloatAsState(
        targetValue = when (passwordStrength) {
            0 -> 0.25f
            1 -> 0.5f
            2 -> 0.75f
            else -> 1f
        },
        animationSpec = tween(300),
        label = "strengthWidth"
    )
    
    // Validation states
    val emailFormatError = remember(email) {
        when {
            email.isNotEmpty() && !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Email không hợp lệ"
            else -> ""
        }
    }
    
    // Kiểm tra email có hợp lệ về định dạng
    val isEmailFormatValid = remember(email, emailFormatError) {
        email.isNotEmpty() && emailFormatError.isEmpty()
    }

    // Xác định trạng thái lỗi cuối cùng cho email field
    val finalEmailError = when {
        authState.emailExists == true -> "Email này đã được đăng ký"
        email.isNotEmpty() && emailFormatError.isNotEmpty() -> emailFormatError
        else -> null
    }
    val isEmailFieldError = finalEmailError != null
    
    // Kiểm tra password có đủ điều kiện để hiển thị trường xác nhận
    val isPasswordValid = remember(password) {
        password.length >= 6 && 
        password.any { it.isDigit() } && 
        password.any { it.isUpperCase() }
    }
    
    val passwordError = remember(password) {
        when {
            password.isBlank() -> ""
            password.length < 6 -> "Mật khẩu phải có ít nhất 6 ký tự"
            !password.any { it.isDigit() } -> "Phải có ít nhất 1 số"
            !password.any { it.isUpperCase() } -> "Phải có ít nhất 1 chữ hoa"
            else -> ""
        }
    }
    
    val confirmPasswordError = remember(password, confirmPassword) {
        when {
            confirmPassword.isBlank() -> ""
            password != confirmPassword -> "Mật khẩu không khớp"
            else -> ""
        }
    }

    // Theo dõi trạng thái bàn phím
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    
    // Tự động cuộn khi trường mật khẩu được focus
    LaunchedEffect(isPasswordValid, confirmPasswordFocused) {
        if (isPasswordValid && confirmPasswordFocused) {
            delay(300) // Đợi bàn phím xuất hiện
            scrollState.animateScrollTo((scrollState.maxValue * 0.35f).toInt())
        }
    }

    // Trigger animations
    LaunchedEffect(Unit) {
        delay(100)
        startAnimation = true
        delay(500)
    }

    // Snackbar
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
                Log.e("RegistrationScreen", "Đăng nhập Google thất bại", e)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar("Đăng nhập Google thất bại: ${e.message}")
                }
            }
        } else {
            Log.e("RegistrationScreen", "Đăng nhập Google bị hủy")
            coroutineScope.launch {
                snackbarHostState.showSnackbar("Đăng nhập Google bị hủy")
            }
        }
    }

    // LaunchedEffect để kiểm tra email khi người dùng nhập
    LaunchedEffect(email) {
        if (email.isNotBlank()) {
            // Gọi hàm kiểm tra trong ViewModel (đã có debounce bên trong)
            viewModel.checkEmailExists(email)
            } else {
            // Reset trạng thái kiểm tra nếu email trống
            viewModel.resetEmailCheckState()
        }
    }

    // Thêm dialog đăng ký thành công
        if (authState.isSuccess) {
        // Dialog hiện đại với animation
        var expandControls by remember { mutableStateOf(false) }

        // Kích hoạt animation sau khi dialog hiển thị
        LaunchedEffect(Unit) {
            delay(150)
            expandControls = true
        }

        Dialog(
            onDismissRequest = {
                viewModel.resetState()
                onNavigateToLogin()
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            // --- Lấy các giá trị màu sắc cần thiết ---
            val dialogPrimaryColor = MaterialTheme.colorScheme.primary
            val dialogTertiaryColor = MaterialTheme.colorScheme.tertiary
            val dialogSurfaceColor = MaterialTheme.colorScheme.surface
            val dialogPrimaryContainerColor = MaterialTheme.colorScheme.primaryContainer
            val dialogSecondaryContainerColor = MaterialTheme.colorScheme.secondaryContainer
            val dialogOnSurfaceColor = MaterialTheme.colorScheme.onSurface
            val dialogOnSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant

            // --- Khai báo animation ---
            val iconScale by animateFloatAsState(
                targetValue = if (expandControls) 1f else 0.5f,
                animationSpec = spring(
                    dampingRatio = 0.6f,
                    stiffness = Spring.StiffnessLow
                ),
                label = "iconScale"
            )

            val rayAnimation by animateFloatAsState(
                targetValue = if (expandControls) 20f else 0f,
                animationSpec = tween(800, easing = EaseOutCubic),
                label = "rayAnimation"
            )

            // Sử dụng rememberInfiniteTransition cho animation lặp lại
            val infiniteTransition = rememberInfiniteTransition(label = "checkmarkPulse")
            val iconAlpha by infiniteTransition.animateFloat(
                initialValue = 0.7f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = EaseInOutCubic),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "iconAlpha"
            )

            val iconBounce by infiniteTransition.animateFloat(
                initialValue = 0.9f,
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = EaseInOutCubic),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "iconBounce"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .shadow(elevation = 24.dp, spotColor = dialogPrimaryColor.copy(alpha = 0.2f), shape = RoundedCornerShape(28.dp))
                    .background(dialogSurfaceColor)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Checkmark icon với animation
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .scale(iconScale)
                            .drawBehind {
                                // Vẽ hình tròn phát sáng phía sau
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            dialogPrimaryColor.copy(alpha = 0.2f),
                                            dialogPrimaryColor.copy(alpha = 0.1f),
                                            dialogPrimaryColor.copy(alpha = 0f)
                                        )
                                    ),
                                    radius = size.width * 0.75f
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        // Outer ring
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            dialogPrimaryColor.copy(alpha = 0.1f),
                                            dialogTertiaryColor.copy(alpha = 0.1f)
                                        )
                                    )
                                )
                                .border(
                                    width = 2.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            dialogPrimaryColor,
                                            dialogTertiaryColor
                                        )
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            // Check icon container
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                dialogPrimaryContainerColor,
                                                dialogSecondaryContainerColor
                                            )
                                        )
                                    )
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Checkmark icon với animation
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = dialogPrimaryColor,
                                    modifier = Modifier
                                        .size(48.dp)
                                        .scale(iconBounce)
                                        .alpha(iconAlpha)
                                )
                            }
                        }

                        // Hiệu ứng tia sáng xung quanh icon
                        Canvas(modifier = Modifier.size(120.dp)) {
                            val radius = size.width / 2
                            val centerX = size.width / 2
                            val centerY = size.height / 2
                            val rayCount = 8
                            val rayLengthPx = rayAnimation
                            val animatedRayLength = rayLengthPx * this.density
                            
                            for (i in 0 until rayCount) {
                                val angle = (i * (360f / rayCount)) * (Math.PI.toFloat() / 180f)
                                val startX = centerX + (radius - 10f.dp.toPx()) * cos(angle)
                                val startY = centerY + (radius - 10f.dp.toPx()) * sin(angle)
                                val endX = centerX + (radius + animatedRayLength - 10f.dp.toPx()) * cos(angle)
                                val endY = centerY + (radius + animatedRayLength - 10f.dp.toPx()) * sin(angle)

                                drawLine(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            dialogPrimaryColor.copy(alpha = 0.7f),
                                            dialogPrimaryColor.copy(alpha = 0f)
                                        ),
                                        start = Offset(startX, startY),
                                        end = Offset(endX, endY)
                                    ),
                                    start = Offset(startX, startY),
                                    end = Offset(endX, endY),
                                    strokeWidth = 2.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Title với animation
                    AnimatedVisibility(
                        visible = expandControls,
                        enter = fadeIn(tween(400)) + expandVertically(tween(400, easing = EaseOutCubic)),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            text = "Đăng Ký Thành Công",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = dialogOnSurfaceColor,
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Email với hiệu ứng typing animation
                    AnimatedVisibility(
                        visible = expandControls,
                        enter = fadeIn(tween(600, delayMillis = 300)) + expandVertically(tween(600, easing = EaseOutCubic, delayMillis = 300)),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            text = email,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = dialogPrimaryColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .background(
                                    color = dialogPrimaryContainerColor.copy(alpha = 0.4f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Message
                    AnimatedVisibility(
                        visible = expandControls,
                        enter = fadeIn(tween(800, delayMillis = 500)) + expandVertically(tween(800, easing = EaseOutCubic, delayMillis = 500)),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            text = "Chúng tôi đã gửi email xác thực đến địa chỉ của bạn.\nVui lòng kiểm tra cả thư mục chính và spam.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = dialogOnSurfaceVariantColor,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Mở ứng dụng Email
                        val context = LocalContext.current

                        AnimatedVisibility(
                            visible = expandControls,
                            enter = fadeIn(tween(1000, delayMillis = 700)) + expandHorizontally(tween(1000, easing = EaseOutCubic, delayMillis = 700)),
                            exit = fadeOut() + shrinkHorizontally(),
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    try {
                                        // Mở ứng dụng email mặc định
                                        val intent = Intent(Intent.ACTION_MAIN)
                                        intent.addCategory(Intent.CATEGORY_APP_EMAIL)
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        context.startActivity(intent)
            } catch (e: Exception) {
                                        // Fallback nếu không tìm thấy ứng dụng email
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://mail.google.com"))
                                        context.startActivity(browserIntent)
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(
                                    width = 1.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            dialogPrimaryColor,
                                            dialogTertiaryColor
                                        )
                                    )
                                ),
                                modifier = Modifier.height(56.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = dialogPrimaryColor
                                )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Email,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Mở Email",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Đăng nhập button
                        AnimatedVisibility(
                            visible = expandControls,
                            enter = fadeIn(tween(1000, delayMillis = 800)) + expandHorizontally(tween(1000, easing = EaseOutCubic, delayMillis = 800)),
                            exit = fadeOut() + shrinkHorizontally(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            viewModel.resetState()
                                    onNavigateToLogin()
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = dialogPrimaryColor
                                )
                            ) {
                                Text(
                                    text = "Đăng nhập",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Filled.ArrowForward,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }

    // Show error messages
    LaunchedEffect(authState.errorMessage) {
        authState.errorMessage?.let { message ->
            // Chỉ hiển thị snackbar cho các lỗi KHÁC lỗi email đã tồn tại
            // vì lỗi đó đã hiển thị trực tiếp trên TextField
            if (message != "Email này đã được đăng ký." && message != "Đang kiểm tra email, vui lòng đợi giây lát.") {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            snackbarHostState.showSnackbar(message)
            }
        }
    }

    // Create decorative background
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.surface,
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
                    )
                )
            )
    ) {
        // Lấy các màu sắc trước khi sử dụng trong Canvas
        val secondaryColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.05f)
        val primaryColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
        val tertiaryColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.05f)
        
        // Decorative background elements
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            
            // Draw decorative shapes in background
            drawCircle(
                color = secondaryColor,  // Sử dụng biến đã lấy bên ngoài
                center = Offset(canvasWidth * 0.85f, canvasHeight * 0.15f),
                radius = canvasWidth * 0.3f
            )
            
            drawCircle(
                color = primaryColor,  // Sử dụng biến đã lấy bên ngoài
                center = Offset(canvasWidth * 0.1f, canvasHeight * 0.85f),
                radius = canvasWidth * 0.25f
            )
            
            // Draw curved line
            val path = Path().apply {
                moveTo(0f, canvasHeight * 0.4f)
                quadraticBezierTo(
                    canvasWidth * 0.5f, canvasHeight * 0.35f,
                    canvasWidth, canvasHeight * 0.5f
                )
            }
            
            drawPath(
                path = path,
                color = tertiaryColor,  // Sử dụng biến đã lấy bên ngoài
                style = Stroke(width = 2.dp.toPx())
            )
        }
        
        // Main content
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
            contentWindowInsets = WindowInsets(0, 0, 0, 0), // Bỏ insets mặc định
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(24.dp) // Sử dụng padding như LoginScreen
                    .imePadding(), // Giữ imePadding để điều chỉnh với bàn phím
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(40.dp)) // Tăng khoảng cách giống LoginScreen
                
                // App Logo with gradient border and scale animation (UPDATED)
                // Lấy màu primaryContainer từ theme để vẽ gradient giống LoginScreen
                val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
                Box(
                    modifier = Modifier
                        .size(100.dp) // Giữ nguyên kích thước Box
                        .scale(logoSize) // Giữ nguyên animation scale
                        // .clip(CircleShape) // Xóa clip khỏi Box ngoài
                        .drawBehind { // Thêm drawBehind để vẽ vòng tròn gradient giống LoginScreen
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(primaryContainerColor, primaryContainerColor.copy(alpha = 0f))
                                ),
                                radius = size.width * 0.7f // Bán kính gradient giống LoginScreen
                            )
                        },
                    contentAlignment = Alignment.Center // Giữ nguyên căn giữa
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app),
                        contentDescription = "App Logo", // Thêm mô tả nội dung
                        modifier = Modifier
                            // .fillMaxSize() // Xóa fillMaxSize
                            .size(80.dp) // Đặt kích thước Image giống LoginScreen
                            .clip(CircleShape) // Clip Image thành hình tròn
                            .padding(8.dp), // Thêm padding giống LoginScreen
                        // contentScale = ContentScale.Crop // Xóa hoặc giữ lại tùy theo thiết kế mong muốn
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp)) // Khoảng cách sau logo giống LoginScreen
                
                // Title với màu chữ rõ ràng cho chế độ tối và alpha animation
                Text(
                    text = "Tạo tài khoản mới",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(8.dp)
                        .alpha(titleAlpha)
                )
                
                Text(
                    text = "Đăng ký để khám phá tất cả tính năng",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .alpha(titleAlpha)
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Registration form with offset animation
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = cardOffset)
                        .padding(horizontal = 8.dp),
                    shape = RoundedCornerShape(28.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Email field with animated icon
                        OutlinedTextField(
                            value = email,
                            onValueChange = {
                                email = it
                                // Reset lỗi chung nếu người dùng bắt đầu sửa email
                                if (authState.errorMessage != null && authState.errorMessage != "Email này đã được đăng ký.") {
                                    viewModel.updateError(null)
                                }
                                // Không cần gọi checkEmailExists ở đây, LaunchedEffect sẽ xử lý
                            },
                            label = { Text("Email") },
                            leadingIcon = {
                                AnimatedContent(
                                    targetState = emailFocused,
                                    transitionSpec = {
                                        fadeIn(animationSpec = tween(150)) with
                                        fadeOut(animationSpec = tween(150))
                                    },
                                    label = "EmailIconAnimation"
                                ) { focused ->
                                    Icon(
                                        imageVector = if (focused) Icons.Filled.AlternateEmail else Icons.Outlined.Email,
                                        contentDescription = null,
                                        tint = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            trailingIcon = {
                                // Ưu tiên 1: Loading
                                if (authState.emailCheckLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                // Chỉ hiển thị icon khác khi không loading và email không trống
                                else if (email.isNotEmpty()) {
                                    // Ưu tiên 2: Lỗi Email Tồn Tại
                                    if (authState.emailExists == true) {
                                        Icon(
                                            imageVector = Icons.Filled.Error,
                                            contentDescription = "Email đã tồn tại",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    // Ưu tiên 3: Lỗi Định Dạng
                                    else if (emailFormatError.isNotEmpty()) {
                                        Icon(
                                            imageVector = Icons.Filled.Error,
                                            contentDescription = emailFormatError,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                    // Ưu tiên 4: Hợp lệ (Định dạng đúng VÀ xác nhận KHÔNG tồn tại)
                                    else if (isEmailFormatValid && authState.emailExists == false) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = "Email hợp lệ và chưa đăng ký",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(emailFocusRequester)
                                .onFocusChanged { state -> 
                                    emailFocused = state.isFocused
                                    if (state.isFocused) {
                                        isKeyboardVisible = true
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                },
                            // isError ưu tiên lỗi tồn tại > lỗi định dạng
                            isError = !authState.emailCheckLoading && (authState.emailExists == true || (email.isNotEmpty() && emailFormatError.isNotEmpty())),
                            supportingText = {
                                // Ưu tiên 1: Lỗi Email Tồn Tại
                                if (authState.emailExists == true) {
                                    Text(
                                        text = "Email này đã được đăng ký",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                // Ưu tiên 2: Lỗi Định Dạng
                                else if (email.isNotEmpty() && emailFormatError.isNotEmpty()) {
                                    Text(
                                        text = emailFormatError,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                                unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                // Màu trailing icon dựa trên trạng thái lỗi cuối cùng
                                focusedTrailingIconColor = when {
                                    authState.emailCheckLoading -> MaterialTheme.colorScheme.primary
                                    authState.emailExists == true || (email.isNotEmpty() && emailFormatError.isNotEmpty()) -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                unfocusedTrailingIconColor = when {
                                    authState.emailExists == true || (email.isNotEmpty() && emailFormatError.isNotEmpty()) -> MaterialTheme.colorScheme.error
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                errorBorderColor = MaterialTheme.colorScheme.error,
                                errorCursorColor = MaterialTheme.colorScheme.error,
                                errorLeadingIconColor = MaterialTheme.colorScheme.error,
                                errorSupportingTextColor = MaterialTheme.colorScheme.error, // Đảm bảo màu supporting text khi lỗi
                                errorTrailingIconColor = MaterialTheme.colorScheme.error // Đảm bảo màu trailing icon khi lỗi
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                // imeAction chỉ là Next khi email hợp lệ VÀ CHƯA được xác nhận là tồn tại
                                imeAction = if (isEmailFormatValid && authState.emailExists != true) ImeAction.Next else ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { 
                                    // Chỉ chuyển focus khi email hợp lệ VÀ CHƯA được xác nhận là tồn tại
                                    if (isEmailFormatValid && authState.emailExists != true) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        passwordFocusRequester.requestFocus()
                                    } else {
                                        // Nếu email tồn tại hoặc không hợp lệ, ẩn bàn phím
                                        keyboardController?.hide()
                                    }
                                },
                                onDone = {
                                    keyboardController?.hide()
                                }
                            )
                        )

                        // Gộp Password và Confirm Password vào cùng một AnimatedVisibility lớn
                        AnimatedVisibility(
                            // Điều kiện hiển thị: email hợp lệ VÀ (chưa kiểm tra HOẶC đã xác nhận không tồn tại)
                            visible = isEmailFormatValid && authState.emailExists != true,
                            enter = expandVertically(animationSpec = tween(300)) +
                                    fadeIn(animationSpec = tween(300)),
                            exit = shrinkVertically(animationSpec = tween(300)) +
                                    fadeOut(animationSpec = tween(300))
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Spacer(modifier = Modifier.height(8.dp))

                                // --- Trường Mật khẩu ---
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Mật khẩu") },
                                    leadingIcon = {
                                        AnimatedContent(
                                            targetState = passwordFocused,
                                            transitionSpec = {
                                                fadeIn(animationSpec = tween(150)) with
                                                fadeOut(animationSpec = tween(150))
                                            },
                                            label = "PasswordIconAnimation"
                                        ) { focused ->
                                            Icon(
                                                imageVector = if (focused) Icons.Filled.Lock else Icons.Outlined.Lock,
                                                contentDescription = null,
                                                tint = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    trailingIcon = {
                                        if (password.isNotEmpty()) {
                                            IconButton(
                                                onClick = { 
                                                    isPasswordVisible1 = !isPasswordVisible1 
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                }
                                            ) {
                                                AnimatedContent(
                                                    targetState = isPasswordVisible1,
                                                    transitionSpec = {
                                                        scaleIn(initialScale = 0.8f) with 
                                                        scaleOut(targetScale = 0.8f)
                                                    },
                                                    label = "PasswordVisibilityAnimation"
                                                ) { visible ->
                                                    Icon(
                                                        imageVector = if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                                        contentDescription = if (visible) "Ẩn mật khẩu" else "Hiện mật khẩu",
                                                        tint = if (passwordFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    visualTransformation = if (isPasswordVisible1) VisualTransformation.None else PasswordVisualTransformation(),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(passwordFocusRequester)
                                        .onFocusChanged { state ->
                                            passwordFocused = state.isFocused
                                            if (state.isFocused) {
                                                isKeyboardVisible = true
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            }
                                        },
                                    isError = password.isNotEmpty() && passwordError.isNotEmpty(),
                                    supportingText = {
                                        if (password.isNotEmpty() && passwordError.isNotEmpty()) {
                                            Text(
                                                text = passwordError,
                                                color = MaterialTheme.colorScheme.error,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(16.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        cursorColor = MaterialTheme.colorScheme.primary,
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                        focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                                        unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        focusedTrailingIconColor = MaterialTheme.colorScheme.primary,
                                        unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        errorBorderColor = MaterialTheme.colorScheme.error,
                                        errorCursorColor = MaterialTheme.colorScheme.error,
                                        errorLeadingIconColor = MaterialTheme.colorScheme.error,
                                        errorTrailingIconColor = MaterialTheme.colorScheme.error
                                    ),
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Password,
                                        imeAction = if (isPasswordValid) ImeAction.Next else ImeAction.Done
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onNext = { 
                                            if (isPasswordValid) {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                confirmPasswordFocusRequester.requestFocus()
                                            }
                                        },
                                        onDone = {
                                            keyboardController?.hide()
                                        }
                                    )
                                )

                                // Password strength indicator
                                if (password.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(4.dp)
                                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp))
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth(strengthWidth)
                                                    .fillMaxHeight()
                                                    .background(strengthColor, RoundedCornerShape(2.dp))
                                            )
                                        }
                                        
                                        Spacer(modifier = Modifier.width(8.dp))
                                        
                                        Text(
                                            text = strengthText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = strengthColor
                                        )
                                    }
                                }
                                // --- Kết thúc Trường Mật khẩu ---

                                // AnimatedVisibility nhỏ hơn cho Confirm Password và Nút Đăng ký
                                AnimatedVisibility(
                                    visible = isPasswordValid,
                                    enter = expandVertically(animationSpec = tween(300)) +
                                            fadeIn(animationSpec = tween(300)),
                                    exit = shrinkVertically(animationSpec = tween(300)) +
                                            fadeOut(animationSpec = tween(300))
                                ) {
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Spacer(modifier = Modifier.height(8.dp))

                                        // --- Trường Xác nhận Mật khẩu ---
                                        OutlinedTextField(
                                            value = confirmPassword,
                                            onValueChange = { confirmPassword = it },
                                            label = { Text("Xác nhận mật khẩu") },
                                            leadingIcon = {
                                                AnimatedContent(
                                                    targetState = confirmPasswordFocused,
                                                    transitionSpec = {
                                                        fadeIn(animationSpec = tween(150)) with
                                                        fadeOut(animationSpec = tween(150))
                                                    },
                                                    label = "ConfirmPasswordIconAnimation"
                                                ) { focused ->
                                                    Icon(
                                                        imageVector = if (focused) Icons.Filled.LockReset else Icons.Outlined.LockReset,
                                                        contentDescription = null,
                                                        tint = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            trailingIcon = {
                                                if (confirmPassword.isNotEmpty()) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                    ) {
                                                        // Hiển thị icon lỗi nếu có lỗi
                                                        if (confirmPasswordError.isNotEmpty()) {
                                                            Icon(
                                                                imageVector = Icons.Filled.Error,
                                                                contentDescription = confirmPasswordError,
                                                                tint = MaterialTheme.colorScheme.error
                                                            )
                                                        } 
                                                        // Hiển thị icon tích nếu mật khẩu khớp và không có lỗi
                                                        else if (password == confirmPassword && password.isNotEmpty()) {
                                                            Icon(
                                                                imageVector = Icons.Filled.CheckCircle,
                                                                contentDescription = "Mật khẩu khớp",
                                                                tint = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                        
                                                        // Nút hiển thị/ẩn mật khẩu
                                                        IconButton(
                                                            onClick = { 
                                                                isPasswordVisible2 = !isPasswordVisible2 
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            }
                                                        ) {
                                                            AnimatedContent(
                                                                targetState = isPasswordVisible2,
                                                                transitionSpec = {
                                                                    scaleIn(initialScale = 0.8f) with 
                                                                    scaleOut(targetScale = 0.8f)
                                                                },
                                                                label = "ConfirmPasswordVisibilityAnimation"
                                                            ) { visible ->
                                                                Icon(
                                                                    imageVector = if (visible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                                                    contentDescription = if (visible) "Ẩn mật khẩu" else "Hiện mật khẩu",
                                                                    tint = if (confirmPasswordFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            visualTransformation = if (isPasswordVisible2) VisualTransformation.None else PasswordVisualTransformation(),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .focusRequester(confirmPasswordFocusRequester)
                                                .onFocusChanged { state ->
                                                    confirmPasswordFocused = state.isFocused
                                                    if (state.isFocused) {
                                                        isKeyboardVisible = true
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    }
                                                },
                                            isError = confirmPassword.isNotEmpty() && confirmPasswordError.isNotEmpty(),
                                            supportingText = {
                                                if (confirmPassword.isNotEmpty() && confirmPasswordError.isNotEmpty()) {
                                                    Text(
                                                        text = confirmPasswordError,
                                                        color = MaterialTheme.colorScheme.error,
                                                        style = MaterialTheme.typography.bodySmall
                                                    )
                                                }
                                            },
                                            singleLine = true,
                                            shape = RoundedCornerShape(16.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                cursorColor = MaterialTheme.colorScheme.primary,
                                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                                                focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                                                unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                focusedTrailingIconColor = MaterialTheme.colorScheme.primary,
                                                unfocusedTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                                errorBorderColor = MaterialTheme.colorScheme.error,
                                                errorCursorColor = MaterialTheme.colorScheme.error,
                                                errorLeadingIconColor = MaterialTheme.colorScheme.error,
                                                errorTrailingIconColor = MaterialTheme.colorScheme.error
                                            ),
                                            keyboardOptions = KeyboardOptions(
                                                keyboardType = KeyboardType.Password,
                                                imeAction = ImeAction.Done
                                            ),
                                            keyboardActions = KeyboardActions(
                                                onDone = { 
                                                    keyboardController?.hide()
                                                    isKeyboardVisible = false
                                                    
                                                    // Check if form is valid
                                                    val isFormValid = isEmailFormatValid && authState.emailExists == false &&
                                                                      isPasswordValid && password == confirmPassword
                                                    
                                                    if (isFormValid) {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        viewModel.register(email.trim(), password)
                                                    } else {
                                                        coroutineScope.launch {
                                                            val errorToShow = when {
                                                                email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() -> "Vui lòng điền đầy đủ thông tin"
                                                                !isEmailFormatValid -> "Email không hợp lệ"
                                                                authState.emailExists == true -> "Email này đã được đăng ký"
                                                                !isPasswordValid -> passwordError.ifEmpty { "Mật khẩu không hợp lệ" }
                                                                password != confirmPassword -> "Mật khẩu không khớp"
                                                                else -> "Vui lòng sửa các lỗi trước khi đăng ký"
                                                            }
                                                            snackbarHostState.showSnackbar(errorToShow)
                                                        }
                                                    }
                                                }
                                            )
                                        )
                                        // --- Kết thúc Trường Xác nhận Mật khẩu ---

                                        Spacer(modifier = Modifier.height(24.dp))

                                        // --- Nút Đăng ký ---
                                        Button(
                                            onClick = {
                                                keyboardController?.hide()
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                
                                                // Check if form is valid (bao gồm cả check email tồn tại từ state)
                                                val isFormValid = isEmailFormatValid && authState.emailExists == false &&
                                                                  isPasswordValid && password == confirmPassword
                                                
                                                if (isFormValid) {
                                                    viewModel.register(email.trim(), password)
                                                } else {
                                                        coroutineScope.launch {
                                                        val errorToShow = when {
                                                            email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty() -> "Vui lòng điền đầy đủ thông tin"
                                                            !isEmailFormatValid -> "Email không hợp lệ"
                                                            authState.emailExists == true -> "Email này đã được đăng ký"
                                                            !isPasswordValid -> passwordError.ifEmpty { "Mật khẩu không hợp lệ" }
                                                            password != confirmPassword -> "Mật khẩu không khớp"
                                                            else -> "Vui lòng sửa các lỗi trước khi đăng ký"
                                                        }
                                                        snackbarHostState.showSnackbar(errorToShow)
                                                    }
                                                }
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(56.dp)
                                                .scale(buttonScale),
                                            shape = RoundedCornerShape(16.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                                disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                            ),
                                            enabled = !authState.isLoading && !authState.emailCheckLoading &&
                                                      isEmailFormatValid && authState.emailExists == false &&
                                                      isPasswordValid && password == confirmPassword
                                        ) {
                                            AnimatedContent(
                                                targetState = authState.isLoading,
                                                transitionSpec = {
                                                    fadeIn(animationSpec = tween(150)) with 
                                                    fadeOut(animationSpec = tween(150))
                                                },
                                                label = "RegisterButtonContent"
                                            ) { isLoading ->
                                                if (isLoading) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(24.dp),
                                                        color = MaterialTheme.colorScheme.onPrimary,
                                                        strokeWidth = 2.dp
                                                    )
                                                } else {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.Center
                                                    ) {
                                                        Text(
                                                            "Đăng ký tài khoản",
                                                            fontSize = 16.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Icon(
                                                            imageVector = Icons.Default.ArrowForward,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                        // --- Kết thúc Nút Đăng ký ---
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Already have account section with alpha animation
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                ) {
                    // Already have account link with alpha animation
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .alpha(titleAlpha)
                    ) {
                        Text(
                            "Đã có tài khoản?",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        TextButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigateToLogin()
                            },
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    "Đăng nhập ngay",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Divider with OR text (Sử dụng AnimatedVisibility)
                    AnimatedVisibility(
                        visible = startAnimation,
                        enter = fadeIn(tween(500, delayMillis = 300))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        ) {
                            Divider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            Text(
                                "  HOẶC  ",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp)
                            )
                            Divider(
                                modifier = Modifier.weight(1f),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Google Sign In Button with shimmer effect (Sử dụng AnimatedVisibility)
                    AnimatedVisibility(
                        visible = startAnimation,
                        enter = fadeIn(tween(500, delayMillis = 400)) + expandVertically(tween(500, easing = EaseOutCubic)),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            val shimmerColors = listOf(
                                Color.White.copy(alpha = 0.3f),
                                Color.White.copy(alpha = 0.5f),
                                Color.White.copy(alpha = 0.3f)
                            )
                            val transition = rememberInfiniteTransition(label = "shimmerTransition")
                            val translateAnim = transition.animateFloat(
                                initialValue = 0f,
                                targetValue = 1000f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(
                                        durationMillis = 1000,
                                        easing = LinearEasing
                                    ),
                                    repeatMode = RepeatMode.Restart
                                ),
                                label = "shimmerTranslateAnim"
                            )

                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isGoogleSignInLoading = true
                                    val signInIntent = googleSignInClient.signInIntent
                                    launcher.launch(signInIntent)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .padding(horizontal = 16.dp)
                                    .clip(RoundedCornerShape(16.dp)),
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
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    containerColor = MaterialTheme.colorScheme.surface
                                ),
                                enabled = !isGoogleSignInLoading && !authState.isLoading
                            ) {
                                AnimatedContent(
                                    targetState = isGoogleSignInLoading,
                                    transitionSpec = {
                                        scaleIn(initialScale = 0.8f) + fadeIn() with 
                                        scaleOut(targetScale = 0.8f) + fadeOut()
                                    },
                                    label = "GoogleSignInButtonContent"
                                ) { isLoading ->
                                    if (isLoading) {
                                        Box(contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                color = MaterialTheme.colorScheme.primary,
                                                strokeWidth = 2.dp
                                            )
                                        }
                                    } else {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_google),
                                                contentDescription = null,
                                                tint = Color.Unspecified,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                "Đăng nhập bằng Google",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Thêm padding dưới cùng để tránh bị che bởi bàn phím
                Spacer(modifier = Modifier.height(100.dp))
            }
        }
    }
}

// Hàm tính toán độ mạnh của mật khẩu
private fun calculatePasswordStrength(password: String): Int {
    if (password.isEmpty()) return 0
    
    var score = 0
    
    // Độ dài mật khẩu
    if (password.length >= 8) score += 1
    if (password.length >= 12) score += 1
    
    // Kiểm tra có số
    if (password.any { it.isDigit() }) score += 1
    
    // Kiểm tra có chữ thường
    if (password.any { it.isLowerCase() }) score += 1
    
    // Kiểm tra có chữ hoa
    if (password.any { it.isUpperCase() }) score += 1
    
    // Kiểm tra có ký tự đặc biệt
    if (password.any { !it.isLetterOrDigit() }) score += 1
    
    // Quy đổi điểm thành mức độ
    return when {
        score <= 2 -> 0 // Yếu
        score <= 4 -> 1 // Trung bình
        score <= 5 -> 2 // Khá
        else -> 3 // Mạnh
    }
}