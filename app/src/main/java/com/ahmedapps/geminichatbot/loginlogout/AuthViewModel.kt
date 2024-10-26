// LoginLogout/AuthViewModel.kt
package com.ahmedapps.geminichatbot.loginlogout

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

data class AuthState(
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: FirebaseAuth
) : ViewModel() {

    private val _authState = MutableStateFlow(AuthState())
    val authState: StateFlow<AuthState> = _authState

    fun login(email: String, password: String) {
        _authState.value = AuthState(isLoading = true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _authState.value = AuthState(isSuccess = true)
                } else {
                    _authState.value = AuthState(errorMessage = task.exception?.message)
                }
            }
    }

    fun register(email: String, password: String) {
        _authState.value = AuthState(isLoading = true)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _authState.value = AuthState(isSuccess = true)
                } else {
                    _authState.value = AuthState(errorMessage = task.exception?.message)
                }
            }
    }

    fun resetState() {
        _authState.value = AuthState()
    }
}
