package com.nowen.video.v2.feature.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nowen.video.v2.core.data.NowenRepository
import com.nowen.video.v2.core.data.ServerSessionStore
import com.nowen.video.v2.core.designsystem.NowenColors
import com.nowen.video.v2.feature.tv.components.tvFocusScale
import com.nowen.video.v2.feature.tv.components.tvRequestInitialFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class TvSettingsViewModel @Inject constructor(
    val sessionStore: ServerSessionStore,
    private val repository: NowenRepository,
) : ViewModel() {
    fun logout() {
        viewModelScope.launch { repository.logout() }
    }
}

/**
 * TV 设置页：服务器信息、外观说明与退出登录。
 * TV 恒为暗色主题，播放偏好沿用账户级服务端配置，无需本地开关。
 */
@Composable
fun TvSettingsScreen(
    onBack: () -> Unit,
    viewModel: TvSettingsViewModel = hiltViewModel(),
) {
    val session by viewModel.sessionStore.snapshot.collectAsState()
    var confirmLogout by remember { mutableStateOf(false) }

    fun onLogout() = viewModel.logout()

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 48.dp),
    ) {
        Spacer(Modifier.height(28.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TvBackButton(onBack, Modifier.tvRequestInitialFocus())
            Spacer(Modifier.width(20.dp))
            Text("设置", style = MaterialTheme.typography.headlineLarge)
        }
        Spacer(Modifier.height(28.dp))
        TvSettingsPanel(title = "服务器", icon = Icons.Filled.Dns) {
            SettingRow(label = "名称", value = session.activeServer?.name ?: "未连接")
            SettingRow(label = "地址", value = session.activeServer?.baseUrl ?: "-")
            SettingRow(label = "账户", value = session.user?.username ?: "-")
        }
        Spacer(Modifier.height(18.dp))
        TvSettingsPanel(title = "外观", icon = Icons.Filled.Palette) {
            SettingRow(label = "主题", value = "暗色（TV 固定）")
        }
        Spacer(Modifier.height(18.dp))
        TvSettingsPanel(title = "账户", icon = Icons.AutoMirrored.Filled.Logout) {
            if (confirmLogout) {
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Button(
                        onClick = ::onLogout,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .tvFocusScale(shape = RoundedCornerShape(12.dp)),
                    ) {
                        Text("确认退出登录")
                    }
                    OutlinedButton(
                        onClick = { confirmLogout = false },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(52.dp)
                            .tvFocusScale(shape = RoundedCornerShape(12.dp)),
                    ) {
                        Text("取消")
                    }
                }
            } else {
                Button(
                    onClick = { confirmLogout = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(52.dp)
                        .tvFocusScale(shape = RoundedCornerShape(12.dp)),
                ) {
                    Text("退出登录")
                }
            }
        }
    }
}

@Composable
private fun TvSettingsPanel(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NowenColors.DeepSurface)
            .padding(24.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = title, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
