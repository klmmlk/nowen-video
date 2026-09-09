package com.nowen.video.v2.feature.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.nowen.video.v2.core.data.NowenRepository
import com.nowen.video.v2.core.data.ServerSessionStore
import com.nowen.video.v2.core.model.HomeContent
import com.nowen.video.v2.core.model.MediaCard
import com.nowen.video.v2.feature.tv.components.TvCardMetrics
import com.nowen.video.v2.feature.tv.components.TvPosterCard
import com.nowen.video.v2.feature.tv.components.TvScrimBrush
import com.nowen.video.v2.feature.tv.components.tvFocusScale
import com.nowen.video.v2.feature.tv.components.tvRequestInitialFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TvHomeUiState(
    val loading: Boolean = true,
    val content: HomeContent = HomeContent(),
    val error: String? = null,
)

@HiltViewModel
class TvHomeViewModel @Inject constructor(
    private val repository: NowenRepository,
    val sessionStore: ServerSessionStore,
) : ViewModel() {
    private val _state = MutableStateFlow(TvHomeUiState())
    val state: StateFlow<TvHomeUiState> = _state

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            repository.loadHome()
                .onSuccess { content -> _state.update { it.copy(loading = false, content = content) } }
                .onFailure { error -> _state.update { it.copy(loading = false, error = error.message ?: "加载失败") } }
        }
    }
}

/** TV 首页：Hero 横幅 + 继续观看 + 最近添加。 */
@Composable
fun TvHomeScreen(
    onMediaClick: (String, Boolean) -> Unit,
    onPlay: (String) -> Unit,
    onLibraryClick: () -> Unit,
    viewModel: TvHomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val session by viewModel.sessionStore.snapshot.collectAsState()
    val baseUrl = session.activeServer?.baseUrl

    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.error != null -> TvErrorPane(
            message = state.error.orEmpty(),
            actionLabel = "重试",
            onAction = viewModel::refresh,
        )
        else -> TvHomeContent(
            content = state.content,
            baseUrl = baseUrl,
            onMediaClick = onMediaClick,
            onPlay = onPlay,
            onLibraryClick = onLibraryClick,
        )
    }
}

@Composable
private fun TvHomeContent(
    content: HomeContent,
    baseUrl: String?,
    onMediaClick: (String, Boolean) -> Unit,
    onPlay: (String) -> Unit,
    onLibraryClick: () -> Unit,
) {
    val hero = content.recent.firstOrNull() ?: content.continueWatching.firstOrNull()
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        if (hero != null) {
            item(key = "hero") {
                TvHero(
                    media = hero,
                    baseUrl = baseUrl,
                    onPlay = {
                        // 剧集条目没有可直接起播的媒体（服务器按媒体 id 出流），进剧集页选集。
                        if (hero.isSeries) onMediaClick(hero.resolvedId, true)
                        else onPlay(hero.resolvedId)
                    },
                    onDetail = { onMediaClick(hero.resolvedId, hero.isSeries) },
                )
            }
        }
        if (content.continueWatching.isNotEmpty()) {
            item(key = "continue_header") { TvRailTitle("继续观看") }
            item(key = "continue_rail") {
                TvMediaRail(
                    items = content.continueWatching,
                    baseUrl = baseUrl,
                    // 继续观看的条目都有播放进度，点击直接续播；
                    // 剧集条目没有可直接播放的媒体，仍进详情选集。
                    onClick = { media ->
                        if (media.isSeries) onMediaClick(media.resolvedId, true)
                        else onPlay(media.resolvedId)
                    },
                )
            }
        }
        if (content.recent.isNotEmpty()) {
            item(key = "recent_header") { TvRailTitle("最近添加") }
            item(key = "recent_rail") {
                TvMediaRail(
                    items = content.recent,
                    baseUrl = baseUrl,
                    onClick = { media -> onMediaClick(media.resolvedId, media.isSeries) },
                )
            }
        }
        if (content.libraries.isNotEmpty()) {
            item(key = "libraries") {
                Column(Modifier.padding(horizontal = 48.dp)) {
                    TvRailTitle("媒体库")
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        content.libraries.forEach { library ->
                            OutlinedButton(
                                onClick = onLibraryClick,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(52.dp),
                            ) {
                                Text(library.name)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun TvRailTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(start = 48.dp, top = 30.dp, bottom = 12.dp),
    )
}

@Composable
internal fun TvMediaRail(
    items: List<MediaCard>,
    baseUrl: String?,
    onClick: (MediaCard) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(TvCardMetrics.CardGap),
    ) {
        items(items, key = { it.resolvedId + it.hashCode() }) { media ->
            TvPosterCard(
                media = media,
                imageUrl = tvArtwork(baseUrl, media.resolvedPoster),
                onClick = { onClick(media) },
                modifier = Modifier.width(TvCardMetrics.PosterWidth),
            )
        }
    }
}

@Composable
private fun TvHero(
    media: MediaCard,
    baseUrl: String?,
    onPlay: () -> Unit,
    onDetail: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 8f),
    ) {
        AsyncImage(
            model = tvArtwork(baseUrl, media.resolvedBackdrop ?: media.resolvedPoster),
            contentDescription = media.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(TvScrimBrush()))
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 48.dp, end = 48.dp, bottom = 26.dp)
                .width(560.dp),
        ) {
            Text(
                media.displayTitle,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                media.year?.takeIf { it > 0 }?.toString(),
                media.rating.takeIf { it > 0.0 }?.let { "★ %.1f".format(it) },
                media.genres.takeIf { it.isNotBlank() },
                media.resolution.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (media.overview.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    media.overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onPlay,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(56.dp)
                        .width(170.dp)
                        .tvRequestInitialFocus()
                        .tvFocusScale(shape = RoundedCornerShape(12.dp)),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("播放", style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = onDetail,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(56.dp)
                        .tvFocusScale(shape = RoundedCornerShape(12.dp)),
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("详情")
                }
            }
        }
    }
}

@Composable
internal fun TvErrorPane(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .height(52.dp)
                    .tvFocusScale(shape = RoundedCornerShape(12.dp)),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(actionLabel)
            }
        }
    }
}

/** 与手机版 resolveImage 一致的相对路径拼接（feature:main 内为 internal，无法跨模块复用）。 */
internal fun tvArtwork(baseUrl: String?, path: String?): String? {
    if (path.isNullOrBlank()) return null
    if (path.startsWith("http://") || path.startsWith("https://")) return path
    val server = baseUrl?.trimEnd('/') ?: return null
    return "$server/${path.trimStart('/')}"
}
