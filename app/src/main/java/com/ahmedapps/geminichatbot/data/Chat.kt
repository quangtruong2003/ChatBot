// data/Chat.kt
package com.ahmedapps.geminichatbot.data

import android.graphics.Bitmap
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "chat_table")
@TypeConverters(Converters::class) // Áp dụng TypeConverters
data class Chat(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val prompt: String,
    val bitmap: Bitmap?,
    val isFromUser: Boolean,
    val isError: Boolean = false
)
