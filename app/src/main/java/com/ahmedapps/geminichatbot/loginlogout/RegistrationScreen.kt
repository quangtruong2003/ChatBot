package com.ahmedapps.geminichatbot.loginlogout

import android.app.Activity
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
    val headerAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(500),
        label = "headerAlpha"
    )
    
    val formTranslateY by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 50.dp,
        animationSpec = tween(700, delayMillis = 100, easing = EaseOutBack),
        label = "formTranslateY"
    )
    
    val buttonAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(300, delayMillis = 800),
        label = "buttonAlpha"
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
    val emailError = remember(email) {
        when {
            email.isBlank() -> ""
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> "Email không hợp lệ"
            else -> ""
        }
    }
    
    // Kiểm tra email có hợp lệ để hiển thị trường mật khẩu
    val isEmailValid = remember(email, emailError) {
        email.isNotEmpty() && emailError.isEmpty()
    }
    
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

    // Tự động cuộn khi trường mật khẩu và xác nhận mật khẩu xuất hiện
    LaunchedEffect(isEmailValid, passwordFocused) {
        if (isEmailValid && passwordFocused) {
            delay(300)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
    
    LaunchedEffect(isPasswordValid, confirmPasswordFocused) {
        if (isPasswordValid && confirmPasswordFocused) {
            delay(300)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // Trigger animations
    LaunchedEffect(Unit) {
        delay(100)
        startAnimation = true
        delay(500)
        emailFocusRequester.requestFocus()
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

    // Handle registration success
    LaunchedEffect(authState.isSuccess) {
        if (authState.isSuccess) {
            keyboardController?.hide()
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            
            // Send verification email
            var verificationEmailSent = false
            var errorMessage: String? = null
            try {
                auth.currentUser?.sendEmailVerification()?.await()
                Log.d("RegistrationScreen", "Verification email sent to ${auth.currentUser?.email}")
                verificationEmailSent = true
            } catch (e: Exception) {
                Log.e("RegistrationScreen", "Failed to send verification email", e)
                errorMessage = "Không thể gửi email xác minh: ${e.message}"
            }

            // Show success message and navigate
            if (verificationEmailSent) {
                snackbarHostState.showSnackbar(
                    message = "Đăng ký thành công! Vui lòng kiểm tra email để xác thực tài khoản.",
                    duration = SnackbarDuration.Long
                )
            } else {
                snackbarHostState.showSnackbar(
                    message = errorMessage ?: "Đăng ký thành công nhưng không thể gửi email xác minh.",
                    duration = SnackbarDuration.Long
                )
            }
            
            delay(1500)
            onNavigateToLogin()
            viewModel.resetState()
        }
    }

    // Show error messages
    LaunchedEffect(authState.errorMessage) {
        authState.errorMessage?.let { message ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            snackbarHostState.showSnackbar(message)
            viewModel.updateError(null)
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
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))
                
                // Header section with logo and title
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .alpha(headerAlpha)
                        .padding(vertical = 16.dp)
                ) {
                    // App Logo with gradient border
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .border(
                                width = 2.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                ),
                                shape = CircleShape
                            )
                            .padding(4.dp)
                            .clip(CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Title với màu chữ rõ ràng cho chế độ tối
                    Text(
                        text = "Tạo tài khoản mới",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        // Thêm màu onSurface để đảm bảo hiển thị đúng trong chế độ tối
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(8.dp)
                    )
                    
                    Text(
                        text = "Đăng ký để khám phá tất cả tính năng",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Registration form with offset animation
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = formTranslateY)
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
                            onValueChange = { email = it },
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
                                if (email.isNotEmpty()) {
                                    if (emailError.isNotEmpty()) {
                                        Icon(
                                            imageVector = Icons.Filled.Error,
                                            contentDescription = emailError,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    } else if (isEmailValid) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = "Email hợp lệ",
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
                            isError = email.isNotEmpty() && emailError.isNotEmpty(),
                            supportingText = {
                                if (email.isNotEmpty() && emailError.isNotEmpty()) {
                                    Text(
                                        text = emailError,
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
                                keyboardType = KeyboardType.Email,
                                imeAction = if (isEmailValid) ImeAction.Next else ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onNext = { 
                                    if (isEmailValid) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        passwordFocusRequester.requestFocus()
                                    }
                                },
                                onDone = {
                                    keyboardController?.hide()
                                }
                            )
                        )

                        // Password field - chỉ hiển thị khi email hợp lệ
                        AnimatedVisibility(
                            visible = isEmailValid,
                            enter = expandVertically(animationSpec = tween(300)) + 
                                   fadeIn(animationSpec = tween(300)),
                            exit = shrinkVertically(animationSpec = tween(300)) + 
                                  fadeOut(animationSpec = tween(300))
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                Spacer(modifier = Modifier.height(16.dp))
                                
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
                                                coroutineScope.launch {
                                                    delay(300)
                                                    scrollState.animateScrollTo(scrollState.maxValue)
                                                }
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
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        // Strength progress bar
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
                                        
                                        // Strength text
                                        Text(
                                            text = strengthText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = strengthColor
                                        )
                                    }
                                }
                            }
                        }

                        // Confirm Password Field - chỉ hiển thị khi mật khẩu hợp lệ
                        AnimatedVisibility(
                            visible = isPasswordValid && isEmailValid,
                            enter = expandVertically(animationSpec = tween(300)) + 
                                   fadeIn(animationSpec = tween(300)),
                            exit = shrinkVertically(animationSpec = tween(300)) + 
                                  fadeOut(animationSpec = tween(300))
                        ) {
                            Column {
                                Spacer(modifier = Modifier.height(16.dp))
                                
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
                                            Row {
                                                if (confirmPasswordError.isEmpty() && password == confirmPassword) {
                                                    Icon(
                                                        imageVector = Icons.Filled.CheckCircle,
                                                        contentDescription = "Mật khẩu khớp",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                } else if (confirmPasswordError.isNotEmpty()) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Error,
                                                        contentDescription = confirmPasswordError,
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                                
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
                                                coroutineScope.launch {
                                                    delay(300)
                                                    scrollState.animateScrollTo(scrollState.maxValue)
                                                }
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
                                            val isFormValid = isEmailValid && isPasswordValid && 
                                                password == confirmPassword
                                                
                                            if (isFormValid) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.register(email.trim(), password)
                                            }
                                        }
                                    )
                                )
                                
                                Spacer(modifier = Modifier.height(24.dp))
                                
                                // Register Button with loading animation
                                Button(
                                    onClick = {
                                        keyboardController?.hide()
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        
                                        // Check if form is valid
                                        val isFormValid = isEmailValid && isPasswordValid && 
                                            password == confirmPassword
                                            
                                        if (isFormValid) {
                                            viewModel.register(email.trim(), password)
                                        } else {
                                            if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Vui lòng điền đầy đủ thông tin")
                                                }
                                            } else {
                                                coroutineScope.launch {
                                                    snackbarHostState.showSnackbar("Vui lòng sửa các lỗi trước khi đăng ký")
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(56.dp),
                                    shape = RoundedCornerShape(16.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                    ),
                                    enabled = !authState.isLoading && isEmailValid && isPasswordValid && 
                                        password == confirmPassword
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
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Already have account section with alpha animation
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.alpha(buttonAlpha)
                ) {
                    // Already have account link
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
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

                    // Divider with OR text
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // Google Sign In Button with shimmer effect
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
                            .graphicsLayer {
                                clip = true
                            },
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
                        enabled = !isGoogleSignInLoading
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
                
                Spacer(modifier = Modifier.height(32.dp))
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