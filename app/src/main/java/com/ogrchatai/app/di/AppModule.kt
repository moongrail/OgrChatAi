package com.ogrchatai.app.di

import android.content.Context
import androidx.room.Room
import com.ogrchatai.app.data.local.AppDatabase
import com.ogrchatai.app.data.local.ChatDao
import com.ogrchatai.app.data.local.ModelDao
import com.ogrchatai.app.data.preferences.UserPreferences
import com.ogrchatai.app.data.remote.HuggingFaceApi
import com.ogrchatai.app.data.remote.HuggingFaceApiClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://huggingface.co/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    @Provides
    @Singleton
    fun provideHuggingFaceApi(retrofit: Retrofit): HuggingFaceApi {
        return retrofit.create(HuggingFaceApi::class.java)
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "chat_llm_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideChatDao(database: AppDatabase): ChatDao {
        return database.chatDao()
    }

    @Provides
    @Singleton
    fun provideModelDao(database: AppDatabase): ModelDao {
        return database.modelDao()
    }

    @Provides
    @Singleton
    fun provideUserPreferences(@ApplicationContext context: Context): UserPreferences {
        return UserPreferences(context)
    }
}
