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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class AuthState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null,
    val isAuthenticated: Boolean = false,
    val userId: String = ""
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

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
                                errorMessage = "Please verify your email address before logging in.",
                                isLoading = false
                            )
                        }
                        // auth.signOut() // Loại bỏ dòng này
                    }
                } else {
                    _authState.update {
                        it.copy(
                            errorMessage = "Login failed. Please check your credentials.",
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

    fun register(email: String, password: String) {
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

        // Cập nhật trạng thái đang tải và xoá thông báo lỗi trước đó
        _authState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                auth.createUserWithEmailAndPassword(email, password).await()
                val user = auth.currentUser

                user?.sendEmailVerification()?.await()

                _authState.update {
                    it.copy(
                        isSuccess = true,
                        isLoading = false,
                        errorMessage = "Registration successful! Please check your email for verification."
                    )
                }

            } catch (e: FirebaseTooManyRequestsException) {
                _authState.update {
                    it.copy(
                        errorMessage = "Too many requests from this device. Please try again later.",
                        isLoading = false
                    )
                }
                Log.e("AuthViewModel", "Too many requests", e)
//            } catch (e: FirebaseAppCheckException) {
//                _authState.update {
//                    it.copy(
//                        errorMessage = "App Check validation failed. Please try again later.",
//                        isLoading = false
//                    )
//                }
//                Log.e("AuthViewModel", "App Check failed", e)
            } catch (e: Exception) {
                _authState.update {
                    it.copy(
                        errorMessage = e.message,
                        isLoading = false
                    )
                }
                Log.e("AuthViewModel", "Registration failed", e)
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
        _authState.value = AuthState(isAuthenticated = _authState.value.isAuthenticated)
    }
}
