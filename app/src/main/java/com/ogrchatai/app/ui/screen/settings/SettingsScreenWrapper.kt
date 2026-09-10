package com.ogrchatai.app.ui.screen.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.ogrchatai.app.ui.viewmodel.SettingsViewModel

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
