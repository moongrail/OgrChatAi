package com.ogrchatai.app.domain.usecase.settings

import com.ogrchatai.app.domain.model.AppSettings
import com.ogrchatai.app.domain.model.GenerationConfig
import com.ogrchatai.app.domain.model.Language
import com.ogrchatai.app.domain.model.ThemeMode
import com.ogrchatai.app.data.preferences.UserPreferences
import com.ogrchatai.app.domain.repository.ModelRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class GetSettingsUseCase @Inject constructor(
    private val modelRepository: ModelRepository,
    private val userPreferences: UserPreferences
) {
    operator fun invoke(): Flow<AppSettings> {
        return combine(
            userPreferences.language,
            userPreferences.themeMode,
            modelRepository.getGenerationConfig()
        ) { langCode, themeMode, genConfig ->
            AppSettings(
                language = Language.fromCode(langCode),
                theme = when (themeMode) {
                    UserPreferences.ThemeMode.LIGHT -> ThemeMode.LIGHT
                    UserPreferences.ThemeMode.DARK -> ThemeMode.DARK
                    UserPreferences.ThemeMode.SYSTEM -> ThemeMode.SYSTEM
                },
                generationConfig = genConfig
            )
        }
    }
}
