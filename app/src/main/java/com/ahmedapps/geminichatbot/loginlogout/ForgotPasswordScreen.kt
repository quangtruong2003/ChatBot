// ForgotPasswordScreen.kt
package com.ahmedapps.geminichatbot.loginlogout

import android.util.Log // Import Log
import android.util.Patterns
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
import androidx.compose.runtime.saveable.rememberSaveable // Import rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmedapps.geminichatbot.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.draw.scale

// Data class quản lý trạng thái ForgotPassword
data class ForgotPasswordState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class ForgotPasswordViewModel @javax.inject.Inject constructor(
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

@OptIn(
    ExperimentalComposeUiApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalAnimationApi::class
)
@Composable
fun ForgotPasswordScreen(
    onBackToLogin: () -> Unit,
    emailFromNav: String = "",
    viewModel: ForgotPasswordViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    
    // Lấy email từ EmailDataHolder
    var emailState by rememberSaveable {
        // Ưu tiên dùng email từ EmailDataHolder, nếu không có thì dùng emailFromNav (nếu có)
        val emailToUse = EmailDataHolder.getEmail().ifEmpty { emailFromNav }
        Log.d("ForgotPasswordScreen", "Initializing emailState with: '$emailToUse'")
        mutableStateOf(emailToUse)
    }

    var emailFocused by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollState = rememberScrollState()
    val haptic = LocalHapticFeedback.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    Log.d("ForgotPasswordScreen", "Recomposing. emailFromNav: '$emailFromNav', current emailState: '$emailState'")

    // Animation states
    var startAnimation by remember { mutableStateOf(false) }
    val logoSize by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.5f,
        animationSpec = tween(500, easing = EaseOutBack),
        label = "logoSize"
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(600, delayMillis = 100),
        label = "titleAlpha"
    )
    val cardOffset by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 50.dp,
        animationSpec = tween(700, delayMillis = 200, easing = EaseOutBack),
        label = "cardOffset"
    )
    val buttonScale by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.8f,
        animationSpec = tween(700, delayMillis = 400, easing = EaseOutCubic),
        label = "buttonScale"
    )

    // Kiểm tra tính hợp lệ của email
    val isEmailValid = remember(emailState) {
        emailState.isNotEmpty() && Patterns.EMAIL_ADDRESS.matcher(emailState).matches()
    }

    LaunchedEffect(Unit) {
        delay(100)
        startAnimation = true
        if (emailFromNav.isEmpty()) {
            delay(500)
            try {
                focusRequester.requestFocus()
                Log.d("ForgotPasswordScreen", "Requesting focus because initial emailFromNav was empty.")
            } catch (e: Exception) {
                Log.e("ForgotPasswordScreen", "Error requesting focus", e)
            }
        }
    }

    if (state.isSuccess) {
        AlertDialog(
            onDismissRequest = {
                viewModel.resetState()
                onBackToLogin()
            },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.MarkEmailRead,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text("Kiểm tra Email")
                }
            },
            icon = {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Email,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            },
            text = {
                Text(
                    "Email đặt lại mật khẩu đã được gửi đến $emailState. Vui lòng kiểm tra hộp thư của bạn (bao gồm cả thư mục spam).",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetState()
                        onBackToLogin()
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("OK")
                }
            }
        )
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { message ->
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            snackbarHostState.showSnackbar(message)
        }
    }

    // Các màu sắc sử dụng trong giao diện
    val surfaceColor = MaterialTheme.colorScheme.surface
    val secondaryContainerColorAlpha = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val primaryContainerColor = MaterialTheme.colorScheme.primaryContainer
    val primaryColorAlpha = primaryColor.copy(alpha = 0.05f)
    val tertiaryColorAlpha = tertiaryColor.copy(alpha = 0.05f)
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline
    val surfaceVariantAlphaColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val errorColor = MaterialTheme.colorScheme.error

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(surfaceColor, surfaceColor, secondaryContainerColorAlpha)
                )
            )
    ) {
        // Decorative background
        Canvas(modifier = Modifier.fillMaxSize()) {
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
            drawPath(
                path = path,
                color = primaryColorAlpha,
                style = Stroke(width = 2.dp.toPx())
            )
        }

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
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(40.dp))
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(logoSize)
                        .drawBehind { /* Vẽ background radial nếu cần */ },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app),
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
                            .border(
                                width = 3.dp,
                                brush = Brush.linearGradient(colors = listOf(primaryColor, tertiaryColor)),
                                shape = CircleShape
                            )
                            .padding(8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Quên Mật Khẩu",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.alpha(titleAlpha)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Nhập email để nhận liên kết đặt lại mật khẩu",
                    style = MaterialTheme.typography.bodyLarge,
                    color = onSurfaceVariantColor,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                        .alpha(titleAlpha)
                )
                Spacer(modifier = Modifier.height(32.dp))
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
                        OutlinedTextField(
                            value = emailState,
                            onValueChange = {
                                emailState = it
                                if (state.errorMessage != null) {
                                    viewModel.resetState()
                                }
                            },
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
                                if (emailState.isNotEmpty()) {
                                    AnimatedVisibility(
                                        visible = isEmailValid,
                                        enter = scaleIn(),
                                        exit = scaleOut()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Email hợp lệ",
                                            tint = Color.Green
                                        )
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged { emailFocused = it.isFocused },
                            singleLine = true,
                            isError = (state.errorMessage != null && emailState.isNotEmpty())
                                    || (emailState.isNotEmpty() && !isEmailValid),
                            supportingText = {
                                val currentError = when {
                                    state.errorMessage != null && emailState.isNotEmpty() -> state.errorMessage
                                    emailState.isNotEmpty() && !isEmailValid -> "Vui lòng nhập email hợp lệ"
                                    else -> null
                                }
                                if (currentError != null) {
                                    Text(
                                        text = currentError,
                                        color = errorColor,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                cursorColor = primaryColor,
                                focusedBorderColor = primaryColor,
                                unfocusedBorderColor = outlineColor,
                                focusedContainerColor = surfaceVariantAlphaColor,
                                unfocusedContainerColor = surfaceVariantAlphaColor,
                                focusedLeadingIconColor = primaryColor,
                                unfocusedLeadingIconColor = onSurfaceVariantColor,
                                errorCursorColor = errorColor,
                                errorBorderColor = errorColor,
                                errorLeadingIconColor = errorColor
                            ),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    if (isEmailValid) {
                                        viewModel.resetPassword(emailState.trim())
                                    } else {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Vui lòng nhập email hợp lệ")
                                        }
                                    }
                                }
                            )
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                keyboardController?.hide()
                                if (isEmailValid) {
                                    viewModel.resetPassword(emailState.trim())
                                } else {
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Vui lòng nhập email hợp lệ")
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .scale(buttonScale),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = primaryColor,
                                contentColor = onPrimaryColor
                            ),
                            enabled = !state.isLoading && isEmailValid
                        ) {
                            AnimatedContent(
                                targetState = state.isLoading,
                                transitionSpec = { fadeIn(tween(150)) with fadeOut(tween(150)) },
                                label = "ResetButtonContent"
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
                                        Text(
                                            "Gửi liên kết",
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Filled.Send,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .alpha(titleAlpha)
                        .padding(8.dp)
                ) {
                    Text("Nhớ mật khẩu?", color = onSurfaceVariantColor)
                    TextButton(
                        onClick = {
                            onBackToLogin()
                        }
                    ) {
                        Text("Quay lại đăng nhập", color = primaryColor, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}