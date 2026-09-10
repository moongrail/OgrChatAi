package com.ogrchatai.app.data.repository

import com.ogrchatai.app.data.preferences.UserPreferences
import com.ogrchatai.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val userPreferences: UserPreferences
) : SettingsRepository {

    override val themeMode: Flow<String>
        get() = userPreferences.themeMode.map { it.name.lowercase() }

    override val language: Flow<String>
        get() = userPreferences.language

    override val fontSize: Flow<Int>
        get() = userPreferences.fontSize

    override val defaultModelId: Flow<String>
        get() = userPreferences.defaultModelId

    override suspend fun setThemeMode(mode: String) {
        userPreferences.setThemeMode(
            when (mode.lowercase()) {
                "light" -> UserPreferences.ThemeMode.LIGHT
                "dark" -> UserPreferences.ThemeMode.DARK
                else -> UserPreferences.ThemeMode.SYSTEM
            }
        )
    }

    override suspend fun setLanguage(language: String) {
        userPreferences.setLanguage(language)
    }

    override suspend fun setFontSize(size: Int) {
        userPreferences.setFontSize(size)
    }

    override suspend fun setDefaultModelId(modelId: String) {
        userPreferences.setDefaultModelId(modelId)
    }
}
