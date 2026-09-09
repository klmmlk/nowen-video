package com.nowen.video.v2.feature.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.nowen.video.v2.core.data.CatalogRepository
import com.nowen.video.v2.core.data.SeriesRepository
import com.nowen.video.v2.core.data.ServerSessionStore
import com.nowen.video.v2.core.model.MediaDetail
import com.nowen.video.v2.core.model.SeriesBundle
import com.nowen.video.v2.core.model.seriesEpisodeSubtitle
import com.nowen.video.v2.feature.tv.components.TvScrimBrush
import com.nowen.video.v2.feature.tv.components.tvFocusScale
import com.nowen.video.v2.feature.tv.components.tvRequestInitialFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TvDetailUiState(
    val loading: Boolean = true,
    val detail: MediaDetail? = null,
    val error: String? = null,
)

@HiltViewModel
class TvDetailViewModel @Inject constructor(
    private val repository: CatalogRepository,
    val sessionStore: ServerSessionStore,
) : ViewModel() {
    private val _state = MutableStateFlow(TvDetailUiState())
    val state: StateFlow<TvDetailUiState> = _state

    fun load(mediaId: String) {
        if (_state.value.detail?.id == mediaId) return
        viewModelScope.launch {
            _state.update { TvDetailUiState(loading = true) }
            repository.detail(mediaId)
                .onSuccess { detail -> _state.update { TvDetailUiState(loading = false, detail = detail) } }
                .onFailure { error -> _state.update { TvDetailUiState(loading = false, error = error.message ?: "加载失败") } }
        }
    }
}

/** TV 单集/电影详情页：横版 Hero + 播放入口。 */
@Composable
fun TvDetailScreen(
    mediaId: String,
    onBack: () -> Unit,
    onPlay: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    viewModel: TvDetailViewModel = hiltViewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(mediaId) { viewModel.load(mediaId) }
    val state by viewModel.state.collectAsState()
    val session by viewModel.sessionStore.snapshot.collectAsState()
    val baseUrl = session.activeServer?.baseUrl

    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.error != null -> TvErrorPane(
            message = state.error.orEmpty(),
            actionLabel = "返回",
            onAction = onBack,
        )
        else -> state.detail?.let { detail ->
            TvDetailContent(
                detail = detail,
                baseUrl = baseUrl,
                onBack = onBack,
                onPlay = { onPlay(detail.id) },
                onOpenSeries = { onOpenSeries(detail.seriesId) },
            )
        }
    }
}

@Composable
private fun TvDetailContent(
    detail: MediaDetail,
    baseUrl: String?,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onOpenSeries: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        val backdrop = detail.backdropPath.takeIf { it.isNotBlank() }
            ?: "/api/media/${detail.id}/backdrop"
        AsyncImage(
            model = tvArtwork(baseUrl, backdrop),
            contentDescription = detail.displayTitle,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(Modifier.fillMaxSize().background(TvScrimBrush()))

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 48.dp, end = 48.dp, bottom = 40.dp),
        ) {
            Spacer(Modifier.height(20.dp))
            TvBackButton(onBack)
            Spacer(Modifier.height(12.dp))
            Text(
                detail.displayTitle,
                style = MaterialTheme.typography.headlineLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(
                detail.year.takeIf { it > 0 }?.toString(),
                detail.rating.takeIf { it > 0.0 }?.let { "★ %.1f".format(it) },
                detail.duration.takeIf { it > 0.0 }?.let { "${(it / 60).toInt().coerceAtLeast(1)} 分钟" },
                detail.genres.takeIf { it.isNotBlank() },
                detail.resolution.takeIf { it.isNotBlank() },
                detail.videoCodec.takeIf { it.isNotBlank() }?.uppercase(),
            ).joinToString(" · ")
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(meta, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (detail.overview.isNotBlank()) {
                Spacer(Modifier.height(14.dp))
                Text(detail.overview, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = onPlay,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(56.dp)
                        .width(180.dp)
                        .tvRequestInitialFocus()
                        .tvFocusScale(shape = RoundedCornerShape(12.dp)),
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(26.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("播放", style = MaterialTheme.typography.titleMedium)
                }
                if (detail.seriesId.isNotBlank()) {
                    OutlinedButton(
                        onClick = onOpenSeries,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(56.dp)
                            .tvFocusScale(shape = RoundedCornerShape(12.dp)),
                    ) {
                        Icon(Icons.Filled.VideoLibrary, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("查看剧集")
                    }
                }
            }
        }
    }
}

@Composable
internal fun TvBackButton(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .tvFocusScale(shape = shape)
            .clip(shape)
            .background(if (focused) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f))
            .clickable(onClick = onBack)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "返回",
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text("返回", color = Color.White)
    }
}

