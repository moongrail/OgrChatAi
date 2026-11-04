package com.ogrchatai.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import com.ogrchatai.app.data.ml.InferenceEngine
import com.ogrchatai.app.ui.navigation.OgrChatAiNavHost
import com.ogrchatai.app.ui.theme.OgrChatAiTheme
import com.ogrchatai.app.ui.viewmodel.SettingsViewModel
import com.ogrchatai.app.domain.model.ThemeMode
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var inferenceEngine: InferenceEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel = hiltViewModel()
            val uiState by settingsViewModel.uiState.collectAsState()

            val isDarkTheme = when (uiState.theme) {
                ThemeMode.DARK -> true
                ThemeMode.LIGHT -> false
                ThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
            }

            OgrChatAiTheme(darkTheme = isDarkTheme, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OgrChatAiNavHost(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }

    override fun onDestroy() {
        // Pressing Back finishes the activity and releases the native model. Removing the task
        // from Recents also destroys the activity without a configuration change. Pressing Home
        // only moves the app to the background and deliberately does not unload the model.
        if (isFinishing || !isChangingConfigurations) {
            inferenceEngine.unloadAllModels()
        }
        super.onDestroy()
    }
}
