package com.mindforge.app.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.mindforge.app.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreenWrapper(
    viewModel: SettingsViewModel,
    onBackClick: () -> Unit
) {
    SettingsScreen(
        viewModel = viewModel,
        onBackClick = onBackClick
    )
}
