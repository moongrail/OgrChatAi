package com.ogrchatai.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ogrchatai.app.data.preferences.UserPreferences
import com.ogrchatai.app.domain.model.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Lightweight theme observer used by [com.ogrchatai.app.MainActivity].
 *
 * The full SettingsViewModel also owns neural-voice files, native TTS and
 * download state. Creating it at the activity level made every cold start pay
 * for work that belongs only to the Settings screen. This ViewModel reads the
 * single preference needed to render the app shell.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    userPreferences: UserPreferences
) : ViewModel() {

    val theme: StateFlow<ThemeMode> = userPreferences.themeMode
        .map { stored ->
            when (stored) {
                UserPreferences.ThemeMode.LIGHT -> ThemeMode.LIGHT
                UserPreferences.ThemeMode.DARK -> ThemeMode.DARK
                UserPreferences.ThemeMode.SYSTEM -> ThemeMode.SYSTEM
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ThemeMode.SYSTEM
        )
}
