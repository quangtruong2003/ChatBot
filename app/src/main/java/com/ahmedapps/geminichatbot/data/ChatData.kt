//package com.ahmedapps.geminichatbot.data
//
//import android.graphics.Bitmap
//import com.ahmedapps.geminichatbot.BuildConfig
//import com.google.ai.client.generativeai.GenerativeModel
//import com.google.ai.client.generativeai.type.content
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.withContext
//
//object ChatData {
//
//    private val apiKey = BuildConfig.API_KEY
//
//    suspend fun getResponse(prompt: String): Chat {
//        val generativeModel = GenerativeModel(
//            modelName = "gemini-1.5-flash",
//            apiKey = apiKey
//        )
//
//        return try {
//            val response = withContext(Dispatchers.IO) {
//                generativeModel.generateContent(prompt)
//            }
//
//            Chat(
//                prompt = response.text ?: "Error: Empty response",
//                bitmap = null,
//                isFromUser = false
//            )
//
//        } catch (e: Exception) {
//            Chat(
//                prompt = "Error: ${e.message}",
//                bitmap = null,
//                isFromUser = false
//            )
//        }
//    }
//
//    suspend fun getResponseWithImage(prompt: String, bitmap: Bitmap): Chat {
//        val generativeModel = GenerativeModel(
//            modelName = "gemini-1.5-flash",
//            apiKey = apiKey
//        )
//
//        return try {
//            val inputContent = content {
//                image(bitmap)
//                text(prompt)
//            }
//
//            val response = withContext(Dispatchers.IO) {
//                generativeModel.generateContent(inputContent)
//            }
//
//            Chat(
//                prompt = response.text ?: "Error: Empty response",
//                bitmap = null,
//                isFromUser = false
//            )
//
//        } catch (e: Exception) {
//            Chat(
//                prompt = "Error: ${e.message}",
//                bitmap = null,
//                isFromUser = false
//            )
//        }
//    }
//}
