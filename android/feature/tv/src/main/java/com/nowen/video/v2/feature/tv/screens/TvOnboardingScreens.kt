package com.nowen.video.v2.feature.tv.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nowen.video.v2.core.data.NowenRepository
import com.nowen.video.v2.feature.main.LoginViewModel
import com.nowen.video.v2.feature.main.ServerSetupViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * TV 服务器连接页：复用手机版 ServerSetupViewModel，
 * 仅重排为 10-foot 布局（大输入框、大按钮、D-pad 焦点）。
 * 局域网自动发现放在最上面：首次配置最常见的路径是直接点选发现的设备。
 * TV 无摄像头，不提供扫码入口。
 */
@Composable
fun TvServerSetupScreen(viewModel: ServerSetupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.startDiscovery() }

    TvOnboardingScaffold(title = "连接服务器", subtitle = "选择局域网内发现的服务器，或手动输入地址") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "局域网内发现的服务器",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                onClick = viewModel::startDiscovery,
                enabled = !state.isScanning,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(44.dp),
            ) {
                Text(if (state.isScanning) "搜索中…" else "重新搜索")
            }
        }
        Spacer(Modifier.height(12.dp))
        when {
            state.discoveredServers.isNotEmpty() -> state.discoveredServers.forEach { server ->
                DiscoveredServerRow(name = server.name, url = server.url) {
                    viewModel.addDiscovered(server)
                }
                Spacer(Modifier.height(10.dp))
            }
            state.isScanning -> Text(
                "正在搜索局域网…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Text(
                "未发现服务器，可等待搜索完成，或在下方手动输入地址",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(28.dp))
        Text("手动连接", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.address,
            onValueChange = viewModel::address,
            label = { Text("服务器地址，如 http://192.168.1.10:9000") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::name,
            label = { Text("名称（可选）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = viewModel::connect,
            enabled = !state.loading,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.height(48.dp).width(180.dp),
        ) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.height(22.dp).width(22.dp), strokeWidth = 2.dp)
            } else {
                Text("连接", style = MaterialTheme.typography.titleMedium)
            }
        }
        state.error?.let { error ->
            Spacer(Modifier.height(16.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
        if (state.servers.isNotEmpty()) {
            Spacer(Modifier.height(28.dp))
            Text("已保存的服务器", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            state.servers.forEach { server ->
                DiscoveredServerRow(name = server.name, url = server.baseUrl) {
                    viewModel.activate(server.id)
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DiscoveredServerRow(name: String, url: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    androidx.compose.material3.Surface(
        onClick = onClick,
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text(name.ifBlank { "未命名服务器" }, style = MaterialTheme.typography.titleMedium)
            Text(
                url,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** TV 登录页：复用手机版 LoginViewModel，仅调整布局密度。 */
@Composable
fun TvLoginScreen(viewModel: LoginViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    TvOnboardingScaffold(title = "欢迎回来", subtitle = "登录 ${viewModel.serverName}") {
        OutlinedTextField(
            value = state.username,
            onValueChange = viewModel::username,
            label = { Text("用户名") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::password,
            label = { Text("密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(
                onClick = viewModel::login,
                enabled = !state.loading,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(48.dp).width(180.dp),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(Modifier.height(22.dp).width(22.dp), strokeWidth = 2.dp)
                } else {
                    Text("登录", style = MaterialTheme.typography.titleMedium)
                }
            }
            OutlinedButton(
                onClick = viewModel::changeServer,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.height(48.dp),
            ) {
                Text("切换服务器")
            }
        }
        state.error?.let { error ->
            Spacer(Modifier.height(16.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}

data class TvPasswordUiState(
    val oldPassword: String = "",
    val newPassword: String = "",
    val confirm: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class TvPasswordViewModel @Inject constructor(
    private val repository: NowenRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(TvPasswordUiState())
    val state: StateFlow<TvPasswordUiState> = _state

    fun oldPassword(value: String) = _state.update { it.copy(oldPassword = value, error = null) }
    fun newPassword(value: String) = _state.update { it.copy(newPassword = value, error = null) }
    fun confirm(value: String) = _state.update { it.copy(confirm = value, error = null) }

    fun submit() {
        val current = _state.value
        if (current.oldPassword.isBlank() || current.newPassword.isBlank()) {
            _state.update { it.copy(error = "请输入旧密码和新密码") }
            return
        }
        if (current.newPassword != current.confirm) {
            _state.update { it.copy(error = "两次输入的新密码不一致") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            repository.changePassword(current.oldPassword, current.newPassword)
                .onFailure { error -> _state.update { it.copy(error = error.message ?: "修改失败") } }
            _state.update { it.copy(loading = false) }
        }
    }
}

/** TV 强制改密页：对应手机版 ForcePasswordScreen。 */
@Composable
fun TvForcePasswordScreen(viewModel: TvPasswordViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()

    TvOnboardingScaffold(title = "修改密码", subtitle = "首次登录或管理员要求修改密码") {
        OutlinedTextField(
            value = state.oldPassword,
            onValueChange = viewModel::oldPassword,
            label = { Text("旧密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.newPassword,
            onValueChange = viewModel::newPassword,
            label = { Text("新密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.confirm,
            onValueChange = viewModel::confirm,
            label = { Text("确认新密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = viewModel::submit,
            enabled = !state.loading,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.height(48.dp).width(180.dp),
        ) {
            if (state.loading) {
                CircularProgressIndicator(Modifier.height(22.dp).width(22.dp), strokeWidth = 2.dp)
            } else {
                Text("确认修改", style = MaterialTheme.typography.titleMedium)
            }
        }
        state.error?.let { error ->
            Spacer(Modifier.height(16.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** TV 引导页通用骨架：居中窄栏 + 滚动，背景与主壳一致。 */
@Composable
private fun TvOnboardingScaffold(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit,
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(520.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(title, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(6.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(32.dp))
            content()
        }
    }
}
