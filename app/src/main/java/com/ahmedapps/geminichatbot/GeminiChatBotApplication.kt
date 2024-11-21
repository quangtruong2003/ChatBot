// GeminiChatBotApplication.kt
package com.ahmedapps.geminichatbot

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GeminiChatBotApplication : Application(){
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)

//        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
//            PlayIntegrityAppCheckProviderFactory.getInstance()
//        )

    }
}
