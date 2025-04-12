package com.ahmedapps.geminichatbot.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Class tích hợp giữa nhận dạng giọng nói offline (Vosk) và online (SpeechRecognizer)
 * Tự động chọn phương thức phù hợp dựa trên kết nối mạng và sự có sẵn của mô hình
 * Đã cập nhật để hỗ trợ mô hình vosk-model-vn-0.4
 */
class ImprovedVoiceRecognitionHelper(
    private val context: Context,
    private val onRecognitionResult: (String) -> Unit,
    private val onStatusChange: (VoiceRecognitionStatus) -> Unit = {}
) {
    companion object {
        private const val TAG = "ImprovedVoiceRecognition"
        // Bỏ VOSK_MODEL_PATH vì đã dùng VoskModelManager
    }
    
    // Các trạng thái nhận dạng giọng nói
    enum class VoiceRecognitionStatus {
        IDLE,               // Không hoạt động
        INITIALIZING,       // Đang khởi tạo
        PREPARING_MODEL,    // Đang chuẩn bị mô hình Vosk
        LISTENING,          // Đang lắng nghe
        PROCESSING,         // Đang xử lý (sau khi dừng nghe)
        ERROR,              // Lỗi
        NO_PERMISSION       // Không có quyền
    }

    // Loại bộ nhận dạng đang hoạt động
    private enum class ActiveRecognizer {
        NONE, VOSK, ANDROID
    }
    
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    // Các helper
    private var voskHelper: VoskRecognitionHelper? = null
    private var androidHelper: VoiceRecognitionHelper? = null
    
    // ModelManager
    private val modelManager = VoskModelManager.getInstance(context) // Sử dụng singleton
    
    @Volatile private var currentStatus = VoiceRecognitionStatus.IDLE
    @Volatile private var activeRecognizer = ActiveRecognizer.NONE // Theo dõi helper nào đang chạy

    init {
        // Khởi tạo helper Android (luôn sẵn sàng, dùng online)
        initializeAndroidHelper()

        // Khởi tạo Vosk Helper nếu model đã được cài đặt sẵn
        // Việc này giúp giảm độ trễ khi người dùng bấm ghi âm lần đầu nếu model đã có
        coroutineScope.launch {
            if (modelManager.isModelInstalled()) {
                try {
                    initializeVoskModelInternal() // Thử khởi tạo sớm
                } catch (e: Exception) {
                    Log.w(TAG, "Pre-initialization of Vosk failed (model might be invalid): ${e.message}")
                    // Không sao, sẽ thử lại khi startListening được gọi
                }
            }
        }
    }

    // Hàm riêng để khởi tạo Android Helper, có thể gọi lại khi reset
    private fun initializeAndroidHelper() {
        androidHelper = VoiceRecognitionHelper(context) { result ->
             Log.d(TAG, "Android Result Received: '$result'")
             if (activeRecognizer == ActiveRecognizer.ANDROID) { // Chỉ xử lý nếu Android đang active
                 onRecognitionResult(result)
                 updateStatus(VoiceRecognitionStatus.IDLE)
                 activeRecognizer = ActiveRecognizer.NONE
             } else {
                 Log.w(TAG, "Received Android result, but VOSK or NONE was active.")
             }
         }
    }
    
    /**
     * Cập nhật trạng thái và thông báo qua callback onStatusChange
     */
    private fun updateStatus(newStatus: VoiceRecognitionStatus) {
        // Chỉ cập nhật và thông báo nếu trạng thái thực sự thay đổi
        if (currentStatus != newStatus) {
            currentStatus = newStatus
            Log.d(TAG, "Status changed to: $newStatus")
            // Sử dụng launch trên coroutineScope để đảm bảo onStatusChange chạy trên Main thread
            coroutineScope.launch { 
                 onStatusChange(newStatus)
            }
        }
    }
    
    /**
     * Kiểm tra quyền ghi âm
     */
    fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    /**
     * Kiểm tra kết nối internet
     */
    private fun isNetworkAvailable(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    
    /**
     * Kiểm tra xem mô hình Vosk có hợp lệ và sẵn sàng để sử dụng không
     * Hàm này kiểm tra xem voskHelper đã được khởi tạo thành công chưa
     */
    private fun isVoskReady(): Boolean {
         // voskHelper chỉ khác null nếu initializeModel() thành công
        return voskHelper != null
    }
    
    /**
     * Hàm nội bộ để khởi tạo Vosk Helper và Model.
     * Ném Exception nếu thất bại.
     */
    private suspend fun initializeVoskModelInternal() = withContext(Dispatchers.IO) {
        if (isVoskReady()) return@withContext // Đã sẵn sàng, không cần làm gì

        Log.d(TAG, "Initializing Vosk Helper and Model...")
        // Chỉ cập nhật trạng thái nếu chưa ở trạng thái chuẩn bị
        if (currentStatus != VoiceRecognitionStatus.PREPARING_MODEL) {
             updateStatus(VoiceRecognitionStatus.PREPARING_MODEL)
        }

        try {
             // Chuẩn bị model (tải/giải nén nếu cần)
             modelManager.prepareModel() 

             // Nếu voskHelper chưa có, tạo mới
             if (voskHelper == null) {
                 voskHelper = VoskRecognitionHelper(context) { result ->
                     Log.d(TAG, "Vosk Result Received: '$result'")
                      if (activeRecognizer == ActiveRecognizer.VOSK) { // Chỉ xử lý nếu Vosk đang active
                         onRecognitionResult(result)
                         updateStatus(VoiceRecognitionStatus.IDLE)
                         activeRecognizer = ActiveRecognizer.NONE
                      } else {
                         Log.w(TAG, "Received Vosk result, but ANDROID or NONE was active.")
                      }
                 }
             }
            
             // Khởi tạo model bên trong VoskHelper
             voskHelper?.initializeModel() // Hàm này đã xử lý lỗi nội bộ và gọi triggerErrorCallback nếu cần
             
             // Kiểm tra lại sau khi gọi initializeModel, vì nó có thể thất bại
             if (!isVoskReady()) {
                throw IOException("VoskHelper initializeModel failed internally.")
             }

             Log.d(TAG, "Vosk Helper and Model initialized successfully.")
             // Không reset status về IDLE ở đây, để startListening quyết định status tiếp theo

        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Vosk: ${e.message}")
            // Dọn dẹp nếu khởi tạo thất bại
            voskHelper?.destroy()
            voskHelper = null
            activeRecognizer = ActiveRecognizer.NONE
            updateStatus(VoiceRecognitionStatus.ERROR) // Cập nhật trạng thái lỗi
            throw e // Ném lại lỗi để báo cho nơi gọi
        }
    }

    /**
     * Bắt đầu nhận dạng giọng nói. Tự động chọn Vosk (offline) nếu sẵn sàng,
     * nếu không thì dùng Android SpeechRecognizer (online nếu có mạng).
     */
    fun startListening() {
        // Chỉ bắt đầu nếu đang IDLE hoặc bị lỗi trước đó
        if (currentStatus != VoiceRecognitionStatus.IDLE && currentStatus != VoiceRecognitionStatus.ERROR) {
            Log.w(TAG, "Already in state $currentStatus, ignoring startListening")
            return
        }
        
        // Kiểm tra quyền trước
        if (!hasRecordAudioPermission()) {
            Log.e(TAG, "No record audio permission.")
            updateStatus(VoiceRecognitionStatus.NO_PERMISSION)
            onRecognitionResult("") // Gửi kết quả rỗng khi không có quyền
            return
        }
        
        updateStatus(VoiceRecognitionStatus.INITIALIZING) // Bắt đầu khởi tạo
        activeRecognizer = ActiveRecognizer.NONE // Reset trạng thái active

        coroutineScope.launch {
            try {
                var useVosk = false
                // Ưu tiên Vosk nếu model đã được cài đặt
                if (modelManager.isModelInstalled()) {
                    try {
                         // Thử khởi tạo Vosk (nếu chưa)
                         if (!isVoskReady()) {
                            initializeVoskModelInternal()
                         }
                         // Nếu khởi tạo thành công
                         if (isVoskReady()) {
                            useVosk = true
                         }
                    } catch (e: Exception) {
                         Log.e(TAG, "Failed to initialize Vosk for starting, fallback to Android: ${e.message}")
                         useVosk = false
                         // Không cần cập nhật status lỗi ở đây, sẽ fallback
                    }
                }

                // Quyết định sử dụng helper nào
                if (useVosk && isVoskReady()) {
                    Log.d(TAG, "Using Vosk (offline)")
                    activeRecognizer = ActiveRecognizer.VOSK
                    updateStatus(VoiceRecognitionStatus.LISTENING)
                    voskHelper?.startListening() // VoskHelper đã xử lý lỗi nội bộ
                } else {
                    // Fallback sử dụng Android Speech Recognizer
                    if (isNetworkAvailable()) {
                        Log.d(TAG, "Using Android SpeechRecognizer (online)")
                    } else {
                         Log.w(TAG, "Using Android SpeechRecognizer (network unavailable, might use offline if supported)")
                    }
                    activeRecognizer = ActiveRecognizer.ANDROID
                    updateStatus(VoiceRecognitionStatus.LISTENING)
                    androidHelper?.startListening("vi-VN") // Android helper cũng xử lý lỗi nội bộ
                }

            } catch (e: Exception) {
                 // Bắt lỗi không mong muốn trong quá trình quyết định và khởi động
                Log.e(TAG, "Error during startListening logic: ${e.message}")
                updateStatus(VoiceRecognitionStatus.ERROR)
                activeRecognizer = ActiveRecognizer.NONE
                onRecognitionResult("") // Gửi kết quả rỗng khi có lỗi
            }
        }
    }
    
    /**
     * Dừng nhận dạng giọng nói đang hoạt động
     */
    fun stopListening() {
        // Chỉ dừng nếu đang thực sự lắng nghe
        if (currentStatus != VoiceRecognitionStatus.LISTENING) {
            Log.w(TAG, "Not in LISTENING state (current: $currentStatus), ignoring stopListening")
            return
        }
        
        Log.d(TAG, "Stopping listening, current active recognizer: $activeRecognizer")
        updateStatus(VoiceRecognitionStatus.PROCESSING) // Chuyển sang trạng thái đang xử lý
        
        // Sử dụng launch để không block nếu stop mất thời gian
        coroutineScope.launch { 
            try {
                // Gọi stop trên helper đang hoạt động
                when (activeRecognizer) {
                    ActiveRecognizer.VOSK -> voskHelper?.stopListening()
                    ActiveRecognizer.ANDROID -> androidHelper?.stopListening()
                    ActiveRecognizer.NONE -> {
                         // Trường hợp hiếm gặp: đang LISTENING nhưng không có recognizer nào active?
                         Log.e(TAG, "In LISTENING state but no active recognizer found!")
                         updateStatus(VoiceRecognitionStatus.IDLE) // Quay về IDLE nếu không có gì để dừng
                    }
                }
                // Callback onRecognitionResult từ helper sẽ cập nhật status về IDLE/ERROR
                // và reset activeRecognizer
            } catch (e: Exception) {
                 // Lỗi xảy ra trong quá trình gọi stop (ít khả năng vì lỗi đã được xử lý bên trong helper)
                Log.e(TAG, "Error calling stopListening on active helper: ${e.message}")
                updateStatus(VoiceRecognitionStatus.ERROR)
                activeRecognizer = ActiveRecognizer.NONE
                onRecognitionResult("") // Gửi kết quả rỗng
            }
        }
    }
    
    /**
     * Hủy bỏ các tài nguyên, dừng mọi hoạt động
     */
    fun destroy() {
        Log.d(TAG, "Destroying ImprovedVoiceRecognitionHelper...")
        // Dừng helper đang hoạt động (nếu có) trước khi hủy
        if (currentStatus == VoiceRecognitionStatus.LISTENING || currentStatus == VoiceRecognitionStatus.PROCESSING) {
            Log.w(TAG, "Destroy called while listening or processing. Forcing stop.")
             // Không gọi stopListening() vì nó dùng coroutine có thể bị hủy
             // Gọi trực tiếp destroy trên các helper
        }
        
        try {
            voskHelper?.destroy() // An toàn để gọi kể cả khi null
            androidHelper?.destroy() // An toàn để gọi kể cả khi null
        } catch (e: Exception) {
            Log.e(TAG, "Error during helper destruction: ${e.message}")
        } finally {
             voskHelper = null
             androidHelper = null
             updateStatus(VoiceRecognitionStatus.IDLE) // Đặt trạng thái về IDLE
             activeRecognizer = ActiveRecognizer.NONE // Reset recognizer đang hoạt động
             // Không cần hủy coroutineScope nếu muốn nó tiếp tục chạy cho các tác vụ khác
             // Nếu scope này chỉ dành riêng cho helper thì có thể hủy: coroutineScope.cancel()
        }
    }
    
    /**
     * Khởi tạo lại helper sau khi có lỗi hoặc cần reset
     */
    fun reset() {
        Log.d(TAG, "Resetting ImprovedVoiceRecognitionHelper...")
        destroy() // Hủy các helper và reset trạng thái

        // Khởi tạo lại Android Helper
        initializeAndroidHelper()

        // Thử khởi tạo lại Vosk nếu model có sẵn (như trong init)
        coroutineScope.launch {
            if (modelManager.isModelInstalled()) {
                try {
                    initializeVoskModelInternal() 
                } catch (e: Exception) {
                    Log.w(TAG, "Reset: Pre-initialization of Vosk failed: ${e.message}")
                }
            }
        }
         Log.d(TAG, "Reset complete.")
    }
}