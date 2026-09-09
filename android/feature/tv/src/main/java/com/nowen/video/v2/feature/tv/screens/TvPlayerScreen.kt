package com.nowen.video.v2.feature.tv.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.nowen.video.v2.core.model.SubtitleTrack
import com.nowen.video.v2.feature.main.PlayerViewModel
import com.nowen.video.v2.feature.tv.components.TvPlayerControls
import com.nowen.video.v2.feature.tv.components.formatTvTime
import java.util.Locale
import kotlinx.coroutines.delay

private const val PERIODIC_PROGRESS_INTERVAL_MS = 10_000L
private const val TV_CONTROLS_TIMEOUT_MS = 5_000L
private const val SEEK_STEP_MS = 10_000L
/** D-pad 连续 seek 的预览提交延时：停按左右键这么久后才真正执行 seek。 */
private const val SEEK_PREVIEW_COMMIT_DELAY_MS = 450L

/**
 * TV 播放器：复用手机版 PlayerViewModel 的会话管理、进度上报与心跳，
 * 仅重写交互层 —— 触摸手势全部替换为 D-pad 控制。
 *
 * 控件隐藏时：OK 播放/暂停，左右连续 seek（预览偏移，停手后提交），
 * 下键调出播放菜单，上键无操作（音量由电视遥控器音量键负责）。
 * 控件显示时：方向键在控件间移动焦点，进度条聚焦后左右 seek。
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun TvPlayerScreen(
    mediaId: String,
    onBack: () -> Unit,
    onPlayNext: (String) -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val session by viewModel.sessionStore.snapshot.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val token = session.token.orEmpty()
    var controlsVisible by rememberSaveable(mediaId) { mutableStateOf(true) }
    var controlsEpoch by remember(mediaId) { mutableIntStateOf(0) }
    var isPlaying by remember(mediaId) { mutableStateOf(false) }
    var subtitlesEnabled by rememberSaveable(mediaId) { mutableStateOf(true) }
    var displayPositionMs by remember(mediaId) { mutableStateOf(0L) }
    var seekPreviewMs by remember(mediaId) { mutableStateOf<Long?>(null) }
    var playerDurationMs by remember(mediaId) { mutableStateOf(0L) }
    var unsupportedTrackMessage by remember(mediaId) { mutableStateOf<String?>(null) }
    val rootFocusRequester = remember { FocusRequester() }

    LaunchedEffect(mediaId) { viewModel.load(mediaId) }

    // 播放器是沉浸页，打开即持有焦点：不依赖侧边栏焦点状态（railHoldsFocus），
    // 否则焦点残留在侧边栏 tab 上，用户按 OK/方向键会作用到 tab 把播放器弹掉。
    LaunchedEffect(mediaId) {
        runCatching { rootFocusRequester.requestFocus() }
    }

    val player = remember(token) {
        val httpFactory = DefaultHttpDataSource.Factory().apply {
            if (token.isNotBlank()) setDefaultRequestProperties(mapOf("Authorization" to "Bearer $token"))
            setAllowCrossProtocolRedirects(true)
        }
        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)
        // 软解兜底：模拟器/老设备没有 AV1/HEVC 硬解时回退软件解码。
        // EXTENSION_RENDERER_MODE_ON = 硬解可用则用硬解，仅在没有可用硬解时启用 libgav1 软解；
        // enableDecoderFallback 让 MediaCodec 硬解初始化失败时回退到系统自带的软件 MediaCodec。
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
    }

    fun revealControls() {
        controlsVisible = true
        controlsEpoch += 1
    }

    fun reportCurrentProgress(force: Boolean) {
        val current = viewModel.state.value
        val exoDuration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
        val duration = current.mediaDurationMs.takeIf { it > 0L } ?: exoDuration ?: 0L
        viewModel.reportProgress(
            mediaId = mediaId,
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration.coerceAtLeast(0L),
            force = force,
        )
    }

    fun leavePlayback(reason: String, action: () -> Unit) {
        reportCurrentProgress(force = true)
        viewModel.heartbeat(
            relativePositionMs = player.currentPosition.coerceAtLeast(0L),
            relativeBufferedEndMs = player.bufferedPosition.coerceAtLeast(0L),
            paused = true,
        )
        viewModel.closePlaybackSession(reason)
        action()
    }

    fun effectiveDurationMs(): Long = state.mediaDurationMs.takeIf { it > 0L }
        ?: playerDurationMs.takeIf { it > 0L }
        ?: 0L

    fun seekToAbsolute(targetMs: Long, reason: String) {
        val duration = effectiveDurationMs()
        if (duration <= 0L) return
        val target = targetMs.coerceIn(0L, duration)
        seekPreviewMs = null
        displayPositionMs = target
        if (state.sessionManaged) {
            player.pause()
            isPlaying = false
            viewModel.restartPlaybackSession(target, reason)
        } else {
            player.seekTo(target)
        }
        revealControls()
    }

    fun seekBy(deltaMs: Long) {
        val base = seekPreviewMs ?: displayPositionMs
        seekToAbsolute(base + deltaMs, "tv_dpad_seek")
    }

    /**
     * 控件隐藏时的 D-pad seek：只更新预览位置、不弹出播放菜单，
     * 连续按/长按左右键可持续累积偏移，停手 [SEEK_PREVIEW_COMMIT_DELAY_MS]
     * 后由下面的防抖 effect 统一提交一次真正的 seek。
     */
    fun previewSeekBy(deltaMs: Long) {
        val duration = effectiveDurationMs()
        if (duration <= 0L) return
        val base = seekPreviewMs ?: displayPositionMs
        seekPreviewMs = (base + deltaMs).coerceIn(0L, duration)
    }

    // D-pad seek 防抖提交：预览期间每次按键都会重启本 effect，
    // 停手后才落到真正的 seek —— 长按/连按只触发一次会话重启。
    LaunchedEffect(seekPreviewMs) {
        val preview = seekPreviewMs ?: return@LaunchedEffect
        delay(SEEK_PREVIEW_COMMIT_DELAY_MS)
        seekPreviewMs = null
        displayPositionMs = preview
        if (state.sessionManaged) {
            player.pause()
            isPlaying = false
            viewModel.restartPlaybackSession(preview, "tv_dpad_seek")
        } else {
            player.seekTo(preview)
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
            reportCurrentProgress(force = true)
        } else {
            player.play()
        }
        revealControls()
    }

    fun toggleSubtitles() {
        subtitlesEnabled = !subtitlesEnabled
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesEnabled)
            .build()
        revealControls()
    }

    BackHandler(enabled = controlsVisible) {
        controlsVisible = false
    }
    BackHandler(enabled = !controlsVisible) {
        leavePlayback("navigate_back", onBack)
    }

    LaunchedEffect(state.playbackUrl, state.resumePositionMs, state.externalSubtitles, session.activeServer?.baseUrl) {
        if (state.playbackUrl.isNotBlank()) {
            val item = MediaItem.Builder()
                .setUri(state.playbackUrl)
                .setSubtitleConfigurations(
                    tvExternalSubtitleConfigurations(
                        baseUrl = session.activeServer?.baseUrl,
                        tracks = state.externalSubtitles,
                    ),
                )
                .build()
            if (state.resumePositionMs > 0L && !state.sessionManaged) player.setMediaItem(item, state.resumePositionMs)
            else player.setMediaItem(item)
            player.prepare()
            player.playWhenReady = true
            revealControls()
        }
    }

    LaunchedEffect(state.playbackSpeed) {
        player.setPlaybackSpeed(state.playbackSpeed)
    }

    LaunchedEffect(controlsVisible, controlsEpoch, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(TV_CONTROLS_TIMEOUT_MS)
            controlsVisible = false
        }
    }

    LaunchedEffect(!controlsVisible) {
        if (!controlsVisible) rootFocusRequester.requestFocus()
    }

    LaunchedEffect(player, mediaId) {
        while (true) {
            delay(PERIODIC_PROGRESS_INTERVAL_MS)
            if (player.isPlaying) reportCurrentProgress(force = false)
        }
    }

    LaunchedEffect(player, state.sessionId, state.sessionHeartbeatIntervalMs) {
        if (!state.sessionManaged || state.sessionId.isBlank()) return@LaunchedEffect
        while (true) {
            delay(state.sessionHeartbeatIntervalMs.coerceAtLeast(5_000L))
            viewModel.heartbeat(
                relativePositionMs = player.currentPosition.coerceAtLeast(0L),
                relativeBufferedEndMs = player.bufferedPosition.coerceAtLeast(0L),
                paused = !player.isPlaying,
            )
        }
    }

    LaunchedEffect(player, state.sessionManaged, state.sessionOffsetMs, state.mediaDurationMs) {
        while (true) {
            displayPositionMs = if (state.sessionManaged) {
                (player.currentPosition.coerceAtLeast(0L) + state.sessionOffsetMs.coerceAtLeast(0L))
            } else {
                player.currentPosition.coerceAtLeast(0L)
            }
            val reportedDuration = state.mediaDurationMs.takeIf { it > 0L }
            val exoDuration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
            playerDurationMs = reportedDuration ?: exoDuration ?: playerDurationMs
            delay(250L)
        }
    }

    DisposableEffect(player, mediaId, lifecycleOwner) {
        val playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                if (!playing && player.playbackState == Player.STATE_READY) {
                    reportCurrentProgress(force = true)
                    revealControls()
                }
                viewModel.heartbeat(
                    relativePositionMs = player.currentPosition.coerceAtLeast(0L),
                    relativeBufferedEndMs = player.bufferedPosition.coerceAtLeast(0L),
                    paused = !playing,
                )
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L }
                if (duration != null) playerDurationMs = duration
                android.util.Log.i("TvPlayer", "state=$playbackState duration=$duration tracks=${player.currentTracks.groups.joinToString { it.toString() }}")
                if (playbackState == Player.STATE_ENDED) {
                    val current = viewModel.state.value
                    viewModel.reportProgress(
                        mediaId = mediaId,
                        positionMs = current.mediaDurationMs,
                        durationMs = current.mediaDurationMs.takeIf { it > 0L } ?: playerDurationMs,
                        force = true,
                    )
                    viewModel.closePlaybackSession("playback_ended")
                    val next = current.nextEpisode
                    if (next != null && current.autoPlayNext) {
                        leavePlayback("next_media") { onPlayNext(next.id) }
                    } else {
                        revealControls()
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("TvPlayer", "playback error code=${error.errorCode} name=${error.errorCodeName} msg=${error.message}", error)
                viewModel.onPlayerError(error, player.currentPosition)
            }

            override fun onTracksChanged(tracks: Tracks) {
                android.util.Log.i("TvPlayer", "tracks changed: groups=${tracks.groups.size} groups=${tracks.groups.joinToString { g -> "${g.type}-${g.length}-${g.getTrackFormat(0).sampleMimeType ?: "?"}" }}")
                // AOSP TV x86 emulator 等设备可能没有 AV1/HEVC 解码器，
                // ExoPlayer 找不到解码器时静默跳过视频轨道 —— 不抛 onPlayerError，
                // 导致画面纯黑、只有声音。这里主动检测到 video track 全部
                // unsupported 时上报一个明确的错误，让 UI 显示原因而不是黑屏。
                val videoGroups = tracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                val allUnsupported = videoGroups.isNotEmpty() && videoGroups.all { group ->
                    (0 until group.length).all { idx -> !group.isTrackSupported(idx) }
                }
                if (allUnsupported) {
                    val mime = videoGroups.firstNotNullOfOrNull { it.getTrackFormat(0).sampleMimeType } ?: "未知"
                    val codecLabel = when (mime) {
                        MimeTypes.VIDEO_AV1 -> "AV1"
                        MimeTypes.VIDEO_H265 -> "HEVC (H.265)"
                        MimeTypes.VIDEO_H264 -> "H.264"
                        else -> mime
                    }
                    val message = "当前设备不支持该视频编码（$codecLabel），请尝试其他剧集或更换设备"
                    android.util.Log.w("TvPlayer", "unsupported video track: $mime, surfacing error UI")
                    unsupportedTrackMessage = message
                    revealControls()
                } else if (videoGroups.any { group ->
                        (0 until group.length).any { idx -> group.isTrackSupported(idx) }
                    }) {
                    // 至少有一个 video track 可用 —— 清除之前的 unsupported 标记。
                    if (unsupportedTrackMessage != null) {
                        unsupportedTrackMessage = null
                    }
                }
            }

            override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
                android.util.Log.i("TvPlayer", "video size changed: $videoSize")
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                val current = viewModel.state.value
                if (reason == Player.DISCONTINUITY_REASON_SEEK && current.sessionManaged) {
                    val target = (current.sessionOffsetMs + newPosition.positionMs.coerceAtLeast(0L))
                        .coerceIn(0L, current.mediaDurationMs.takeIf { it > 0L } ?: Long.MAX_VALUE)
                    player.pause()
                    viewModel.restartPlaybackSession(target, "exo_player_seek")
                } else if (reason == Player.DISCONTINUITY_REASON_SEEK) {
                    reportCurrentProgress(force = true)
                }
            }
        }
        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                reportCurrentProgress(force = true)
                viewModel.heartbeat(
                    relativePositionMs = player.currentPosition.coerceAtLeast(0L),
                    relativeBufferedEndMs = player.bufferedPosition.coerceAtLeast(0L),
                    paused = true,
                )
            }
        }
        player.addListener(playerListener)
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        onDispose {
            reportCurrentProgress(force = true)
            viewModel.closePlaybackSession("player_disposed")
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            player.removeListener(playerListener)
            player.release()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .focusRequester(rootFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                // 控件可见时把方向键留给焦点导航；隐藏时接管全部 D-pad 按键。
                if (controlsVisible || event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter -> {
                        togglePlayPause()
                        true
                    }
                    Key.DirectionLeft -> {
                        previewSeekBy(-SEEK_STEP_MS)
                        true
                    }
                    Key.DirectionRight -> {
                        previewSeekBy(SEEK_STEP_MS)
                        true
                    }
                    // 上键不映射任何操作（音量交给电视遥控器的音量键），
                    // 消费掉事件防止焦点意外逃逸。
                    Key.DirectionUp -> true
                    // 下键调出播放菜单（控件）。
                    Key.DirectionDown -> {
                        revealControls()
                        true
                    }
                    else -> false
                }
            },
    ) {
        AndroidView(
            factory = { viewContext ->
                PlayerView(viewContext).apply {
                    useController = false
                    // 修复 TV 上"画面纯黑、只有声音"的关键点：
                    // PlayerView 内部使用 SurfaceView，在 Compose AndroidView 包装下，
                    // 表面（Surface）与 Compose 重组/焦点切换存在同步窗口，
                    // 会让 SurfaceView 露出黑色 shutter 背景。Media3 1.5.1 提供
                    // `setEnableComposeSurfaceSyncWorkaround` 专门修复这一组合下的
                    // surface lifecycle 不同步问题。TV 上 D-pad 焦点切换频繁打开播放器，
                    // 必须启用该 workaround 才能保证视频帧稳定显示。
                    setEnableComposeSurfaceSyncWorkaround(true)
                    // 切换/重置 Player 时保留上一帧内容，避免出现一瞬黑色 shutter。
                    setKeepContentOnPlayerReset(true)
                    // shutter 默认黑色：解码器/网络出错时会一直停留在这里，
                    // 给用户错误感（"画面死了"）。改成透明让错误状态更显眼。
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    keepScreenOn = true
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        unsupportedTrackMessage?.let { message ->
            // 视频轨道不被设备支持（AOSP TV x86 等没有 AV1/HEVC 解码器）时
            // ExoPlayer 不会抛 onPlayerError，画面只是黑屏。这里用半透明遮罩
            // 提示用户原因，并指引退出。
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color(0xB0000000)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "视频无法播放",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        message,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "按返回键退出",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        state.error?.let { error ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(error, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "按返回键退出",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        // D-pad 预览 seek 的轻量提示：只显示方向与目标时间，不弹出完整播放菜单。
        seekPreviewMs?.let { preview ->
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xCC20202E))
                    .padding(horizontal = 22.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        if (preview > displayPositionMs) "▶▶" else "◀◀",
                        color = Color(0xFF8F7AFF),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "${formatTvTime(preview)} / ${formatTvTime(effectiveDurationMs())}",
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
        if (controlsVisible && state.error == null && unsupportedTrackMessage == null) {
            TvPlayerControls(
                title = state.title,
                positionMs = displayPositionMs,
                durationMs = effectiveDurationMs(),
                isPlaying = isPlaying,
                subtitlesEnabled = subtitlesEnabled,
                onTogglePlayPause = ::togglePlayPause,
                onSeekDelta = ::seekBy,
                onToggleSubtitles = ::toggleSubtitles,
                onExit = { leavePlayback("controls_exit", onBack) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** 与手机版 externalSubtitleConfigurations 相同的拼接逻辑（feature:main 内为 internal）。 */
private fun tvExternalSubtitleConfigurations(
    baseUrl: String?,
    tracks: List<SubtitleTrack>,
): List<MediaItem.SubtitleConfiguration> {
    if (baseUrl.isNullOrBlank()) return emptyList()
    return tracks.mapNotNull { track ->
        val path = track.sourcePath.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val uri = Uri.parse(baseUrl).buildUpon()
            .appendEncodedPath("api/subtitle/external")
            .appendQueryParameter("path", path)
            .appendQueryParameter("format", "raw")
            .build()
        MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(tvSubtitleMimeType(track.format, path))
            .setLanguage(track.language.ifBlank { "und" })
            .setLabel(track.displayLabel)
            .build()
    }
}

private fun tvSubtitleMimeType(format: String, path: String = ""): String {
    val resolved = format.ifBlank { path.substringAfterLast('.', "") }.lowercase(Locale.ROOT)
    return when (resolved) {
        "vtt", "webvtt" -> MimeTypes.TEXT_VTT
        "ass", "ssa" -> MimeTypes.TEXT_SSA
        "ttml", "xml" -> MimeTypes.APPLICATION_TTML
        else -> MimeTypes.APPLICATION_SUBRIP
    }
}
