package com.lumera.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.lumera.app.R
import com.lumera.app.data.model.ProfileEntity
import com.lumera.app.data.model.WatchHistoryEntity
import com.lumera.app.data.model.stremio.MetaItem
import com.lumera.app.domain.DashboardTab
import com.lumera.app.domain.HeroConfig
import com.lumera.app.domain.HubGroupRow
import com.lumera.app.domain.CategoryRow
import com.lumera.app.domain.HomeRowItem
import com.lumera.app.domain.heroFor
import com.lumera.app.domain.layoutFor
import com.lumera.app.ui.components.LumeraBackground
import com.lumera.app.ui.components.LumeraCard
import kotlinx.coroutines.delay

private const val RAPID_VERTICAL_NAV_WINDOW_MS = 220L
private const val RAPID_PREVIEW_UPDATE_MIN_INTERVAL_MS = 120L
private const val DPAD_REPEAT_INTERVAL_HORIZONTAL_MS = 150L
private const val DPAD_REPEAT_INTERVAL_VERTICAL_MS = 200L

private class HomeFocusTimingTracker {
    var previous: Long = 0L
    var current: Long = 0L

    fun mark(now: Long = System.currentTimeMillis()) {
        previous = current
        current = now
    }

    fun isRapid(windowMs: Long): Boolean = current - previous in 1..windowMs
}

private class PreviewUpdateGate {
    var lastUpdateMs: Long = 0L
}

@Composable
fun HomeScreen(
    tab: DashboardTab,
    entryRequester: FocusRequester,
    drawerRequester: FocusRequester,
    viewModel: HomeViewModel = hiltViewModel(),
    currentProfile: ProfileEntity?,
    onMovieClick: (MetaItem) -> Unit,
    onViewMore: (String, List<MetaItem>, String) -> Unit = { _, _, _ -> }
) {
    val state by viewModel.state.collectAsState()
    val layoutMode = currentProfile?.layoutFor(tab) ?: "simple"
    val isTopNav = currentProfile?.navPosition == "top"
    val isLandscapeContinueWatching = currentProfile?.continueWatchingShape == "landscape"
    val infoTopPadding = if (isTopNav) 60.dp else 30.dp
    val startPadding = if (isTopNav) 50.dp else 120.dp

    val screenName = remember(tab) {
        when (tab) {
            DashboardTab.HOME -> "home"
            DashboardTab.MOVIES -> "movies"
            DashboardTab.SERIES -> "series"
        }
    }

    // DEFERMENT: Avoid rendering heavy rows during navigation transition
    var isTransitioning by remember { mutableStateOf(true) }
    LaunchedEffect(tab) {
        isTransitioning = true
        delay(250) // Wait for Drawer to close and Crossfade to finish
        isTransitioning = false
    }

    // During tab switch, ignore persisted focus/scroll until this tab is actually loaded.
    // This avoids one-frame carry-over from the previous tab.
    val isCurrentTabLoaded = state.loadedScreen == screenName && state.loadedProfileId == currentProfile?.id
    val lastFocusedKey = if (isCurrentTabLoaded) state.lastFocusedKey else null
    val rowScrollPositions = if (isCurrentTabLoaded) viewModel.getRowScrollPositions() else emptyMap()
    val verticalScrollPosition = if (isCurrentTabLoaded) viewModel.getVerticalScrollPosition() else Pair(0, 0)

    // Track if content has focus to conditionally enable BackHandler
    var isContentFocused by remember { mutableStateOf(false) }
    // Guards against double-back race: when returning from details, focus restoration
    // takes ~200ms. Until focus is established, keep BackHandler enabled so a quick
    // second back press doesn't exit the app. Resets on each fresh composition.
    var focusEverSet by remember { mutableStateOf(false) }

    // Only enable explicit back handling if:
    // 1. We are in Side-Nav mode (Always handle)
    // 2. We are in Top-Nav mode AND content is focused (Handle = Open Nav)
    // 3. Top-Nav mode AND focus not yet established (transition guard)
    // If Top-Nav mode AND content is NOT focused AND focus was already set, disable this
    // handler so TopNavigationBar's handler can "Close Nav" (return to content).
    BackHandler(enabled = !isTopNav || isContentFocused || !focusEverSet) {
        drawerRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onFocusChanged {
                isContentFocused = it.hasFocus
                if (it.hasFocus) focusEverSet = true
            }
    ) {
        LumeraBackground {
        CompositionLocalProvider(com.lumera.app.ui.components.LocalWatchedIds provides state.watchedIds) {
        // LOGIC: If we are just starting OR the ViewModel is loading, OR we are in transition, show the Loading Box.
        // This box accepts focus immediately, which forces the NavDrawer to collapse.
        if (isTransitioning || state.isLoading || state.loadedScreen != screenName || state.loadedProfileId != currentProfile?.id) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(entryRequester)
                    .focusable()
            ) {
            }
        } else {
            // DATA IS READY: Show Content
            val newEpisodesItems = remember(state.newEpisodesSeries, currentProfile?.homeNewEpisodesEnabled) {
                if (currentProfile?.homeNewEpisodesEnabled == true) {
                    state.newEpisodesSeries.map { entity ->
                        MetaItem(
                            id = entity.id,
                            type = entity.type,
                            name = entity.title,
                            poster = entity.poster,
                            hasNewEpisode = true
                        )
                    }
                } else {
                    emptyList()
                }
            }
            val hasInProgressHistory = remember(state.history, state.seriesNextUp) {
                state.history.any { !it.watched } || state.seriesNextUp.any { !it.isComplete }
            }
            val hasNewEpisodesRow = newEpisodesItems.isNotEmpty()
            val historyAdjustment = if (viewModel.needsHistoryScrollAdjustment(hasInProgressHistory)) 1 else 0
            val newEpisodesAdjustment = if (viewModel.needsHistoryScrollAdjustment(hasNewEpisodesRow)) 1 else 0
            val combinedAdjustment = historyAdjustment + newEpisodesAdjustment

            if (layoutMode == "cinematic") {
                CinematicLayout(
                    infoTopPadding = infoTopPadding,
                    startPadding = startPadding,
                    isTopNav = isTopNav,
                    state = state,
                    viewModel = viewModel,
                    onMovieClick = onMovieClick,
                    onViewMore = onViewMore,
                    onHubClick = { hubItem ->
                        viewModel.openHub(hubItem) { title, items ->
                            onViewMore(title, items, hubItem.categoryId)
                        }
                    },
                    onLoadMore = { configId -> viewModel.loadMoreItems(configId) },
                    entryRequester = entryRequester,
                    drawerRequester = drawerRequester,
                    lastFocusedKey = lastFocusedKey,
                    rowScrollPositions = rowScrollPositions,
                    verticalScrollPosition = verticalScrollPosition,
                    historyScrollAdjustment = combinedAdjustment,
                    onFocusChange = { viewModel.setLastFocusedKey(it) },
                    onScrollPositionChange = { key, pos -> viewModel.setRowScrollPosition(key, pos) },
                    onVerticalScrollChange = { viewModel.setVerticalScrollPosition(it, hasInProgressHistory || hasNewEpisodesRow) },
                    onPreviewItemVisible = { viewModel.ensureMetadataFallback(it); viewModel.ensureTmdbEnrichment(it) },
                    isLandscapeContinueWatching = isLandscapeContinueWatching,
                    newEpisodesItems = newEpisodesItems
                )
            } else {
                val heroConfig = currentProfile?.heroFor(tab) ?: HeroConfig(null, 10, 0)
                val heroItems = remember(state.heroRow, heroConfig.posterCount) {
                    state.heroRow?.items?.take(heroConfig.posterCount) ?: emptyList()
                }

                SimpleLayout(
                    startPadding = startPadding,
                    isTopNav = isTopNav,
                    state = state,
                    viewModel = viewModel,
                    heroItems = heroItems,
                    heroAutoScrollSeconds = heroConfig.autoScrollSeconds,
                    onMovieClick = onMovieClick,
                    onViewMore = onViewMore,
                    onHubClick = { hubItem ->
                        viewModel.openHub(hubItem) { title, items ->
                            onViewMore(title, items, hubItem.categoryId)
                        }
                    },
                    onLoadMore = { configId -> viewModel.loadMoreItems(configId) },
                    entryRequester = entryRequester,
                    drawerRequester = drawerRequester,
                    lastFocusedKey = lastFocusedKey,
                    rowScrollPositions = rowScrollPositions,
                    verticalScrollPosition = verticalScrollPosition,
                    historyScrollAdjustment = if (
                        viewModel.needsHistoryScrollAdjustment(hasInProgressHistory || hasNewEpisodesRow) &&
                        (heroItems.isEmpty() || verticalScrollPosition.first > 0)
                    ) combinedAdjustment else 0,
                    onFocusChange = { viewModel.setLastFocusedKey(it) },
                    onScrollPositionChange = { key, pos -> viewModel.setRowScrollPosition(key, pos) },
                    onVerticalScrollChange = { viewModel.setVerticalScrollPosition(it, hasInProgressHistory || hasNewEpisodesRow) },
                    onHeroItemVisible = { viewModel.ensureMetadataFallback(it); viewModel.ensureTmdbEnrichment(it) },
                    isLandscapeContinueWatching = isLandscapeContinueWatching,
                    newEpisodesItems = newEpisodesItems
                )
            }
        }
        } // CompositionLocalProvider
    }
}
}

