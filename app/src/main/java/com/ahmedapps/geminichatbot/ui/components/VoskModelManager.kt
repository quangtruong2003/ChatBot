package com.ahmedapps.geminichatbot.ui.components

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.vosk.Model
import org.vosk.android.StorageService
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Quản lý mô hình Vosk - tải, lưu trữ, và khởi tạo
 * Đã cập nhật để hỗ trợ mô hình vosk-model-vn-0.4
 */
class VoskModelManager(private val context: Context) {
    companion object {
        private const val TAG = "VoskModelManager"
        private const val MODEL_FOLDER = "vosk-model-vn-0.4" // Mô hình Vosk mới
        private const val LEGACY_MODEL_FOLDER = "model-vi" // Thư mục mô hình cũ (để tương thích ngược)
        
        // Singleton instance
        @Volatile
        private var INSTANCE: VoskModelManager? = null
        
        fun getInstance(context: Context): VoskModelManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VoskModelManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
    
    private var model: Model? = null
    
    /**
     * Kiểm tra xem mô hình đã được cài đặt chưa
     * Hỗ trợ cả mô hình mới (vosk-model-vn-0.4) và cũ (model-vi)
     */
    fun isModelInstalled(): Boolean {
        // Kiểm tra mô hình mới
        val newModelDir = File(context.filesDir, MODEL_FOLDER)
        val isNewModelInstalled = newModelDir.exists() && newModelDir.isDirectory && 
               (File(newModelDir, "README").exists() || 
                File(newModelDir, "final.mdl").exists() ||
                File(newModelDir, "conf").exists())
                
        // Kiểm tra mô hình cũ (để tương thích ngược)
        val legacyModelDir = File(context.filesDir, LEGACY_MODEL_FOLDER)
        val isLegacyModelInstalled = legacyModelDir.exists() && legacyModelDir.isDirectory && 
               (File(legacyModelDir, "README").exists() || 
                File(legacyModelDir, "final.mdl").exists() ||
                File(legacyModelDir, "conf").exists())
                
        return isNewModelInstalled || isLegacyModelInstalled
    }
    
    /**
     * Lấy đường dẫn đến thư mục mô hình
     * Ưu tiên mô hình mới, nếu không có thì dùng mô hình cũ
     */
    fun getModelPath(): String {
        val newModelDir = File(context.filesDir, MODEL_FOLDER)
        val legacyModelDir = File(context.filesDir, LEGACY_MODEL_FOLDER)
        
        return when {
            isValidModelDirectory(newModelDir) -> newModelDir.absolutePath
            isValidModelDirectory(legacyModelDir) -> legacyModelDir.absolutePath
            else -> File(context.filesDir, MODEL_FOLDER).absolutePath
        }
    }
    
    /**
     * Kiểm tra xem thư mục có chứa mô hình hợp lệ không
     */
    private fun isValidModelDirectory(directory: File): Boolean {
        return directory.exists() && directory.isDirectory &&
               (File(directory, "README").exists() || 
                File(directory, "final.mdl").exists() ||
                File(directory, "conf").exists())
    }
    
    /**
     * Chuẩn bị mô hình - tải từ assets nếu chưa có
     * Trả về đường dẫn đến mô hình
     */
    suspend fun prepareModel(): String = withContext(Dispatchers.IO) {
        try {
            if (!isModelInstalled()) {
                Log.d(TAG, "Model not installed, extracting from assets...")
                
                // Thử trích xuất mô hình mới trước
                try {
                    extractModelFromAssets(MODEL_FOLDER, MODEL_FOLDER)
                } catch (e: Exception) {
                    Log.e(TAG, "Error extracting new model, trying legacy model: ${e.message}")
                    // Nếu không được, thử trích xuất mô hình cũ
                    extractModelFromAssets(LEGACY_MODEL_FOLDER, LEGACY_MODEL_FOLDER)
                }
            } else {
                Log.d(TAG, "Model already installed at ${getModelPath()}")
            }
            getModelPath()
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing model: ${e.message}")
            throw e
        }
    }
    
    /**
     * Khởi tạo và trả về mô hình Vosk
     */
    suspend fun getModel(): Model = withContext(Dispatchers.IO) {
        if (model != null) return@withContext model!!
        
        try {
            val modelPath = prepareModel()
            model = Model(modelPath)
            model!!
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing model: ${e.message}")
            throw e
        }
    }
    
    /**
     * Giải nén mô hình từ assets vào bộ nhớ trong
     */
    private suspend fun extractModelFromAssets(assetsFolder: String, targetFolder: String) = withContext(Dispatchers.IO) {
        val assetManager = context.assets
        val modelDir = File(context.filesDir, targetFolder)
        
        if (!modelDir.exists()) {
            modelDir.mkdirs()
        }
        
        try {
            // Liệt kê tất cả các file trong thư mục mô hình trong assets
            val assetFiles = assetManager.list(assetsFolder) ?: emptyArray()
            
            if (assetFiles.isEmpty()) {
                throw IOException("No files found in assets folder: $assetsFolder")
            }
            
            for (fileName in assetFiles) {
                val outFile = File(modelDir, fileName)
                
                // Nếu là file, copy trực tiếp
                if (fileName.contains(".")) {
                    assetManager.open("$assetsFolder/$fileName").use { inputStream ->
                        FileOutputStream(outFile).use { outputStream ->
                            copyFile(inputStream, outputStream)
                        }
                    }
                } else {
                    // Tạo thư mục con
                    outFile.mkdirs()
                    // Đệ quy giải nén thư mục con
                    val subFiles = assetManager.list("$assetsFolder/$fileName") ?: emptyArray()
                    for (subFile in subFiles) {
                        val subOutFile = File(outFile, subFile)
                        assetManager.open("$assetsFolder/$fileName/$subFile").use { inputStream ->
                            FileOutputStream(subOutFile).use { outputStream ->
                                copyFile(inputStream, outputStream)
                            }
                        }
                    }
                }
            }
            
            Log.d(TAG, "Model extracted successfully to $targetFolder")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to extract model from $assetsFolder to $targetFolder: ${e.message}")
            throw e
        }
    }
    
    /**
     * Sao chép file từ InputStream sang OutputStream
     */
    private fun copyFile(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(8192)
        var read: Int
        while (input.read(buffer).also { read = it } != -1) {
            output.write(buffer, 0, read)
        }
    }
    
    /**
     * Giải phóng tài nguyên
     */
    fun releaseModel() {
        model?.close()
        model = null
    }
} 