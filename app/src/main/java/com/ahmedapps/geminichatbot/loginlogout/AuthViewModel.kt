// AuthViewModel.kt
package com.ahmedapps.geminichatbot.loginlogout

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
    val authState: StateFlow<AuthState> = _authState

    init {
        checkCurrentUser()
    }

    private fun checkCurrentUser() {
        viewModelScope.launch {
            val user = auth.currentUser
            if (user != null) {
                _authState.value = AuthState(isAuthenticated = true, userId = user.uid)
            } else {
                _authState.value = AuthState(isAuthenticated = false, userId = "")
            }
        }
    }

    fun login(email: String, password: String) {
        _authState.value = AuthState(isLoading = true)
        viewModelScope.launch {
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                val user = auth.currentUser
                _authState.value = AuthState(isSuccess = true, isAuthenticated = true, userId = user?.uid ?: "")
            } catch (e: Exception) {
                _authState.value = AuthState(errorMessage = e.message, isLoading = false)
                Log.e("AuthViewModel", "Đăng nhập thất bại", e)
            }
        }
    }

    fun register(email: String, password: String) {
        _authState.value = AuthState(isLoading = true)
        viewModelScope.launch {
            try {
                auth.createUserWithEmailAndPassword(email, password).await()
                _authState.value = AuthState(isSuccess = true, isAuthenticated = true, userId = auth.currentUser?.uid ?: "")
            } catch (e: Exception) {
                _authState.value = AuthState(errorMessage = e.message, isLoading = false)
                Log.e("AuthViewModel", "Đăng ký thất bại", e)
            }
        }
    }

    fun resetState() {
        _authState.value = AuthState()
    }
}
