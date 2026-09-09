package com.nowen.video.v2.feature.tv.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/** TV 播放器控件：标题、D-pad 进度条、大按钮组。 */
@Composable
fun TvPlayerControls(
    title: String,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    subtitlesEnabled: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeekDelta: (Long) -> Unit,
    onToggleSubtitles: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(controlsScrimBrush())
            .padding(horizontal = 48.dp, vertical = 26.dp),
    ) {
        Text(
            title.ifBlank { "正在播放" },
            style = MaterialTheme.typography.titleLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(14.dp))
        TvSeekBar(positionMs = positionMs, durationMs = durationMs, onSeekDelta = onSeekDelta)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                formatTvTime(positionMs),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "/ ${formatTvTime(durationMs)}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            TvControlButton(
                icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                label = if (isPlaying) "暂停" else "播放",
                requestInitialFocus = true,
                onClick = onTogglePlayPause,
            )
            TvControlButton(
                icon = Icons.Filled.Replay10,
                label = "后退 10 秒",
                onClick = { onSeekDelta(-10_000L) },
            )
            TvControlButton(
                icon = Icons.Filled.Forward10,
                label = "快进 10 秒",
                onClick = { onSeekDelta(10_000L) },
            )
            TvControlButton(
                icon = Icons.Filled.ClosedCaption,
                label = if (subtitlesEnabled) "关闭字幕" else "开启字幕",
                onClick = onToggleSubtitles,
            )
            TvControlButton(
                icon = Icons.AutoMirrored.Filled.ExitToApp,
                label = "退出播放",
                onClick = onExit,
            )
        }
    }
}

/** D-pad 进度条：聚焦后左右键 seek ±10s，长按连发由按键重复自然实现。 */
@Composable
private fun TvSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeekDelta: (Long) -> Unit,
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    val fraction = (positionMs.coerceIn(0L, safeDuration).toFloat() / safeDuration).coerceIn(0f, 1f)
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionLeft -> {
                            onSeekDelta(-10_000L)
                            true
                        }
                        Key.DirectionRight -> {
                            onSeekDelta(10_000L)
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            }
            .focusable(),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(8.dp)
                .background(
                    Color.White.copy(alpha = if (focused) 0.42f else 0.22f),
                    RoundedCornerShape(4.dp),
                ),
        )
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(fraction)
                .height(8.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
        )
        Row(
            Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(fraction),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(if (focused) 24.dp else 18.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
            )
        }
    }
}

@Composable
private fun TvControlButton(
    icon: ImageVector,
    label: String,
    requestInitialFocus: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .then(if (requestInitialFocus) Modifier.tvRequestInitialFocus() else Modifier)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clip(shape)
            .background(
                if (focused) MaterialTheme.colorScheme.primary
                else Color.White.copy(alpha = 0.10f),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = Color.White, style = MaterialTheme.typography.titleMedium)
    }
}

private fun controlsScrimBrush(): Brush = Brush.verticalGradient(
    colors = listOf(
        Color.Transparent,
        Color.Black.copy(alpha = 0.72f),
        Color.Black.copy(alpha = 0.88f),
    ),
)

internal fun formatTvTime(positionMs: Long): String {
    val totalSeconds = positionMs.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}
