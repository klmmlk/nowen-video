package com.nowen.video.v2.feature.tv

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.nowen.video.v2.core.designsystem.NowenColors
import com.nowen.video.v2.feature.main.MainShellViewModel
import com.nowen.video.v2.feature.tv.components.LocalTvRailHoldsFocus
import com.nowen.video.v2.feature.tv.components.tvRequestInitialFocus

enum class TvTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Home("home", "首页", Icons.Outlined.Home, Icons.Filled.Home),
    Library("library", "影视库", Icons.Outlined.VideoLibrary, Icons.Filled.VideoLibrary),
    Search("search", "搜索", Icons.Outlined.Search, Icons.Filled.Search),
    Profile("profile", "我的", Icons.Outlined.Person, Icons.Filled.Person),
}

/** 侧边栏"焦点即选中"的按键判定窗口与方向键集合。 */
private const val DIRECTIONAL_SELECT_WINDOW_MS = 600L
private val DirectionalKeys = setOf(
    Key.DirectionUp,
    Key.DirectionDown,
    Key.DirectionLeft,
    Key.DirectionRight,
)

/** 焦点到达是否由用户方向键驱动（而非页面切换引起的焦点逃逸）。 */
internal fun isDirectionalKeyDriven(lastDirectionalKeyAt: MutableLongState): Boolean =
    System.currentTimeMillis() - lastDirectionalKeyAt.longValue <= DIRECTIONAL_SELECT_WINDOW_MS

/**
 * TV 主壳：左侧竖向导航栏 + 主内容区，替代手机版底部 Tab。
 * 导航目标最小 64dp 高、焦点高亮明确，适配遥控器操作。
 */
