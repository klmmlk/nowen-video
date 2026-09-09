package com.nowen.video.v2.feature.tv

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.nowen.video.v2.feature.tv.screens.TvDetailScreen
import com.nowen.video.v2.feature.tv.screens.TvHomeScreen
import com.nowen.video.v2.feature.tv.screens.TvLibraryScreen
import com.nowen.video.v2.feature.tv.screens.TvPlayerScreen
import com.nowen.video.v2.feature.tv.screens.TvProfileScreen
import com.nowen.video.v2.feature.tv.screens.TvSearchScreen
import com.nowen.video.v2.feature.tv.screens.TvSeriesDetailScreen
import com.nowen.video.v2.feature.tv.screens.TvSettingsScreen

/** TV 路由常量，与手机版 MainShell 的路由结构保持一一对应。 */
internal object TvRoutes {
    const val DETAIL_ROUTE = "detail/{mediaId}"
    const val SERIES_ROUTE = "series/{seriesId}"
    const val PLAYER_ROUTE = "player/{mediaId}"
    const val SETTINGS_ROUTE = "settings"
}

@Composable
fun TvNavHost(
    navController: NavHostController,
    openDetail: (String, Boolean) -> Unit,
    openPlayer: (String) -> Unit,
) {
    NavHost(
        navController = navController,
        startDestination = TvTab.Home.route,
    ) {
        composable(TvTab.Home.route) {
            TvHomeScreen(
                onMediaClick = openDetail,
                onPlay = openPlayer,
                onLibraryClick = { navController.navigate(TvTab.Library.route) },
            )
        }
        composable(TvTab.Library.route) {
            TvLibraryScreen(onMediaClick = openDetail)
        }
        composable(TvTab.Search.route) {
            TvSearchScreen(onMediaClick = openDetail)
        }
        composable(TvTab.Profile.route) {
            TvProfileScreen(
                onSettings = { navController.navigate(TvRoutes.SETTINGS_ROUTE) },
            )
        }
        composable(TvRoutes.SETTINGS_ROUTE) {
            TvSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = TvRoutes.SERIES_ROUTE,
            arguments = listOf(navArgument("seriesId") { type = NavType.StringType }),
        ) { entry ->
            TvSeriesDetailScreen(
                seriesId = entry.arguments?.getString("seriesId").orEmpty(),
                onBack = { navController.popBackStack() },
                onEpisodeClick = openDetail,
                onPlayEpisode = openPlayer,
            )
        }
        composable(
            route = TvRoutes.DETAIL_ROUTE,
            arguments = listOf(navArgument("mediaId") { type = NavType.StringType }),
        ) { entry ->
            TvDetailScreen(
                mediaId = entry.arguments?.getString("mediaId").orEmpty(),
                onBack = { navController.popBackStack() },
                onPlay = openPlayer,
                onOpenSeries = { seriesId ->
                    if (seriesId.isNotBlank()) {
                        navController.navigate("series/${android.net.Uri.encode(seriesId)}")
                    }
                },
            )
        }
        composable(
            route = TvRoutes.PLAYER_ROUTE,
            arguments = listOf(navArgument("mediaId") { type = NavType.StringType }),
        ) { entry ->
            val mediaId = entry.arguments?.getString("mediaId").orEmpty()
            TvPlayerScreen(
                mediaId = mediaId,
                onBack = { navController.popBackStack() },
                onPlayNext = { nextId ->
                    navController.navigate("player/${android.net.Uri.encode(nextId)}") {
                        popUpTo(entry.destination.id) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }
    }
}
