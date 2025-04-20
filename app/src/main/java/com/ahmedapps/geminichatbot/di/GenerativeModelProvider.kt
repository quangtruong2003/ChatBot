package com.ahmedapps.geminichatbot.di

import com.ahmedapps.geminichatbot.BuildConfig
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerationConfig
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenerativeModelProvider @Inject constructor() {

    private var modelName: String = "gemini-2.0-flash-exp"

    fun updateGenerativeModel(newModelName: String) {
        if (modelName != newModelName) {
            modelName = newModelName
        }
    }

    fun createModelWithConfig(config: GenerationConfig, safetySettings: List<com.google.ai.client.generativeai.type.SafetySetting>? = null): GenerativeModel {
        return GenerativeModel(
            modelName = modelName,
            apiKey = BuildConfig.API_KEY,
            generationConfig = config,
            safetySettings = safetySettings
        )
    }

    fun getCurrentModelName(): String {
        return modelName
    }
}