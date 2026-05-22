package com.lumera.app.ui.details

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lumera.app.data.local.AddonDao
import com.lumera.app.data.model.stremio.MetaItem
import com.lumera.app.data.model.stremio.MetaVideo
import com.lumera.app.data.model.stremio.Stream
import com.lumera.app.data.model.StreamQuality
import com.lumera.app.data.player.PlaybackTrackSelectionStore
import com.lumera.app.data.player.SourceSelectionStore
import com.lumera.app.data.profile.ProfileConfigurationManager
import com.lumera.app.data.repository.AddonRepository
import com.lumera.app.data.repository.SubtitleRepository
import com.lumera.app.data.stream.StreamSortingService
import com.lumera.app.data.tmdb.TmdbEnrichment
import com.lumera.app.data.tmdb.TmdbEpisodeEnrichment
import com.lumera.app.data.tmdb.TmdbMetaPreview
import com.lumera.app.data.tmdb.TmdbMetadataService
import com.lumera.app.data.tmdb.TmdbService
import com.lumera.app.data.tmdb.TmdbVideoInfo
import com.lumera.app.domain.AddonSubtitle
import com.lumera.app.domain.episodeStreamId
import com.lumera.app.data.trakt.TraktSyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import com.lumera.app.data.model.SeriesNextUpEntity
import com.lumera.app.data.model.WatchHistoryEntity
import com.lumera.app.data.model.WatchlistEntity
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DetailsViewModel @Inject constructor(
    private val dao: AddonDao,
    private val sourceSelectionStore: SourceSelectionStore,
    private val playbackTrackSelectionStore: PlaybackTrackSelectionStore,
    private val repository: AddonRepository,
    private val subtitleRepository: SubtitleRepository,
    private val profileConfigurationManager: ProfileConfigurationManager,
    private val streamSortingService: StreamSortingService,
    private val tmdbService: TmdbService,
    private val tmdbMetadataService: TmdbMetadataService,
    private val traktSyncManager: TraktSyncManager
) : ViewModel() {

    /** Per-episode watch progress for the episodes sidebar. */
    data class EpisodeProgress(
        val progress: Float,  // 0.0–1.0
        val watched: Boolean
    )

    data class DetailsState(
        val meta: MetaItem? = null,
        val resolvedId: String? = null, // IMDb ID resolved from tmdb: prefixes, used for stream/subtitle fetching
        val contentKey: String? = null, // Tracks which item this state belongs to
        val isLoading: Boolean = true,
        val isLoadingStreams: Boolean = false,
        val resumePlaybackId: String? = null,
        val isMovieWatched: Boolean = false,
        val autoPlayStream: Stream? = null,
        val addonSubtitles: List<AddonSubtitle> = emptyList(),
        val availableStreams: List<Stream> = emptyList(),
        val sidebarState: SidebarState = SidebarState.Closed,
        val episodeProgressMap: Map<String, EpisodeProgress> = emptyMap(), // "S1:E3" → progress
        val episodeEnrichmentMap: Map<String, TmdbEpisodeEnrichment> = emptyMap(), // "S1:E3" → TMDB data
        // TMDB enrichment
        val tmdbEnabled: Boolean = false,
        val tmdbLoading: Boolean = false,
        val tmdbEnrichment: TmdbEnrichment? = null,
        val tmdbRecommendations: List<TmdbMetaPreview> = emptyList(),
        val tmdbTrailer: TmdbVideoInfo? = null,
        val tmdbCollection: List<TmdbMetaPreview> = emptyList(),
        val tmdbCollectionName: String? = null,
        val parentalPin: String = "",
        val parentalAgeLimit: Int = 0
    )

    private val _state = MutableStateFlow(DetailsState())
    val state: StateFlow<DetailsState> = _state

    /** Reactive watchlist status — emits true/false as the current item's watchlist state changes. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val isInWatchlist: StateFlow<Boolean> = _state
        .map { it.resolvedId ?: it.meta?.id }
        .flatMapLatest { id -> if (id != null) dao.isInWatchlistFlow(id) else flowOf(false) }
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), false)

    private var loadDetailsJob: Job? = null
    private var loadStreamsJob: Job? = null
    private var tmdbEnrichmentJob: Job? = null
    private var loadRequestVersion: Long = 0L
    private var loadedContentKey: String? = null

    // Prefetched streams cache
    private var prefetchStreamsJob: Job? = null
    private var prefetchedStreamKey: String? = null
    private var prefetchedStreams: List<Stream>? = null
    private var prefetchedSubtitles: List<AddonSubtitle>? = null


    fun loadDetails(type: String, id: String, addonBaseUrl: String? = null) {
        val requestKey = "$type:$id"

        // Keep current details when reopening the same item (e.g., returning from player).
        if (
            loadedContentKey == requestKey &&
            _state.value.meta != null &&
            !_state.value.isLoading
        ) {
            refreshResumeStateIfNeeded(_state.value.meta)
            if (_state.value.sidebarState !is SidebarState.Closed) {
                _state.value = _state.value.copy(sidebarState = SidebarState.Closed)
            }
            return
        }

        loadDetailsJob?.cancel()
        loadRequestVersion += 1
        val requestVersion = loadRequestVersion

        // Reset immediately so previous movie details never flash for a new item.
        _state.value = DetailsState(
            isLoading = true,
            resumePlaybackId = null,
            autoPlayStream = null,
            addonSubtitles = emptyList(),
            availableStreams = emptyList(),
            sidebarState = SidebarState.Closed
        )

        loadDetailsJob = viewModelScope.launch {
            try {
                // Resolve tmdb: IDs to IMDb IDs via TMDB API so all addons work consistently
                val profileId = profileConfigurationManager.getLastActiveProfileId()
                val profile = profileId?.let { dao.getProfileById(it) }
                val isTmdbEnabled = profile?.tmdbEnabled == true
                val resolvedId = if (isTmdbEnabled && id.startsWith("tmdb:", ignoreCase = true)) {
                    val tmdbNumericId = id.substringAfter(':').substringBefore(':').toIntOrNull()
                    val mediaType = tmdbService.normalizeMediaType(type)
                    tmdbNumericId?.let { tmdbService.tmdbToImdb(it, mediaType) } ?: id
                } else id

                val details = repository.resolveMetaDetails(type, resolvedId, addonBaseUrl)
                    ?: throw Exception("No meta found")
                if (requestVersion != loadRequestVersion) return@launch
                loadedContentKey = requestKey
                // Use resolved ID for streams — guarantees IMDb format for stream addons
                val streamFetchId = if (details.id.startsWith("tt")) details.id else resolvedId
                val resumePlaybackId = if (details.type == "series") {
                    val latest = dao.getLatestSeriesEpisodeHistory("${streamFetchId}:%")
                    if (latest != null && !latest.watched) {
                        latest.id // In-progress episode — resume it
                    } else {
                        // All episodes watched or no history — use next-up if aired
                        val nextUp = dao.getSeriesNextUp(streamFetchId)
                        val today = java.time.LocalDate.now().toString()
                        val hasAired = nextUp != null && !nextUp.isComplete &&
                            (nextUp.nextReleased == null || nextUp.nextReleased <= today)
                        if (hasAired) {
                            "${streamFetchId}:${nextUp!!.nextSeason}:${nextUp.nextEpisode}"
                        } else null
                    }
                } else {
                    val movieHistory = dao.getHistoryItem(streamFetchId)
                    if (movieHistory?.watched == true) null else movieHistory?.id
                }
                val isMovieWatched = if (details.type != "series") {
                    dao.getHistoryItem(streamFetchId)?.watched == true
                } else false
                // Build per-episode progress map for the episodes sidebar
                val episodeProgressMap = if (details.type == "series") {
                    buildEpisodeProgressMap(streamFetchId)
                } else emptyMap()

                _state.value = _state.value.copy(
                    meta = details,
                    resolvedId = streamFetchId,
                    contentKey = requestKey,
                    isLoading = false,
                    resumePlaybackId = resumePlaybackId,
                    isMovieWatched = isMovieWatched,
                    episodeProgressMap = episodeProgressMap,
                    autoPlayStream = null,
                    addonSubtitles = emptyList(),
                    availableStreams = emptyList(),
                    tmdbEnrichment = null,
                    tmdbRecommendations = emptyList(),
                    tmdbTrailer = null,
                    tmdbCollection = emptyList(),
                    tmdbCollectionName = null,
                    tmdbEnabled = isTmdbEnabled,
                    tmdbLoading = isTmdbEnabled || (profile?.parentalAgeLimit ?: 0) > 0,
                    parentalPin = profile?.parentalPin ?: "",
                    parentalAgeLimit = profile?.parentalAgeLimit ?: 0
                )
                // Update next-up entry when details load
                if (details.type == "series") {
                    computeAndStoreNextUp(streamFetchId, details.name, details.poster, details.videos)
                }
                // Fire TMDB enrichment in background (non-blocking)
                loadTmdbEnrichment(details.type, streamFetchId, requestKey)

                // Prefetch streams so they're ready when the user hits Play
                val prefetchId = if (resumePlaybackId != null) {
                    resumePlaybackId
                } else if (details.type == "series") {
                    val firstEpisode = details.videos
                        ?.filter { it.season > 0 && it.episode > 0 }
                        ?.minWithOrNull(compareBy<com.lumera.app.data.model.stremio.MetaVideo> { it.season }.thenBy { it.episode })
                    firstEpisode?.let { episodeStreamId(streamFetchId, it) } ?: streamFetchId
                } else {
                    streamFetchId
                }
                prefetchStreams(details.type, prefetchId)
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                if (requestVersion != loadRequestVersion) return@launch
                loadedContentKey = null
                _state.value = _state.value.copy(
                    meta = null,
                    isLoading = false,
                    resumePlaybackId = null,
                    autoPlayStream = null,
                    addonSubtitles = emptyList(),
                    availableStreams = emptyList()
                )
            }
        }
    }

    private fun refreshResumeStateIfNeeded(meta: MetaItem?) {
        if (meta == null) {
            if (_state.value.resumePlaybackId != null) {
                _state.value = _state.value.copy(resumePlaybackId = null)
            }
            return
        }

        viewModelScope.launch {
            val resumePlaybackId = if (meta.type == "series") {
                val latest = dao.getLatestSeriesEpisodeHistory("${meta.id}:%")
                if (latest != null && !latest.watched) {
                    latest.id
                } else {
                    val nextUp = dao.getSeriesNextUp(meta.id)
                    val today = java.time.LocalDate.now().toString()
                    val hasAired = nextUp != null && !nextUp.isComplete &&
                        (nextUp.nextReleased == null || nextUp.nextReleased <= today)
                    if (hasAired) {
                        "${meta.id}:${nextUp!!.nextSeason}:${nextUp.nextEpisode}"
                    } else null
                }
            } else {
                val movieHistory = dao.getHistoryItem(meta.id)
                if (movieHistory?.watched == true) null else movieHistory?.id
            }
            val isMovieWatched = if (meta.type != "series") {
                dao.getHistoryItem(meta.id)?.watched == true
            } else false
            if (_state.value.meta?.id == meta.id && _state.value.meta?.type == meta.type) {
                val episodeProgressMap = if (meta.type == "series") {
                    buildEpisodeProgressMap(meta.id)
                } else emptyMap()
                _state.value = _state.value.copy(
                    resumePlaybackId = resumePlaybackId,
                    isMovieWatched = isMovieWatched,
                    autoPlayStream = null,
                    episodeProgressMap = episodeProgressMap
                )
                // Update next-up after returning from player
                if (meta.type == "series") {
                    computeAndStoreNextUp(meta.id, meta.name, meta.poster, meta.videos)
                }
            }
        }
    }

    fun refreshResumeState() {
        refreshResumeStateIfNeeded(_state.value.meta)
    }

    /**
     * Build a map of "S{season}:E{episode}" → EpisodeProgress from watch history.
     * Checks both with and without stream index suffix.
     */
    private suspend fun buildEpisodeProgressMap(seriesId: String): Map<String, EpisodeProgress> {
        val historyItems = dao.getSeriesEpisodeHistory("$seriesId:%")
        if (historyItems.isEmpty()) return emptyMap()

        val map = mutableMapOf<String, EpisodeProgress>()
        for (item in historyItems) {
            val parts = item.id.split(":")
            if (parts.size < 3) continue
            // Extract season and episode from the playback ID
            val hasStreamIndex = parts.size >= 4 && parts.last().toIntOrNull() != null
            val season = parts[parts.size - if (hasStreamIndex) 3 else 2].toIntOrNull() ?: continue
            val episode = parts[parts.size - if (hasStreamIndex) 2 else 1].toIntOrNull() ?: continue
            val key = "S${season}:E${episode}"

            // Keep the most recent entry if there are duplicates
            val existing = map[key]
            if (existing == null || (!existing.watched && item.watched)) {
                map[key] = EpisodeProgress(
                    progress = item.progress(),
                    watched = item.watched
                )
            }
        }
        return map
    }

    /**
     * Compute and store the next unwatched episode for a series.
     * Called when an episode is watched (auto or manual) and when details load.
     */
    suspend fun computeAndStoreNextUp(
        seriesId: String,
        title: String,
        poster: String?,
        videos: List<MetaVideo>?
    ) {
        if (videos.isNullOrEmpty()) return

        // Get all watched episodes for this series
        val progressMap = buildEpisodeProgressMap(seriesId)

        // If no episodes have been watched, remove the next-up entry entirely
        val hasAnyWatched = progressMap.values.any { it.watched }
        if (!hasAnyWatched) {
            dao.deleteSeriesNextUp(seriesId)
            return
        }

        // Sort episodes by season then episode number
        val sortedEpisodes = videos
            .filter { it.season > 0 && it.episode > 0 }
            .sortedWith(compareBy({ it.season }, { it.episode }))

        if (sortedEpisodes.isEmpty()) return

        // Find the first unwatched episode
        val nextEpisode = sortedEpisodes.firstOrNull { ep ->
            val key = "S${ep.season}:E${ep.episode}"
            progressMap[key]?.watched != true
        }

        val existing = dao.getSeriesNextUp(seriesId)

        if (nextEpisode != null) {
            val epTitle = nextEpisode.title.takeIf { it.isNotBlank() && it != "Episode" }
            // Only update timestamp if the next episode actually changed
            val unchanged = existing != null &&
                !existing.isComplete &&
                existing.nextSeason == nextEpisode.season &&
                existing.nextEpisode == nextEpisode.episode
            // Badge: set when show was complete and now has a new episode
            // Keep if unchanged (user hasn't watched the new ep yet)
            // Clear once user watches the episode (next computeAndStoreNextUp will have a different next ep)
            val revived = existing?.isComplete == true
            val badgeState = when {
                revived -> true
                unchanged -> existing?.isNewEpisode ?: false
                else -> false
            }
            dao.upsertSeriesNextUp(
                SeriesNextUpEntity(
                    seriesId = seriesId,
                    title = title,
                    poster = poster ?: existing?.poster,
                    nextSeason = nextEpisode.season,
                    nextEpisode = nextEpisode.episode,
                    nextEpisodeTitle = epTitle,
                    nextReleased = nextEpisode.released?.take(10),
                    isComplete = false,
                    isNewEpisode = badgeState,
                    updatedAt = if (unchanged) existing.updatedAt else System.currentTimeMillis()
                )
            )
        } else {
            // All local episodes watched — mark as complete locally.
            // If Trakt knows about a future episode, syncSeriesNextUp will correct this.
            val alreadyComplete = existing?.isComplete == true
            dao.upsertSeriesNextUp(
                SeriesNextUpEntity(
                    seriesId = seriesId,
                    title = title,
                    poster = poster ?: existing?.poster,
                    nextSeason = 0,
                    nextEpisode = 0,
                    nextEpisodeTitle = null,
                    nextReleased = null,
                    isComplete = true,
                    updatedAt = if (alreadyComplete) existing.updatedAt else System.currentTimeMillis()
                )
            )
        }
    }

    private fun loadTmdbEnrichment(type: String, videoId: String, contentKey: String) {
        tmdbEnrichmentJob?.cancel()
        tmdbEnrichmentJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // Check if TMDB is enabled or parental limit is active
                val profileId = profileConfigurationManager.getLastActiveProfileId()
                val profile = profileId?.let { dao.getProfileById(it) }
                val isTmdbEnabled = profile?.tmdbEnabled == true
                val hasParentalLimit = (profile?.parentalAgeLimit ?: 0) > 0
                if (!isTmdbEnabled && !hasParentalLimit) {
                    _state.value = _state.value.copy(tmdbEnabled = false, tmdbLoading = false)
                    return@launch
                }

                _state.value = _state.value.copy(tmdbEnabled = isTmdbEnabled, tmdbLoading = true)

                val language = profile?.tmdbLanguage?.ifBlank { null } ?: "en"
                val mediaType = tmdbService.normalizeMediaType(type)

                // Resolve TMDB ID — if unresolvable (e.g. Kitsu IDs), stop loading and show addon data
                val tmdbId = tmdbService.ensureTmdbId(videoId, mediaType)
                if (tmdbId == null) {
                    _state.value = _state.value.copy(tmdbLoading = false)
                    return@launch
                }

                // Fetch enrichment, recommendations, and videos in parallel
                val enrichmentDeferred = async { tmdbMetadataService.fetchEnrichment(tmdbId, mediaType, language) }
                val recommendationsDeferred = async { tmdbMetadataService.fetchRecommendations(tmdbId, mediaType, language) }
                val trailerDeferred = async { tmdbMetadataService.fetchBestTrailerKey(tmdbId, mediaType, language) }

                val enrichment = enrichmentDeferred.await()
                val recommendations = recommendationsDeferred.await()
                val trailer = trailerDeferred.await()

                // Fetch collection if available (movies only)
                val collection = if (enrichment?.collectionId != null) {
                    tmdbMetadataService.fetchCollection(enrichment.collectionId, language)
                } else emptyList()

                // Only update if we're still showing the same content
                if (_state.value.contentKey != contentKey) return@launch

                // Apply enrichment — overlay TMDB data onto existing metadata where it adds value
                val currentMeta = _state.value.meta
                val enrichedMeta = if (isTmdbEnabled && currentMeta != null && enrichment != null) {
                    currentMeta.copy(
                        // Localized title
                        name = enrichment.localizedTitle ?: currentMeta.name,
                        // Localized description
                        description = enrichment.description ?: currentMeta.description,
                        // Better images
                        logo = enrichment.logo ?: currentMeta.logo,
                        background = enrichment.backdrop ?: currentMeta.background,
                        poster = enrichment.poster ?: currentMeta.poster,
                        // Localized genres
                        genres = enrichment.genres.ifEmpty { currentMeta.genres },
                        // Release info
                        releaseInfo = enrichment.releaseInfo ?: currentMeta.releaseInfo,
                        // Rating from TMDB
                        imdbRating = enrichment.rating?.let {
                            String.format("%.1f", it)
                        } ?: currentMeta.imdbRating,
                        // Runtime
                        runtime = enrichment.runtimeMinutes?.let { "${it}m" } ?: currentMeta.runtime
                    )
                } else currentMeta

                // Fetch per-episode enrichment for series (synopsis, runtime, thumbnails)
                val episodeEnrichmentMap = if (mediaType == "tv" && tmdbId != null) {
                    val seasons = enrichedMeta?.videos
                        ?.filter { it.season > 0 }
                        ?.map { it.season }
                        ?.distinct() ?: emptyList()
                    if (seasons.isNotEmpty()) {
                        val raw = tmdbMetadataService.fetchEpisodeEnrichment(tmdbId, seasons, language)
                        raw.mapKeys { (key, _) -> "S${key.first}:E${key.second}" }
                    } else emptyMap()
                } else emptyMap()

                _state.value = _state.value.copy(
                    meta = enrichedMeta,
                    tmdbLoading = false,
                    tmdbEnrichment = enrichment,
                    tmdbRecommendations = recommendations,
                    tmdbTrailer = trailer,
                    tmdbCollection = collection,
                    tmdbCollectionName = enrichment?.collectionName,
                    episodeEnrichmentMap = episodeEnrichmentMap
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("DetailsViewModel", "TMDB enrichment failed: ${e.message}")
                _state.value = _state.value.copy(tmdbLoading = false)
            }
        }
    }

    // ── Mark episode watched/unwatched ──

    fun toggleMovieWatched() {
        val meta = _state.value.meta ?: return
        if (meta.type == "series") return
        val itemId = _state.value.resolvedId ?: meta.id
        val isCurrentlyWatched = _state.value.isMovieWatched

        viewModelScope.launch(Dispatchers.IO) {
            if (isCurrentlyWatched) {
                dao.deleteHistoryItem(itemId)
                traktSyncManager.pushMovieUnwatched(itemId)
            } else {
                dao.upsertHistory(
                    WatchHistoryEntity(
                        id = itemId,
                        title = meta.name,
                        poster = meta.poster,
                        position = 0L,
                        duration = 0L,
                        lastWatched = System.currentTimeMillis(),
                        type = "movie",
                        watched = true,
                        scrobbled = true
                    )
                )
                traktSyncManager.pushMovieWatched(itemId)
            }
            _state.value = _state.value.copy(
                isMovieWatched = !isCurrentlyWatched,
                resumePlaybackId = null
            )
        }
    }

    fun toggleEpisodeWatched(episode: MetaVideo) {
        val meta = _state.value.meta ?: return
        val streamId = _state.value.resolvedId ?: meta.id
        val key = "S${episode.season}:E${episode.episode}"
        val currentProgress = _state.value.episodeProgressMap[key]
        val isCurrentlyWatched = currentProgress?.watched ?: false

        viewModelScope.launch(Dispatchers.IO) {
            if (isCurrentlyWatched) {
                // Unmark: remove watched entry from history
                val playbackId = "$streamId:${episode.season}:${episode.episode}"
                dao.deleteHistoryItem(playbackId)
                // Also try with stream index variants
                dao.getSeriesEpisodeHistory("$playbackId:%").forEach {
                    dao.deleteHistoryItem(it.id)
                }
                traktSyncManager.pushEpisodeUnwatched(streamId, episode.season, episode.episode)
            } else {
                // Mark as watched: create a watched history entry
                val playbackId = "$streamId:${episode.season}:${episode.episode}"
                dao.upsertHistory(
                    WatchHistoryEntity(
                        id = playbackId,
                        title = episode.title.takeIf { it.isNotBlank() && it != "Episode" }
                            ?: "S${episode.season}:E${episode.episode} - ${meta.name}",
                        poster = meta.poster,
                        position = 0L,
                        duration = 0L,
                        lastWatched = System.currentTimeMillis(),
                        type = "series",
                        watched = true,
                        scrobbled = true
                    )
                )
                traktSyncManager.pushEpisodeWatched(streamId, episode.season, episode.episode)
            }

            // Refresh the progress map and next-up entry
            val updatedMap = buildEpisodeProgressMap(streamId)
            _state.value = _state.value.copy(episodeProgressMap = updatedMap)
            computeAndStoreNextUp(streamId, meta.name, meta.poster, meta.videos)
        }
    }

    // 1. Open Episodes (Series)
    fun openEpisodes() {
        val videos = _state.value.meta?.videos ?: emptyList()
        _state.value = _state.value.copy(
            autoPlayStream = null,
            addonSubtitles = emptyList(),
            availableStreams = emptyList(),
            sidebarState = SidebarState.Episodes(videos)
        )
    }

    private fun prefetchStreams(type: String, id: String) {
        prefetchStreamsJob?.cancel()
        val key = "$type:$id"
        prefetchedStreamKey = key
        prefetchedStreams = null
        prefetchedSubtitles = null
        prefetchStreamsJob = viewModelScope.launch {
            try {
                val streamsDeferred = async { repository.getStreams(type, id) }
                val alternateId = if (type == "movie") {
                    _state.value.meta?.imdbId
                } else {
                    val episode = _state.value.meta?.videos?.find { it.id == id }
                    episode?.imdbId ?: _state.value.meta?.imdbId?.let { "$it:${episode?.season ?: 1}:${episode?.episode ?: 1}" }
                }
                val subtitlesDeferred = async { subtitleRepository.getSubtitles(type, id, alternateId = alternateId) }
                prefetchedStreams = streamsDeferred.await()
                prefetchedSubtitles = subtitlesDeferred.await()
            } catch (_: Exception) {
                // Prefetch failed silently — loadStreams will fetch fresh
            }
        }
    }

    // 2. Open Sources (Movie OR Specific Episode)
    fun loadStreams(
        type: String,
        id: String,
        displayTitle: String,
        sourceSelectionId: String = id,
        forceSourcePicker: Boolean = false,
        autoSelectSource: Boolean = false,
        rememberSourceSelection: Boolean = true
    ) {
        loadStreamsJob?.cancel()
        loadStreamsJob = viewModelScope.launch {
            // Show immediate loading feedback:
            // - Show sources sidebar when user needs to pick manually
            // - Centered spinner when auto-resolve is expected (auto-select or remembered source)
            val hasRemembered = rememberSourceSelection && sourceSelectionStore.hasRememberedSelection(sourceSelectionId)
            val showSidebar = forceSourcePicker || (!autoSelectSource && !hasRemembered)

            _state.value = _state.value.copy(
                autoPlayStream = null,
                addonSubtitles = emptyList(),
                availableStreams = emptyList(),
                isLoadingStreams = true,
                sidebarState = if (showSidebar) SidebarState.Sources(displayTitle, null)
                               else SidebarState.Closed
            )

            try {
                val rawStreams: List<Stream>
                val addonSubtitles: List<AddonSubtitle>
                val prefetchKey = "$type:$id"

                if (prefetchedStreamKey == prefetchKey) {
                    prefetchStreamsJob?.join()
                    if (prefetchedStreams != null) {
                        rawStreams = prefetchedStreams!!
                        addonSubtitles = prefetchedSubtitles ?: emptyList()
                    } else {
                        val streamsDeferred = async { repository.getStreams(type, id) }
                        val altId = resolveAlternateId(type, id, _state.value.meta)
                        val subtitlesDeferred = async { subtitleRepository.getSubtitles(type, id, alternateId = altId) }
                        rawStreams = streamsDeferred.await()
                        addonSubtitles = subtitlesDeferred.await()
                    }
                } else {
                    val streamsDeferred = async { repository.getStreams(type, id) }
                    val altId = resolveAlternateId(type, id, _state.value.meta)
                    val subtitlesDeferred = async { subtitleRepository.getSubtitles(type, id, alternateId = altId) }
                    rawStreams = streamsDeferred.await()
                    addonSubtitles = subtitlesDeferred.await()
                }

                // Read sorting preferences from the active profile
                val activeProfileId = profileConfigurationManager.getLastActiveProfileId()
                val profile = activeProfileId?.let { dao.getProfileById(it) }

                val streams = if (profile?.sourceSortingEnabled != false) {
                    val enabledQualities = StreamSortingService.parseEnabledQualities(profile?.sourceEnabledQualities ?: "4k,1080p,720p,unknown")
                    val excludePhrases = StreamSortingService.parseExcludePhrases(profile?.sourceExcludePhrases ?: "")
                    val addonSortOrders = dao.getAllAddons().firstOrNull()
                        ?.associate { it.transportUrl to it.sortOrder } ?: emptyMap()
                    val excludedFormats = StreamSortingService.parseExcludedFormats(profile?.sourceExcludedFormats ?: "")
                    streamSortingService.sortAndFilter(rawStreams, enabledQualities, excludePhrases, addonSortOrders, profile?.sourceSortPrimary ?: "quality", profile?.sourceMaxSizeGb ?: 0, excludedFormats)
                } else rawStreams

                val preferredStream = if (forceSourcePicker || !rememberSourceSelection) {
                    null
                } else {
                    sourceSelectionStore.findPreferredStream(sourceSelectionId, streams)
                }

                if (preferredStream != null) {
                    val specificSubs = try {
                        subtitleRepository.getSubtitles(
                            type = type,
                            playbackId = sourceSelectionId,
                            alternateId = resolveAlternateId(type, sourceSelectionId, _state.value.meta),
                            videoHash = preferredStream.behaviorHints?.videoHash,
                            videoSize = preferredStream.behaviorHints?.videoSize,
                            filename = preferredStream.behaviorHints?.filename
                        )
                    } catch (e: Exception) { addonSubtitles }
                    
                    _state.value = _state.value.copy(
                        isLoadingStreams = false,
                        sidebarState = SidebarState.Closed,
                        autoPlayStream = preferredStream,
                        addonSubtitles = specificSubs,
                        availableStreams = streams
                    )
                    return@launch
                }

                // Auto-select first playable source when enabled
                if (autoSelectSource && !forceSourcePicker) {
                    val firstPlayable = streams.firstOrNull {
                        !it.url.isNullOrBlank() || !it.infoHash.isNullOrBlank()
                    }
                    if (firstPlayable != null) {
                        val specificSubs = try {
                            subtitleRepository.getSubtitles(
                                type = type,
                                playbackId = sourceSelectionId,
                                alternateId = resolveAlternateId(type, sourceSelectionId, _state.value.meta),
                                videoHash = firstPlayable.behaviorHints?.videoHash,
                                videoSize = firstPlayable.behaviorHints?.videoSize,
                                filename = firstPlayable.behaviorHints?.filename
                            )
                        } catch (e: Exception) { addonSubtitles }
                        
                        _state.value = _state.value.copy(
                            isLoadingStreams = false,
                            sidebarState = SidebarState.Closed,
                            autoPlayStream = firstPlayable,
                            addonSubtitles = specificSubs,
                            availableStreams = streams
                        )
                        return@launch
                    }
                }

                // Update sidebar with results
                _state.value = _state.value.copy(
                    isLoadingStreams = false,
                    autoPlayStream = null,
                    addonSubtitles = addonSubtitles,
                    availableStreams = streams,
                    sidebarState = SidebarState.Sources(displayTitle, streams)
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoadingStreams = false,
                    autoPlayStream = null,
                    addonSubtitles = emptyList(),
                    availableStreams = emptyList(),
                    sidebarState = SidebarState.Sources(displayTitle, emptyList())
                )
            }
        }
    }

    fun consumeAutoPlayStream() {
        if (_state.value.autoPlayStream == null) return
        _state.value = _state.value.copy(autoPlayStream = null)
    }

    fun selectStreamAndPlay(type: String, playbackId: String, stream: Stream) {
        loadStreamsJob?.cancel()
        _state.value = _state.value.copy(
            autoPlayStream = stream,
            sidebarState = SidebarState.Closed
        )
    }

    // --- Clear Progress (with confirmation dialog) ---

    fun confirmClearProgress() {
        val meta = _state.value.meta ?: return

        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            // Collect items to clear
            val historyItems = if (meta.type == "series") {
                dao.getSeriesEpisodeHistory("${meta.id}:%")
            } else {
                listOfNotNull(dao.getHistoryItem(meta.id))
            }

            // Delete from local DB
            if (meta.type == "series") {
                dao.deleteSeriesHistory("${meta.id}:%")
                dao.deleteSeriesNextUp(meta.id)
                sourceSelectionStore.clearSelectionsForPrefix(meta.id)
                playbackTrackSelectionStore.clearSelectionsForPrefix(meta.id)
            } else {
                dao.deleteHistoryItem(meta.id)
                sourceSelectionStore.clearSelection(meta.id)
                playbackTrackSelectionStore.clearSelection(meta.id)
            }

            // Delete from Trakt (playback progress + watched history)
            for (item in historyItems) {
                if (item.scrobbled) {
                    traktSyncManager.deletePlaybackFromTrakt(item.id)
                }
            }
            // Remove watched episodes from Trakt history for series
            if (meta.type == "series") {
                val streamId = _state.value.resolvedId ?: meta.id
                val watchedEpisodes = historyItems.filter { it.watched }
                for (ep in watchedEpisodes) {
                    val parts = ep.id.split(":")
                    if (parts.size >= 3) {
                        val hasStreamIdx = parts.size >= 4 && parts.last().toIntOrNull() != null
                        val season = parts[parts.size - if (hasStreamIdx) 3 else 2].toIntOrNull() ?: continue
                        val episode = parts[parts.size - if (hasStreamIdx) 2 else 1].toIntOrNull() ?: continue
                        traktSyncManager.pushEpisodeUnwatched(streamId, season, episode)
                    }
                }
            }

            profileConfigurationManager.saveActiveRuntimeState()

            // Refresh episode progress map
            val streamId = _state.value.resolvedId ?: meta.id
            val updatedMap = if (meta.type == "series") buildEpisodeProgressMap(streamId) else emptyMap()

            _state.value = _state.value.copy(
                resumePlaybackId = null,
                episodeProgressMap = updatedMap
            )
        }
    }

    // --- Watchlist toggle ---

    fun toggleWatchlist() {
        val meta = _state.value.meta ?: return
        val itemId = _state.value.resolvedId ?: meta.id
        viewModelScope.launch(Dispatchers.IO) {
            if (dao.isInWatchlist(itemId)) {
                dao.removeFromWatchlist(itemId)
                traktSyncManager.pushRemove(itemId, meta.type)
            } else {
                val entity = WatchlistEntity(
                    id = itemId,
                    type = meta.type,
                    title = meta.name,
                    poster = meta.poster,
                    addedAt = System.currentTimeMillis()
                )
                dao.addToWatchlist(entity)
                traktSyncManager.pushAdd(entity)
            }
        }
    }

    // 3. Close Logic
    fun closeSidebar() {
        loadStreamsJob?.cancel()
        loadStreamsJob = null
        _state.value = _state.value.copy(
            isLoadingStreams = false,
            autoPlayStream = null,
            availableStreams = emptyList(),
            sidebarState = SidebarState.Closed
        )
    }

    // 4. Back Button Logic (Drill Up)
    fun goBackInSidebar() {
        val currentState = _state.value.sidebarState

        // If viewing Sources for a Series, go back to Episode List
        if (currentState is SidebarState.Sources && _state.value.meta?.type == "series") {
            openEpisodes()
        } else {
            closeSidebar()
        }
    }
    private fun resolveAlternateId(type: String, id: String, meta: MetaItem?): String? {
        if (meta == null) return null
        return if (type == "movie") {
            meta.imdbId
        } else {
            val episode = meta.videos?.find { it.id == id }
            episode?.imdbId ?: meta.imdbId?.let { "$it:${episode?.season ?: 1}:${episode?.episode ?: 1}" }
        }
    }
}
