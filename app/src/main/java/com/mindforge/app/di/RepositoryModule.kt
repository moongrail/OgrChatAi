package com.mindforge.app.di

import com.mindforge.app.data.repository.ChatRepository
import com.mindforge.app.data.repository.ChatRepositoryImpl
import com.mindforge.app.data.repository.ModelRepository
import com.mindforge.app.data.repository.ModelRepositoryImpl
import com.mindforge.app.data.repository.SettingsRepository
import com.mindforge.app.data.repository.SettingsRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindChatRepository(
        chatRepositoryImpl: ChatRepositoryImpl
    ): ChatRepository

    @Binds
    @Singleton
    abstract fun bindModelRepository(
        modelRepositoryImpl: ModelRepositoryImpl
    ): ModelRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(
        settingsRepositoryImpl: SettingsRepositoryImpl
    ): SettingsRepository
}
