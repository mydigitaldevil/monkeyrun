package com.junglerun.game.ui

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.junglerun.game.AppContainer
import com.junglerun.game.game.GameMode
import com.junglerun.game.ui.screens.AnimalsScreen
import com.junglerun.game.ui.screens.GameScreen
import com.junglerun.game.ui.screens.HomeScreen
import com.junglerun.game.ui.screens.LeaderboardScreen
import com.junglerun.game.ui.screens.LobbyScreen
import com.junglerun.game.ui.screens.MissionsScreen
import com.junglerun.game.ui.screens.MultiplayerScreen
import com.junglerun.game.ui.screens.SettingsScreen
import com.junglerun.game.ui.screens.ShopScreen

object Routes {
    const val HOME = "home"
    const val ANIMALS = "animals"
    const val SHOP = "shop"
    const val MISSIONS = "missions"
    const val SETTINGS = "settings"
    const val MULTIPLAYER = "multiplayer"
    const val LOBBY = "lobby"
    const val LEADERBOARD = "leaderboard"
    const val GAME = "game/{mode}?p2={p2}&trial={trial}"

    fun game(mode: GameMode, p2: String? = null, trial: String? = null): String {
        val q = listOfNotNull(p2?.let { "p2=$it" }, trial?.let { "trial=$it" })
        return "game/${mode.name}" + if (q.isEmpty()) "" else q.joinToString("&", prefix = "?")
    }
}

@Composable
fun JungleRunNavHost(container: AppContainer, activity: Activity) {
    val nav = rememberNavController()
    NavHost(navController = nav, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(container, activity, onNavigate = { nav.navigate(it) })
        }
        composable(Routes.ANIMALS) {
            AnimalsScreen(container, activity, onBack = { nav.popBackStack() }, onShop = { nav.navigate(Routes.SHOP) })
        }
        composable(Routes.SHOP) {
            ShopScreen(container, activity, onBack = { nav.popBackStack() })
        }
        composable(Routes.MISSIONS) {
            MissionsScreen(container, activity, onBack = { nav.popBackStack() })
        }
        composable(Routes.LEADERBOARD) {
            LeaderboardScreen(container, onBack = { nav.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(container, activity, onBack = { nav.popBackStack() })
        }
        composable(Routes.MULTIPLAYER) {
            MultiplayerScreen(
                container, activity,
                onBack = { nav.popBackStack() },
                onLocalMatch = { p2 -> nav.navigate(Routes.game(GameMode.LOCAL_2P, p2)) },
                onRoomOpened = { nav.navigate(Routes.LOBBY) },
            )
        }
        composable(Routes.LOBBY) {
            LobbyScreen(
                container,
                onLeave = { nav.popBackStack(Routes.MULTIPLAYER, inclusive = false) },
                onStart = { online ->
                    nav.navigate(Routes.game(if (online) GameMode.ONLINE else GameMode.LAN))
                },
            )
        }
        composable(
            Routes.GAME,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("p2") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("trial") { type = NavType.StringType; nullable = true; defaultValue = null },
            ),
        ) { entry ->
            val mode = GameMode.valueOf(entry.arguments?.getString("mode") ?: GameMode.SOLO.name)
            val p2 = entry.arguments?.getString("p2")
            val trial = entry.arguments?.getString("trial")
            GameScreen(
                container, activity, mode,
                p2Animal = p2,
                onExit = { nav.popBackStack() },
                onRestart = {
                    nav.popBackStack()
                    nav.navigate(Routes.game(mode, p2, trial))
                },
                onShop = { nav.navigate(Routes.SHOP) },
                trialAnimal = trial,
                onTrial = { id ->
                    nav.popBackStack()
                    nav.navigate(Routes.game(GameMode.SOLO, trial = id))
                },
                onAnimals = {
                    nav.popBackStack()
                    nav.navigate(Routes.ANIMALS)
                },
            )
        }
    }
}
