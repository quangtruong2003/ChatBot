package fomatText

/**
 * Cấu hình cho hiệu ứng typing text
 */
object TypingConfig {
    /** Tốc độ typing mặc định (milliseconds giữa các ký tự) */
    const val DEFAULT_TYPING_SPEED = 5L
    
    /** Tốc độ typing tối thiểu (cho tin nhắn dài) */
    const val MIN_TYPING_SPEED = 2L
    
    /** Tốc độ typing tối đa */
    const val MAX_TYPING_SPEED = 15L
    
    /** Có sử dụng tốc độ typing thông minh không? */
    const val USE_SMART_TYPING = true
    
    /** Ngưỡng độ dài tin nhắn để giảm tốc độ */
    const val LONG_MESSAGE_THRESHOLD = 500
    
    /** Ngưỡng độ dài tin nhắn để giảm nhiều tốc độ */
    const val VERY_LONG_MESSAGE_THRESHOLD = 1000
    
    /** Hệ số tốc độ cho dấu câu */
    const val PUNCTUATION_SPEED_FACTOR = 3L
    
    /** Hệ số tốc độ cho khoảng trắng */
    const val SPACE_SPEED_FACTOR = 2L
    
    /** Hệ số tốc độ cho xuống dòng */
    const val NEWLINE_SPEED_FACTOR = 4L
} 