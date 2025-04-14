package com.ahmedapps.geminichatbot.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import android.webkit.MimeTypeMap
import com.ahmedapps.geminichatbot.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

object FileUtils {
    /**
     * Lấy tên file từ Uri
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var fileName: String? = null
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (displayNameIndex != -1) {
                    fileName = cursor.getString(displayNameIndex)
                }
            }
        }
        return fileName
    }
    
    /**
     * Lấy loại file từ Uri
     */
    fun getMimeType(context: Context, uri: Uri): String? {
        // Thử lấy mime type từ ContentResolver
        var mimeType = context.contentResolver.getType(uri)
        
        // Nếu không có, thử lấy từ phần mở rộng
        if (mimeType == null) {
            val fileName = getFileNameFromUri(context, uri)
            if (fileName != null) {
                val extension = MimeTypeMap.getFileExtensionFromUrl(fileName)
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            }
        }
        
        return mimeType
    }
    
    /**
     * Kiểm tra xem file có phải là file âm thanh hay không
     */
    fun isAudioFile(context: Context, uri: Uri): Boolean {
        val mimeType = getMimeType(context, uri)
        return mimeType?.startsWith("audio/") == true
    }
    
    /**
     * Kiểm tra xem file có phải là file hình ảnh hay không
     */
    fun isImageFile(context: Context, uri: Uri): Boolean {
        val mimeType = getMimeType(context, uri)
        return mimeType?.startsWith("image/") == true
    }
    
    /**
     * Kiểm tra xem file có phải là file văn bản hay không
     */
    fun isTextFile(context: Context, uri: Uri): Boolean {
        val mimeType = getMimeType(context, uri)
        val fileName = getFileNameFromUri(context, uri)?.lowercase() ?: return false
        
        // Kiểm tra theo mime type
        if (mimeType != null && (
            mimeType.startsWith("text/") ||
            mimeType == "application/json" ||
            mimeType == "application/xml" ||
            mimeType == "application/javascript"
        )) {
            return true
        }
        
        // Kiểm tra theo phần mở rộng nếu mime type không rõ ràng
        val textExtensions = listOf(
            ".txt", ".md", ".csv", ".json", ".xml", ".html", ".htm", ".css", ".js", 
            ".java", ".kt", ".py", ".c", ".cpp", ".h", ".cs", ".php", ".rb", ".go", 
            ".swift", ".dart", ".sh", ".bat", ".ps1", ".sql", ".yaml", ".yml", ".toml",
            ".ini", ".conf", ".properties", ".gradle", ".tsx", ".jsx", ".vue"
        )
        
        return textExtensions.any { fileName.endsWith(it) }
    }
    
    /**
     * Kiểm tra xem file có phải là file PDF hay không
     */
    fun isPdfFile(context: Context, uri: Uri): Boolean {
        val mimeType = getMimeType(context, uri)
        val fileName = getFileNameFromUri(context, uri)?.lowercase() ?: return false
        
        return mimeType == "application/pdf" || fileName.endsWith(".pdf")
    }
    
    /**
     * Kiểm tra xem file có phải là file Word hay không
     */
    fun isWordFile(context: Context, uri: Uri): Boolean {
        val mimeType = getMimeType(context, uri)
        val fileName = getFileNameFromUri(context, uri)?.lowercase() ?: return false
        
        return mimeType == "application/msword" || 
               mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
               fileName.endsWith(".doc") || fileName.endsWith(".docx")
    }
    
    /**
     * Lấy icon phù hợp cho loại file
     * (Trả về id resource)
     */
    fun getFileIconResource(context: Context, uri: Uri): Int {
        return when {
            isAudioFile(context, uri) -> R.drawable.ic_fileuploaded
            isImageFile(context, uri) -> R.drawable.ic_fileuploaded
            isPdfFile(context, uri) -> R.drawable.ic_fileuploaded
            isWordFile(context, uri) -> R.drawable.ic_fileuploaded
            isTextFile(context, uri) -> R.drawable.ic_fileuploaded
            else -> R.drawable.ic_fileuploaded // generic file icon
        }
    }
    
    /**
     * Trích xuất nội dung từ file dựa vào loại file
     * Lưu ý: Phương thức này chỉ dùng để phân tích nội dung file
     * khi cần thiết, không sử dụng khi chọn file thông thường
     */
    suspend fun extractFileContent(context: Context, uri: Uri, fileName: String): String = withContext(Dispatchers.IO) {
        return@withContext try {
            when {
                // File TXT và các file văn bản thông thường
                isTextFile(context, uri) -> {
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val content = inputStream.bufferedReader().readText()
                        // Giới hạn nội dung để không quá dài
                        if (content.length > 20000) {
                            content.substring(0, 20000) + "...\n(Nội dung đã được cắt ngắn)"
                        } else {
                            content
                        }
                    } ?: "Không thể đọc file"
                }
                
                // PDF, DOC, DOCX và các loại khác
                else -> "File ${fileName} được chọn. Hãy mô tả nội dung của file này."
            }
        } catch (e: Exception) {
            Log.e("FileUtils", "Error extracting content: ${e.message}")
            "Không thể đọc nội dung file. Lỗi: ${e.message}"
        }
    }
    
    /**
     * Lưu file tạm từ Uri
     */
    suspend fun saveTempFile(context: Context, uri: Uri, prefix: String, suffix: String): File = withContext(Dispatchers.IO) {
        val tempFile = File.createTempFile(prefix, suffix, context.cacheDir)
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> 
                input.copyTo(output)
            }
        }
        return@withContext tempFile
    }
} 