package com.ogrchatai.app.ui.navigation

sealed class Screen(val route: String) {
    data object Chats : Screen("chats")
    data object ChatDetail : Screen("chats/{chatId}") {
        fun createRoute(chatId: Long): String = "chats/$chatId"
    }
    data object ModelBrowser : Screen("models")
    data object ModelDetail : Screen("models/{modelId}") {
        fun createRoute(modelId: String): String = "models/$modelId"
    }
    data object Settings : Screen("settings")
}
