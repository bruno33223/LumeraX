package com.lumera.app.ui.home

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumera.app.data.local.AddonDao
import com.lumera.app.data.model.WatchHistoryEntity
import com.lumera.app.data.model.WatchlistEntity
import com.lumera.app.data.repository.AddonRepository
import com.lumera.app.data.tmdb.TmdbMetadataService
import com.lumera.app.data.tmdb.TmdbService
import com.lumera.app.domain.HomeRow
import com.lumera.app.domain.HubGroupRow
import com.lumera.app.ui.utils.ImagePrefetcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.lumera.app.domain.HomeRowItem
import com.lumera.app.domain.CategoryRow
import com.lumera.app.data.model.stremio.MetaItem
import com.lumera.app.domain.DashboardTab
import com.lumera.app.domain.heroFor

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: AddonRepository,
    private val dao: AddonDao,
    @ApplicationContext private val context: Context,
    private val tmdbService: TmdbService,
    private val tmdbMetadataService: TmdbMetadataService
) : ViewModel() {

    private var loadJob: kotlinx.coroutines.Job? = null
    private var lastFocusedKeyMemory: String? = null
    private val rowScrollPositionsMemory = mutableMapOf<String, Pair<Int, Int>>()
    private var verticalScrollPositionMemory: Pair<Int, Int> = Pair(0, 0)
    private var hadHistoryWhenPositionSaved: Boolean = false
    private var isRestoringPosition: Boolean = false

    data class HomeState(
        val mixedRows: List<HomeRowItem> = emptyList(),
        val rows: List<HomeRow> = emptyList(),
        val hubRows: List<HubGroupRow> = emptyList(),
        val history: List<WatchHistoryEntity> = emptyList(),
        val seriesNextUp: List<com.lumera.app.data.model.SeriesNextUpEntity> = emptyList(),
        val isLoading: Boolean = true,
        val lastFocusedKey: String? = null,
        val loadedScreen: String? = null,
        // Row scroll positions: rowKey -> Pair(firstVisibleItemIndex, firstVisibleItemScrollOffset)
        val rowScrollPositions: Map<String, Pair<Int, Int>> = emptyMap(),
        // Vertical list scroll position: Pair(firstVisibleItemIndex, firstVisibleItemScrollOffset)
        val verticalScrollPosition: Pair<Int, Int> = Pair(0, 0),
        val heroRow: HomeRow? = null,
        val loadedProfileId: Int? = null,
        val watchedIds: Set<String> = emptySet(), // IMDb IDs of watched items (movies + series)
        val enrichedMeta: Map<String, MetaItem> = emptyMap(),
        val historyItems: List<MetaItem> = emptyList(),
        val tmdbEnabled: Boolean = false,
        val tmdbEnrichedIds: Set<String> = emptySet(),
        val newEpisodesSeries: List<WatchlistEntity> = emptyList()
    )

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state

    fun getRowScrollPositions(): Map<String, Pair<Int, Int>> = rowScrollPositionsMemory

    fun getVerticalScrollPosition(): Pair<Int, Int> = verticalScrollPositionMemory

    fun setLastFocusedKey(key: String?) {
        if (lastFocusedKeyMemory == key) return
        lastFocusedKeyMemory = key
        _state.value = _state.value.copy(lastFocusedKey = key)
    }
    
    fun setRowScrollPosition(rowKey: String, position: Pair<Int, Int>) {
        if (rowScrollPositionsMemory[rowKey] == position) return
        rowScrollPositionsMemory[rowKey] = position
    }
    
    fun setVerticalScrollPosition(position: Pair<Int, Int>, hasHistory: Boolean = hadHistoryWhenPositionSaved) {
        if (verticalScrollPositionMemory == position && hadHistoryWhenPositionSaved == hasHistory) return
        verticalScrollPositionMemory = position
        hadHistoryWhenPositionSaved = hasHistory
    }

    fun needsHistoryScrollAdjustment(hasHistory: Boolean): Boolean {
        return hasHistory && !hadHistoryWhenPositionSaved && isRestoringPosition
    }

    // Track which rows are currently loading more items to prevent duplicate fetches
    private val loadingMoreRows = mutableSetOf<String>()
    private val metadataFallbackCache = mutableMapOf<String, MetadataFallback?>()
    private val metadataRequestsInFlight = mutableSetOf<String>()
    private val hubInitialLoadCount = 100
    private val initialDashboardBatchSize = 6
    private val initialDashboardTimeoutMs = 2_500L

    // UI batching: per-row pending buffers and dedup tracking
    companion object {
        private const val ROW_BATCH_SIZE = 50
    }
    private val pendingRowItems = mutableMapOf<String, MutableList<MetaItem>>()
    private val allFetchedRowIds = mutableMapOf<String, MutableSet<String>>()

    private data class MetadataFallback(
        val poster: String?,
        val background: String?,
        val logo: String?,
        val description: String?,
        val releaseInfo: String?,
        val imdbRating: String?,
        val runtime: String?,
        val genres: List<String>?
    )

    /**
     * Loads the next page of items for a specific catalog row.
     * Called when the user scrolls near the end of the row's current items.
     * Uses UI batching: reveals ROW_BATCH_SIZE items at a time from a pending buffer,
     * only fetching from the API when the buffer is empty.
     */
    fun loadMoreItems(configId: String) {
        if (configId in loadingMoreRows) return // Already loading

        // Find the row in the current state
        val currentRows = _state.value.rows
        val row = currentRows.find { it.configId == configId } ?: return
        if (row.catalogUrl.isEmpty() || !row.supportsSkip) return

        // First: reveal items from the pending buffer (no API call needed)
        val pending = pendingRowItems[configId]
        if (pending != null && pending.isNotEmpty()) {
            val batch = pending.take(ROW_BATCH_SIZE)
            pendingRowItems[configId] = pending.drop(ROW_BATCH_SIZE).toMutableList()
            appendItemsToRow(configId, batch)
            return
        }

        // Pending buffer empty — fetch next page from API
        loadingMoreRows.add(configId)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Initialize dedup set from current row items if not yet tracked
                val fetchedIds = allFetchedRowIds.getOrPut(configId) {
                    row.items.map { "${it.type}:${it.id}" }.toMutableSet()
                }

                // Skip = total fetched count for this row
                val nextSkip = fetchedIds.size
                val newItems = repository.fetchNextCatalogPage(row.catalogUrl, nextSkip)

                // Deduplicate against all previously fetched items
                val newUniqueItems = newItems.filter { item ->
                    fetchedIds.add("${item.type}:${item.id}")
                }

                if (newUniqueItems.isNotEmpty()) {
                    // Show first batch immediately, buffer the rest
                    val batch = newUniqueItems.take(ROW_BATCH_SIZE)
                    if (newUniqueItems.size > ROW_BATCH_SIZE) {
                        pendingRowItems.getOrPut(configId) { mutableListOf() }
                            .addAll(newUniqueItems.drop(ROW_BATCH_SIZE))
                    }
                    appendItemsToRow(configId, batch)
                }
            } catch (_: Exception) {
                // Silently fail - user can try scrolling again
            } finally {
                loadingMoreRows.remove(configId)
            }
        }
    }

    private fun appendItemsToRow(configId: String, newItems: List<MetaItem>) {
        _state.update { current ->
            val updatedRows = current.rows.map { r ->
                if (r.configId == configId) r.copy(items = r.items + newItems)
                else r
            }

            val updatedMixed = current.mixedRows.map { item ->
                if (item is CategoryRow && item.id == configId) {
                    val updatedRow = updatedRows.find { it.configId == configId }
                    if (updatedRow != null) CategoryRow.fromHomeRow(updatedRow) else item
                } else item
            }

            current.copy(
                rows = updatedRows,
                mixedRows = updatedMixed
            )
        }
    }

    /**
     * Invalidates the cached screen data, forcing a reload on next loadScreen() call.
     * Call this after making changes in the Dashboard Editor.
     */
    fun invalidate() {
        pendingRowItems.clear()
        allFetchedRowIds.clear()
        _state.value = _state.value.copy(
            loadedScreen = null,
            loadedProfileId = null
        )
    }
    
    /**
     * Prefetch first visible images from loaded rows.
     * Called after data loads to ensure images are in cache before scroll starts.
     */
    private fun prefetchFirstVisibleImages(rows: List<HomeRow>) {
        // Prefetch first 10 images from first 3 rows (most likely to be visible on screen)
        val imagesToPrefetch = rows
            .take(3)
            .flatMap { row -> row.items.take(10) }
            .mapNotNull { it.poster }
        
        imagesToPrefetch.forEach { url ->
            ImagePrefetcher.prefetch(context, url)
        }
    }

    /**
     * Prefetch a small set of likely-visible metadata so cinematic/hero surfaces render smoothly.
     * Keep this intentionally tiny to avoid unnecessary metadata requests.
     */
    private fun prefetchLikelyVisibleMetadata(rows: List<HomeRow>, heroRow: HomeRow?) {
        val candidates = buildList {
            addAll(heroRow?.items?.take(4) ?: emptyList())
            addAll(rows.getOrNull(0)?.items?.take(3) ?: emptyList())
            addAll(rows.getOrNull(1)?.items?.take(2) ?: emptyList())
        }
            .distinctBy { "${it.type}:${it.id}" }
            .take(6)

        candidates.forEach { ensureMetadataFallback(it) }
    }

    private fun needsMetadataFallback(item: MetaItem): Boolean {
        return item.poster.isNullOrBlank() ||
            item.background.isNullOrBlank() ||
            item.logo.isNullOrBlank() ||
            item.description.isNullOrBlank() ||
            item.releaseInfo.isNullOrBlank() ||
            item.imdbRating.isNullOrBlank() ||
            item.runtime.isNullOrBlank() ||
            item.genres.isNullOrEmpty()
    }

    fun ensureMetadataFallback(item: MetaItem?) {
        if (item == null || !needsMetadataFallback(item)) return
        val key = "${item.type}:${item.id}"

        if (metadataFallbackCache.containsKey(key)) {
            val cachedFallback = metadataFallbackCache[key]
            if (cachedFallback != null) {
                applyMetadataFallbackToState(type = item.type, id = item.id, fallback = cachedFallback, sourceItem = item)
            }
            return
        }

        if (!metadataRequestsInFlight.add(key)) return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val meta = repository.resolveMetaDetails(item.type, item.id)
                    ?: throw Exception("No meta found")
                val fallback = MetadataFallback(
                    poster = meta.poster,
                    background = meta.background,
                    logo = meta.logo,
                    description = meta.description,
                    releaseInfo = meta.releaseInfo,
                    imdbRating = meta.imdbRating,
                    runtime = meta.runtime,
                    genres = meta.genres
                )
                metadataFallbackCache[key] = fallback
                applyMetadataFallbackToState(type = item.type, id = item.id, fallback = fallback, sourceItem = item)

                // Persist resolved images to watch history + series next-up DB
                launch(Dispatchers.IO) {
                    // For series, the history stores episode-level IDs (e.g. tt123:1:3)
                    // but the MetaItem uses the canonical series ID (tt123).
                    // Look up both the exact ID and all episode entries by prefix.
                    val historyItems = if (item.type == "series") {
                        dao.getHistoryItemsByPrefix(item.id)
                    } else {
                        listOfNotNull(dao.getHistoryItem(item.id))
                    }
                    for (historyItem in historyItems) {
                        val needsPoster = historyItem.poster.isNullOrBlank() && !fallback.poster.isNullOrBlank()
                        val needsBackground = historyItem.background.isNullOrBlank() && !fallback.background.isNullOrBlank()
                        val needsLogo = historyItem.logo.isNullOrBlank() && !fallback.logo.isNullOrBlank()
                        if (needsPoster || needsBackground || needsLogo) {
                            dao.updateHistoryImages(
                                id = historyItem.id,
                                poster = if (needsPoster) fallback.poster else historyItem.poster,
                                background = if (needsBackground) fallback.background else historyItem.background,
                                logo = if (needsLogo) fallback.logo else historyItem.logo
                            )
                        }
                    }
                    // Also update series next-up poster if missing
                    if (!fallback.poster.isNullOrBlank()) {
                        val nextUp = dao.getSeriesNextUp(item.id)
                        if (nextUp != null && nextUp.poster.isNullOrBlank()) {
                            dao.upsertSeriesNextUp(nextUp.copy(poster = fallback.poster))
                        }
                    }
                }
            } catch (_: Exception) {
                metadataFallbackCache[key] = null
            } finally {
                metadataRequestsInFlight.remove(key)
            }
        }
    }

    private fun applyFallbackToMeta(meta: MetaItem, fallback: MetadataFallback): MetaItem {
        val patchedPoster = if (meta.poster.isNullOrBlank()) fallback.poster else meta.poster
        val patchedBackground = if (meta.background.isNullOrBlank()) fallback.background else meta.background
        val patchedLogo = if (meta.logo.isNullOrBlank()) fallback.logo else meta.logo
        val patchedDescription = if (meta.description.isNullOrBlank()) fallback.description else meta.description
        val patchedReleaseInfo = if (meta.releaseInfo.isNullOrBlank()) fallback.releaseInfo else meta.releaseInfo
        val patchedImdbRating = if (meta.imdbRating.isNullOrBlank()) fallback.imdbRating else meta.imdbRating
        val patchedRuntime = if (meta.runtime.isNullOrBlank()) fallback.runtime else meta.runtime
        val patchedGenres = if (meta.genres.isNullOrEmpty()) fallback.genres else meta.genres

        return meta.copy(
            poster = patchedPoster,
            background = patchedBackground,
            logo = patchedLogo,
            description = patchedDescription,
            releaseInfo = patchedReleaseInfo,
            imdbRating = patchedImdbRating,
            runtime = patchedRuntime,
            genres = patchedGenres
        )
    }

    private fun patchMetaListWithFallback(
        items: List<MetaItem>,
        type: String,
        id: String,
        fallback: MetadataFallback
    ): Pair<List<MetaItem>, Boolean> {
        var changed = false
        val patched = items.map { meta ->
            if (meta.type == type && meta.id == id) {
                val merged = applyFallbackToMeta(meta, fallback)
                if (merged != meta) changed = true
                merged
            } else {
                meta
            }
        }
        return patched to changed
    }

    private fun applyMetadataFallbackToState(type: String, id: String, fallback: MetadataFallback, sourceItem: MetaItem? = null) {
        _state.update { current ->
            val enrichedKey = "$type:$id"
            val itemToEnrich = current.enrichedMeta[enrichedKey] ?: sourceItem ?: return@update current
            
            val enriched = applyFallbackToMeta(itemToEnrich, fallback)
            if (enriched == itemToEnrich && current.enrichedMeta.containsKey(enrichedKey)) return@update current

            current.copy(
                enrichedMeta = current.enrichedMeta + (enrichedKey to enriched)
            )
        }
    }

    private val tmdbEnrichmentInFlight = mutableSetOf<String>()
    private var tmdbProfileCache: com.lumera.app.data.model.ProfileEntity? = null

    /**
     * Enriches a single item with TMDB metadata. Called for every item as it becomes visible,
     * piggybacking on the existing ensureMetadataFallback flow.
     */
    fun ensureTmdbEnrichment(item: MetaItem?) {
        if (item == null) return
        val profile = tmdbProfileCache ?: return
        if (!profile.tmdbEnabled) return

        val key = "tmdb:${item.type}:${item.id}"
        if (!tmdbEnrichmentInFlight.add(key)) return

        val language = profile.tmdbLanguage.ifBlank { null } ?: "en"

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mediaType = tmdbService.normalizeMediaType(item.type)
                val tmdbId = tmdbService.ensureTmdbId(item.id, mediaType)
                if (tmdbId == null) {
                    // Can't resolve (e.g. Kitsu IDs) — mark as done so UI doesn't stay hidden
                    markTmdbEnriched(item.type, item.id)
                    return@launch
                }
                val enrichment = tmdbMetadataService.fetchEnrichment(tmdbId, mediaType, language)
                if (enrichment == null) {
                    markTmdbEnriched(item.type, item.id)
                    return@launch
                }

                val fallback = MetadataFallback(
                    poster = null, // Keep addon poster
                    background = enrichment.backdrop,
                    logo = enrichment.logo,
                    description = enrichment.description,
                    releaseInfo = enrichment.releaseInfo,
                    imdbRating = enrichment.rating?.let { String.format("%.1f", it) },
                    runtime = enrichment.runtimeMinutes?.let { "${it}m" },
                    genres = enrichment.genres.ifEmpty { null }
                )

                applyTmdbEnrichmentToState(item.type, item.id, fallback, item)
            } catch (e: Exception) {
                Log.w("HomeViewModel", "TMDB enrichment failed for ${item.id}: ${e.message}")
                markTmdbEnriched(item.type, item.id)
            } finally {
                tmdbEnrichmentInFlight.remove(key)
            }
        }
    }

    private fun markTmdbEnriched(type: String, id: String) {
        _state.update { it.copy(tmdbEnrichedIds = it.tmdbEnrichedIds + "$type:$id") }
    }

    /**
     * Applies TMDB enrichment to state — overwrites fields (unlike addon fallback which only fills blanks).
     * This ensures localized content from TMDB takes priority.
     */
    private fun applyTmdbEnrichmentToState(type: String, id: String, fallback: MetadataFallback, sourceItem: MetaItem) {
        _state.update { current ->
            val enrichedKey = "$type:$id"
            val base = current.enrichedMeta[enrichedKey] ?: sourceItem
            val enriched = base.copy(
                background = fallback.background ?: base.background,
                logo = fallback.logo ?: base.logo,
                description = fallback.description ?: base.description,
                releaseInfo = fallback.releaseInfo ?: base.releaseInfo,
                imdbRating = fallback.imdbRating ?: base.imdbRating,
                runtime = fallback.runtime ?: base.runtime,
                genres = fallback.genres ?: base.genres
            )

            current.copy(
                enrichedMeta = current.enrichedMeta + (enrichedKey to enriched),
                tmdbEnrichedIds = current.tmdbEnrichedIds + "$type:$id"
            )
        }
    }

    fun loadScreen(screenName: String, currentProfile: com.lumera.app.data.model.ProfileEntity?) {
        val currentProfileId = currentProfile?.id
        // ... (existing code)
        // Skip reload if this screen is already loaded with data
        if (
            _state.value.loadedScreen == screenName &&
            _state.value.loadedProfileId == currentProfileId &&
            _state.value.rows.isNotEmpty()
        ) {
            isRestoringPosition = true
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch(Dispatchers.IO) {
            // Clear 'rows' immediately so the UI doesn't show "Ghost Data" from the previous tab
            pendingRowItems.clear()
            allFetchedRowIds.clear()
            _state.value = _state.value.copy(
                isLoading = true,
                mixedRows = emptyList(),
                rows = emptyList(),
                hubRows = emptyList(),
                lastFocusedKey = null,  // Reset focus when loading new screen
                rowScrollPositions = emptyMap(),  // Reset scroll positions for new screen
                verticalScrollPosition = Pair(0, 0),  // Reset vertical scroll for new screen
                loadedProfileId = null,
                enrichedMeta = emptyMap(),
                tmdbEnrichedIds = emptySet(),
                tmdbEnabled = currentProfile?.tmdbEnabled == true,
                newEpisodesSeries = emptyList()
            )
            tmdbEnrichmentInFlight.clear()
            lastFocusedKeyMemory = null
            rowScrollPositionsMemory.clear()
            verticalScrollPositionMemory = Pair(0, 0)
            hadHistoryWhenPositionSaved = false
            isRestoringPosition = false

            // Load History + Series Next Up only for Home
            if (screenName == "home") {
                launch {
                    val historyFlow = dao.getWatchHistory()
                    val nextUpFlow = dao.getActiveSeriesNextUp()
                    val newEpisodesFlow = if (currentProfile?.homeNewEpisodesEnabled == true) {
                        dao.getWatchlistSeriesWithNewEpisodes()
                    } else {
                        kotlinx.coroutines.flow.flowOf(emptyList())
                    }
                    
                    kotlinx.coroutines.flow.combine(historyFlow, nextUpFlow, newEpisodesFlow) { history, nextUp, newEpisodes ->
                        Triple(history, nextUp, newEpisodes)
                    }.collect { (history, nextUp, newEpisodes) ->
                        val historyItems = buildContinueWatchingItems(history, nextUp)
                        _state.update { it.copy(
                            history = history, 
                            seriesNextUp = nextUp,
                            historyItems = historyItems,
                            newEpisodesSeries = newEpisodes
                        ) }
                    }
                }
            } else {
                _state.value = _state.value.copy(
                    history = emptyList(), 
                    seriesNextUp = emptyList(),
                    historyItems = emptyList(),
                    newEpisodesSeries = emptyList()
                )
            }

            // Load watched IDs for all tabs (watched indicator on posters)
            launch {
                dao.getWatchedIds().collect { ids ->
                    // Extract canonical series ID from episode IDs (tt123:1:3 → tt123)
                    val canonicalIds = ids.map { id ->
                        val parts = id.split(":")
                        if (parts.size >= 3) parts.first() else id
                    }.toSet()
                    _state.update { it.copy(watchedIds = canonicalIds) }
                }
            }

            try {
                // Stage 1: Load only a small first batch so the screen opens quickly.
                val initialRowsDeferred = async {
                    repository.getDashboardRows(
                        screen = screenName,
                        skipConfigs = 0,
                        maxConfigs = initialDashboardBatchSize,
                        catalogTimeoutMs = initialDashboardTimeoutMs
                    )
                }
                val hubRowsDeferred = async { repository.getHubRows(screenName) }

                val initialRows = initialRowsDeferred.await()
                val hubRows = hubRowsDeferred.await()

                // Fetch HERO row separately (even if hidden in dashboard).
                val tabEnum = DashboardTab.fromString(screenName)
                val heroConfig = currentProfile?.heroFor(tabEnum)
                val heroRow = if (heroConfig?.categoryId != null) {
                    initialRows.find { it.configId == heroConfig.categoryId }
                        ?: repository.getCategoryRowPreview(
                            configId = heroConfig.categoryId,
                            maxItems = heroConfig.posterCount,
                            timeoutMs = initialDashboardTimeoutMs
                        )
                } else null

                // Prefetch first visible images BEFORE updating state.
                prefetchFirstVisibleImages(initialRows)

                val initialMixedList = (hubRows + initialRows.map { CategoryRow.fromHomeRow(it) })
                    .sortedBy { it.order }

                // Cache profile for TMDB enrichment (called per-item as they become visible)
                tmdbProfileCache = currentProfile

                _state.update {
                    it.copy(
                        mixedRows = initialMixedList,
                        rows = initialRows,
                        hubRows = hubRows,
                        heroRow = heroRow,
                        isLoading = false,
                        loadedScreen = screenName,
                        loadedProfileId = currentProfileId,
                        tmdbEnabled = currentProfile?.tmdbEnabled == true
                    )
                }

                // Start a tiny metadata warmup pass for items likely to render first.
                prefetchLikelyVisibleMetadata(rows = initialRows, heroRow = heroRow)

                // Stage 2: Load remaining categories in the background and append.
                launch {
                    val remainingRows = repository.getDashboardRows(
                        screen = screenName,
                        skipConfigs = initialDashboardBatchSize
                    )
                    if (remainingRows.isEmpty()) return@launch

                    _state.update { currentState ->
                        if (currentState.loadedScreen != screenName || currentState.loadedProfileId != currentProfileId) {
                            return@update currentState
                        }

                        val allRows = (currentState.rows + remainingRows)
                            .distinctBy { it.configId }
                            .sortedBy { it.order }

                        val resolvedHeroRow = when {
                            currentState.heroRow != null -> currentState.heroRow
                            heroConfig?.categoryId != null -> allRows.find { it.configId == heroConfig.categoryId }
                            else -> null
                        }

                        val mixedList = (hubRows + allRows.map { CategoryRow.fromHomeRow(it) })
                            .sortedBy { it.order }

                        currentState.copy(
                            mixedRows = mixedList,
                            rows = allRows,
                            heroRow = resolvedHeroRow
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false) }
            }
        }
    }

    /**
     * Opens a hub item, fetching the category content if it's not already loaded.
     */
    fun openHub(
        hubItem: com.lumera.app.domain.HubItem,
        onResult: (String, List<MetaItem>) -> Unit
    ) {
        // 1. Try to find in currently loaded rows (Legacy)
        val legacyRow = _state.value.rows.find { it.configId == hubItem.categoryId }
        if (legacyRow != null) {
            onResult(legacyRow.title, legacyRow.items)
            return
        }

        // 2. Try to find in mixed rows (New Architecture)
        val mixedRow = _state.value.mixedRows.find { it.id == hubItem.categoryId } as? CategoryRow
        if (mixedRow != null) {
            onResult(mixedRow.title, mixedRow.items)
            return
        }

        // 3. If not found, fetch from repository
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val fetchedRow = repository.getCategoryRowPreview(
                    configId = hubItem.categoryId,
                    maxItems = hubInitialLoadCount
                )
                if (fetchedRow != null) {
                    // Cache fetched row so GridView can lazy-load additional pages via loadMoreItems().
                    _state.update { current ->
                        val updatedRows = current.rows
                            .filterNot { it.configId == fetchedRow.configId } + fetchedRow
                        current.copy(rows = updatedRows)
                    }
                    onResult(fetchedRow.title, fetchedRow.items)
                }
            } catch (_: Exception) {
                // Ignore error
            }
        }
    }

    private fun buildContinueWatchingItems(
        history: List<WatchHistoryEntity>,
        seriesNextUp: List<com.lumera.app.data.model.SeriesNextUpEntity> = emptyList()
    ): List<MetaItem> {
        val result = mutableListOf<Pair<Long, MetaItem>>()
        val seriesIdsIncluded = mutableSetOf<String>()

        val inProgress = history.filter { !it.watched }
        val seriesByCanonicalId = mutableMapOf<String, MutableList<WatchHistoryEntity>>()
        val movieById = mutableMapOf<String, WatchHistoryEntity>()

        inProgress.forEach { entry ->
            if (entry.type == "series") {
                val canonicalId = canonicalSeriesId(entry.id)
                seriesByCanonicalId.getOrPut(canonicalId) { mutableListOf() }.add(entry)
            } else {
                movieById.putIfAbsent(entry.id, entry)
            }
        }

        val chosenSeries = mutableMapOf<String, WatchHistoryEntity>()
        seriesByCanonicalId.forEach { (canonicalId, entries) ->
            val preferred = entries.firstOrNull { isEpisodePlaybackId(it.id) } ?: entries.firstOrNull()
            if (preferred != null) {
                chosenSeries[canonicalId] = preferred
            }
        }

        inProgress.forEach { entry ->
            if (entry.type == "series") {
                val canonicalId = canonicalSeriesId(entry.id)
                val chosen = chosenSeries[canonicalId] ?: return@forEach
                if (chosen.id != entry.id) return@forEach
                seriesIdsIncluded.add(canonicalId)
                result.add(chosen.lastWatched to MetaItem(
                    id = canonicalId,
                    type = entry.type,
                    name = entry.title,
                    poster = entry.poster,
                    background = chosen.background,
                    logo = chosen.logo,
                    progress = chosen.progress()
                ))
            } else {
                val chosen = movieById[entry.id] ?: return@forEach
                if (chosen.id != entry.id) return@forEach
                result.add(entry.lastWatched to MetaItem(
                    id = entry.id,
                    type = entry.type,
                    name = entry.title,
                    poster = entry.poster,
                    background = entry.background,
                    logo = entry.logo,
                    progress = entry.progress()
                ))
            }
        }

        val today = java.time.LocalDate.now().toString()
        for (nextUp in seriesNextUp) {
            if (nextUp.seriesId in seriesIdsIncluded) continue
            val released = nextUp.nextReleased
            if (released != null && released > today) continue
            if (nextUp.isComplete && released == null) continue
            val isReturning = nextUp.isComplete || nextUp.isNewEpisode
            result.add(nextUp.updatedAt to MetaItem(
                id = nextUp.seriesId,
                type = "series",
                name = nextUp.title,
                poster = nextUp.poster,
                hasNewEpisode = isReturning
            ))
        }

        return result.sortedByDescending { it.first }.map { it.second }
    }

    private fun canonicalSeriesId(playbackId: String): String {
        if (!isEpisodePlaybackId(playbackId)) return playbackId
        val parts = playbackId.split(":")
        return parts.dropLast(2).joinToString(":")
    }

    private fun isEpisodePlaybackId(playbackId: String): Boolean {
        val parts = playbackId.split(":")
        if (parts.size < 3) return false
        return parts[parts.lastIndex - 1].toIntOrNull() != null && parts.last().toIntOrNull() != null
    }

    fun resolveEffectiveFocusKey(
        lastFocusedKey: String?,
        historyItems: List<MetaItem>
    ): String? {
        if (lastFocusedKey == null || !lastFocusedKey.startsWith("-1_")) return lastFocusedKey

        val firstSep = lastFocusedKey.indexOf('_')
        if (firstSep < 0) return lastFocusedKey
        val remainder = lastFocusedKey.substring(firstSep + 1)
        val itemId = remainder.substringBeforeLast("_")
        val savedIndex = remainder.substringAfterLast("_").toIntOrNull()

        val currentIndex = historyItems.indexOfFirst { it.id == itemId }

        if (currentIndex < 0) {
            return if (historyItems.isNotEmpty()) {
                "-1_${historyItems.first().id}_0"
            } else {
                null
            }
        }

        if (savedIndex != null && currentIndex != savedIndex) {
            return "-1_${itemId}_${currentIndex}"
        }

        return lastFocusedKey
    }

    fun resolveLatestPreviewItem(
        current: MetaItem?,
        state: HomeState,
        historyItems: List<MetaItem>
    ): MetaItem? {
        val item = current ?: return null

        val enriched = state.enrichedMeta["${item.type}:${item.id}"]
        if (enriched != null && !enriched.logo.isNullOrEmpty()) return enriched

        val fromHistory = historyItems.firstOrNull { it.type == item.type && it.id == item.id }
        if (fromHistory != null && !fromHistory.logo.isNullOrEmpty()) return fromHistory

        val fromHero = state.heroRow?.items?.firstOrNull { it.type == item.type && it.id == item.id }
        if (fromHero != null && !fromHero.logo.isNullOrEmpty()) return fromHero

        val fromRows = state.mixedRows
            .asSequence()
            .filterIsInstance<CategoryRow>()
            .flatMap { row -> row.items.asSequence() }
            .firstOrNull { it.type == item.type && it.id == item.id }
        if (fromRows != null && !fromRows.logo.isNullOrEmpty()) return fromRows

        return item
    }

    fun resolveCinematicPreviewItem(
        lastFocusedKey: String?,
        mixedRows: List<HomeRowItem>,
        historyItems: List<MetaItem>
    ): MetaItem? {
        if (lastFocusedKey.isNullOrEmpty()) return null
        if (lastFocusedKey.startsWith("hub_") || lastFocusedKey.startsWith("hero_")) return null

        val firstSeparator = lastFocusedKey.indexOf('_')
        if (firstSeparator <= 0) return null

        val rowToken = lastFocusedKey.substring(0, firstSeparator)
        val itemToken = lastFocusedKey.substring(firstSeparator + 1).substringBeforeLast("_")
        if (itemToken.isEmpty() || itemToken == "viewmore") return null

        if (rowToken == "-1") {
            return historyItems.firstOrNull { it.id == itemToken }
        }
        if (rowToken == "-2") {
            return _state.value.newEpisodesSeries.firstOrNull { it.id == itemToken }?.let { entity ->
                MetaItem(
                    id = entity.id,
                    type = entity.type,
                    name = entity.title,
                    poster = entity.poster,
                    hasNewEpisode = true
                )
            }
        }

        val rowIndex = rowToken.toIntOrNull() ?: return null
        val row = mixedRows.getOrNull(rowIndex) as? CategoryRow ?: return null
        return row.items.firstOrNull { it.id == itemToken }
    }
}
