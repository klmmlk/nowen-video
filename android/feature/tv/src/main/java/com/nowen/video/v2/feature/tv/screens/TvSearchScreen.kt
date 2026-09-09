package com.nowen.video.v2.feature.tv.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nowen.video.v2.core.data.NowenRepository
import com.nowen.video.v2.core.data.ServerSessionStore
import com.nowen.video.v2.core.model.MediaCard
import com.nowen.video.v2.feature.tv.components.TvCardMetrics
import com.nowen.video.v2.feature.tv.components.TvPosterCard
import com.nowen.video.v2.feature.tv.components.tvRequestInitialFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

data class TvSearchUiState(
    val query: String = "",
    val searching: Boolean = false,
    val searched: Boolean = false,
    val results: List<MediaCard> = emptyList(),
    val error: String? = null,
)

@OptIn(FlowPreview::class)
@HiltViewModel
class TvSearchViewModel @Inject constructor(
    private val repository: NowenRepository,
    val sessionStore: ServerSessionStore,
) : ViewModel() {
    private val _state = MutableStateFlow(TvSearchUiState())
    val state: StateFlow<TvSearchUiState> = _state
    private val queryInput = MutableStateFlow("")

    init {
        viewModelScope.launch {
            queryInput.debounce(500).distinctUntilChanged().collect { query ->
                if (query.isBlank()) {
                    _state.value = TvSearchUiState(query = query)
                    return@collect
                }
                _state.value = _state.value.copy(query = query, searching = true, error = null)
                repository.search(query)
                    .onSuccess { results ->
                        _state.value = _state.value.copy(searching = false, searched = true, results = results)
                    }
                    .onFailure { error ->
                        _state.value = _state.value.copy(
                            searching = false,
                            searched = true,
                            error = error.message ?: "搜索失败",
                        )
                    }
            }
        }
    }

    fun query(value: String) {
        _state.value = _state.value.copy(query = value)
        queryInput.value = value
    }
}

/** TV 搜索页：大搜索框 + 结果网格；输入依赖系统遥控器输入法。 */
@Composable
fun TvSearchScreen(
    onMediaClick: (String, Boolean) -> Unit,
    viewModel: TvSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val session by viewModel.sessionStore.snapshot.collectAsState()
    val baseUrl = session.activeServer?.baseUrl

    Column(Modifier.fillMaxSize().padding(horizontal = 48.dp)) {
        Spacer(Modifier.height(28.dp))
        Text("搜索", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::query,
            placeholder = { Text("输入片名、演员或关键词") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { viewModel.query(state.query) }),
            modifier = Modifier.fillMaxWidth().height(64.dp).tvRequestInitialFocus(),
        )
        Spacer(Modifier.height(20.dp))
        when {
            state.searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.error != null -> TvErrorPane(
                message = state.error.orEmpty(),
                actionLabel = "重试",
                onAction = { viewModel.query(state.query) },
            )
            state.searched && state.results.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("没有找到「${state.query}」相关内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = TvCardMetrics.PosterWidth),
                contentPadding = PaddingValues(bottom = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(TvCardMetrics.CardGap),
                verticalArrangement = Arrangement.spacedBy(TvCardMetrics.RailGap),
            ) {
                items(state.results, key = { it.resolvedId + it.hashCode() }) { media ->
                    TvPosterCard(
                        media = media,
                        imageUrl = tvArtwork(baseUrl, media.resolvedPoster),
                        onClick = { onMediaClick(media.resolvedId, media.isSeries) },
                    )
                }
            }
        }
    }
}
