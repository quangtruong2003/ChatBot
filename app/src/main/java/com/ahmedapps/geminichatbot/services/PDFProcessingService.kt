package com.ahmedapps.geminichatbot.services

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

object PDFProcessingService {
    private const val TAG = "PDFProcessingService"
    
    // Khởi tạo PDFBox (chỉ cần gọi một lần)
    fun init(context: Context) {
        // Đã đổi sang sử dụng iText thay vì Tom Roush PDFBox
        Log.d(TAG, "PDF Service initialized")
    }
    
    fun extractTextFromPDF(context: Context, uri: Uri): String {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                Log.e(TAG, "Không thể mở tệp PDF")
                return "Không thể mở tệp PDF"
            }
            
            // Sử dụng iText để trích xuất văn bản
            // Lưu ý: Đoạn mã này sẽ cần được thay đổi để sử dụng iText đúng cách
            // Ví dụ đơn giản:
            val reader = com.itextpdf.text.pdf.PdfReader(inputStream)
            val text = StringBuilder()
            
            for (i in 1..reader.numberOfPages) {
                val page = com.itextpdf.text.pdf.parser.PdfTextExtractor.getTextFromPage(reader, i)
                text.append(page).append("\n")
            }
            
            reader.close()
            return text.toString()
            
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi trích xuất văn bản từ PDF", e)
            return "Đã xảy ra lỗi khi trích xuất văn bản từ PDF: ${e.message}"
        }
    }
    
    // Phương thức trích xuất văn bản từ tệp TXT
    fun extractTextFromTXT(context: Context, uri: Uri): String {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            return inputStream?.bufferedReader()?.use { it.readText() } ?: "Không thể đọc tệp TXT"
        } catch (e: Exception) {
            Log.e(TAG, "Lỗi khi đọc tệp TXT", e)
            return "Đã xảy ra lỗi khi đọc tệp TXT: ${e.message}"
        }
    }
} 