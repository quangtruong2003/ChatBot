// di/AppModule.kt
package com.ahmedapps.geminichatbot.di

import android.content.Context
import com.ahmedapps.geminichatbot.BuildConfig
import com.ahmedapps.geminichatbot.data.ChatRepository
import com.google.ai.client.generativeai.GenerativeModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
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
    fun provideGenerativeModelProvider(): GenerativeModelProvider {
        return GenerativeModelProvider()
    }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth {
        return FirebaseAuth.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore {
        return FirebaseFirestore.getInstance()
    }

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage {
        return FirebaseStorage.getInstance()
    }

    @Provides
    @Singleton
    fun provideChatRepository(
        @ApplicationContext context: Context,
        generativeModelProvider: GenerativeModelProvider,
        db: FirebaseFirestore,
        storage: FirebaseStorage
    ): ChatRepository {
        return ChatRepository(context, generativeModelProvider, db, storage)
    }
}
