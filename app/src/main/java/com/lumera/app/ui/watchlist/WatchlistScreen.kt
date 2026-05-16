package com.lumera.app.ui.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lumera.app.data.model.ProfileEntity
import com.lumera.app.data.model.stremio.MetaItem
import com.lumera.app.ui.home.DpadRepeatGate
import com.lumera.app.ui.home.InfiniteLoopRow
import com.lumera.app.ui.home.UpKeyDebouncer

@Composable
fun WatchlistScreen(
    currentProfile: ProfileEntity?,
    entryRequester: FocusRequester,
    drawerRequester: FocusRequester,
    onMovieClick: (MetaItem) -> Unit,
    watchedIds: Set<String> = emptySet(),
    viewModel: WatchlistViewModel = hiltViewModel()
) {
    val movies by viewModel.movieItems.collectAsState()
    val series by viewModel.seriesItems.collectAsState()

    androidx.activity.compose.BackHandler { drawerRequester.requestFocus() }

    val upKeyDebouncer = remember { UpKeyDebouncer() }
    val dpadRepeatGate = remember { DpadRepeatGate() }

    var lastFocusedKey by remember { mutableStateOf(viewModel.lastFocusedKey) }

    // Redirect focus when the focused item was removed (e.g., unwatchlisted from details)
    LaunchedEffect(movies, series) {
        val key = lastFocusedKey ?: return@LaunchedEffect
        val parts = key.split("_")
        val rowIndex = parts.getOrNull(0)?.toIntOrNull()
        val itemId = parts.getOrNull(1)
        if (itemId == null) return@LaunchedEffect

        val items = if (rowIndex == 0) movies else series
        val stillExists = items.any { it.id == itemId }
        if (!stillExists && items.isNotEmpty()) {
            // Focus the last item in the same row (closest to where the removed item was)
            val fallbackItem = items.last()
            val fallbackIndex = items.lastIndex
            lastFocusedKey = "${rowIndex}_${fallbackItem.id}_$fallbackIndex"
            viewModel.lastFocusedKey = lastFocusedKey
        } else if (!stillExists && items.isEmpty()) {
            // Row is now empty — focus the other row if it exists
            val otherItems = if (rowIndex == 0) series else movies
            if (otherItems.isNotEmpty()) {
                val otherRow = if (rowIndex == 0) 1 else 0
                lastFocusedKey = "${otherRow}_${otherItems.first().id}_0"
                viewModel.lastFocusedKey = lastFocusedKey
            } else {
                lastFocusedKey = null
                viewModel.lastFocusedKey = null
            }
        }
    }

    // Resolve missing posters (e.g., items pulled from Trakt)
    LaunchedEffect(movies) { movies.forEach { viewModel.resolvePosterIfNeeded(it) } }
    LaunchedEffect(series) { series.forEach { viewModel.resolvePosterIfNeeded(it) } }

    val isTopNav = currentProfile?.navPosition == "top"
    val startPadding = if (isTopNav) 50.dp else 120.dp
    val topPadding = if (isTopNav) 24.dp else 16.dp

    androidx.compose.runtime.CompositionLocalProvider(com.lumera.app.ui.components.LocalWatchedIds provides watchedIds) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (movies.isEmpty() && series.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(id = com.lumera.app.R.string.watchlist_empty),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 20.dp))

                if (movies.isNotEmpty()) {
                    InfiniteLoopRow(
                        startPadding = startPadding,
                        isTopNav = isTopNav,
                        rowIndex = 0,
                        title = stringResource(id = com.lumera.app.R.string.search_tab_movies),
                        items = movies,
                        onMovieClick = onMovieClick,
                        onViewMore = {},
                        onFocused = { _: MetaItem?, key: String ->
                            lastFocusedKey = key
                            viewModel.lastFocusedKey = key
                        },
                        entryRequester = entryRequester,
                        drawerRequester = drawerRequester,
                        locallyFocusedItemId = if (lastFocusedKey?.startsWith("0_") == true) lastFocusedKey else null,
                        isGlobalFocusPresent = lastFocusedKey != null,
                        isFirstRow = true,
                        isInfiniteLoopEnabled = false,
                        upKeyDebouncer = upKeyDebouncer,
                        repeatGate = dpadRepeatGate,
                        externalListState = viewModel.movieRowState
                    )
                }

                if (series.isNotEmpty()) {
                    InfiniteLoopRow(
                        startPadding = startPadding,
                        isTopNav = isTopNav,
                        rowIndex = 1,
                        title = stringResource(id = com.lumera.app.R.string.search_tab_series),
                        items = series,
                        onMovieClick = onMovieClick,
                        onViewMore = {},
                        onFocused = { _: MetaItem?, key: String ->
                            lastFocusedKey = key
                            viewModel.lastFocusedKey = key
                        },
                        entryRequester = entryRequester,
                        drawerRequester = drawerRequester,
                        locallyFocusedItemId = if (lastFocusedKey?.startsWith("1_") == true) lastFocusedKey else null,
                        isGlobalFocusPresent = lastFocusedKey != null,
                        isFirstRow = movies.isEmpty(),
                        isInfiniteLoopEnabled = false,
                        upKeyDebouncer = upKeyDebouncer,
                        repeatGate = dpadRepeatGate,
                        externalListState = viewModel.seriesRowState
                    )
                }
            }
        }
    }
    } // CompositionLocalProvider
}
