package com.ogrchatai.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.gson.Gson
import com.ogrchatai.app.domain.model.ModelSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

@Singleton
class UserPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val dataStore = context.dataStore
    private val gson = Gson()

    val themeMode: Flow<ThemeMode> = dataStore.data.map { preferences ->
        when (preferences[THEME_MODE_KEY]) {
            "light" -> ThemeMode.LIGHT
            "dark" -> ThemeMode.DARK
            else -> ThemeMode.SYSTEM
        }
    }

    val language: Flow<String> = dataStore.data.map { preferences ->
        preferences[LANGUAGE_KEY] ?: "en"
    }

    val fontSize: Flow<Int> = dataStore.data.map { preferences ->
        preferences[FONT_SIZE_KEY] ?: 16
    }

    val showTokenCount: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[SHOW_TOKEN_COUNT_KEY] ?: false
    }

    val defaultModelId: Flow<String> = dataStore.data.map { preferences ->
        preferences[DEFAULT_MODEL_ID_KEY] ?: ""
    }

    val temperature: Flow<Float> = dataStore.data.map { preferences ->
        (preferences[TEMPERATURE_KEY] ?: 0.7).toFloat()
    }

    val maxTokens: Flow<Int> = dataStore.data.map { preferences ->
        preferences[MAX_TOKENS_KEY] ?: 2048
    }

    val topP: Flow<Float> = dataStore.data.map { preferences ->
        (preferences[TOP_P_KEY] ?: 0.9).toFloat()
    }

    val streamingEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[STREAMING_ENABLED_KEY] ?: true
    }

    fun getModelSettings(modelId: String): Flow<ModelSettings> {
        val key = stringPreferencesKey("model_settings_$modelId")
        return dataStore.data.map { preferences ->
            val json = preferences[key] ?: return@map ModelSettings.default(modelId)
            try {
                gson.fromJson(json, ModelSettings::class.java)
            } catch (e: Exception) {
                ModelSettings.default(modelId)
            }
        }
    }

    suspend fun getModelSettingsOnce(modelId: String): ModelSettings {
        val key = stringPreferencesKey("model_settings_$modelId")
        return dataStore.data.first().let { preferences ->
            val json = preferences[key] ?: return@let ModelSettings.default(modelId)
            try {
                gson.fromJson(json, ModelSettings::class.java)
            } catch (e: Exception) {
                ModelSettings.default(modelId)
            }
        }
    }

    suspend fun saveModelSettings(settings: ModelSettings) {
        val key = stringPreferencesKey("model_settings_${settings.modelId}")
        val json = gson.toJson(settings)
        dataStore.edit { preferences ->
            preferences[key] = json
        }
    }

    suspend fun setThemeMode(themeMode: ThemeMode) {
        dataStore.edit { preferences ->
            preferences[THEME_MODE_KEY] = themeMode.name.lowercase()
        }
    }

    suspend fun setLanguage(language: String) {
        dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = language
        }
    }

    suspend fun setFontSize(size: Int) {
        dataStore.edit { preferences ->
            preferences[FONT_SIZE_KEY] = size
        }
    }

    suspend fun setShowTokenCount(show: Boolean) {
        dataStore.edit { preferences ->
            preferences[SHOW_TOKEN_COUNT_KEY] = show
        }
    }

    suspend fun setDefaultModelId(modelId: String) {
        dataStore.edit { preferences ->
            preferences[DEFAULT_MODEL_ID_KEY] = modelId
        }
    }

    suspend fun setTemperature(temperature: Float) {
        dataStore.edit { preferences ->
            preferences[TEMPERATURE_KEY] = temperature.toDouble()
        }
    }

    suspend fun setMaxTokens(maxTokens: Int) {
        dataStore.edit { preferences ->
            preferences[MAX_TOKENS_KEY] = maxTokens
        }
    }

    suspend fun setTopP(topP: Float) {
        dataStore.edit { preferences ->
            preferences[TOP_P_KEY] = topP.toDouble()
        }
    }

    suspend fun setStreamingEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[STREAMING_ENABLED_KEY] = enabled
        }
    }

    enum class ThemeMode {
        SYSTEM, LIGHT, DARK
    }

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val LANGUAGE_KEY = stringPreferencesKey("language")
        private val FONT_SIZE_KEY = intPreferencesKey("font_size")
        private val SHOW_TOKEN_COUNT_KEY = booleanPreferencesKey("show_token_count")
        private val DEFAULT_MODEL_ID_KEY = stringPreferencesKey("default_model_id")
        private val TEMPERATURE_KEY = doublePreferencesKey("temperature_raw")
        private val MAX_TOKENS_KEY = intPreferencesKey("max_tokens")
        private val TOP_P_KEY = doublePreferencesKey("top_p_raw")
        private val STREAMING_ENABLED_KEY = booleanPreferencesKey("streaming_enabled")
    }
}
