package com.ahmedapps.geminichatbot.di

import com.ahmedapps.geminichatbot.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenerativeModelProvider @Inject constructor() {

    private var modelName: String = "gemini-2.0-flash-exp"
    private var generativeModel: GenerativeModel = createGenerativeModel()

    fun getGenerativeModel(): GenerativeModel {
        return generativeModel
    }

    fun updateGenerativeModel(newModelName: String) {
        if (modelName != newModelName) {
            modelName = newModelName
            generativeModel = createGenerativeModel()
        }
    }

    private fun createGenerativeModel(): GenerativeModel {
        return GenerativeModel(
            modelName = modelName,
            apiKey = BuildConfig.API_KEY
        )
    }
}