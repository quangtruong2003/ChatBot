package com.ahmedapps.geminichatbot.utils

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import org.jcodec.common.io.NIOUtils
import org.jcodec.containers.mp4.demuxer.MP4Demuxer
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import com.google.android.exoplayer2.util.Util
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaCodec
import android.media.MediaMuxer
import java.nio.ByteBuffer
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode

/**
 * Lớp tiện ích để xử lý và chuyển đổi tệp âm thanh
 */
object AudioConverter {
    private const val TAG = "AudioConverter"
    private const val BUFFER_SIZE = 1024 * 1024 // 1MB buffer
    
    /**
     * Chuyển đổi tệp âm thanh sang định dạng OGG (Vorbis)
     * 
     * @param inputPath Đường dẫn đến tệp đầu vào (M4A, MP3, WAV,...)
     * @param outputPath Đường dẫn đến tệp đầu ra OGG
     * @return true nếu chuyển đổi thành công, false nếu thất bại
     */
    fun convertToOgg(inputPath: String, outputPath: String): Boolean {
        try {
            // Sử dụng FFmpegKit để chuyển đổi file thay vì MediaMuxer/MediaExtractor
            val command = "-i \"$inputPath\" -c:a libvorbis -q:a 4 \"$outputPath\""
            Log.d(TAG, "Running FFmpeg command: $command")
            
            val session = FFmpegKit.execute(command)
            val returnCode = session.returnCode
            
            // Kiểm tra kết quả
            val success = ReturnCode.isSuccess(returnCode)
            if (!success) {
                Log.e(TAG, "FFmpeg failed with state: ${session.state} and return code: $returnCode")
                Log.e(TAG, "FFmpeg output: ${session.output}")
            } else {
                Log.d(TAG, "FFmpeg conversion successful")
            }
            
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Error converting audio to OGG: ${e.message}")
            e.printStackTrace()
            return false
        }
    }
    
    /**
     * Chọn track audio từ MediaExtractor
     */
    private fun selectTrack(extractor: MediaExtractor): Int {
        val numTracks = extractor.trackCount
        for (i in 0 until numTracks) {
            val format = extractor.getTrackFormat(i)
            val mime = format.getString(MediaFormat.KEY_MIME)
            if (mime?.startsWith("audio/") == true) {
                return i
            }
        }
        return -1
    }
    
    /**
     * Sao chép tệp âm thanh từ Uri vào bộ nhớ tạm và trả về File
     */
    fun copyUriToTempFile(context: Context, uri: Uri, prefix: String, suffix: String): File? {
        try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File.createTempFile(prefix, suffix, context.cacheDir)
            
            FileOutputStream(tempFile).use { outputStream ->
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } > 0) {
                    outputStream.write(buffer, 0, bytesRead)
                }
            }
            
            inputStream.close()
            return tempFile
        } catch (e: Exception) {
            Log.e(TAG, "Error copying audio file from URI: ${e.message}")
            return null
        }
    }
    
    /**
     * Lấy đường dẫn file thực từ Uri
     */
    fun getPathFromUri(context: Context, uri: Uri): String? {
        // Trường hợp "file://"
        if (uri.scheme == "file") {
            return uri.path
        }
        
        // Trường hợp "content://"
        if (uri.scheme == "content") {
            // Thử sử dụng ContentResolver để lấy tên file và lưu vào thư mục tạm
            val fileName = getFileNameFromUri(context, uri)
            if (fileName != null) {
                val suffix = if (fileName.contains(".")) {
                    fileName.substring(fileName.lastIndexOf("."))
                } else {
                    ".tmp"
                }
                
                val prefix = if (fileName.contains(".")) {
                    fileName.substring(0, fileName.lastIndexOf("."))
                } else {
                    fileName
                }
                
                val tempFile = copyUriToTempFile(context, uri, prefix, suffix)
                return tempFile?.absolutePath
            }
        }
        
        return null
    }
    
    /**
     * Lấy tên file từ Uri
     */
    fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        }
        
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        
        return result
    }
    
    /**
     * Lấy kích thước file từ Uri
     */
    fun getFileSizeFromUri(context: Context, uri: Uri): Long {
        var size = -1L
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIndex != -1) {
                        size = it.getLong(sizeIndex)
                    }
                }
            }
        }
        
        if (size == -1L) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                size = inputStream?.available()?.toLong() ?: -1L
                inputStream?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error getting file size: ${e.message}")
            }
        }
        
        return size
    }
} 