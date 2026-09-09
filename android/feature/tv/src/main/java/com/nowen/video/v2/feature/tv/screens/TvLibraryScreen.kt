package com.nowen.video.v2.feature.tv.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nowen.video.v2.core.data.CatalogRepository
import com.nowen.video.v2.core.data.ServerSessionStore
import com.nowen.video.v2.core.model.LibrarySummary
import com.nowen.video.v2.core.model.MediaCard
import com.nowen.video.v2.core.model.PaginatedEnvelope
import com.nowen.video.v2.feature.tv.components.TvCardMetrics
import com.nowen.video.v2.feature.tv.components.TvPosterCard
import com.nowen.video.v2.feature.tv.components.tvRequestInitialFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TvLibraryUiState(
    val libraries: List<LibrarySummary> = emptyList(),
    val selectedLibraryId: String? = null,
    val items: List<MediaCard> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val endReached: Boolean = false,
    val error: String? = null,
) {
    val selectedLibraryName: String
        get() = libraries.firstOrNull { it.id == selectedLibraryId }?.name ?: "全部媒体"
}

private const val PAGE_SIZE = 60

@HiltViewModel
class TvLibraryViewModel @Inject constructor(
    private val repository: CatalogRepository,
    val sessionStore: ServerSessionStore,
) : ViewModel() {
    private val _state = MutableStateFlow(TvLibraryUiState())
    val state: StateFlow<TvLibraryUiState> = _state
    private var page = 1

    init { reload() }

    fun reload() {
        page = 1
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, items = emptyList(), endReached = false) }
            repository.libraries()
                .onSuccess { libraries ->
                    _state.update { it.copy(libraries = libraries) }
                }
            loadPage()
        }
    }

    fun selectLibrary(libraryId: String?) {
        if (_state.value.selectedLibraryId == libraryId) return
        _state.update { it.copy(selectedLibraryId = libraryId) }
        reload()
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || current.endReached) return
        page += 1
        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            loadPage()
        }
    }

    private suspend fun loadPage() {
        repository.media(
            page = page,
            size = PAGE_SIZE,
            libraryId = _state.value.selectedLibraryId,
        )
            .onSuccess { envelope: PaginatedEnvelope<MediaCard> ->
                val batch = envelope.data
                _state.update {
                    val merged = if (page == 1) batch else it.items + batch
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        items = merged,
                        endReached = batch.size < PAGE_SIZE || merged.size >= envelope.total,
                    )
                }
            }
            .onFailure { error ->
                _state.update {
                    it.copy(
                        loading = false,
                        loadingMore = false,
                        error = error.message ?: "加载失败",
                    )
                }
            }
    }
}

/** TV 影视库：库切换 + 焦点海报网格，滚动到底自动加载。 */
@Composable
fun TvLibraryScreen(
    onMediaClick: (String, Boolean) -> Unit,
    viewModel: TvLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val session by viewModel.sessionStore.snapshot.collectAsState()
    val baseUrl = session.activeServer?.baseUrl
    val gridState = rememberLazyGridState()

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            last >= info.totalItemsCount - 12 && info.totalItemsCount > 0
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadMore()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp)) {
        Spacer(Modifier.height(28.dp))
        Text("影视库", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TvLibraryChip(
                label = "全部媒体",
                selected = state.selectedLibraryId == null,
                requestInitialFocus = true,
                onClick = { viewModel.selectLibrary(null) },
            )
            state.libraries.forEach { library ->
                TvLibraryChip(
                    label = library.name,
                    selected = state.selectedLibraryId == library.id,
                    onClick = { viewModel.selectLibrary(library.id) },
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> TvErrorPane(
                message = state.error.orEmpty(),
                actionLabel = "重试",
                onAction = viewModel::reload,
            )
            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = TvCardMetrics.PosterWidth),
                contentPadding = PaddingValues(bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(TvCardMetrics.CardGap),
                verticalArrangement = Arrangement.spacedBy(TvCardMetrics.RailGap),
            ) {
                items(state.items, key = { it.resolvedId + it.hashCode() }) { media ->
                    TvPosterCard(
                        media = media,
                        imageUrl = tvArtwork(baseUrl, media.resolvedPoster),
                        onClick = { onMediaClick(media.resolvedId, media.isSeries) },
                    )
                }
                if (state.loadingMore) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TvLibraryChip(
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
