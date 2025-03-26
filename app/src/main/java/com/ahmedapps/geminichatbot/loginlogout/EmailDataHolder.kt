package com.ahmedapps.geminichatbot.loginlogout

import android.util.Log

/**
 * Đối tượng singleton đơn giản để lưu trữ email tạm thời
 * giữa các màn hình trong quá trình đăng nhập/quên mật khẩu
 */
object EmailDataHolder {
    private var emailForReset: String = ""
    
    fun setEmail(email: String) {
        emailForReset = email.trim()
        Log.d("EmailDataHolder", "Email đã được lưu: '$emailForReset'")
    }
    
    fun getEmail(): String {
        Log.d("EmailDataHolder", "Email đang được lấy: '$emailForReset'")
        return emailForReset
    }
    
    fun clearEmail() {
        Log.d("EmailDataHolder", "Email đã được xóa (trước đó: '$emailForReset')")
        emailForReset = ""
    }
} 