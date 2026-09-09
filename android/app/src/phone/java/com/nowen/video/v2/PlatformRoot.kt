package com.nowen.video.v2

import androidx.compose.runtime.Composable
import com.nowen.video.v2.feature.main.NowenApp

/** phone 变体入口：加载手机版 Compose 根组件。 */
object PlatformRoot {
    @Composable
    fun Content() {
        NowenApp()
    }
}
