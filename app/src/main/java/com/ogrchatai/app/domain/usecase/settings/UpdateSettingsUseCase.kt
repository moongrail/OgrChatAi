package com.ogrchatai.app.domain.usecase.settings

import com.ogrchatai.app.data.preferences.UserPreferences
import com.ogrchatai.app.domain.model.AppSettings
import com.ogrchatai.app.domain.repository.ChatRepository
import com.ogrchatai.app.domain.repository.ModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class UpdateSettingsUseCase @Inject constructor(
    private val modelRepository: ModelRepository,
    private val chatRepository: ChatRepository,
    private val userPreferences: UserPreferences
) {
    suspend operator fun invoke(
        settings: AppSettings,
        clearHistory: Boolean = false
    ): Result<Unit> {
        return runCatching {
            userPreferences.setLanguage(settings.language.code)
            userPreferences.setThemeMode(
                when (settings.theme) {
                    com.ogrchatai.app.domain.model.ThemeMode.LIGHT -> UserPreferences.ThemeMode.LIGHT
                    com.ogrchatai.app.domain.model.ThemeMode.DARK -> UserPreferences.ThemeMode.DARK
                    com.ogrchatai.app.domain.model.ThemeMode.SYSTEM -> UserPreferences.ThemeMode.SYSTEM
                }
            )
            modelRepository.updateGenerationConfig(settings.generationConfig)
            userPreferences.setVoiceGender(settings.voiceGender)

            if (clearHistory) {
                withContext(Dispatchers.IO) {
                    chatRepository.deleteAllChats()
                    modelRepository.getDownloadedModels().first().forEach { model ->
                        modelRepository.deleteModel(model.id)
                    }
                }
            }
        }
    }
}
