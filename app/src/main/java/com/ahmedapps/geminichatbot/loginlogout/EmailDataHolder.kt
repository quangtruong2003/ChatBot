package com.ahmedapps.geminichatbot.loginlogout

import android.util.Log

/**
 * Đối tượng singleton đơn giản để lưu trữ email tạm thời
 * giữa các màn hình trong quá trình đăng nhập/quên mật khẩu
 */
object EmailDataHolder {
    private var email: String = ""
    
    fun setEmail(newEmail: String) {
        email = newEmail
    }
    
    fun getEmail(): String {
        return email
    }
    
    fun clearEmail() {
        email = ""
    }
} 