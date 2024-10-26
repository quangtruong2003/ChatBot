//// data/ChatDao.kt
//package com.ahmedapps.geminichatbot.data
//
//import androidx.room.Dao
//import androidx.room.Insert
//import androidx.room.OnConflictStrategy
//import androidx.room.Query
//
//@Dao
//interface ChatDao {
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    suspend fun insertChat(chat: Chat)
//
//    @Query("SELECT * FROM chat_table WHERE userId = :userId ORDER BY id DESC")
//    suspend fun getAllChats(userId: String): List<Chat>
//
//    @Query("DELETE FROM chat_table WHERE userId = :userId")
//    suspend fun deleteAllChats(userId: String)
//}
