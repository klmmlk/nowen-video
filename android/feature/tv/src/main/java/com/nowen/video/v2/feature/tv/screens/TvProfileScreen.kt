package com.nowen.video.v2.feature.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nowen.video.v2.core.designsystem.NowenColors
import com.nowen.video.v2.feature.tv.components.tvRequestInitialFocus

/**
 * TV 个人中心：账户卡 + 设置入口。
 * 收藏/观看历史沿用首页与搜索入口；TV 端离线下载暂不提供。
 */
@Composable
fun TvProfileScreen(
    onSettings: () -> Unit,
    viewModel: TvSettingsViewModel = hiltViewModel(),
) {
    val session by viewModel.sessionStore.snapshot.collectAsState()
    val user = session.user

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 28.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        Text("我的", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(NowenColors.Lavender.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    (user?.nickname ?: user?.username ?: "?")
                        .take(1)
                        .uppercase(),
                    style = MaterialTheme.typography.headlineLarge,
                    color = NowenColors.Lavender,
                )
            }
            Spacer(Modifier.width(24.dp))
            Column {
                Text(
                    user?.nickname?.takeIf { it.isNotBlank() } ?: user?.username ?: "未登录",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${session.activeServer?.name ?: "未连接"} · ${session.activeServer?.baseUrl ?: "-"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
        TvProfileEntry(
            icon = Icons.Filled.Settings,
            title = "设置",
            subtitle = "服务器信息与账户管理",
            requestInitialFocus = true,
            onClick = onSettings,
        )
    }
}

@Composable
private fun TvProfileEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    requestInitialFocus: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (requestInitialFocus) Modifier.tvRequestInitialFocus() else Modifier)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clip(shape)
            .background(if (focused) MaterialTheme.colorScheme.primary else NowenColors.DeepSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = title,
            tint = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = if (focused) MaterialTheme.colorScheme.onPrimary else Color.Unspecified,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
