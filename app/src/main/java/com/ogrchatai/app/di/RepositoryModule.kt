package com.ogrchatai.app.di

import com.ogrchatai.app.data.repository.ChatRepositoryImpl
import com.ogrchatai.app.data.repository.ModelRepositoryImpl
import com.ogrchatai.app.data.repository.SettingsRepositoryImpl
import com.ogrchatai.app.domain.repository.ChatRepository
import com.ogrchatai.app.domain.repository.ModelRepository
import com.ogrchatai.app.domain.repository.SettingsRepository
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