@Composable
fun TvMainShell(viewModel: MainShellViewModel = hiltViewModel()) {
    val session by viewModel.store.snapshot.collectAsState()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val selectedTab = when (currentRoute) {
        TvTab.Home.route -> TvTab.Home
        TvTab.Search.route -> TvTab.Search
        TvTab.Profile.route, TvRoutes.SETTINGS_ROUTE -> TvTab.Profile
        TvTab.Library.route, TvRoutes.DETAIL_ROUTE, TvRoutes.SERIES_ROUTE -> TvTab.Library
        else -> null
    }

    fun openDetail(mediaId: String, isSeries: Boolean = false) {
        if (mediaId.isBlank()) return
        val encoded = Uri.encode(mediaId)
        navController.navigate(if (isSeries) "series/$encoded" else "detail/$encoded")
    }

    fun openPlayer(mediaId: String) {
        if (mediaId.isNotBlank()) navController.navigate("player/${Uri.encode(mediaId)}")
    }

    fun selectTab(tab: TvTab) {
        // 目标 tab 已在返回栈中（如设置页叠在"我的"上）时直接弹回它；
        // 否则 navigate+restoreState 会把之前保存的整段栈（含 tab 上的子页面）一并恢复，
        // 导致页面停留在子页面（如设置页）不切换。
        if (!navController.popBackStack(tab.route, inclusive = false)) {
            navController.navigate(tab.route) {
                popUpTo(TvTab.Home.route) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    // 方向键时间戳：tab 的"焦点即选中"只在方向键移动到达时触发。
    // 页面切换时旧页面的焦点节点销毁，焦点会逃逸进侧边栏 tab，
    // 这种"被动获焦"不应触发导航（否则刚打开的播放器/详情页会被 pop 掉）。
    val lastDirectionalKeyAt = remember { mutableLongStateOf(0L) }
    // 播放页是沉浸式全屏页：收起侧边栏让画面占满整屏，
    // 也避免遥控器左键把焦点挪进侧边栏误触切换页面。
    val immersive = currentRoute?.startsWith("player/") == true
    Row(
        Modifier
            .fillMaxSize()
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key in DirectionalKeys) {
                    lastDirectionalKeyAt.longValue = System.currentTimeMillis()
                }
                false
            },
    ) {
        // 侧边栏"焦点即选中"：焦点在侧边栏时切换页面，新页面据此不抢回焦点。
        val railHoldsFocus = remember { mutableStateOf(false) }
        CompositionLocalProvider(LocalTvRailHoldsFocus provides railHoldsFocus) {
            if (!immersive) {
                TvNavigationRail(
                    username = session.user?.nickname?.takeIf { it.isNotBlank() }
                        ?: session.user?.username.orEmpty(),
                    selectedTab = selectedTab,
                    onSelect = ::selectTab,
                    onSettings = {
                        navController.navigate(TvRoutes.SETTINGS_ROUTE) { launchSingleTop = true }
                    },
                    railHoldsFocus = railHoldsFocus,
                    lastDirectionalKeyAt = lastDirectionalKeyAt,
                )
            }
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                TvNavHost(
                    navController = navController,
                    openDetail = ::openDetail,
                    openPlayer = ::openPlayer,
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun TvNavigationRail(
    username: String,
    selectedTab: TvTab?,
    onSelect: (TvTab) -> Unit,
    onSettings: () -> Unit,
    railHoldsFocus: MutableState<Boolean>,
    lastDirectionalKeyAt: MutableLongState,
) {
    val tabFocusRequesters = remember { TvTab.entries.associateWith { FocusRequester() } }
    Column(
        modifier = Modifier
            .width(216.dp)
            .fillMaxHeight()
            .background(NowenColors.DeepSurface)
            .focusProperties {
                // 焦点从内容区（或页面切换后的焦点逃逸）进入侧边栏时，
                // 直接对齐当前页面所属的 tab，而不是恢复上次聚焦过的 tab。
                // focusGroup 让该 Column 成为焦点树上的分组节点，enter 才会被查询。
                enter = { tabFocusRequesters.getValue(selectedTab ?: TvTab.Home) }
            }
            .focusGroup()
            .onFocusChanged { railHoldsFocus.value = it.hasFocus }
            .padding(horizontal = 18.dp, vertical = 28.dp),
    ) {
        TvRailBrand()
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TvTab.entries.forEach { tab ->
                TvRailTabButton(
                    tab = tab,
                    selected = selectedTab == tab,
                    focusRequester = tabFocusRequesters.getValue(tab),
                    requestInitialFocus = tab == TvTab.Home,
                    onClick = { onSelect(tab) },
                    lastDirectionalKeyAt = lastDirectionalKeyAt,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        TvRailFooter(
            username = username,
            onSettings = onSettings,
            lastDirectionalKeyAt = lastDirectionalKeyAt,
        )
    }
}

@Composable
private fun TvRailBrand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF8F7AFF), NowenColors.Lavender))),
            contentAlignment = Alignment.Center,
        ) {
            Text("N", color = Color.White, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.width(11.dp))
        Text(
            "NOWEN VIDEO",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TvRailTabButton(
    tab: TvTab,
    selected: Boolean,
    focusRequester: FocusRequester,
    requestInitialFocus: Boolean = false,
    onClick: () -> Unit,
    lastDirectionalKeyAt: MutableLongState,
) {
    val shape = RoundedCornerShape(14.dp)
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .focusRequester(focusRequester)
            .then(if (requestInitialFocus) Modifier.tvRequestInitialFocus() else Modifier)
            .onFocusChanged {
                focused = it.isFocused || it.hasFocus
                if (it.isFocused && isDirectionalKeyDriven(lastDirectionalKeyAt)) onClick()
            }
            .clip(shape)
            .background(
                when {
                    focused -> MaterialTheme.colorScheme.primary
                    selected -> NowenColors.Lavender.copy(alpha = 0.16f)
                    else -> Color.Transparent
                },
            )
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(5.dp)
                .height(30.dp)
                .padding(start = 0.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (selected || focused) NowenColors.Lavender else Color.Transparent),
        )
        Spacer(Modifier.width(14.dp))
        Icon(
            if (selected || focused) tab.selectedIcon else tab.icon,
            contentDescription = tab.label,
            tint = if (focused) MaterialTheme.colorScheme.onPrimary
            else if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(26.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(
            tab.label,
            style = MaterialTheme.typography.titleMedium,
            color = if (focused) MaterialTheme.colorScheme.onPrimary
            else if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun TvRailFooter(
    username: String,
    onSettings: () -> Unit,
    lastDirectionalKeyAt: MutableLongState,
) {
    val shape = RoundedCornerShape(14.dp)
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged {
                focused = it.isFocused || it.hasFocus
                if (it.isFocused && isDirectionalKeyDriven(lastDirectionalKeyAt)) onSettings()
            }
            .clip(shape)
            .background(if (focused) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onSettings)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Settings,
            contentDescription = "设置",
            tint = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            username.ifBlank { "未登录" },
            style = MaterialTheme.typography.bodyMedium,
            color = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
