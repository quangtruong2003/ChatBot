package com.ahmedapps.geminichatbot.loginlogout

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SharedAuthViewModel @Inject constructor() : ViewModel() {
    private val _tempEmail = MutableStateFlow("")
    val tempEmail: StateFlow<String> = _tempEmail.asStateFlow()
    
    fun setTempEmail(email: String) {
        _tempEmail.value = email
    }
    
    fun clearTempEmail() {
        _tempEmail.value = ""
    }
} 