// AuthViewModel.kt
package com.ahmedapps.geminichatbot.loginlogout

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
                isAuthenticated = user != null,
                userId = user?.uid.orEmpty()
            )
        }
    }

    fun login(email: String, password: String) {
        _authState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                auth.signInWithEmailAndPassword(email, password).await()
                val user = auth.currentUser
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
                        errorMessage = e.message,
                        isLoading = false
                    )
                }
                Log.e("AuthViewModel", "Login failed", e)
            }
        }
    }

    fun register(email: String, password: String) {
        _authState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                auth.createUserWithEmailAndPassword(email, password).await()
                val user = auth.currentUser
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
