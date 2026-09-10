package com.ogrchatai.app.domain.repository

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val themeMode: Flow<String>
    val language: Flow<String>
    val fontSize: Flow<Int>
    val defaultModelId: Flow<String>

    suspend fun setThemeMode(mode: String)
    suspend fun setLanguage(language: String)
    suspend fun setFontSize(size: Int)
    suspend fun setDefaultModelId(modelId: String)
}
