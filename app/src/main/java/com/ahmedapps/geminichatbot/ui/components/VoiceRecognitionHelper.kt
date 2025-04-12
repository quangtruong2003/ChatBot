package com.ahmedapps.geminichatbot.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.Locale

/**
 * Helper class để xử lý nhận dạng giọng nói.
 * 
 * @param context Context của ứng dụng
 * @param onRecognitionResult Callback được gọi khi quá trình nhận dạng hoàn tất hoặc bị lỗi
 */
class VoiceRecognitionHelper(
    private val context: Context,
    private val onRecognitionResult: (String) -> Unit = {}
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val recognitionResult = Channel<RecognitionResult>()
    private var isListening = false
    private var hasCalledCallback = false
    
    val resultFlow = recognitionResult.receiveAsFlow()
    
    sealed class RecognitionResult {
        data class Success(val text: String) : RecognitionResult()
        data class Error(val errorMessage: String) : RecognitionResult()
        object Started : RecognitionResult()
        object ReadyForSpeech : RecognitionResult()
    }
    
    /**
     * Kiểm tra xem ứng dụng có quyền ghi âm không
     */
    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Bắt đầu quá trình nhận dạng giọng nói
     * 
     * @param language Ngôn ngữ sử dụng cho nhận dạng giọng nói (mặc định là Tiếng Việt)
     */
    fun startListening(language: String = "vi-VN") {
        if (isListening) {
            Log.d("VoiceRecognition", "Already listening, ignoring startListening call")
            return
        }
        
        // Reset trạng thái callback
        hasCalledCallback = false
        
        if (!hasRecordAudioPermission()) {
            Log.e("VoiceRecognition", "No record audio permission")
            recognitionResult.trySend(RecognitionResult.Error("Không có quyền ghi âm"))
            onRecognitionResult("") // Gọi callback với chuỗi rỗng khi không có quyền
            hasCalledCallback = true
            return
        }
        
        try {
            // Khởi tạo SpeechRecognizer nếu chưa có
            if (speechRecognizer == null) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            } else {
                // Nếu đã có, hủy bỏ instance cũ và tạo mới để tránh lỗi
                speechRecognizer?.destroy()
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            }
            
            val recognitionListener = createRecognitionListener()
            speechRecognizer?.setRecognitionListener(recognitionListener)
            
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5) // Tăng số lượng kết quả để cải thiện độ chính xác
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                // Cải thiện các tham số nhận dạng
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L) // Giảm xuống để nhận dạng nhanh hơn
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false) // Sử dụng cả online/offline để cải thiện kết quả
            }
            
            isListening = true
            speechRecognizer?.startListening(intent)
            Log.d("VoiceRecognition", "Started listening")
        } catch (e: Exception) {
            Log.e("VoiceRecognition", "Error starting speech recognition: ${e.message}")
            recognitionResult.trySend(RecognitionResult.Error("Không thể khởi động nhận dạng giọng nói: ${e.message}"))
            onRecognitionResult("") // Gọi callback với chuỗi rỗng khi có lỗi khởi động
            hasCalledCallback = true
            isListening = false
        }
    }
    
    /**
     * Tạo và trả về RecognitionListener mới
     */
    private fun createRecognitionListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d("VoiceRecognition", "Ready for speech")
                recognitionResult.trySend(RecognitionResult.ReadyForSpeech)
            }

            override fun onBeginningOfSpeech() {
                Log.d("VoiceRecognition", "Beginning of speech")
                recognitionResult.trySend(RecognitionResult.Started)
            }

            override fun onRmsChanged(rmsdB: Float) {
                // Có thể sử dụng để hiển thị mức độ âm thanh
                // Log.v("VoiceRecognition", "RMS changed: $rmsdB")
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                Log.d("VoiceRecognition", "Buffer received")
            }

            override fun onEndOfSpeech() {
                Log.d("VoiceRecognition", "End of speech")
                // Không cập nhật isListening ở đây vì chúng ta vẫn đang chờ kết quả
            }

            override fun onError(error: Int) {
                val errorMessage = getErrorMessage(error)
                Log.e("VoiceRecognition", "Error: $errorMessage (code: $error)")
                
                // Nếu lỗi là không phát hiện giọng nói hoặc không đủ điều kiện 
                // và nó không phải do người dùng chủ động dừng (ERROR_CLIENT),
                // thì hiển thị thông báo
                if (error != SpeechRecognizer.ERROR_CLIENT && isListening) {
                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                }
                
                recognitionResult.trySend(RecognitionResult.Error(errorMessage))
                
                // Chỉ gọi callback nếu chưa được gọi trước đó
                if (!hasCalledCallback) {
                    onRecognitionResult("") // Gọi callback với chuỗi rỗng khi có lỗi
                    hasCalledCallback = true
                }
                
                isListening = false
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                
                if (!matches.isNullOrEmpty()) {
                    // Lấy kết quả tốt nhất (đầu tiên)
                    val text = matches[0]
                    Log.d("VoiceRecognition", "Results: $text")
                    recognitionResult.trySend(RecognitionResult.Success(text))
                    
                    // Ghi log tất cả các kết quả để debug
                    if (matches.size > 1) {
                        Log.d("VoiceRecognition", "All results: ${matches.joinToString(" | ")}")
                    }
                    
                    // Chỉ gọi callback nếu chưa được gọi trước đó
                    if (!hasCalledCallback) {
                        onRecognitionResult(text) // Gọi callback với văn bản nhận dạng được
                        hasCalledCallback = true
                    }
                } else {
                    Log.e("VoiceRecognition", "No recognition results")
                    recognitionResult.trySend(RecognitionResult.Error("Không nhận dạng được giọng nói"))
                    
                    // Chỉ gọi callback nếu chưa được gọi trước đó
                    if (!hasCalledCallback) {
                        onRecognitionResult("") // Gọi callback với chuỗi rỗng khi không nhận dạng được
                        hasCalledCallback = true
                    }
                }
                
                isListening = false
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    val text = matches[0]
                    Log.d("VoiceRecognition", "Partial results: $text")
                    // Có thể hiển thị kết quả tạm thời nếu muốn
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                Log.d("VoiceRecognition", "Event: $eventType")
            }
        }
    }
    
    /**
     * Lấy thông báo lỗi từ mã lỗi
     */
    private fun getErrorMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Lỗi audio"
            SpeechRecognizer.ERROR_CLIENT -> "Lỗi client"
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Không đủ quyền"
            SpeechRecognizer.ERROR_NETWORK -> "Lỗi mạng"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Hết thời gian mạng"
            SpeechRecognizer.ERROR_NO_MATCH -> "Không nhận dạng được giọng nói"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Dịch vụ đang bận"
            SpeechRecognizer.ERROR_SERVER -> "Lỗi server"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Không phát hiện giọng nói"
            else -> "Lỗi không xác định (mã: $error)"
        }
    }
    
    /**
     * Dừng quá trình nhận dạng giọng nói đang diễn ra
     */
    fun stopListening() {
        if (!isListening) {
            Log.d("VoiceRecognition", "Not listening, ignoring stopListening call")
            return
        }
        
        try {
            Log.d("VoiceRecognition", "Stopping listening")
            speechRecognizer?.stopListening()
            // isListening and callback sẽ được xử lý trong callbacks onResults hoặc onError
        } catch (e: Exception) {
            Log.e("VoiceRecognition", "Error stopping speech recognition: ${e.message}")
            // Nếu có lỗi khi dừng, cập nhật trạng thái và gọi callback nếu cần
            isListening = false
            
            if (!hasCalledCallback) {
                onRecognitionResult("") // Gọi callback với chuỗi rỗng nếu có lỗi
                hasCalledCallback = true
            }
        }
    }
    
    /**
     * Hủy bỏ và giải phóng tài nguyên
     */
    fun destroy() {
        try {
            isListening = false
            
            // Đảm bảo callback được gọi nếu destroy trước khi có kết quả
            if (!hasCalledCallback) {
                onRecognitionResult("")
                hasCalledCallback = true
            }
            
            speechRecognizer?.destroy()
            speechRecognizer = null
            Log.d("VoiceRecognition", "Destroyed")
        } catch (e: Exception) {
            Log.e("VoiceRecognition", "Error destroying speech recognizer: ${e.message}")
        }
    }
} 