@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CinematicLayout(
    infoTopPadding: androidx.compose.ui.unit.Dp,
    startPadding: androidx.compose.ui.unit.Dp,
    isTopNav: Boolean,
    state: HomeViewModel.HomeState,
    viewModel: HomeViewModel,
    onMovieClick: (MetaItem) -> Unit,
    onViewMore: (String, List<MetaItem>, String) -> Unit,
    onHubClick: (com.lumera.app.domain.HubItem) -> Unit,
    onLoadMore: (String) -> Unit,
    entryRequester: FocusRequester,
    drawerRequester: FocusRequester,
    lastFocusedKey: String?,
    rowScrollPositions: Map<String, Pair<Int, Int>>,
    verticalScrollPosition: Pair<Int, Int>,
    historyScrollAdjustment: Int,
    onFocusChange: (String) -> Unit,
    onScrollPositionChange: (String, Pair<Int, Int>) -> Unit,
    onVerticalScrollChange: (Pair<Int, Int>) -> Unit,
    onPreviewItemVisible: (MetaItem) -> Unit,
    isLandscapeContinueWatching: Boolean = false,
    newEpisodesItems: List<MetaItem> = emptyList()
) {
    var instantFocusItem by remember { mutableStateOf<MetaItem?>(null) }
    var displayedItem by remember { mutableStateOf<MetaItem?>(null) }
    var hasRequestedFocus by remember { mutableStateOf(false) }
    val previewUpdateGate = remember { PreviewUpdateGate() }


    // Proactive metadata fetch for continue watching cards (posters + landscape)
    LaunchedEffect(state.historyItems) {
        state.historyItems.take(15).forEach { item -> onPreviewItemVisible(item) }
    }

    // Redirect stale continue-watching focus key after progress was cleared
    val effectiveLastFocusedKey: String? = remember(lastFocusedKey, state.historyItems) {
        viewModel.resolveEffectiveFocusKey(lastFocusedKey, state.historyItems)
    }
    val historyFocusRedirected = effectiveLastFocusedKey != lastFocusedKey

    val restoredPreviewItem = remember(effectiveLastFocusedKey, state.mixedRows, state.historyItems) {
        viewModel.resolveCinematicPreviewItem(
            lastFocusedKey = effectiveLastFocusedKey,
            mixedRows = state.mixedRows,
            historyItems = state.historyItems
        )
    }
    val renderedPreviewItem: MetaItem? = remember(displayedItem, state.mixedRows, state.heroRow, state.historyItems, state.enrichedMeta) {
        viewModel.resolveLatestPreviewItem(
            current = displayedItem,
            state = state,
            historyItems = state.historyItems
        )
    }

    LaunchedEffect(state.rows, state.history, restoredPreviewItem, effectiveLastFocusedKey) {
        if (instantFocusItem == null) {
            val first: MetaItem? = when {
                restoredPreviewItem != null -> restoredPreviewItem
                // Returning from details with a saved focus key: avoid showing the wrong
                // poster while focus restoration is still in progress.
                effectiveLastFocusedKey != null -> null
                else -> state.historyItems.firstOrNull() ?: state.rows.firstOrNull()?.items?.firstOrNull()
            }
            instantFocusItem = first
            displayedItem = first
            if (first != null) {
                onPreviewItemVisible(first)
            }
        }

        // SMART FOCUS:
        // We are now safe to request focus because HomeScreen ensures we only reach here when data is ready.
        if (!hasRequestedFocus && (state.historyItems.isNotEmpty() || state.mixedRows.isNotEmpty())) {
            delay(100)
            entryRequester.requestFocus()
            hasRequestedFocus = true
        }
    }

    LaunchedEffect(instantFocusItem) {
        val target = instantFocusItem ?: return@LaunchedEffect
        delay(300)
        displayedItem = target
    }

    val density = LocalDensity.current
    // Adjust pivot with 40dp offset to prevent slide-up (5.dp content padding + 40.dp)
    val titleHeadroomPx = remember(density) { with(density) { 45.dp.toPx() } }
    
    // Skip vertical scroll on restoration (when we have a saved focus key)
    val isVerticalRestoration = effectiveLastFocusedKey != null
    var skipVerticalScroll by remember { mutableStateOf(isVerticalRestoration) }

    // Reset skip flag after restoration is complete
    LaunchedEffect(isVerticalRestoration) {
        if (isVerticalRestoration) {
            delay(300)
            skipVerticalScroll = false
        }
    }

    // Track focus timing without triggering recomposition on every focus hop.
    val verticalFocusTiming = remember { HomeFocusTimingTracker() }

    val verticalPivot = remember(titleHeadroomPx) {
        FocusPivotSpec(
            customOffset = titleHeadroomPx,
            skipScrollProvider = { skipVerticalScroll },
            stiffnessProvider = { Spring.StiffnessLow }
        )
    }
    
    // Wrap onFocusChange to track timing for vertical dynamic stiffness
    val wrappedOnFocusChange: (String) -> Unit = remember(onFocusChange) {
        { key ->
            verticalFocusTiming.mark()
            onFocusChange(key)
        }
    }

    fun updatePreviewItem(newItem: MetaItem?) {
        if (newItem == null) return
        onPreviewItemVisible(newItem)
        val now = System.currentTimeMillis()
        val isRapid = verticalFocusTiming.isRapid(RAPID_VERTICAL_NAV_WINDOW_MS)
        val allowUpdate = !isRapid || now - previewUpdateGate.lastUpdateMs >= RAPID_PREVIEW_UPDATE_MIN_INTERVAL_MS
        if (allowUpdate && (instantFocusItem?.id != newItem.id || instantFocusItem?.type != newItem.type)) {
            instantFocusItem = newItem
            previewUpdateGate.lastUpdateMs = now
        } else if (allowUpdate) {
            previewUpdateGate.lastUpdateMs = now
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Only show background when TMDB enrichment is done (or disabled) to prevent flash
        val bgItem: MetaItem? = if (!state.tmdbEnabled || renderedPreviewItem == null ||
            state.tmdbEnrichedIds.contains("${renderedPreviewItem.type}:${renderedPreviewItem.id}")) {
            renderedPreviewItem
        } else null
        CinematicBackground(bgItem)

        Column(modifier = Modifier.fillMaxSize().zIndex(2f)) {
            // INFO SECTION
            Box(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxWidth()
                    .padding(start = startPadding, top = infoTopPadding),
                contentAlignment = Alignment.TopStart
            ) {
                AnimatedContent(
                    targetState = renderedPreviewItem,
                    transitionSpec = { fadeIn(tween(400)).togetherWith(fadeOut(tween(200))) },
                    label = "Info"
                ) { item: MetaItem? ->
                    if (item != null) {
                        // Hide info content while waiting for TMDB enrichment
                        val itemReady = !state.tmdbEnabled || state.tmdbEnrichedIds.contains("${item.type}:${item.id}")
                        Column(modifier = Modifier.alpha(if (itemReady) 1f else 0f)) {
                            Box(
                                modifier = Modifier
                                    .width(700.dp)
                                    .height(90.dp),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                if (!item.logo.isNullOrEmpty()) {
                                    var isError by remember(item.logo) { mutableStateOf(false) }
                                    Box {
                                        AsyncImage(
                                            model = item.logo,
                                            contentDescription = item.name,
                                            contentScale = ContentScale.Fit,
                                            alignment = Alignment.BottomStart,
                                            onState = { coilState ->
                                                isError = coilState is coil.compose.AsyncImagePainter.State.Error
                                            },
                                            modifier = Modifier
                                                .widthIn(max = 500.dp)
                                                .heightIn(max = 90.dp)
                                        )
                                        if (isError) {
                                            Text(
                                                text = item.name,
                                                style = MaterialTheme.typography.displaySmall.copy(
                                                    fontWeight = FontWeight.ExtraBold,
                                                    fontSize = 32.sp,
                                                    lineHeight = 1.05.em
                                                ),
                                                color = Color.White,
                                                maxLines = 2,
                                                softWrap = true,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                } else {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.displaySmall.copy(
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 32.sp,
                                            lineHeight = 1.05.em
                                        ),
                                        color = Color.White,
                                        maxLines = 2,
                                        softWrap = true,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            HomeMetaStrip(item = item)
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = item.description ?: "",
                                maxLines = if (isTopNav) 3 else 4,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, letterSpacing = 0.sp, lineHeight = 1.2.em),
                                color = Color.White.copy(0.85f),
                                textAlign = TextAlign.Start,
                                modifier = Modifier.width(450.dp)
                            )
                        }
                    }
                }
            }

            // LIST SECTION
            Box(modifier = Modifier.weight(0.55f)) {
                CompositionLocalProvider(LocalBringIntoViewSpec provides verticalPivot) {
                    // Use persisted vertical scroll position for instant restoration.
                    // When the continue watching row first appears at index 0, all saved
                    // indices are off by 1 — adjust the initial index to compensate.
                    val verticalListState = rememberLazyListState(
                        initialFirstVisibleItemIndex = verticalScrollPosition.first + historyScrollAdjustment,
                        initialFirstVisibleItemScrollOffset = verticalScrollPosition.second
                    )

                    // Global UP key debouncer to prevent accidental navbar navigation
                    val upKeyDebouncer = remember { UpKeyDebouncer() }
                    val dpadRepeatGate = remember {
                        DpadRepeatGate(
                            horizontalRepeatIntervalMs = DPAD_REPEAT_INTERVAL_HORIZONTAL_MS,
                            verticalRepeatIntervalMs = DPAD_REPEAT_INTERVAL_VERTICAL_MS
                        )
                    }

                    PersistLazyListPosition(
                        listState = verticalListState,
                        key = "cinematic_vertical",
                        minOffsetDeltaPx = 36,
                        onPositionChanged = onVerticalScrollChange
                    )

                    // Pre-scroll warmup: compose off-screen row to populate recycler + compile GPU shaders
                    LaunchedEffect(Unit) {
                        withFrameNanos { }
                        val idx = verticalListState.firstVisibleItemIndex
                        val off = verticalListState.firstVisibleItemScrollOffset
                        verticalListState.scrollToItem(idx + 1)
                        verticalListState.scrollToItem(idx, off)
                    }

                    LazyColumn(
                        state = verticalListState,
                        modifier = Modifier
                            .fillMaxSize(), // Focus managed by items
                        contentPadding = PaddingValues(top = 5.dp, bottom = 400.dp),
                        verticalArrangement = Arrangement.spacedBy((-12).dp)
                    ) {
                        if (state.historyItems.isNotEmpty()) {
                            item(key = "history_header", contentType = "row") {
                                // Reset scroll position when focus was redirected (cleared item removed)
                                val savedPosition = if (historyFocusRedirected) Pair(0, 0)
                                    else rowScrollPositions["history"] ?: Pair(0, 0)
                                val historyRowState = rememberLazyListState(
                                    initialFirstVisibleItemIndex = savedPosition.first,
                                    initialFirstVisibleItemScrollOffset = savedPosition.second
                                )

                                PersistLazyListPosition(
                                    listState = historyRowState,
                                    key = "cinematic_history",
                                    minOffsetDeltaPx = 36,
                                    onPositionChanged = { onScrollPositionChange("history", it) }
                                )

                                InfiniteLoopRow(
                                    startPadding = startPadding,
                                    isTopNav = isTopNav,
                                    rowIndex = -1,
                                    title = "Continue Watching",
                                    items = state.historyItems,
                                    onMovieClick = onMovieClick,
                                    onViewMore = { onViewMore("Continue Watching", state.historyItems, "") },
                                    onFocused = remember(wrappedOnFocusChange) {
                                        { item: MetaItem?, key: String ->
                                            updatePreviewItem(item)
                                            wrappedOnFocusChange(key)
                                        }
                                    },
                                    entryRequester = entryRequester,
                                    drawerRequester = drawerRequester,
                                    locallyFocusedItemId = if (effectiveLastFocusedKey?.startsWith("-1_") == true) effectiveLastFocusedKey else null,
                                    isGlobalFocusPresent = effectiveLastFocusedKey != null,
                                    isFirstRow = true,
                                    isInfiniteLoopEnabled = false,
                                    visibleItemCount = 15,
                                    isInfiniteScrollingEnabled = true,
                                    externalListState = historyRowState,
                                    upKeyDebouncer = upKeyDebouncer,
                                    repeatGate = dpadRepeatGate,
                                    isLandscapeCards = isLandscapeContinueWatching,
                                    enrichedItems = state.enrichedMeta,
                                    rowHeight = if (isLandscapeContinueWatching) 140.dp else 210.dp
                                )
                            }
                        }

                        if (newEpisodesItems.isNotEmpty()) {
                            item(key = "new_episodes_header", contentType = "row") {
                                val savedPosition = rowScrollPositions["new_episodes"] ?: Pair(0, 0)
                                val newEpisodesRowState = rememberLazyListState(
                                    initialFirstVisibleItemIndex = savedPosition.first,
                                    initialFirstVisibleItemScrollOffset = savedPosition.second
                                )

                                PersistLazyListPosition(
                                    listState = newEpisodesRowState,
                                    key = "cinematic_new_episodes",
                                    minOffsetDeltaPx = 36,
                                    onPositionChanged = { onScrollPositionChange("new_episodes", it) }
                                )

                                val rowTitle = stringResource(id = R.string.home_new_episodes)
                                val badgeText = stringResource(id = R.string.badge_new)

                                InfiniteLoopRow(
                                    startPadding = startPadding,
                                    isTopNav = isTopNav,
                                    rowIndex = -2,
                                    title = rowTitle,
                                    items = newEpisodesItems,
                                    onMovieClick = onMovieClick,
                                    onViewMore = { onViewMore(rowTitle, newEpisodesItems, "") },
                                    onFocused = remember(wrappedOnFocusChange) {
                                        { item: MetaItem?, key: String ->
                                            updatePreviewItem(item)
                                            wrappedOnFocusChange(key)
                                        }
                                    },
                                    entryRequester = entryRequester,
                                    drawerRequester = drawerRequester,
                                    locallyFocusedItemId = if (effectiveLastFocusedKey?.startsWith("-2_") == true) effectiveLastFocusedKey else null,
                                    isGlobalFocusPresent = effectiveLastFocusedKey != null,
                                    isFirstRow = state.historyItems.isEmpty(),
                                    isInfiniteLoopEnabled = false,
                                    visibleItemCount = 15,
                                    isInfiniteScrollingEnabled = true,
                                    externalListState = newEpisodesRowState,
                                    upKeyDebouncer = upKeyDebouncer,
                                    repeatGate = dpadRepeatGate,
                                    isLandscapeCards = false,
                                    enrichedItems = state.enrichedMeta,
                                    badgeText = badgeText
                                )
                            }
                        }

                        itemsIndexed(
                            items = state.mixedRows,
                            key = { _, item -> 
                                when(item) {
                                    is HubGroupRow -> "hub_row_${item.id}"
                                    is CategoryRow -> "row_${item.id}"
                                    else -> "unknown_${item.id}"
                                }
                            },
                            contentType = { _, item -> 
                                when(item) {
                                    is HubGroupRow -> "hub_row"
                                    is CategoryRow -> "row"
                                    else -> "unknown"
                                }
                            }
                        ) { index, item ->
                            when (item) {
                                is HubGroupRow -> {

                                    // Create list state with saved scroll position for this hub row
                                    // Reset when focus was redirected from a removed history item to this first row
                                    val hubKey = "hub_${item.id}"
                                    val resetScroll = historyFocusRedirected && state.historyItems.isEmpty() && index == 0
                                    val savedHubPosition = if (resetScroll) null else rowScrollPositions[hubKey]
                                    val hubListState = rememberLazyListState(
                                        initialFirstVisibleItemIndex = savedHubPosition?.first ?: 0,
                                        initialFirstVisibleItemScrollOffset = savedHubPosition?.second ?: 0
                                    )

                                    PersistLazyListPosition(
                                        listState = hubListState,
                                        key = "cinematic_$hubKey",
                                        minOffsetDeltaPx = 36,
                                        onPositionChanged = { onScrollPositionChange(hubKey, it) }
                                    )

                                    HubRow(
                                        hubGroup = item,
                                        startPadding = startPadding,
                                        onHubClick = onHubClick,
                                        onFocused = { _, focusKey ->
                                            instantFocusItem = null
                                            wrappedOnFocusChange("hub_$focusKey")
                                        },
                                        entryRequester = entryRequester,
                                        drawerRequester = drawerRequester,
                                        locallyFocusedItemId = if (effectiveLastFocusedKey?.startsWith("hub_") == true) effectiveLastFocusedKey else null,
                                        isTopNav = isTopNav,
                                        rowIndex = index,
                                        externalListState = hubListState,
                                        upKeyDebouncer = upKeyDebouncer,
                                        repeatGate = dpadRepeatGate,
                                        isLastRow = index == state.mixedRows.lastIndex,
                                        isFirstRow = state.historyItems.isEmpty() && newEpisodesItems.isEmpty() && index == 0,
                                        isGlobalFocusPresent = effectiveLastFocusedKey != null
                                    )
                                }
                                is CategoryRow -> {
                                    // Create list state with saved scroll position for this row
                                    // Reset when focus was redirected from a removed history item to this first row
                                    val rowKey = item.id
                                    val resetScroll = historyFocusRedirected && state.historyItems.isEmpty() && index == 0
                                    val savedPosition = if (resetScroll) null else rowScrollPositions[rowKey]

                                    val initialIndex = savedPosition?.first ?: 0
                                    val initialOffset = savedPosition?.second ?: 0

                                    val rowListState = rememberLazyListState(
                                        initialFirstVisibleItemIndex = initialIndex,
                                        initialFirstVisibleItemScrollOffset = initialOffset
                                    )

                                    PersistLazyListPosition(
                                        listState = rowListState,
                                        key = "cinematic_$rowKey",
                                        minOffsetDeltaPx = 36,
                                        onPositionChanged = { onScrollPositionChange(rowKey, it) }
                                    )
                                    
                                    // Lazy pagination: load more items when scrolling near the end
                                    // Use snapshotFlow to avoid reading layoutInfo during composition
                                    val itemId = item.id
                                    val itemSize = item.items.size
                                    LaunchedEffect(rowListState, itemId) {
                                        snapshotFlow {
                                            rowListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                        }.collect { lastVisibleIndex ->
                                            if (itemSize > 0 && lastVisibleIndex >= itemSize - 10) {
                                                onLoadMore(itemId)
                                            }
                                        }
                                    }

                                    InfiniteLoopRow(
                                        startPadding = startPadding,
                                        isTopNav = isTopNav,
                                        rowIndex = index,
                                        title = item.title,
                                        items = item.items,
                                        onMovieClick = onMovieClick,
                                        onViewMore = remember(item.title, item.items, item.id, onViewMore) {
                                            { onViewMore(item.title, item.items, item.id) }
                                        },
                                        onFocused = remember(wrappedOnFocusChange) {
                                            { focusItem: MetaItem?, key: String ->
                                                updatePreviewItem(focusItem)
                                                wrappedOnFocusChange(key)
                                            }
                                        },
                                        entryRequester = entryRequester,
                                        drawerRequester = drawerRequester,
                                        locallyFocusedItemId = if (effectiveLastFocusedKey?.startsWith("${index}_") == true) effectiveLastFocusedKey else null,
                                        isGlobalFocusPresent = effectiveLastFocusedKey != null,
                                        isFirstRow = state.historyItems.isEmpty() && newEpisodesItems.isEmpty() && index == 0,
                                        isInfiniteLoopEnabled = item.isInfiniteLoopEnabled,
                                        visibleItemCount = item.visibleItemCount,
                                        isInfiniteScrollingEnabled = item.isInfiniteScrollingEnabled,
                                        externalListState = rowListState,
                                        upKeyDebouncer = upKeyDebouncer,
                                        repeatGate = dpadRepeatGate
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}



@OptIn(ExperimentalTvMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SimpleLayout(
    startPadding: androidx.compose.ui.unit.Dp,
    isTopNav: Boolean,
    state: HomeViewModel.HomeState,
    viewModel: HomeViewModel,
    heroItems: List<MetaItem> = emptyList(),
    heroAutoScrollSeconds: Int = 0,
    onMovieClick: (MetaItem) -> Unit,
    onViewMore: (String, List<MetaItem>, String) -> Unit,
    onHubClick: (com.lumera.app.domain.HubItem) -> Unit,
    onLoadMore: (String) -> Unit,
    entryRequester: FocusRequester,
    drawerRequester: FocusRequester,
    lastFocusedKey: String?,
    rowScrollPositions: Map<String, Pair<Int, Int>>,
    verticalScrollPosition: Pair<Int, Int>,
    historyScrollAdjustment: Int,
    onFocusChange: (String) -> Unit,
    onScrollPositionChange: (String, Pair<Int, Int>) -> Unit,
    onVerticalScrollChange: (Pair<Int, Int>) -> Unit,
    onHeroItemVisible: (MetaItem) -> Unit,
    isLandscapeContinueWatching: Boolean = false,
    newEpisodesItems: List<MetaItem> = emptyList()
) {
    var hasRequestedFocus by remember { mutableStateOf(false) }
    // Proactive metadata fetch for continue watching cards (posters + landscape)
    LaunchedEffect(state.historyItems) {
        state.historyItems.take(15).forEach { item -> onHeroItemVisible(item) }
    }

    // Redirect stale continue-watching focus key after progress was cleared
    val effectiveLastFocusedKey: String? = remember(lastFocusedKey, state.historyItems) {
        viewModel.resolveEffectiveFocusKey(lastFocusedKey, state.historyItems)
    }
    val historyFocusRedirected = effectiveLastFocusedKey != lastFocusedKey

    // Global UP key debouncer to prevent accidental navbar navigation
    val upKeyDebouncer = remember { UpKeyDebouncer() }
    val dpadRepeatGate = remember {
        DpadRepeatGate(
            horizontalRepeatIntervalMs = DPAD_REPEAT_INTERVAL_HORIZONTAL_MS,
            verticalRepeatIntervalMs = DPAD_REPEAT_INTERVAL_VERTICAL_MS
        )
    }
    // Focus requester for the first row's pivot item (used by hero carousel DOWN key)
    val firstRowPivotRequester = remember { FocusRequester() }

    LaunchedEffect(state.rows, state.historyItems) {
        if (!hasRequestedFocus && (state.rows.isNotEmpty() || state.historyItems.isNotEmpty())) {
            delay(100)
            entryRequester.requestFocus()
            hasRequestedFocus = true
        }
    }

    // Smooth vertical scrolling pivot for Simple layout
    val density = LocalDensity.current

    // Skip vertical scroll on restoration (when we have a saved focus key)
    val isVerticalRestoration = effectiveLastFocusedKey != null
    var skipVerticalScroll by remember { mutableStateOf(isVerticalRestoration) }

    // Reset skip flag after restoration is complete
    LaunchedEffect(isVerticalRestoration) {
        if (isVerticalRestoration) {
            delay(300)
            skipVerticalScroll = false
        }
    }

    val verticalPivotPx = remember(density, isTopNav) { with(density) { (if (isTopNav) 91.dp else 71.dp).toPx() } }

    val verticalPivot = remember(verticalPivotPx) {
        FocusPivotSpec(
            customOffset = verticalPivotPx,
            skipScrollProvider = { skipVerticalScroll },
            stiffnessProvider = { Spring.StiffnessLow }
        )
    }

    // Wrap onFocusChange to track timing for vertical dynamic stiffness
    val wrappedOnFocusChange: (String) -> Unit = remember(onFocusChange) {
        { key ->
            onFocusChange(key)
        }
    }
    
    CompositionLocalProvider(LocalBringIntoViewSpec provides verticalPivot) {
        // Use persisted vertical scroll position for instant restoration.
        // When the continue watching row first appears at index 0, all saved
        // indices are off by 1 — adjust the initial index to compensate.
        val verticalListState = rememberLazyListState(
            initialFirstVisibleItemIndex = verticalScrollPosition.first + historyScrollAdjustment,
            initialFirstVisibleItemScrollOffset = verticalScrollPosition.second
        )

        PersistLazyListPosition(
            listState = verticalListState,
            key = "simple_vertical",
            minOffsetDeltaPx = 36,
            onPositionChanged = onVerticalScrollChange
        )

        // Pre-scroll warmup: compose off-screen row to populate recycler + compile GPU shaders
        LaunchedEffect(Unit) {
            withFrameNanos { }
            val idx = verticalListState.firstVisibleItemIndex
            val off = verticalListState.firstVisibleItemScrollOffset
            verticalListState.scrollToItem(idx + 1)
            verticalListState.scrollToItem(idx, off)
        }

        LazyColumn(
            state = verticalListState,
            modifier = Modifier.fillMaxSize(), // Focus managed by items
            contentPadding = PaddingValues(top = if (heroItems.isNotEmpty()) 0.dp else if (isTopNav) 60.dp else 40.dp, bottom = 0.dp),
            verticalArrangement = Arrangement.spacedBy(15.dp)
        ) {
            if (heroItems.isNotEmpty()) {
                item(key = "hero_carousel") {
                    HeroCarousel(
                        items = heroItems,
                        autoScrollSeconds = heroAutoScrollSeconds,
                        onItemClick = onMovieClick,
                        startPadding = startPadding,
                        onFocusChange = { wrappedOnFocusChange("hero_$it") },
                        onCurrentItemVisible = onHeroItemVisible,
                        entryRequester = if (effectiveLastFocusedKey == null || effectiveLastFocusedKey.startsWith("hero_")) entryRequester else null,
                        drawerRequester = drawerRequester,
                        isFirstItem = true,
                        isTopNav = isTopNav,
                        upKeyDebouncer = upKeyDebouncer,
                        repeatGate = dpadRepeatGate,
                        onNavigateDown = { firstRowPivotRequester.requestFocus() },
                        restoreItemId = if (effectiveLastFocusedKey?.startsWith("hero_") == true) effectiveLastFocusedKey.removePrefix("hero_") else null,
                        tmdbEnabled = state.tmdbEnabled,
                        tmdbEnrichedIds = state.tmdbEnrichedIds
                    )
                }
            }

            if (state.historyItems.isNotEmpty()) {
                item(key = "simple_history_header", contentType = "row") {
                    // Reset scroll position when focus was redirected (cleared item removed)
                    val savedPosition = if (historyFocusRedirected) Pair(0, 0)
                        else rowScrollPositions["history"] ?: Pair(0, 0)
                    val historyRowState = rememberLazyListState(
                        initialFirstVisibleItemIndex = savedPosition.first,
                        initialFirstVisibleItemScrollOffset = savedPosition.second
                    )

                    PersistLazyListPosition(
                        listState = historyRowState,
                        key = "simple_history",
                        minOffsetDeltaPx = 36,
                        onPositionChanged = { onScrollPositionChange("history", it) }
                    )

                    InfiniteLoopRow(
                        startPadding = startPadding,
                        isTopNav = isTopNav,
                        rowIndex = -1,
                        title = "Continue Watching",
                        items = state.historyItems,
                        onMovieClick = onMovieClick,
                        onViewMore = { onViewMore("Continue Watching", state.historyItems, "") },
                        onFocused = remember(wrappedOnFocusChange) {
                            { _: MetaItem?, key: String -> wrappedOnFocusChange(key) }
                        },
                        entryRequester = entryRequester,
                        drawerRequester = drawerRequester,
                        locallyFocusedItemId = if (effectiveLastFocusedKey?.startsWith("-1_") == true) effectiveLastFocusedKey else null,
                        isGlobalFocusPresent = effectiveLastFocusedKey != null,
                        isFirstRow = heroItems.isEmpty(),
                        isInfiniteLoopEnabled = false,
                        visibleItemCount = 15,
                        isInfiniteScrollingEnabled = true,
                        externalListState = historyRowState,
                        upKeyDebouncer = upKeyDebouncer,
                        repeatGate = dpadRepeatGate,
                        pivotFocusRequester = if (heroItems.isNotEmpty()) firstRowPivotRequester else null,
                        isLandscapeCards = isLandscapeContinueWatching,
                        enrichedItems = state.enrichedMeta,
                        rowHeight = if (isLandscapeContinueWatching) 140.dp else 210.dp
                    )
                }
            }

            if (newEpisodesItems.isNotEmpty()) {
                item(key = "simple_new_episodes_header", contentType = "row") {
                    val savedPosition = rowScrollPositions["new_episodes"] ?: Pair(0, 0)
                    val newEpisodesRowState = rememberLazyListState(
                        initialFirstVisibleItemIndex = savedPosition.first,
                        initialFirstVisibleItemScrollOffset = savedPosition.second
                    )

                    PersistLazyListPosition(
                        listState = newEpisodesRowState,
                        key = "simple_new_episodes",
                        minOffsetDeltaPx = 36,
                        onPositionChanged = { onScrollPositionChange("new_episodes", it) }
                    )

                    val rowTitle = stringResource(id = R.string.home_new_episodes)
                    val badgeText = stringResource(id = R.string.badge_new)

                    InfiniteLoopRow(
                        startPadding = startPadding,
                        isTopNav = isTopNav,
                        rowIndex = -2,
                        title = rowTitle,
                        items = newEpisodesItems,
                        onMovieClick = onMovieClick,
                        onViewMore = { onViewMore(rowTitle, newEpisodesItems, "") },
                        onFocused = remember(wrappedOnFocusChange) {
                            { _: MetaItem?, key: String -> wrappedOnFocusChange(key) }
                        },
                        entryRequester = entryRequester,
                        drawerRequester = drawerRequester,
                        locallyFocusedItemId = if (effectiveLastFocusedKey?.startsWith("-2_") == true) effectiveLastFocusedKey else null,
                        isGlobalFocusPresent = effectiveLastFocusedKey != null,
                        isFirstRow = heroItems.isEmpty() && state.historyItems.isEmpty(),
                        isInfiniteLoopEnabled = false,
                        visibleItemCount = 15,
                        isInfiniteScrollingEnabled = true,
                        externalListState = newEpisodesRowState,
                        upKeyDebouncer = upKeyDebouncer,
                        repeatGate = dpadRepeatGate,
                        pivotFocusRequester = if (heroItems.isNotEmpty() && state.historyItems.isEmpty()) firstRowPivotRequester else null,
                        isLandscapeCards = false,
                        enrichedItems = state.enrichedMeta,
                        badgeText = badgeText
                    )
                }
            }

            itemsIndexed(
                items = state.mixedRows,
                key = { _, item -> 
                    when(item) {
                        is HubGroupRow -> "simple_hub_${item.id}"
                        is CategoryRow -> "simple_row_${item.id}"
                        else -> "simple_unknown_${item.id}"
                    }
                },
                contentType = { _, item -> 
                    when(item) {
                        is HubGroupRow -> "hub_row"
                        is CategoryRow -> "row"
                        else -> "unknown"
                    }
                }
            ) { rowIndex, item ->
                when (item) {
                    is HubGroupRow -> {

                        // Create list state with saved scroll position for this hub row
                        // Reset when focus was redirected from a removed history item to this first row
                        val hubKey = "hub_${item.id}"
                        val resetScroll = historyFocusRedirected && state.historyItems.isEmpty() && rowIndex == 0
                        val savedHubPosition = if (resetScroll) null else rowScrollPositions[hubKey]
                        val hubListState = rememberLazyListState(
                            initialFirstVisibleItemIndex = savedHubPosition?.first ?: 0,
                            initialFirstVisibleItemScrollOffset = savedHubPosition?.second ?: 0
                        )

                        PersistLazyListPosition(
                            listState = hubListState,
                            key = "simple_$hubKey",
                            minOffsetDeltaPx = 36,
                            onPositionChanged = { onScrollPositionChange(hubKey, it) }
                        )

                        HubRow(
                            hubGroup = item,
                            startPadding = startPadding,
                            onHubClick = onHubClick,
                            onFocused = { _, focusKey ->
                                wrappedOnFocusChange("hub_$focusKey")
                            },
                            entryRequester = entryRequester,
                            drawerRequester = drawerRequester,
                            locallyFocusedItemId = if (effectiveLastFocusedKey?.startsWith("hub_") == true) effectiveLastFocusedKey else null,
                            isTopNav = isTopNav,
                            rowIndex = rowIndex,
                            isFirstRow = heroItems.isEmpty() && state.historyItems.isEmpty() && newEpisodesItems.isEmpty() && rowIndex == 0, // Only steal focus if no hero/history/new episodes row
                            externalListState = hubListState,
                            upKeyDebouncer = upKeyDebouncer,
                            repeatGate = dpadRepeatGate,
                            isLastRow = rowIndex == state.mixedRows.lastIndex,
                            pivotFocusRequester = if (heroItems.isNotEmpty() && state.historyItems.isEmpty() && newEpisodesItems.isEmpty() && rowIndex == 0) firstRowPivotRequester else null,
                            isGlobalFocusPresent = effectiveLastFocusedKey != null
                        )
                    }
                    is CategoryRow -> {
                        // Create list state with saved scroll position for this row
                        // Reset when focus was redirected from a removed history item to this first row
                        val rowKey = item.id
                        val resetScroll = historyFocusRedirected && state.historyItems.isEmpty() && rowIndex == 0
                        val savedPosition = if (resetScroll) null else rowScrollPositions[rowKey]

                        val initialIndex = savedPosition?.first ?: 0
                        val initialOffset = savedPosition?.second ?: 0

                        val rowListState = rememberLazyListState(
                            initialFirstVisibleItemIndex = initialIndex,
                            initialFirstVisibleItemScrollOffset = initialOffset
                        )

                        PersistLazyListPosition(
                            listState = rowListState,
                            key = "simple_$rowKey",
                            minOffsetDeltaPx = 36,
                            onPositionChanged = { onScrollPositionChange(rowKey, it) }
                        )
                        
                        // Lazy pagination: load more items when scrolling near the end
                        // Use snapshotFlow to avoid reading layoutInfo during composition
                        val itemId = item.id
                        val itemSize = item.items.size
                        LaunchedEffect(rowListState, itemId) {
                            snapshotFlow {
                                rowListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            }.collect { lastVisibleIndex ->
                                if (itemSize > 0 && lastVisibleIndex >= itemSize - 10) {
                                    onLoadMore(itemId)
                                }
                            }
                        }
                        
                        // Custom row height: 193dp for last row in Top Nav, 210dp standard
                        val rowHeight = if (isTopNav && rowIndex == state.mixedRows.lastIndex) 193.dp else 210.dp

                        // STABILIZE LAMBDAS:
                        // These specific lambda instances must remain referentially equal
                        // across recompositions to allow InfiniteLoopRow to skip logic.
                        val onRowViewMore = remember(item.items, item.title, item.id, onViewMore) {
                            { onViewMore(item.title, item.items, item.id) }
                        }
                        
                        val onRowFocused = remember(wrappedOnFocusChange) {
                            { _: MetaItem?, key: String -> wrappedOnFocusChange(key) }
                        }

                        InfiniteLoopRow(
                            startPadding = startPadding,
                            isTopNav = isTopNav,
                            rowIndex = rowIndex,
                            title = item.title,
                            items = item.items,
                            onMovieClick = onMovieClick,
                            onViewMore = onRowViewMore,
                            onFocused = onRowFocused,
                            entryRequester = entryRequester,
                            drawerRequester = drawerRequester,
                            locallyFocusedItemId = if (effectiveLastFocusedKey?.startsWith("${rowIndex}_") == true) effectiveLastFocusedKey else null,
                            isGlobalFocusPresent = effectiveLastFocusedKey != null,
                            isFirstRow = heroItems.isEmpty() && state.historyItems.isEmpty() && newEpisodesItems.isEmpty() && rowIndex == 0,
                            isInfiniteLoopEnabled = item.isInfiniteLoopEnabled,
                            visibleItemCount = item.visibleItemCount,
                            isInfiniteScrollingEnabled = item.isInfiniteScrollingEnabled,
                            externalListState = rowListState,
                            rowHeight = rowHeight,
                            upKeyDebouncer = upKeyDebouncer,
                            repeatGate = dpadRepeatGate,
                            pivotFocusRequester = if (heroItems.isNotEmpty() && state.historyItems.isEmpty() && newEpisodesItems.isEmpty() && rowIndex == 0) firstRowPivotRequester else null
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PersistLazyListPosition(
    listState: androidx.compose.foundation.lazy.LazyListState,
    key: String,
    minOffsetDeltaPx: Int = 32,
    onPositionChanged: (Pair<Int, Int>) -> Unit
) {
    var lastIndex by remember(key, listState) { mutableIntStateOf(Int.MIN_VALUE) }
    var lastOffset by remember(key, listState) { mutableIntStateOf(Int.MIN_VALUE) }

    LaunchedEffect(key, listState) {
        snapshotFlow {
            Pair(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }.collect { (index, offset) ->
            val shouldEmit =
                lastIndex == Int.MIN_VALUE ||
                    index != lastIndex ||
                    kotlin.math.abs(offset - lastOffset) >= minOffsetDeltaPx

            if (shouldEmit) {
                lastIndex = index
                lastOffset = offset
                onPositionChanged(Pair(index, offset))
            }
        }
    }

    DisposableEffect(key, listState) {
        onDispose {
            val finalIndex = listState.firstVisibleItemIndex
            val finalOffset = listState.firstVisibleItemScrollOffset
            if (finalIndex != lastIndex || finalOffset != lastOffset) {
                onPositionChanged(Pair(finalIndex, finalOffset))
            }
        }
    }
}

@Composable
fun CinematicBackground(item: MetaItem?) {
    // Use the theme's actual background color
    val backgroundColor = androidx.compose.material3.MaterialTheme.colorScheme.background
    
    // Only fade LEFT and BOTTOM edges - top/right are at screen edge
    val leftFade = Brush.horizontalGradient(
        colorStops = arrayOf(
            0.0f to backgroundColor,
            0.1f to backgroundColor.copy(0.95f),
            0.2f to backgroundColor.copy(0.85f),
            0.3f to backgroundColor.copy(0.72f),
            0.4f to backgroundColor.copy(0.55f),
            0.55f to backgroundColor.copy(0.35f),
            0.7f to backgroundColor.copy(0.18f),
            0.85f to backgroundColor.copy(0.07f),
            1.0f to Color.Transparent
        ),
        startX = 0f,
        endX = 500f
    )
    
    // Bottom fade: blend into the rows area
    val bottomFade = Brush.verticalGradient(
        colorStops = arrayOf(
            0.0f to Color.Transparent,
            0.2f to backgroundColor.copy(0.05f),
            0.35f to backgroundColor.copy(0.15f),
            0.45f to backgroundColor.copy(0.25f),
            0.55f to backgroundColor.copy(0.38f),
            0.65f to backgroundColor.copy(0.52f),
            0.75f to backgroundColor.copy(0.68f),
            0.85f to backgroundColor.copy(0.82f),
            0.92f to backgroundColor.copy(0.92f),
            1.0f to backgroundColor
        )
    )

    Box(modifier = Modifier.fillMaxSize().zIndex(0f)) {
        Box(modifier = Modifier.align(Alignment.TopEnd).fillMaxWidth(0.65f).fillMaxHeight(0.65f)) {
            Crossfade(
                targetState = item, 
                animationSpec = tween(700), 
                label = "HeroBg"
            ) { currentItem ->
                if (currentItem != null) {
                    val image = currentItem.background ?: currentItem.poster
                    val context = LocalContext.current
                    val imageRequest = remember(image) {
                        ImageRequest.Builder(context)
                            .data(image)
                            .crossfade(false)
                            .size(1920, 1080)
                            .build()
                    }

                    val scale = remember(currentItem) { Animatable(1.0f) }
                    LaunchedEffect(currentItem) {
                        scale.animateTo(
                            targetValue = 1.05f,
                            animationSpec = tween(
                                durationMillis = 8000,
                                easing = LinearOutSlowInEasing
                            )
                        )
                    }

                    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale.value,
                                    scaleY = scale.value
                                )
                        )
                        // Left and bottom edge fades only
                        Spacer(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithCache {
                                    onDrawWithContent {
                                        drawRect(brush = leftFade)
                                        drawRect(brush = bottomFade)
                                    }
                                }
                        )
                        com.lumera.app.ui.components.NoiseOverlay()
                    }
                }
            }
        }
    }
}


@Composable
fun Badge(text: String, color: Color, textColor: Color) {
    Text(text = text, color = textColor, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), modifier = Modifier.background(color, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp, vertical = 4.dp))
}

@Composable
private fun HomeMetaStrip(item: MetaItem) {
    val typeLabel = item.type.replaceFirstChar { it.uppercase() }
    val genreLabel = item.genres
        ?.firstOrNull()
        ?.replaceFirstChar { it.uppercase() }
        ?: "Unknown"
    val yearLabel = extractHomePrimaryYear(item.releaseInfo)
    val valueColor = Color.White.copy(alpha = 0.92f)
    val textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = typeLabel, style = textStyle, color = valueColor)
        HomeMetaDot()
        Text(text = genreLabel, style = textStyle, color = valueColor)
        HomeMetaDot()
        Text(text = yearLabel, style = textStyle, color = valueColor)

        item.runtime?.filter { it.isDigit() }?.toIntOrNull()?.let { mins ->
            HomeMetaDot()
            val hours = mins / 60
            val m = mins % 60
            val display = if (hours > 0) "${hours}h ${m}m" else "${m}m"
            Text(text = display, style = textStyle, color = valueColor)
        }

        item.imdbRating?.takeIf { it.isNotBlank() }?.let { rating ->
            HomeMetaDot()
            HomeImdbBadge()
            Text(text = rating, style = textStyle, color = valueColor)
        }
    }
}

@Composable
private fun HomeMetaDot() {
    Text(
        text = ".",
        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
        color = Color.White.copy(alpha = 0.55f)
    )
}

@Composable
private fun HomeImdbBadge() {
    Image(
        painter = painterResource(id = R.drawable.imdb_logo),
        contentDescription = "IMDb",
        modifier = Modifier.height(16.dp)
    )
}

private fun extractHomePrimaryYear(releaseInfo: String?): String {
    if (releaseInfo.isNullOrBlank()) return "----"
    val year = releaseInfo.take(4)
    return if (year.all { it.isDigit() }) year else "----"
}