data class TvSeriesUiState(
    val loading: Boolean = true,
    val bundle: SeriesBundle? = null,
    val error: String? = null,
)

@HiltViewModel
class TvSeriesViewModel @Inject constructor(
    private val repository: SeriesRepository,
    val sessionStore: ServerSessionStore,
) : ViewModel() {
    private val _state = MutableStateFlow(TvSeriesUiState())
    val state: StateFlow<TvSeriesUiState> = _state

    fun load(seriesId: String) {
        if (_state.value.bundle?.series?.id == seriesId) return
        viewModelScope.launch {
            _state.update { TvSeriesUiState(loading = true) }
            repository.load(seriesId)
                .onSuccess { bundle -> _state.update { TvSeriesUiState(loading = false, bundle = bundle) } }
                .onFailure { error -> _state.update { TvSeriesUiState(loading = false, error = error.message ?: "加载失败") } }
        }
    }
}

/** TV 剧集详情页：季切换 + 横向集卡片。 */
@Composable
fun TvSeriesDetailScreen(
    seriesId: String,
    onBack: () -> Unit,
    onEpisodeClick: (String, Boolean) -> Unit,
    onPlayEpisode: (String) -> Unit,
    viewModel: TvSeriesViewModel = hiltViewModel(),
) {
    androidx.compose.runtime.LaunchedEffect(seriesId) { viewModel.load(seriesId) }
    val state by viewModel.state.collectAsState()
    val session by viewModel.sessionStore.snapshot.collectAsState()
    val baseUrl = session.activeServer?.baseUrl

    when {
        state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        state.error != null -> TvErrorPane(
            message = state.error.orEmpty(),
            actionLabel = "返回",
            onAction = onBack,
        )
        else -> state.bundle?.let { bundle ->
            TvSeriesContent(
                bundle = bundle,
                baseUrl = baseUrl,
                onBack = onBack,
                onEpisodeClick = onEpisodeClick,
                onPlayEpisode = onPlayEpisode,
            )
        }
    }
}

@Composable
private fun TvSeriesContent(
    bundle: SeriesBundle,
    baseUrl: String?,
    onBack: () -> Unit,
    onEpisodeClick: (String, Boolean) -> Unit,
    onPlayEpisode: (String) -> Unit,
) {
    var selectedSeason by remember { mutableStateOf(0) }
    val seasons = remember(bundle) {
        bundle.seasons.sortedWith(
            compareBy({ it.seasonNumber == 0 }, { it.seasonNumber }),
        )
    }
    val safeIndex = selectedSeason.coerceIn(0, seasons.lastIndex.coerceAtLeast(0))
    val season = seasons.getOrNull(safeIndex)

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "header") {
            Spacer(Modifier.height(20.dp))
            TvBackButton(onBack)
            Spacer(Modifier.height(12.dp))
            Text(bundle.series.displayTitle, style = MaterialTheme.typography.headlineLarge)
            if (bundle.series.metadataLabel.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(bundle.series.metadataLabel, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (bundle.series.overview.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    bundle.series.overview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item(key = "season_tabs") {
            Row(
                Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                seasons.forEachIndexed { index, item ->
                    TvSeasonChip(
                        label = item.label,
                        selected = index == safeIndex,
                        requestInitialFocus = index == 0,
                        onClick = { selectedSeason = index },
                    )
                }
            }
        }
        item(key = "episodes") {
            if (season == null || season.episodes.isEmpty()) {
                Text("本季暂无剧集", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    season.episodes.forEach { episode ->
                        TvEpisodeRow(
                            episode = episode,
                            posterUrl = tvArtwork(baseUrl, "/api/media/${episode.id}/poster"),
                            onClick = { onPlayEpisode(episode.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSeasonChip(
    label: String,
    selected: Boolean,
    requestInitialFocus: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .height(48.dp)
            .then(if (requestInitialFocus) Modifier.tvRequestInitialFocus() else Modifier)
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clip(shape)
            .background(
                when {
                    focused -> MaterialTheme.colorScheme.primary
                    selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                    else -> MaterialTheme.colorScheme.surface
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = if (focused || selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun TvEpisodeRow(
    episode: MediaDetail,
    posterUrl: String?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused || it.hasFocus }
            .clip(shape)
            .background(if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(112.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            AsyncImage(
                model = posterUrl,
                contentDescription = episode.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                episode.displayTitle,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                episode.seriesEpisodeSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = "播放",
            tint = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(30.dp),
        )
    }
}
