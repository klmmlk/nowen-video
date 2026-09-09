package com.nowen.video.v2

import androidx.compose.runtime.Composable
import com.nowen.video.v2.feature.tv.TvApp

/** tv 变体入口：加载 Android TV 版 Compose 根组件。 */
object PlatformRoot {
    @Composable
    fun Content() {
        TvApp()
    }
}
