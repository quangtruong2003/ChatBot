package com.ahmedapps.geminichatbot.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.StorageService
import java.io.File
import java.io.IOException

/**
 * Helper class để xử lý nhận dạng giọng nói sử dụng thư viện Vosk
 * Hỗ trợ nhận dạng offline với độ chính xác cao
 * 
 * @param context Context của ứng dụng
 * @param onRecognitionResult Callback được gọi khi quá trình nhận dạng hoàn tất hoặc bị lỗi
 */
class VoskRecognitionHelper(
    private val context: Context,
    private val onRecognitionResult: (String) -> Unit
) {
    companion object {
        private const val TAG = "VoskRecognition"
        private const val SAMPLE_RATE = 16000
        private const val BUFFER_SIZE_FACTOR = 5 // Tăng kích thước buffer để xử lý tốt hơn
        private const val MODEL_PATH = "vosk-model-vn-0.4" // Đường dẫn đến mô hình tiếng Việt 0.4
        private const val MODEL_ASSETS_PATH = "model-vi" // Đường dẫn trong assets
        private const val TIMEOUT_DURATION = 10000L // Timeout sau 10 giây
    }

    @Volatile private var recognizer: Recognizer? = null
    @Volatile private var model: Model? = null
    @Volatile private var audioRecord: AudioRecord? = null
    @Volatile private var isListening = false
    @Volatile private var hasCalledCallback = false
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var recordingJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var timeoutJob: Job? = null

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
     * Khởi tạo Vosk recognizer và mô hình
     * Phải gọi phương thức này trước khi bắt đầu nhận dạng
     */
    suspend fun initializeModel() = withContext(Dispatchers.IO) {
        if (model != null) return@withContext

        try {
            // Đặt log level
            LibVosk.setLogLevel(LogLevel.INFO)

            // Tạo đường dẫn đến mô hình
            val modelDirPath = File(context.filesDir, MODEL_PATH).absolutePath
            
            // Thử tìm mô hình trong filesDir trước
            var modelDir = File(modelDirPath)
            
            // Nếu mô hình chưa được giải nén vào filesDir, sử dụng StorageService để đồng bộ từ assets
            if (!modelDir.exists() || !isValidModelDirectory(modelDir)) {
                Log.d(TAG, "Model not found in filesDir, syncing from assets")
                val syncedPath = StorageService.sync(context, MODEL_ASSETS_PATH, modelDirPath)
                modelDir = File(syncedPath)
            }
            
            // Kiểm tra xem mô hình có hợp lệ không
            if (!isValidModelDirectory(modelDir)) {
                throw IOException("Invalid model directory. Please make sure the model is correctly installed.")
            }
            
            // Khởi tạo model
            model = Model(modelDir.absolutePath)
            
            Log.d(TAG, "Model initialized successfully at ${modelDir.absolutePath}")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to initialize model: ${e.message}")
            triggerErrorCallback() // Trigger error if model init fails
        } catch (e: UnsatisfiedLinkError) {
            Log.e(TAG, "JNA UnsatisfiedLinkError. Ensure JNA library is included correctly: ${e.message}")
            triggerErrorCallback()
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error initializing model: ${e.message}")
            triggerErrorCallback()
        }
    }
    
    /**
     * Kiểm tra xem thư mục mô hình có hợp lệ không
     */
    private fun isValidModelDirectory(directory: File): Boolean {
        return directory.exists() && 
               directory.isDirectory &&
               // Kiểm tra sự tồn tại của một số file/thư mục cốt lõi của mô hình Vosk
               (File(directory, "am").exists() ||
                File(directory, "conf").exists() ||
                File(directory, "final.mdl").exists() ||
                File(directory, "model.conf").exists()) // Thêm kiểm tra file config phổ biến
    }

    /**
     * Bắt đầu quá trình nhận dạng giọng nói
     */
    fun startListening() {
        if (isListening) {
            Log.d(TAG, "Already listening, ignoring startListening call")
            return
        }
        if (model == null) {
             Log.e(TAG, "Model not initialized before startListening()")
             triggerErrorCallback()
             return
        }

        hasCalledCallback = false // Reset flag khi bắt đầu nghe mới

        if (!hasRecordAudioPermission()) {
            Log.e(TAG, "No record audio permission")
            triggerErrorCallback()
            return
        }

        try {
            if (recognizer == null) {
                 recognizer = Recognizer(model, SAMPLE_RATE.toFloat())
            } else {
                 recognizer?.reset() // Reset recognizer để bắt đầu phiên mới
            }

            val minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBufferSize == AudioRecord.ERROR_BAD_VALUE) {
                throw IOException("Invalid parameters for AudioRecord buffer size calculation.")
            }
            val bufferSize = minBufferSize * BUFFER_SIZE_FACTOR

            // Kiểm tra quyền trước khi khởi tạo AudioRecord
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) != PackageManager.PERMISSION_GRANTED) {
                // Mặc dù đã kiểm tra ở trên, kiểm tra lại ngay trước khi tạo để chắc chắn
                throw SecurityException("Missing RECORD_AUDIO permission before creating AudioRecord.")
            }

            // Khởi tạo AudioRecord
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                 throw IOException("AudioRecord failed to initialize. State: ${audioRecord?.state}")
            }

            audioRecord?.startRecording()
            isListening = true
            Log.d(TAG, "Started listening with Vosk")
            
            // Bắt đầu timeout ngay sau khi bắt đầu ghi âm
            startTimeout()

            // Coroutine để đọc audio buffer và đưa vào recognizer
            recordingJob = coroutineScope.launch(Dispatchers.IO) {
                val buffer = ShortArray(bufferSize / 2) // Buffer cho dữ liệu 16-bit PCM

                while (isListening) { // Vòng lặp chạy khi isListening là true
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    
                    if (bytesRead > 0) {
                        // Đưa dữ liệu audio vào recognizer
                        // Lưu ý: recognizer.acceptWaveForm mong đợi byte array, cần chuyển đổi nếu cần
                        // Tuy nhiên, phiên bản vosk-android-demo thường dùng buffer short array trực tiếp
                        // Đảm bảo bạn đang dùng phiên bản phù hợp hoặc chuyển đổi nếu cần
                        val accepted = recognizer?.acceptWaveForm(buffer, bytesRead) ?: false
                        //if (!accepted) {
                            // Optional: log if recognizer didn't accept waveform for debugging
                            // Log.v(TAG, "Recognizer did not accept waveform chunk")
                        //}
                    } else if (bytesRead < 0) {
                        Log.e(TAG, "AudioRecord read error: $bytesRead")
                        // Nếu có lỗi đọc, dừng vòng lặp và có thể trigger lỗi
                        isListening = false // Dừng vòng lặp
                        // Consider triggering error callback from here if read fails critically
                        // triggerErrorCallback() // Might cause issues if stopListening is called concurrently
                        break
                    }
                    // Nếu bytesRead == 0, không có dữ liệu mới, tiếp tục vòng lặp
                }
                Log.d(TAG, "Recording loop finished (isListening is false or error occurred).")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting speech recognition: ${e.message}", e)
            isListening = false
            releaseAudioRecordSafely() // Đảm bảo giải phóng recorder khi có lỗi khởi động
            triggerErrorCallback() // Gọi callback lỗi
        }
    }
    
    /**
     * Giải phóng AudioRecord một cách an toàn
     */
    private fun releaseAudioRecordSafely() {
        if (audioRecord == null) return

        try {
             // Chỉ stop nếu đang ghi
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
                Log.d(TAG,"AudioRecord stopped.")
            }
        } catch (e: Exception) {
             Log.e(TAG, "Error stopping AudioRecord: ${e.message}")
        }
        
        try {
             audioRecord?.release()
             Log.d(TAG,"AudioRecord released.")
        } catch (e: Exception) {
             Log.e(TAG, "Error releasing AudioRecord: ${e.message}")
        } finally {
             audioRecord = null
        }
    }

    /**
     * Xử lý kết quả JSON từ Vosk và gọi callback cuối cùng
     */
    private fun processResult(jsonResult: String?) {
        var recognizedText = ""
        if (!jsonResult.isNullOrEmpty()) {
            try {
                val json = JSONObject(jsonResult)
                // optString trả về chuỗi rỗng nếu key không tồn tại hoặc không phải string
                recognizedText = json.optString("text", "").trim() 
                Log.d(TAG, "Vosk final result parsed: '$recognizedText'")
            } catch (e: JSONException) {
                Log.e(TAG, "Error parsing final JSON result: ${e.message}")
                // Giữ recognizedText là chuỗi rỗng khi lỗi parse
            }
        } else {
            Log.d(TAG, "Vosk final result received was null or empty.")
            // Giữ recognizedText là chuỗi rỗng
        }

        // Luôn gọi callback cuối cùng với kết quả đã xử lý (có thể rỗng)
        triggerFinalCallback(recognizedText) 
    }

    /**
     * Chạy code trên Main Thread
     */
    private fun onMainThread(action: () -> Unit) {
        if (Thread.currentThread() == Looper.getMainLooper().thread) {
            action()
        } else {
            mainHandler.post(action)
        }
    }

    /**
    * Gọi callback kết quả cuối cùng, đảm bảo chỉ gọi một lần.
    */
    private fun triggerFinalCallback(text: String) {
        cancelTimeout() // Đảm bảo timeout bị hủy khi có kết quả cuối cùng
        if (!hasCalledCallback) {
            hasCalledCallback = true // *** Đặt cờ ngay lập tức để tránh race condition ***
            onMainThread {
                Log.d(TAG, "Triggering final callback with text: '$text'")
                onRecognitionResult(text)
            }
        } else {
             Log.w(TAG, "Final callback already triggered, ignoring subsequent call with text: '$text'")
        }
    }

    /**
    * Gọi callback lỗi (kết quả rỗng), đảm bảo chỉ gọi một lần.
    */
    private fun triggerErrorCallback() {
        cancelTimeout() // Đảm bảo timeout bị hủy khi có lỗi
        if (!hasCalledCallback) {
            hasCalledCallback = true // *** Đặt cờ ngay lập tức để tránh race condition ***
            onMainThread {
                 Log.e(TAG, "Triggering error callback (empty result).")
                 onRecognitionResult("") // Gửi kết quả rỗng khi có lỗi
            }
        } else {
             Log.w(TAG, "Error callback already triggered, ignoring.")
        }
    }

    /**
     * Thiết lập timeout để tự động hủy xử lý sau TIMEOUT_DURATION
     */
    private fun startTimeout() {
        cancelTimeout() // Hủy timeout cũ nếu có
        timeoutJob = coroutineScope.launch {
            try {
                delay(TIMEOUT_DURATION) 
                Log.w(TAG, "Recognition timeout reached after ${TIMEOUT_DURATION}ms")
                if (isListening) { // Chỉ xử lý timeout nếu vẫn đang nghe
                    Log.d(TAG, "Force stopping due to timeout")
                    // Dừng vòng lặp ghi âm và giải phóng tài nguyên trên luồng IO
                    withContext(Dispatchers.IO) {
                        isListening = false // Dừng vòng lặp trong recordingJob
                        releaseAudioRecordSafely() // Giải phóng AudioRecord
                    }
                    // Gọi callback lỗi (kết quả rỗng) vì timeout
                    triggerErrorCallback() 
                } else {
                    Log.d(TAG,"Timeout reached but no longer listening.")
                }
            } catch (e: Exception) {
                // Coroutine có thể bị hủy (ví dụ khi destroy được gọi), không cần log lỗi nếu là CancellationException
                if (e !is kotlinx.coroutines.CancellationException) {
                    Log.e(TAG, "Error in timeout handler: ${e.message}")
                }
            }
        }
    }

    /**
     * Hủy timeout hiện tại
     */
    private fun cancelTimeout() {
        timeoutJob?.cancel()
        timeoutJob = null
        Log.d(TAG, "Timeout cancelled.")
    }

    /**
     * Dừng quá trình nhận dạng giọng nói đang diễn ra
     */
    fun stopListening() {
        if (!isListening) {
            Log.d(TAG, "Not listening, ignoring stopListening call")
            // Nếu gọi stop khi không nghe, không cần làm gì thêm,
            // vì callback đã được gọi (hoặc sẽ được gọi bởi lỗi/timeout trước đó)
            // Tránh gọi triggerFinalCallback("") ở đây để không ghi đè kết quả hợp lệ có thể vừa đến
            return
        }

        Log.d(TAG, "stopListening() called.")
        isListening = false // Yêu cầu dừng vòng lặp ghi âm (recordingJob)

        // Hủy timeout ngay lập tức vì người dùng chủ động dừng
        cancelTimeout() 

        // Chạy toàn bộ quá trình dừng trên luồng IO
        coroutineScope.launch(Dispatchers.IO) { 
            var finalResultJson: String? = null
            try {
                // 1. Đợi recordingJob hoàn thành việc ghi nhận audio vào recognizer
                Log.d(TAG, "Waiting for recording job to finish...")
                try {
                    recordingJob?.cancelAndJoin() // Hủy và đợi job đọc audio kết thúc
                    Log.d(TAG, "Recording job finished or cancelled.")
                } catch (e: Exception) {
                     // Log lỗi nhưng vẫn tiếp tục cố gắng lấy kết quả
                    Log.e(TAG, "Error waiting for recording job: ${e.message}")
                }

                // 2. Dừng và giải phóng AudioRecord (đã được gọi trong releaseAudioRecordSafely)
                Log.d(TAG, "Releasing AudioRecord...")
                releaseAudioRecordSafely() // Gọi hàm giải phóng an toàn

                // 3. Lấy kết quả cuối cùng từ recognizer
                Log.d(TAG, "Getting final result from recognizer...")
                try {
                    finalResultJson = recognizer?.getFinalResult()
                    Log.d(TAG, "Got final result JSON: $finalResultJson")
                } catch (e: Exception) {
                    Log.e(TAG, "Error getting final result from recognizer: ${e.message}")
                    finalResultJson = null // Đặt là null nếu có lỗi
                }

                // 4. Xử lý kết quả (parse JSON và gọi callback trên Main thread)
                // Hàm này sẽ gọi triggerFinalCallback hoặc triggerErrorCallback nếu cần
                processResult(finalResultJson) 

            } catch (e: Exception) {
                 // Bắt các lỗi không mong muốn trong quá trình dừng
                Log.e(TAG, "Unexpected error during stopListening IO process: ${e.message}", e)
                // Đảm bảo callback lỗi được gọi nếu có lỗi nghiêm trọng
                triggerErrorCallback()
            } finally {
                 // Đảm bảo AudioRecord chắc chắn được giải phóng
                if (audioRecord != null) {
                     Log.w(TAG, "AudioRecord was still not null in finally block, attempting release again.")
                     releaseAudioRecordSafely()
                }
                Log.d(TAG, "stopListening() IO coroutine finished.")
                // Không gọi callback từ finally nữa, processResult hoặc triggerErrorCallback đã xử lý
            }
        }
    }

    /**
     * Hủy bỏ và giải phóng tài nguyên
     */
    fun destroy() {
        Log.d(TAG, "destroy() called.")
        cancelTimeout() // Hủy mọi timeout đang chờ

        // Nếu vẫn đang nghe, cố gắng dừng lại một cách an toàn
        if (isListening) {
             Log.w(TAG, "Destroy called while still listening. Attempting to stop.")
             // Không gọi stopListening() trực tiếp để tránh tạo coroutine mới có thể bị hủy ngay lập tức
             // Thay vào đó, chỉ đặt cờ và giải phóng tài nguyên
             isListening = false
             recordingJob?.cancel() // Hủy job đọc audio
             releaseAudioRecordSafely()
             // Gọi callback lỗi nếu chưa có callback nào được gọi trước đó
             triggerErrorCallback() 
        }
        
        // Hủy coroutine scope để dừng mọi coroutine đang chạy hoặc chờ xử lý
        coroutineScope.cancel() 

        // Giải phóng recognizer và model một cách an toàn
        try {
            recognizer?.close() // Sử dụng close thay vì destroy nếu API hỗ trợ
            recognizer = null
             Log.d(TAG, "Recognizer closed.")
        } catch (e: Exception) {
             Log.e(TAG, "Error closing recognizer: ${e.message}")
        }
        
        try {
            model?.close() // Đảm bảo model cũng được giải phóng
            model = null
             Log.d(TAG, "Model closed.")
        } catch (e: Exception) {
             Log.e(TAG, "Error closing model: ${e.message}")
        }
    }
}