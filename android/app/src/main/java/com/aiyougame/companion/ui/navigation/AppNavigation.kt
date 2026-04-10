package com.aiyougame.companion.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.aiyougame.companion.ui.characters.CharacterSelectScreen
import com.aiyougame.companion.ui.chat.ChatScreen
import com.aiyougame.companion.ui.profile.ProfileScreen
import com.aiyougame.companion.ui.purchase.PurchaseScreen
import com.aiyougame.companion.ui.settings.SettingsScreen
import com.aiyougame.companion.ui.startup.LoadingScreen

sealed class Screen(val route: String) {
    object Loading : Screen("loading")
    object CharacterSelect : Screen("characters")
    object Chat : Screen("chat")
    object Profile : Screen("profile")
    object Settings : Screen("settings")
    object Purchase : Screen("purchase")
}

@Composable
fun AppNavigation(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = Screen.Loading.route) {
        composable(Screen.Loading.route) {
            LoadingScreen(
                onNavigateToChat = {
                    navController.navigate(Screen.Chat.route) {
                        popUpTo(Screen.Loading.route) { inclusive = true }
                    }
                },
                onNavigateToCharacterSelect = {
                    navController.navigate(Screen.CharacterSelect.route) {
                        popUpTo(Screen.Loading.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.CharacterSelect.route) {
            CharacterSelectScreen(
                onCharacterSelected = { _ ->
                    navController.navigate(Screen.Chat.route) {
                        popUpTo(Screen.Loading.route) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Chat.route) {
            ChatScreen(
                onNavigateToProfile = { navController.navigate(Screen.Profile.route) },
                onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateToPurchase = { navController.navigate(Screen.Purchase.route) }
            )
        }
        composable(Screen.Profile.route) {
            ProfileScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Purchase.route) {
            PurchaseScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
