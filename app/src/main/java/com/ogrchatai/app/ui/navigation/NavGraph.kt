package com.ogrchatai.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ogrchatai.app.ui.screen.chatdetail.ChatDetailScreen
import com.ogrchatai.app.ui.screen.chats.ChatsScreenWrapper
import com.ogrchatai.app.ui.screen.modelbrowser.ModelBrowserScreen
import com.ogrchatai.app.ui.screen.settings.SettingsScreenWrapper
import com.ogrchatai.app.ui.viewmodel.ChatDetailViewModel
import com.ogrchatai.app.ui.viewmodel.ChatsViewModel
import com.ogrchatai.app.ui.viewmodel.SettingsViewModel

private const val ANIMATION_DURATION = 300

private val slideInFromRight: EnterTransition = slideInHorizontally(
    initialOffsetX = { fullWidth -> fullWidth },
    animationSpec = tween(ANIMATION_DURATION)
) + fadeIn(animationSpec = tween(ANIMATION_DURATION))

private val slideOutToRight: ExitTransition = slideOutHorizontally(
    targetOffsetX = { fullWidth -> fullWidth },
    animationSpec = tween(ANIMATION_DURATION)
) + fadeOut(animationSpec = tween(ANIMATION_DURATION))

private val slideInFromLeft: EnterTransition = slideInHorizontally(
    initialOffsetX = { fullWidth -> -fullWidth },
    animationSpec = tween(ANIMATION_DURATION)
) + fadeIn(animationSpec = tween(ANIMATION_DURATION))

private val slideOutToLeft: ExitTransition = slideOutHorizontally(
    targetOffsetX = { fullWidth -> -fullWidth },
    animationSpec = tween(ANIMATION_DURATION)
) + fadeOut(animationSpec = tween(ANIMATION_DURATION))

@Composable
fun OgrChatAiNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
    startDestination: String = Screen.Chats.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { slideInFromRight },
        exitTransition = { slideOutToLeft },
        popEnterTransition = { slideInFromLeft },
        popExitTransition = { slideOutToRight }
    ) {
        chatsGraph(navController)
        modelGraph(navController)
        settingsGraph(navController)
    }
}

private fun NavGraphBuilder.chatsGraph(navController: NavHostController) {
    composable(
        route = Screen.Chats.route,
        enterTransition = { slideInFromRight },
        exitTransition = { slideOutToLeft },
        popEnterTransition = { slideInFromLeft },
        popExitTransition = { slideOutToRight }
    ) {
        val viewModel: ChatsViewModel = hiltViewModel()
        ChatsScreenWrapper(
            viewModel = viewModel,
            onChatClick = { chatId ->
                navController.navigate(Screen.ChatDetail.createRoute(chatId))
            },
            onModelBrowserClick = {
                navController.navigate(Screen.ModelBrowser.route)
            },
            onSettingsClick = {
                navController.navigate(Screen.Settings.route)
            }
        )
    }

    composable(
        route = Screen.ChatDetail.route,
        arguments = listOf(
            navArgument("chatId") {
                type = NavType.LongType
            }
        ),
        enterTransition = { slideInFromRight },
        exitTransition = { slideOutToLeft },
        popEnterTransition = { slideInFromLeft },
        popExitTransition = { slideOutToRight }
    ) { backStackEntry ->
        val chatId = backStackEntry.arguments?.getLong("chatId") ?: return@composable
        val viewModel: ChatDetailViewModel = hiltViewModel()
        ChatDetailScreen(
            viewModel = viewModel,
            chatId = chatId,
            onBackClick = { navController.popBackStack() }
        )
    }
}

private fun NavGraphBuilder.modelGraph(navController: NavHostController) {
    composable(
        route = Screen.ModelBrowser.route,
        enterTransition = { slideInFromRight },
        exitTransition = { slideOutToLeft },
        popEnterTransition = { slideInFromLeft },
        popExitTransition = { slideOutToRight }
    ) {
        ModelBrowserScreen(
            onModelClick = { modelId ->
                navController.navigate(Screen.ModelDetail.createRoute(modelId))
            },
            onBackClick = { navController.popBackStack() }
        )
    }

    composable(
        route = Screen.ModelDetail.route,
        arguments = listOf(
            navArgument("modelId") {
                type = NavType.StringType
            }
        ),
        enterTransition = { slideInFromRight },
        exitTransition = { slideOutToLeft },
        popEnterTransition = { slideInFromLeft },
        popExitTransition = { slideOutToRight }
    ) { backStackEntry ->
        val modelId = backStackEntry.arguments?.getString("modelId") ?: return@composable
        ModelBrowserScreen(
            onModelClick = { },
            onBackClick = { navController.popBackStack() }
        )
    }
}

private fun NavGraphBuilder.settingsGraph(navController: NavHostController) {
    composable(
        route = Screen.Settings.route,
        enterTransition = { slideInFromRight },
        exitTransition = { slideOutToLeft },
        popEnterTransition = { slideInFromLeft },
        popExitTransition = { slideOutToRight }
    ) {
        val viewModel: SettingsViewModel = hiltViewModel()
        SettingsScreenWrapper(
            viewModel = viewModel,
            onBackClick = { navController.popBackStack() }
        )
    }
}
