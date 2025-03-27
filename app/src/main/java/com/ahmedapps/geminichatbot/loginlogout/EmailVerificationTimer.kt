package com.ahmedapps.geminichatbot.loginlogout

import android.os.CountDownTimer
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object EmailVerificationTimer {
    private const val COUNTDOWN_TIME = 60000L // 60 giây
    
    // Sử dụng MutableState thay vì StateFlow để dễ theo dõi trong Compose
    private val _isRunning = mutableStateOf(false)
    val isRunning: State<Boolean> = _isRunning
    
    private val _timeLeft = mutableStateOf(0L)
    val timeLeft: State<Long> = _timeLeft
    
    private var countDownTimer: CountDownTimer? = null
    
    fun startTimer() {
        Log.d("EmailTimer", "Timer started with countdown: $COUNTDOWN_TIME")
        countDownTimer?.cancel()
        
        _isRunning.value = true
        _timeLeft.value = COUNTDOWN_TIME
        
        countDownTimer = object : CountDownTimer(COUNTDOWN_TIME, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                _timeLeft.value = millisUntilFinished
                Log.d("EmailTimer", "onTick: ${millisUntilFinished/1000}s remaining")
            }
            
            override fun onFinish() {
                _isRunning.value = false
                _timeLeft.value = 0
                Log.d("EmailTimer", "Timer finished")
            }
        }.start()
    }
    
    fun resetTimer() {
        countDownTimer?.cancel()
        _isRunning.value = false
        _timeLeft.value = 0
    }
} 