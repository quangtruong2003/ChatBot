// di/AppModule.kt
package com.ahmedapps.geminichatbot.di

import android.content.Context
import androidx.room.Room
import com.ahmedapps.geminichatbot.BuildConfig
import com.ahmedapps.geminichatbot.data.ChatDao
import com.ahmedapps.geminichatbot.data.ChatDatabase
import com.ahmedapps.geminichatbot.data.ChatRepository
import com.google.ai.client.generativeai.GenerativeModel
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGenerativeModel(): GenerativeModel {
        return GenerativeModel(
            modelName = "gemini-1.5-flash",
            apiKey = BuildConfig.API_KEY
        )
    }

    @Provides
    @Singleton
    fun provideChatDatabase(@ApplicationContext context: Context): ChatDatabase {
        return Room.databaseBuilder(
            context,
            ChatDatabase::class.java,
            "chat_database"
        ).build()
    }

    @Provides
    fun provideChatDao(chatDatabase: ChatDatabase): ChatDao {
        return chatDatabase.chatDao()
    }

    @Provides
    @Singleton
    fun provideChatRepository(
        generativeModel: GenerativeModel,
        chatDao: ChatDao
    ): ChatRepository {
        return ChatRepository(generativeModel, chatDao)
    }
}
