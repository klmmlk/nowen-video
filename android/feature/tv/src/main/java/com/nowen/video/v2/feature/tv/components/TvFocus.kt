package com.nowen.video.v2.feature.tv.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nowen.video.v2.core.designsystem.NowenColors

/**
 * 侧边栏导航栏当前是否持有焦点。
 * 侧边栏 tab 采用"焦点即选中"：焦点到达即切换页面，此时新页面不得抢回焦点，
 * 否则用户无法在侧边栏连续移动。主壳在 [TvMainShell] 中 provide 该状态。
 */
val LocalTvRailHoldsFocus = staticCompositionLocalOf<MutableState<Boolean>> {
    mutableStateOf(false)
}

/**
 * TV 焦点视觉基准：D-pad 聚焦时轻微放大并描边，模拟 10-foot UI 的焦点反馈。
 * 所有 TV 可交互元素统一走该修饰符，避免各页面焦点样式不一致。
 */
fun Modifier.tvFocusScale(
    focusedScale: Float = 1.05f,
    borderWidth: Dp = 3.dp,
    borderColor: Color = NowenColors.Lavender,
    shape: Shape,
): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    this
        .tvFocusedAppearance(
            focused = focused,
            focusedScale = focusedScale,
            borderWidth = borderWidth,
            borderColor = borderColor,
            shape = shape,
        )
        .onFocusChanged { focused = it.isFocused || it.hasFocus }
}

/**
 * 纯视觉版焦点反馈（不监听焦点，由调用方传入状态）。
 * 供焦点节点在外层、视觉反馈只想作用于局部（如海报卡仅放大图片、文字不动）的场景使用。
 */
fun Modifier.tvFocusedAppearance(
    focused: Boolean,
    focusedScale: Float = 1.05f,
    borderWidth: Dp = 3.dp,
    borderColor: Color = NowenColors.Lavender,
    shape: Shape,
): Modifier = composed {
    val scale by animateFloatAsState(
        targetValue = if (focused) focusedScale else 1f,
        animationSpec = tween(durationMillis = 140),
        label = "tvFocusScale",
    )
    this
        .scale(scale)
        .border(
            width = if (focused) borderWidth else 0.dp,
            color = if (focused) borderColor else Color.Transparent,
            shape = shape,
        )
}

/**
 * 界面进入时主动为控件建立初始焦点。
 * Compose 在跨界面导航后焦点会丢失且方向键无法重新激活（TV 遥控器全部失灵），
 * 因此每个页面的首个可交互控件都应挂载该修饰符，保证焦点链可用。
 *
 * 例外：侧边栏正持有焦点时（tab 焦点即选中触发页面切换）不请求，
 * 避免焦点被抢回内容区、用户无法在侧边栏连续移动。
 */
fun Modifier.tvRequestInitialFocus(): Modifier = composed {
    val requester = remember { FocusRequester() }
    val railHoldsFocus = LocalTvRailHoldsFocus.current
    LaunchedEffect(Unit) {
        if (!railHoldsFocus.value) {
            runCatching { requester.requestFocus() }
        }
    }
    this.focusRequester(requester)
}
