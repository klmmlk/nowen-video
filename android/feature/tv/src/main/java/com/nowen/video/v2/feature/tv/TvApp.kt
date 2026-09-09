package com.nowen.video.v2.feature.tv

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.nowen.video.v2.core.designsystem.NowenTheme
import com.nowen.video.v2.feature.main.AppViewModel
import com.nowen.video.v2.feature.tv.screens.TvForcePasswordScreen
import com.nowen.video.v2.feature.tv.screens.TvLoginScreen
import com.nowen.video.v2.feature.tv.screens.TvServerSetupScreen

/**
 * TV 版根组件：与手机版 NowenApp 共用 AppViewModel 的会话路由，
 * 仅替换各目的地为 D-pad 友好的 TV 页面，并强制暗色主题。
 */
@Composable
fun TvApp(viewModel: AppViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsState()

    NowenTheme(darkTheme = true) {
        // Surface 提供 LocalContentColor（暗色主题下近白），
        // 否则页面里未显式指定颜色的文字/图标会落入 Compose 默认黑色。
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            AnimatedContent(
                targetState = when {
                    !session.initialized -> TvRootDestination.Loading
                    session.activeServer == null -> TvRootDestination.Server
                    !session.isAuthenticated -> TvRootDestination.Login
                    session.user?.mustChangePassword == true -> TvRootDestination.Password
                    else -> TvRootDestination.Main
                },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "tv_root_destination",
            ) { destination ->
                when (destination) {
                    TvRootDestination.Loading -> Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    TvRootDestination.Server -> TvServerSetupScreen()
                    TvRootDestination.Login -> TvLoginScreen()
                    TvRootDestination.Password -> TvForcePasswordScreen()
                    TvRootDestination.Main -> TvMainShell()
                }
            }
        }
    }
}

private enum class TvRootDestination { Loading, Server, Login, Password, Main }
