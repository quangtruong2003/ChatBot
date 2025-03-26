// AuthViewModel.kt
package com.ahmedapps.geminichatbot.loginlogout

import android.util.Log
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException

data class AuthState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
    val isAuthenticated: Boolean = false,
    val userId: String = "",
    val emailCheckLoading: Boolean = false,
    val emailExists: Boolean? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private var emailCheckJob: Job? = null

    init {
        checkCurrentUser()
    }

    private fun checkCurrentUser() {
        val user = auth.currentUser
        _authState.update { currentState ->
            currentState.copy(
                isAuthenticated = user != null && user.isEmailVerified,
                userId = user?.uid.orEmpty()
            )
        }
    }

    fun login(email: String, password: String) {
        // Kiểm tra điều kiện đầu vào
        if (email.isEmpty() || password.isEmpty()) {
            _authState.update {
                it.copy(errorMessage = "Email and password cannot be empty.", isLoading = false)
            }
            return
        }

        // Kiểm tra định dạng email hợp lệ
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _authState.update {
                it.copy(errorMessage = "Please enter a valid email address.", isLoading = false)
            }
            return
        }

        // Cập nhật trạng thái đang tải và xoá thông báo lỗi
        _authState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                val user = auth.currentUser

                if (user != null) {
                    if (user.isEmailVerified) {
                        _authState.update {
                            AuthState(
                                isSuccess = true,
                                isAuthenticated = true,
                                userId = user.uid,
                                isLoading = false
                            )
                        }
                    } else {
                        _authState.update {
                            it.copy(
                                errorMessage = "Vui lòng xác minh địa chỉ email của bạn trước khi đăng nhập",
                                isLoading = false
                            )
                        }
                    }
                } else {
                    _authState.update {
                        it.copy(
                            errorMessage = "Đăng nhập thất bại. Vui lòng kiểm tra thông tin đăng nhập của bạn",
                            isLoading = false
                        )
                    }
                }

            } catch (e: Exception) {
                _authState.update {
                    it.copy(
                        errorMessage = e.message,
                        isLoading = false
                    )
                }
                Log.e("AuthViewModel", "Login failed", e)
            }
        }
    }

    fun checkEmailExists(email: String) {
        emailCheckJob?.cancel()

        if (email.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _authState.update { it.copy(emailExists = null, emailCheckLoading = false) }
            return
        }

        emailCheckJob = viewModelScope.launch(Dispatchers.IO) {
            delay(700)

            _authState.update { it.copy(emailCheckLoading = true, emailExists = null, errorMessage = null) }

            try {
                // Cách 1: Kiểm tra bằng fetchSignInMethodsForEmail
                val signInMethods = auth.fetchSignInMethodsForEmail(email).await().signInMethods
                var exists = !signInMethods.isNullOrEmpty()
                
                // Nếu signInMethods trả về false, thử kiểm tra bằng cách tạo tài khoản tạm
                if (!exists) {
                    try {
                        // Cách 2: Thử tạo tài khoản với mật khẩu tạm để xem có lỗi không
                        // Sử dụng mật khẩu phức tạp tạm thời
                        val tempPassword = "Temp@123456789"
                        auth.createUserWithEmailAndPassword(email, tempPassword).await()
                        // Nếu tạo được, email chưa tồn tại, xóa ngay tài khoản vừa tạo
                        auth.currentUser?.delete()?.await()
                        exists = false
                    } catch (e: Exception) {
                        // Nếu lỗi collision, chứng tỏ email đã tồn tại
                        if (e is com.google.firebase.auth.FirebaseAuthUserCollisionException) {
                            exists = true
                            Log.d("AuthViewModel", "Phát hiện email tồn tại thông qua phương pháp 2")
                        }
                    }
                }

                _authState.update { it.copy(emailExists = exists, emailCheckLoading = false) }
                Log.d("AuthViewModel", "Email '$email' exists: $exists (Phương pháp kết hợp)")
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Email check failed for '$email'", e)
                _authState.update { it.copy(emailExists = null, emailCheckLoading = false) }
            }
        }
    }

    fun resetEmailCheckState() {
        emailCheckJob?.cancel()
        _authState.update { it.copy(emailExists = null, emailCheckLoading = false) }
    }

    fun register(email: String, password: String) {
        // Kiểm tra đầu vào cơ bản
        if (email.isBlank() || password.isBlank()) {
            _authState.value = AuthState(errorMessage = "Email và mật khẩu không được để trống.")
            return
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
             _authState.value = AuthState(errorMessage = "Định dạng email không hợp lệ.")
             return
        }
        // Các kiểm tra mật khẩu khác nếu cần

        // *** Chỉ dựa vào trạng thái đã kiểm tra trước đó ***
        if (_authState.value.emailExists == true) {
             // Cập nhật errorMessage để Snackbar hiển thị nếu cần, không cần set lại emailExists
             _authState.update { it.copy(errorMessage = "Email này đã được đăng ký.", isLoading = false) }
             Log.w("AuthViewModel", "Registration attempt failed: Email already exists ('$email').")
             return
        }
        // Đảm bảo việc kiểm tra đã chạy (emailExists không phải là null)
        if (_authState.value.emailExists == null && Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _authState.update { it.copy(errorMessage = "Đang kiểm tra email, vui lòng đợi giây lát.", isLoading = false) }
            Log.w("AuthViewModel", "Registration attempt failed: Email check pending ('$email').")
            // Có thể kích hoạt lại kiểm tra hoặc chỉ thông báo
            // checkEmailExists(email)
            return
        }


        _authState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // *** BỎ ĐOẠN KIỂM TRA EMAIL THỪA TRONG NÀY ***
                /*
                val signInMethods = auth.fetchSignInMethodsForEmail(email).await().signInMethods
                if (signInMethods?.isNotEmpty() == true) {
                    _authState.update { it.copy(errorMessage = "Email này đã được đăng ký.", isLoading = false, emailExists = true) }
                    return@launch
                }
                */

                // *** Tiến hành tạo tài khoản trực tiếp ***
                val userCredential = auth.createUserWithEmailAndPassword(email, password).await()
                Log.d("AuthViewModel", "User created successfully: ${userCredential.user?.uid}")

                // *** Gửi email xác thực NGAY SAU KHI TẠO THÀNH CÔNG ***
                try {
                    userCredential.user?.sendEmailVerification()?.await()
                    Log.d("AuthViewModel", "Verification email sent to ${userCredential.user?.email}")
                    // Cập nhật trạng thái thành công CUỐI CÙNG
                    _authState.update { it.copy(isSuccess = true, isLoading = false) }
                } catch (verificationError: Exception) {
                    Log.e("AuthViewModel", "Failed to send verification email", verificationError)
                    // Vẫn coi là đăng ký thành công, nhưng có thể thêm thông báo lỗi phụ
                    _authState.update { it.copy(isSuccess = true, isLoading = false, errorMessage = "Đăng ký thành công nhưng không thể gửi email xác minh.") }
                }

            } catch (e: Exception) {
                Log.e("AuthViewModel", "Registration failed", e)
                val errorMsg: String
                var finalEmailExists: Boolean? = _authState.value.emailExists // Giữ giá trị cũ trừ khi có lỗi collision

                when (e) {
                    is FirebaseAuthWeakPasswordException -> errorMsg = "Mật khẩu quá yếu."
                    is FirebaseAuthInvalidCredentialsException -> errorMsg = "Định dạng email không hợp lệ." // Lẽ ra đã bắt ở ngoài
                    is FirebaseNetworkException -> errorMsg = "Lỗi mạng, vui lòng kiểm tra kết nối."
                    is FirebaseTooManyRequestsException -> errorMsg = "Quá nhiều yêu cầu, vui lòng thử lại sau."
                    // Bắt lỗi cụ thể nếu email đã tồn tại (dự phòng trường hợp check trước đó bị sai)
                    is com.google.firebase.auth.FirebaseAuthUserCollisionException -> {
                        errorMsg = "Email này đã được đăng ký."
                        finalEmailExists = true // Cập nhật lại emailExists nếu gặp lỗi này
                        Log.w("AuthViewModel", "FirebaseAuthUserCollisionException caught for '$email'.")
                    }
                    else -> errorMsg = e.localizedMessage ?: "Đăng ký thất bại. Vui lòng thử lại."
                }

                // Cập nhật trạng thái lỗi, bao gồm cả emailExists nếu có lỗi collision
                _authState.update { it.copy(errorMessage = errorMsg, isLoading = false, emailExists = finalEmailExists) }
            }
        }
    }

    fun firebaseAuthWithGoogle(idToken: String) {
        _authState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val credential = GoogleAuthProvider.getCredential(idToken, null)
                val authResult = auth.signInWithCredential(credential).await()
                val user = authResult.user
                _authState.update {
                    AuthState(
                        isSuccess = true,
                        isAuthenticated = true,
                        userId = user?.uid.orEmpty(),
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _authState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.message
                    )
                }
                Log.e("AuthViewModel", "Google sign-in failed", e)
            }
        }
    }

    fun updateError(message: String?) {
        _authState.update { it.copy(errorMessage = message) }
    }

    fun resetState() {
        emailCheckJob?.cancel()
        _authState.value = AuthState()
        checkCurrentUser()
    }
}
