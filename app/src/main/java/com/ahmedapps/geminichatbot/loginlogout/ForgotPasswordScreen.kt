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
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding

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

    fun initiatePasswordReset(email: String) {
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
                val signInMethods = auth.fetchSignInMethodsForEmail(email).await().signInMethods
                if (signInMethods.isNullOrEmpty()) {
                    _state.value = ForgotPasswordState(errorMessage = "Email không tồn tại trong hệ thống")
                } else {
                    auth.sendPasswordResetEmail(email).await()
                    _state.value = ForgotPasswordState(isSuccess = true)
                }
            } catch (e: Exception) {
                Log.e("ForgotPasswordVM", "Error initiating password reset", e)
                val error = e.localizedMessage ?: "Đã xảy ra lỗi không xác định, vui lòng thử lại sau"
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
    
    // Thêm để xử lý IME padding
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0

    Log.d("ForgotPasswordScreen", "Recomposing. emailFromNav: '$emailFromNav', current emailState: '$emailState'")

    // Animation states
    var startAnimation by remember { mutableStateOf(false) }
    val logoSize by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.5f,
        animationSpec = tween(500, easing = EaseOutCubic),
        label = "logoSize"
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(600, delayMillis = 100),
        label = "titleAlpha"
    )
    val cardOffset by animateDpAsState(
        targetValue = if (startAnimation) 0.dp else 50.dp,
        animationSpec = tween(700, delayMillis = 200, easing = EaseOutCubic),
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
        // Loại bỏ phần tự động focus khi mở màn hình
        // if (emailFromNav.isEmpty()) {
        //     delay(500)
        //     try {
        //         focusRequester.requestFocus()
        //         Log.d("ForgotPasswordScreen", "Requesting focus because initial emailFromNav was empty.")
        //     } catch (e: Exception) {
        //         Log.e("ForgotPasswordScreen", "Error requesting focus", e)
        //     }
        // }
    }

    if (state.isSuccess) {
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
                onBackToLogin()
            },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            // --- Lấy các giá trị màu sắc cần thiết TRƯỚC khi vào Box/Canvas ---
            val dialogPrimaryColor = MaterialTheme.colorScheme.primary
            val dialogTertiaryColor = MaterialTheme.colorScheme.tertiary
            val dialogSurfaceColor = MaterialTheme.colorScheme.surface
            val dialogPrimaryContainerColor = MaterialTheme.colorScheme.primaryContainer
            val dialogSecondaryContainerColor = MaterialTheme.colorScheme.secondaryContainer
            val dialogOnSurfaceColor = MaterialTheme.colorScheme.onSurface
            val dialogOnSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
            // --- Kết thúc lấy màu sắc ---

            // --- Di chuyển các khai báo animation vào bên trong lambda của Dialog ---
            val iconScale by animateFloatAsState(
                targetValue = if (expandControls) 1f else 0.5f,
                animationSpec = spring(
                    dampingRatio = 0.6f,
                    stiffness = Spring.StiffnessLow
                ),
                label = "iconScale"
            )

            val rayAnimation by animateFloatAsState(
                // Giá trị target là Dp, toPx sẽ được gọi bên trong Canvas
                targetValue = if (expandControls) 20f else 0f, // Sử dụng Float cho targetValue
                animationSpec = tween(800, easing = EaseOutCubic),
                label = "rayAnimation"
            )

            // Sử dụng rememberInfiniteTransition cho animation lặp lại
            val infiniteTransition = rememberInfiniteTransition(label = "mailIconPulse")
            val iconAlpha by infiniteTransition.animateFloat(
                initialValue = 0.7f, // initialValue hợp lệ với rememberInfiniteTransition
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = EaseInOutCubic),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "iconAlpha"
            )

            val iconBounce by infiniteTransition.animateFloat(
                initialValue = 0.9f, // initialValue hợp lệ với rememberInfiniteTransition
                targetValue = 1.1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = EaseInOutCubic),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "iconBounce"
            )
            // --- Kết thúc di chuyển khai báo animation ---

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .wrapContentHeight()
                    .clip(RoundedCornerShape(28.dp))
                    .shadow(elevation = 24.dp, spotColor = dialogPrimaryColor.copy(alpha = 0.2f), shape = RoundedCornerShape(28.dp)) // Sử dụng biến màu
                    .background(dialogSurfaceColor) // Sử dụng biến màu
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Email icon với animation
                    Box(
                        modifier = Modifier
                            .size(120.dp)
                            .scale(iconScale) // Sử dụng giá trị animation đã khai báo
                            .drawBehind {
                                // Vẽ hình tròn phát sáng phía sau
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            dialogPrimaryColor.copy(alpha = 0.2f), // Sử dụng biến màu
                                            dialogPrimaryColor.copy(alpha = 0.1f), // Sử dụng biến màu
                                            dialogPrimaryColor.copy(alpha = 0f)    // Sử dụng biến màu
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
                                            dialogPrimaryColor.copy(alpha = 0.1f), // Sử dụng biến màu
                                            dialogTertiaryColor.copy(alpha = 0.1f) // Sử dụng biến màu
                                        )
                                    )
                                )
                                .border(
                                    width = 2.dp,
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            dialogPrimaryColor, // Sử dụng biến màu
                                            dialogTertiaryColor // Sử dụng biến màu
                                        )
                                    ),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            // Email icon container
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(CircleShape)
                                    .background(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                dialogPrimaryContainerColor, // Sử dụng biến màu
                                                dialogSecondaryContainerColor // Sử dụng biến màu
                                            )
                                        )
                                    )
                                    .padding(12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                // Email icon với animation lặp lại (pulse effect)
                                Icon(
                                    imageVector = Icons.Outlined.MarkEmailRead,
                                    contentDescription = null,
                                    tint = dialogPrimaryColor, // Sử dụng biến màu
                                    modifier = Modifier
                                        .size(48.dp)
                                        .scale(iconBounce) // Sử dụng giá trị animation
                                        .alpha(iconAlpha) // Sử dụng giá trị animation
                                )
                            }
                        }

                        // Hiệu ứng tia sáng xung quanh icon
                        Canvas(modifier = Modifier.size(120.dp)) {
                            val radius = size.width / 2
                            val centerX = size.width / 2
                            val centerY = size.height / 2
                            val rayCount = 8
                            val rayLengthPx = rayAnimation // Giá trị đã là Float
                            
                            // Đảm bảo phép nhân đúng kiểu Float
                            val animatedRayLength = rayLengthPx * this.density // Sử dụng this.density thay vì density
                            
                            for (i in 0 until rayCount) {
                                val angle = (i * (360f / rayCount)) * (Math.PI.toFloat() / 180f) // Chuyển sang Float
                                // Sửa các phép tính
                                val startX = centerX + (radius - 10f.dp.toPx()) * cos(angle)
                                val startY = centerY + (radius - 10f.dp.toPx()) * sin(angle)
                                val endX = centerX + (radius + animatedRayLength - 10f.dp.toPx()) * cos(angle)
                                val endY = centerY + (radius + animatedRayLength - 10f.dp.toPx()) * sin(angle)

                                drawLine(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            dialogPrimaryColor.copy(alpha = 0.7f), // Sử dụng biến màu
                                            dialogPrimaryColor.copy(alpha = 0f)    // Sử dụng biến màu
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
                    // --- Phần còn lại của Column không thay đổi ---
                    Spacer(modifier = Modifier.height(24.dp))

                    // Title với animation
                    AnimatedVisibility(
                        visible = expandControls,
                        enter = fadeIn(tween(400)) + expandVertically(tween(400, easing = EaseOutCubic)),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Text(
                            text = "Kiểm tra Email",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = dialogOnSurfaceColor, // Sử dụng biến màu
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
                            text = emailState,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = dialogPrimaryColor, // Sử dụng biến màu
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .background(
                                    color = dialogPrimaryContainerColor.copy(alpha = 0.4f), // Sử dụng biến màu
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
                            text = "Chúng tôi đã gửi email với liên kết đặt lại mật khẩu.\nVui lòng kiểm tra cả thư mục chính và spam.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = dialogOnSurfaceVariantColor, // Sử dụng biến màu
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Buttons - staggered animation
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
                                            dialogPrimaryColor, // Sử dụng biến màu
                                            dialogTertiaryColor // Sử dụng biến màu
                                        )
                                    )
                                ),
                                modifier = Modifier.height(56.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = dialogPrimaryColor // Sử dụng biến màu
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

                        // OK button
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
                                    onBackToLogin()
                                },
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.height(56.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = dialogPrimaryColor // Sử dụng biến màu
                                )
                            ) {
                                Text(
                                    text = "Đã hiểu",
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
                } // Kết thúc Column
            } // Kết thúc Box
        } // Kết thúc Dialog
    } // Kết thúc if (state.isSuccess)

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
            contentWindowInsets = WindowInsets(0, 0, 0, 0),  // Sửa để tương đồng với LoginScreen
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(scrollState)
                    .padding(24.dp)
                    .imePadding(),  // Thêm imePadding() ở đây
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(modifier = Modifier.height(40.dp))
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .scale(logoSize)
                        .drawBehind {
                            // Sửa lỗi phép nhân - đảm bảo sử dụng 0.7f (Float)
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(primaryContainerColor, primaryContainerColor.copy(alpha = 0f))
                                ),
                                radius = size.width * 0.7f  // Đảm bảo số nhân là 0.7f (Float) không phải 0.7 (Double)
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.app),
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape)
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
                                        viewModel.initiatePasswordReset(emailState.trim())
                                    } else {
                                        coroutineScope.launch {
                                            snackbarHostState.showSnackbar("Vui lòng nhập email hợp lệ")
                                        }
                                    }
                                }
                            )
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                keyboardController?.hide()
                                if (isEmailValid) {
                                    viewModel.initiatePasswordReset(emailState.trim())
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