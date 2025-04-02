// GeminiChatBotApplication.kt
package com.ahmedapps.geminichatbot

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck

import dagger.hilt.android.HiltAndroidApp
import android.util.Log
import com.ahmedapps.geminichatbot.services.PDFProcessingService


@HiltAndroidApp
class GeminiChatBotApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        FirebaseApp.initializeApp(this)
//        val firebaseAppCheck = FirebaseAppCheck.getInstance()
//        if (BuildConfig.DEBUG) {
//            // Sử dụng DebugAppCheckProvider khi ở chế độ debug
//            firebaseAppCheck.installAppCheckProviderFactory(
//                DebugAppCheckProviderFactory.getInstance()
//            )
//        } else {
//            // Sử dụng PlayIntegrityAppCheckProvider khi ở chế độ release
//            firebaseAppCheck.installAppCheckProviderFactory(
//                PlayIntegrityAppCheckProviderFactory.getInstance()
//            )
//        }

        // Khởi tạo PDFProcessingService
        PDFProcessingService.init(this)
    }
}
