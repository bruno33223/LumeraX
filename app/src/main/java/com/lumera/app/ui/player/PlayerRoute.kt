package com.lumera.app.ui.player

import android.content.Intent
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.lumera.app.BuildConfig
import com.lumera.app.data.model.PlayerState
import com.lumera.app.data.model.PlayerSubtitlePayload
import com.lumera.app.data.model.PendingEpisodeSwitch
import com.lumera.app.data.model.PendingSourceSelection
import com.lumera.app.data.torrent.TorrentService
import com.lumera.app.data.torrent.TorrentProgress
import com.lumera.app.ui.player.base.*
import com.lumera.app.data.model.stremio.MetaVideo
import com.lumera.app.data.model.stremio.Stream
import com.lumera.app.data.player.PlaybackTrackSelectionStore
import com.lumera.app.data.player.SourceSelectionStore
import com.lumera.app.data.repository.IntroRepository
import com.lumera.app.data.repository.AddonRepository
import com.lumera.app.data.repository.SubtitleRepository
import com.lumera.app.data.stream.StreamSortingService
import com.lumera.app.ui.components.dialogs.PlayerChoiceDialog
import com.lumera.app.ui.player.PlayerScreen
import com.lumera.app.ui.player.PlayerSessionResult
import com.lumera.app.domain.AddonSubtitle
import com.lumera.app.domain.findNextEpisode
import com.lumera.app.domain.episodeDisplayTitle
import com.lumera.app.domain.episodePlaybackId
import com.lumera.app.domain.episodeStreamId
import com.lumera.app.ui.player.*

@Composable
fun PlayerRoute(
    uiScope: kotlinx.coroutines.CoroutineScope,
    playerState: PlayerState,
    currentProfile: com.lumera.app.data.model.ProfileEntity?,
    selectedPlaybackId: String,
    selectedMovieId: String,
    selectedPlaybackType: String,
    selectedPlaybackTitle: String,
    selectedMovieTitle: String,
    selectedMovieLogo: String,
    selectedPlaybackPoster: String,
    selectedVideoUrl: String,
    selectedTrailerAudioUrl: String,
    torrentProgress: TorrentProgress?,
    onTorrentProgressChange: (TorrentProgress?) -> Unit,
    onSelectedVideoUrlChange: (String) -> Unit,
    onSelectedPlaybackIdChange: (String) -> Unit,
    onSelectedPlaybackTypeChange: (String) -> Unit,
    onSelectedPlaybackTitleChange: (String) -> Unit,
    onDetailsResumePlaybackHintChange: (String?) -> Unit,
    onTrailerReturnTokenIncrement: () -> Unit,
    onNavigateBack: () -> Unit,
    playbackTrackSelectionStore: PlaybackTrackSelectionStore,
    sourceSelectionStore: SourceSelectionStore,
    introRepository: IntroRepository,
    addonRepository: AddonRepository,
    subtitleRepository: SubtitleRepository,
    streamSortingService: StreamSortingService
) {
    val context = LocalContext.current
    
                            val rememberedTrackSelection = remember(selectedPlaybackId) {
                                playbackTrackSelectionStore.getSelection(selectedPlaybackId)
                            }
                            val playerSources = remember(playerState.selectedPlayerSources) { playerState.selectedPlayerSources }
                            val playerSubtitles = remember(playerState.selectedPlayerSubtitles) {
                                playerState.selectedPlayerSubtitles.map { subtitle ->
                                    PlayerSubtitleSource(
                                        id = subtitle.id,
                                        url = subtitle.url,
                                        label = subtitle.name,
                                        language = subtitle.language
                                    )
                                }
                            }

                            // Compute next episode
                            val isSeries = selectedPlaybackType.equals("series", ignoreCase = true)
                            val shouldAutoplay = currentProfile?.autoplayNextEpisode == true && isSeries
                            val nextEpisode = remember(selectedPlaybackId, selectedMovieId, playerState.currentEpisodeList, isSeries) {
                                if (isSeries && playerState.currentEpisodeList.isNotEmpty()) {
                                    findNextEpisode(selectedMovieId, selectedPlaybackId, playerState.currentEpisodeList)
                                } else null
                            }
                            val nextEpisodeInfo = remember(nextEpisode) {
                                nextEpisode?.let { ep ->
                                    NextEpisodeInfo(
                                        title = episodeDisplayTitle(ep),
                                        thumbnail = ep.thumbnail,
                                        seasonNumber = ep.season,
                                        episodeNumber = ep.episode
                                    )
                                }
                            }

                            // Fetch skip intro/outro segments from IntroDB
                            val skipIntroEnabled = currentProfile?.skipIntro == true
                            val autoplayEnabled = currentProfile?.autoplayNextEpisode == true
                            val needIntroDB = skipIntroEnabled || autoplayEnabled
                            var skipSegmentInfo by remember { mutableStateOf<SkipSegmentInfo?>(null) }
                            LaunchedEffect(selectedPlaybackId, needIntroDB, skipIntroEnabled) {
                                skipSegmentInfo = null
                                if (!needIntroDB) return@LaunchedEffect
                                if (!isSeries || selectedPlaybackId.isBlank()) return@LaunchedEffect
                                val parts = selectedPlaybackId.split(":")
                                if (parts.size < 3) return@LaunchedEffect
                                val imdbId = parts.dropLast(2).joinToString(":")
                                val season = parts[parts.lastIndex - 1].toIntOrNull() ?: return@LaunchedEffect
                                val episode = parts.last().toIntOrNull() ?: return@LaunchedEffect
                                val response = introRepository.getSegments(imdbId, season, episode)
                                if (response != null) {
                                    skipSegmentInfo = SkipSegmentInfo(
                                        introStartMs = if (skipIntroEnabled) response.intro?.start_ms else null,
                                        introEndMs = if (skipIntroEnabled) response.intro?.end_ms else null,
                                        outroStartMs = response.outro?.start_ms,
                                        outroEndMs = response.outro?.end_ms
                                    )
                                }
                            }

                            PlayerScreen(
                                videoUrl = selectedVideoUrl,
                                trailerAudioUrl = selectedTrailerAudioUrl.takeIf { it.isNotBlank() },
                                title = selectedPlaybackTitle.ifBlank { selectedMovieTitle },
                                seriesTitle = selectedMovieTitle.takeIf {
                                    selectedPlaybackType.equals("series", ignoreCase = true)
                                },
                                logoUrl = selectedMovieLogo.takeIf { it.isNotBlank() },
                                poster = selectedPlaybackPoster,
                                movieId = selectedPlaybackId,
                                mediaType = selectedPlaybackType,
                                sources = playerSources,
                                subtitles = playerSubtitles,
                                preferredAudioTrackId = rememberedTrackSelection?.audioTrackId,
                                preferredSubtitleTrackId = rememberedTrackSelection?.subtitleTrackId,
                                initialSubtitleDelayMs = rememberedTrackSelection?.subtitleDelayMs ?: 0L,
                                playbackSettings = PlaybackSettings(
                                    tunnelingEnabled = currentProfile?.tunnelingEnabled ?: false,
                                    mapDV7ToHevc = currentProfile?.mapDV7ToHevc ?: false,
                                    decoderPriority = currentProfile?.decoderPriority ?: 1,
                                    frameRateMatching = currentProfile?.frameRateMatching ?: false,
                                    autoplayNextEpisode = currentProfile?.autoplayNextEpisode ?: false,
                                    autoSelectSource = currentProfile?.autoSelectSource ?: false,
                                    autoplayThresholdMode = currentProfile?.autoplayThresholdMode ?: "percentage",
                                    autoplayThresholdPercent = currentProfile?.autoplayThresholdPercent ?: 95,
                                    autoplayThresholdSeconds = currentProfile?.autoplayThresholdSeconds ?: 30,
                                    preferredAudioLanguage = currentProfile?.preferredAudioLanguage ?: "",
                                    preferredAudioLanguageSecondary = currentProfile?.preferredAudioLanguageSecondary ?: "",
                                    preferredSubtitleLanguage = currentProfile?.preferredSubtitleLanguage ?: "",
                                    preferredSubtitleLanguageSecondary = currentProfile?.preferredSubtitleLanguageSecondary ?: "",
                                    subtitleSize = currentProfile?.subtitleSize ?: 100,
                                    subtitleOffset = currentProfile?.subtitleOffset ?: 0,
                                    subtitleTextColor = currentProfile?.subtitleTextColor?.toInt() ?: 0xFFFFFFFF.toInt(),
                                    subtitleBackgroundColor = currentProfile?.subtitleBackgroundColor?.toInt() ?: 0x00000000,
                                    assRendererEnabled = currentProfile?.assRendererEnabled ?: false
                                ),
                                skipSegmentInfo = skipSegmentInfo,
                                nextEpisodeInfo = if (nextEpisode != null) nextEpisodeInfo else null,
                                onAutoplayNextEpisode = if (nextEpisode != null) {
                                    { playerCurrentSourceUrl ->
                                        // Mark current episode as completed
                                        handlePlayerSessionEnd(
                                            sessionResult = PlayerSessionResult(
                                                positionMs = 0L,
                                                durationMs = null,
                                                isCompleted = true,
                                                selectedSourceUrl = playerCurrentSourceUrl ?: selectedVideoUrl,
                                                selectedAudioTrackId = null,
                                                selectedSubtitleTrackId = null
                                            ),
                                            selectedPlaybackId = selectedPlaybackId,
                                            playbackTrackSelectionStore = playbackTrackSelectionStore,
                                            sourceSelectionStore = sourceSelectionStore,
                                            pendingSourceSelection = playerState.pendingSourceSelection,
                                            onConsumePendingSelection = { playerState.pendingSourceSelection = null },
                                            onResumeHintResolved = { onDetailsResumePlaybackHintChange(it) },
                                            rememberSourceSelection = currentProfile?.rememberSourceSelection ?: true
                                        )

                                        val nextPlaybackId = episodePlaybackId(selectedMovieId, nextEpisode)
                                        val nextStreamId = episodeStreamId(selectedMovieId, nextEpisode)
                                        val nextPlaybackTitle = episodeDisplayTitle(nextEpisode)

                                        uiScope.launch {
                                            // Show loading feedback immediately
                                            val autoplay = currentProfile?.autoplayNextEpisode == true
                                            val autoSelect = currentProfile?.autoSelectSource == true
                                            val willAutoResolve = autoplay || autoSelect

                                            if (willAutoResolve) {
                                                playerState.isEpisodeSwitchLoading = true
                                            } else {
                                                playerState.pendingEpisodeSwitch = PendingEpisodeSwitch(
                                                    playbackId = nextPlaybackId,
                                                    playbackTitle = nextPlaybackTitle,
                                                    streams = null,
                                                    addonSubs = emptyList(),
                                                    playerCurrentSourceUrl = playerCurrentSourceUrl
                                                )
                                            }

                                            val rawStreams = try { addonRepository.getStreams("series", nextStreamId) } catch (_: Exception) { emptyList() }

                                            val streams = if (currentProfile?.sourceSortingEnabled == true) {
                                                val enabledQ = StreamSortingService.parseEnabledQualities(currentProfile?.sourceEnabledQualities ?: "4k,1080p,720p,unknown")
                                                val excludeP = StreamSortingService.parseExcludePhrases(currentProfile?.sourceExcludePhrases ?: "")
                                                val addonOrders = addonRepository.getAddonSortOrders()
                                                val excludedF = StreamSortingService.parseExcludedFormats(currentProfile?.sourceExcludedFormats ?: "")
                                                streamSortingService.sortAndFilter(rawStreams, enabledQ, excludeP, addonOrders, currentProfile?.sourceSortPrimary ?: "quality", currentProfile?.sourceMaxSizeGb ?: 0, excludedF)
                                            } else rawStreams

                                            if (streams.isEmpty()) {
                                                playerState.isEpisodeSwitchLoading = false
                                                playerState.pendingEpisodeSwitch = PendingEpisodeSwitch(
                                                    playbackId = nextPlaybackId,
                                                    playbackTitle = nextPlaybackTitle,
                                                    streams = emptyList(),
                                                    addonSubs = emptyList(),
                                                    playerCurrentSourceUrl = playerCurrentSourceUrl
                                                )
                                                return@launch
                                            }

                                            // Resolve the actual stream the user was watching (may differ from initial if they switched sources)
                                            val actualStream = if (playerCurrentSourceUrl != null) {
                                                playerState.pendingSourceSelection?.candidateStreams?.firstOrNull { candidate ->
                                                    resolvePlayableSourceUrl(candidate) == playerCurrentSourceUrl
                                                } ?: playerState.currentStream
                                            } else playerState.currentStream

                                            // Priority 1: Same bingeGroup + same addon as current stream (when autoplay or autoselect is on)
                                            val currentBingeGroup = actualStream?.behaviorHints?.bingeGroup
                                            val currentAddonUrl = actualStream?.addonTransportUrl
                                            val bingeMatch = if ((autoplay || autoSelect) && !currentBingeGroup.isNullOrBlank()) {
                                                streams.firstOrNull {
                                                    it.behaviorHints?.bingeGroup == currentBingeGroup &&
                                                        it.addonTransportUrl == currentAddonUrl &&
                                                        (!it.url.isNullOrBlank() || !it.infoHash.isNullOrBlank())
                                                }
                                            } else null
                                            // Priority 2: Remembered source
                                            val rememberSource = currentProfile?.rememberSourceSelection ?: true
                                            val preferred = if (rememberSource) sourceSelectionStore.findPreferredStream(nextPlaybackId, streams) else null
                                            // Priority 3: First playable (only when autoSelectSource is on)
                                            val streamToPlay = bingeMatch
                                                ?: preferred
                                                ?: if (autoSelect) streams.firstOrNull { !it.url.isNullOrBlank() || !it.infoHash.isNullOrBlank() } else null

                                            if (streamToPlay == null) {
                                                playerState.isEpisodeSwitchLoading = false
                                                playerState.pendingEpisodeSwitch = PendingEpisodeSwitch(
                                                    playbackId = nextPlaybackId,
                                                    playbackTitle = nextPlaybackTitle,
                                                    streams = streams,
                                                    addonSubs = emptyList(),
                                                    playerCurrentSourceUrl = playerCurrentSourceUrl
                                                )
                                                return@launch
                                            }

                                            val nextUrl = resolvePlayableSourceUrl(streamToPlay)
                                            if (nextUrl == null) {
                                                playerState.isEpisodeSwitchLoading = false
                                                playerState.pendingEpisodeSwitch = PendingEpisodeSwitch(
                                                    playbackId = nextPlaybackId,
                                                    playbackTitle = nextPlaybackTitle,
                                                    streams = streams,
                                                    addonSubs = emptyList(),
                                                    playerCurrentSourceUrl = playerCurrentSourceUrl
                                                )
                                                return@launch
                                            }

                                            // Auto-resolved: clear loading + switch
                                            playerState.isEpisodeSwitchLoading = false
                                            playerState.pendingEpisodeSwitch = null

                                            val subtitlePayload = buildSubtitlePayload(streamToPlay, emptyList())
                                            val sourcePayload = buildSourcePayload(streams, streamToPlay)

                                            playerState.pendingSourceSelection = PendingSourceSelection(
                                                playbackId = nextPlaybackId,
                                                launchedStream = streamToPlay,
                                                candidateStreams = streams
                                            )
                                            playerState.currentStream = streamToPlay

                                            if (nextUrl.startsWith("magnet:")) {
                                                onSelectedPlaybackIdChange(nextPlaybackId)
                                                onSelectedPlaybackTypeChange("series")
                                                onSelectedPlaybackTitleChange(nextPlaybackTitle)
                                                playerState.selectedPlayerSubtitles = subtitlePayload
                                                playerState.selectedPlayerSources = sourcePayload
                                                onTorrentProgressChange(TorrentProgress("Connecting to peers..."))
                                                TorrentService.onStreamReady = { localUrl ->
                                                    onTorrentProgressChange(null)
                                                    onSelectedVideoUrlChange(localUrl)
                                                }
                                                TorrentService.onStreamError = { error ->
                                                    onTorrentProgressChange(null)
                                                    if (BuildConfig.DEBUG) Log.e("LumeraTorrent", "Stream error: $error")
                                                }
                                                TorrentService.onStreamProgress = { progress ->
                                                    onTorrentProgressChange(progress)
                                                }
                                                val intent = Intent(context, TorrentService::class.java).apply {
                                                    putExtra("MAGNET_LINK", nextUrl)
                                                    putExtra("FILE_IDX", streamToPlay.fileIdx ?: -1)
                                                    putExtra("FILE_NAME", streamToPlay.behaviorHints?.filename ?: "")
                                                }
                                                context.startService(intent)
                                            } else {
                                                context.stopService(Intent(context, TorrentService::class.java))
                                                onSelectedPlaybackIdChange(nextPlaybackId)
                                                onSelectedPlaybackTypeChange("series")
                                                onSelectedPlaybackTitleChange(nextPlaybackTitle)
                                                playerState.selectedPlayerSubtitles = subtitlePayload
                                                playerState.selectedPlayerSources = sourcePayload
                                                onSelectedVideoUrlChange(nextUrl)
                                            }

                                                                                        run {
                                                val altId = {
                                                    val ep = playerState.currentEpisodeList.find { it.id == nextStreamId }
                                                    ep?.imdbId ?: (if (selectedMovieId.startsWith("tt")) selectedMovieId else null)?.let { "$it:${ep?.season ?: 1}:${ep?.episode ?: 1}" }
                                                }()
                                                uiScope.fetchAddonSubtitlesAsync(subtitleRepository, "series", nextStreamId, streamToPlay, playerState, alternateId = altId)
                                            }
                                        }
                                    }
                                } else null,
                                episodes = playerState.currentEpisodeList,
                                currentPlaybackId = selectedPlaybackId,
                                onEpisodeSelected = if (playerState.currentEpisodeList.isNotEmpty()) {
                                    { episode, playerCurrentSourceUrl ->
                                        val epPlaybackId = episodePlaybackId(selectedMovieId, episode)
                                        val epStreamId = episodeStreamId(selectedMovieId, episode)
                                        val epTitle = episodeDisplayTitle(episode)

                                        uiScope.launch {
                                            // Show loading feedback immediately
                                            val autoplay = currentProfile?.autoplayNextEpisode == true
                                            val autoSelect = currentProfile?.autoSelectSource == true
                                            val willAutoResolve = autoplay || autoSelect

                                            if (willAutoResolve) {
                                                playerState.isEpisodeSwitchLoading = true
                                            } else {
                                                playerState.pendingEpisodeSwitch = PendingEpisodeSwitch(
                                                    playbackId = epPlaybackId,
                                                    playbackTitle = epTitle,
                                                    streams = null,
                                                    addonSubs = emptyList(),
                                                    playerCurrentSourceUrl = playerCurrentSourceUrl
                                                )
                                            }

                                            val rawStreams2 = try { addonRepository.getStreams("series", epStreamId) } catch (_: Exception) { emptyList() }

                                            val streams = if (currentProfile?.sourceSortingEnabled == true) {
                                                val enabledQ = StreamSortingService.parseEnabledQualities(currentProfile?.sourceEnabledQualities ?: "4k,1080p,720p,unknown")
                                                val excludeP = StreamSortingService.parseExcludePhrases(currentProfile?.sourceExcludePhrases ?: "")
                                                val addonOrders = addonRepository.getAddonSortOrders()
                                                val excludedF = StreamSortingService.parseExcludedFormats(currentProfile?.sourceExcludedFormats ?: "")
                                                streamSortingService.sortAndFilter(rawStreams2, enabledQ, excludeP, addonOrders, currentProfile?.sourceSortPrimary ?: "quality", currentProfile?.sourceMaxSizeGb ?: 0, excludedF)
                                            } else rawStreams2

                                            if (streams.isEmpty()) {
                                                playerState.isEpisodeSwitchLoading = false
                                                playerState.pendingEpisodeSwitch = PendingEpisodeSwitch(
                                                    playbackId = epPlaybackId,
                                                    playbackTitle = epTitle,
                                                    streams = emptyList(),
                                                    addonSubs = emptyList(),
                                                    playerCurrentSourceUrl = playerCurrentSourceUrl
                                                )
                                                return@launch
                                            }

                                            // Resolve the actual stream the user was watching
                                            val actualStream = if (playerCurrentSourceUrl != null) {
                                                playerState.pendingSourceSelection?.candidateStreams?.firstOrNull { candidate ->
                                                    resolvePlayableSourceUrl(candidate) == playerCurrentSourceUrl
                                                } ?: playerState.currentStream
                                            } else playerState.currentStream

                                            // Priority 1: Same bingeGroup + same addon as current stream (when autoplay or autoselect is on)
                                            val currentBingeGroup = actualStream?.behaviorHints?.bingeGroup
                                            val currentAddonUrl = actualStream?.addonTransportUrl
                                            val bingeMatch = if ((autoplay || autoSelect) && !currentBingeGroup.isNullOrBlank()) {
                                                streams.firstOrNull {
                                                    it.behaviorHints?.bingeGroup == currentBingeGroup &&
                                                        it.addonTransportUrl == currentAddonUrl &&
                                                        (!it.url.isNullOrBlank() || !it.infoHash.isNullOrBlank())
                                                }
                                            } else null

                                            // Priority 2: Auto-select first available (only when autoSelectSource is on)
                                            val streamToPlay = bingeMatch
                                                ?: if (autoSelect) streams.firstOrNull { !it.url.isNullOrBlank() || !it.infoHash.isNullOrBlank() } else null

                                            if (streamToPlay == null) {
                                                playerState.isEpisodeSwitchLoading = false
                                                playerState.pendingEpisodeSwitch = PendingEpisodeSwitch(
                                                    playbackId = epPlaybackId,
                                                    playbackTitle = epTitle,
                                                    streams = streams,
                                                    addonSubs = emptyList(),
                                                    playerCurrentSourceUrl = playerCurrentSourceUrl
                                                )
                                                return@launch
                                            }

                                            val epUrl = resolvePlayableSourceUrl(streamToPlay)
                                            if (epUrl == null) {
                                                playerState.isEpisodeSwitchLoading = false
                                                playerState.pendingEpisodeSwitch = PendingEpisodeSwitch(
                                                    playbackId = epPlaybackId,
                                                    playbackTitle = epTitle,
                                                    streams = streams,
                                                    addonSubs = emptyList(),
                                                    playerCurrentSourceUrl = playerCurrentSourceUrl
                                                )
                                                return@launch
                                            }

                                            // Auto-resolved: clear loading + switch
                                            playerState.isEpisodeSwitchLoading = false
                                            playerState.pendingEpisodeSwitch = null
                                            handlePlayerSessionEnd(
                                                sessionResult = PlayerSessionResult(
                                                    positionMs = 0L,
                                                    durationMs = null,
                                                    isCompleted = false,
                                                    selectedSourceUrl = playerCurrentSourceUrl ?: selectedVideoUrl,
                                                    selectedAudioTrackId = null,
                                                    selectedSubtitleTrackId = null
                                                ),
                                                selectedPlaybackId = selectedPlaybackId,
                                                playbackTrackSelectionStore = playbackTrackSelectionStore,
                                                sourceSelectionStore = sourceSelectionStore,
                                                pendingSourceSelection = playerState.pendingSourceSelection,
                                                onConsumePendingSelection = { playerState.pendingSourceSelection = null },
                                                onResumeHintResolved = { onDetailsResumePlaybackHintChange(it) },
                                                rememberSourceSelection = currentProfile?.rememberSourceSelection ?: true
                                            )

                                            val subtitlePayload = buildSubtitlePayload(streamToPlay, emptyList())
                                            val sourcePayload = buildSourcePayload(streams, streamToPlay)

                                            playerState.pendingSourceSelection = PendingSourceSelection(
                                                playbackId = epPlaybackId,
                                                launchedStream = streamToPlay,
                                                candidateStreams = streams
                                            )
                                            playerState.currentStream = streamToPlay

                                            if (epUrl.startsWith("magnet:")) {
                                                onSelectedPlaybackIdChange(epPlaybackId)
                                                onSelectedPlaybackTypeChange("series")
                                                onSelectedPlaybackTitleChange(epTitle)
                                                playerState.selectedPlayerSubtitles = subtitlePayload
                                                playerState.selectedPlayerSources = sourcePayload
                                                onTorrentProgressChange(TorrentProgress("Connecting to peers..."))
                                                TorrentService.onStreamReady = { localUrl ->
                                                    onTorrentProgressChange(null)
                                                    onSelectedVideoUrlChange(localUrl)
                                                }
                                                TorrentService.onStreamError = { error ->
                                                    onTorrentProgressChange(null)
                                                    if (BuildConfig.DEBUG) Log.e("LumeraTorrent", "Stream error: $error")
                                                }
                                                TorrentService.onStreamProgress = { progress ->
                                                    onTorrentProgressChange(progress)
                                                }
                                                val intent = Intent(context, TorrentService::class.java).apply {
                                                    putExtra("MAGNET_LINK", epUrl)
                                                    putExtra("FILE_IDX", streamToPlay.fileIdx ?: -1)
                                                    putExtra("FILE_NAME", streamToPlay.behaviorHints?.filename ?: "")
                                                }
                                                context.startService(intent)
                                            } else {
                                                context.stopService(Intent(context, TorrentService::class.java))
                                                onSelectedPlaybackIdChange(epPlaybackId)
                                                onSelectedPlaybackTypeChange("series")
                                                onSelectedPlaybackTitleChange(epTitle)
                                                playerState.selectedPlayerSubtitles = subtitlePayload
                                                playerState.selectedPlayerSources = sourcePayload
                                                onSelectedVideoUrlChange(epUrl)
                                            }

                                                                                        run {
                                                val altId = {
                                                    val ep = playerState.currentEpisodeList.find { it.id == epStreamId }
                                                    ep?.imdbId ?: (if (selectedMovieId.startsWith("tt")) selectedMovieId else null)?.let { "$it:${ep?.season ?: 1}:${ep?.episode ?: 1}" }
                                                }()
                                                uiScope.fetchAddonSubtitlesAsync(subtitleRepository, "series", epStreamId, streamToPlay, playerState, alternateId = altId)
                                            }
                                        }
                                    }
                                } else null,
                                episodeSwitchSources = playerState.pendingEpisodeSwitch?.let { pending ->
                                    pending.streams?.mapNotNull { stream ->
                                        val url = resolvePlayableSourceUrl(stream) ?: return@mapNotNull null
                                        PlayerSourceOption(
                                            id = url,
                                            url = url,
                                            label = sourceDisplayLabel(stream),
                                            name = stream.name,
                                            title = stream.title,
                                            description = stream.description,
                                            fileIdx = stream.fileIdx ?: -1,
                                            fileName = stream.behaviorHints?.filename ?: ""
                                        )
                                    }?.distinctBy { it.url }
                                },
                                isEpisodeSwitchLoading = playerState.isEpisodeSwitchLoading,
                                episodeSwitchTitle = playerState.pendingEpisodeSwitch?.playbackTitle,
                                onEpisodeSwitchSourceSelected = playerState.pendingEpisodeSwitch?.let { pending ->
                                    { sourceUrl: String ->
                                        val streamToPlay = pending.streams?.firstOrNull { resolvePlayableSourceUrl(it) == sourceUrl }
                                        if (streamToPlay == null) {
                                            playerState.pendingEpisodeSwitch = null
                                            return@let
                                        }

                                        // Now save progress for current episode
                                        handlePlayerSessionEnd(
                                            sessionResult = PlayerSessionResult(
                                                positionMs = 0L,
                                                durationMs = null,
                                                isCompleted = false,
                                                selectedSourceUrl = pending.playerCurrentSourceUrl ?: selectedVideoUrl,
                                                selectedAudioTrackId = null,
                                                selectedSubtitleTrackId = null
                                            ),
                                            selectedPlaybackId = selectedPlaybackId,
                                            playbackTrackSelectionStore = playbackTrackSelectionStore,
                                            sourceSelectionStore = sourceSelectionStore,
                                            pendingSourceSelection = playerState.pendingSourceSelection,
                                            onConsumePendingSelection = { playerState.pendingSourceSelection = null },
                                            onResumeHintResolved = { onDetailsResumePlaybackHintChange(it) },
                                            rememberSourceSelection = currentProfile?.rememberSourceSelection ?: true
                                        )

                                        val subtitlePayload = buildSubtitlePayload(streamToPlay, emptyList())
                                        val sourcePayload = buildSourcePayload(pending.streams, streamToPlay)

                                        playerState.pendingSourceSelection = PendingSourceSelection(
                                            playbackId = pending.playbackId,
                                            launchedStream = streamToPlay,
                                            candidateStreams = pending.streams
                                        )
                                        playerState.currentStream = streamToPlay
                                        
                                        playerState.selectedPlayerSubtitles = subtitlePayload
                                        playerState.selectedPlayerSources = sourcePayload
                                        playerState.pendingEpisodeSwitch = null

                                        if (sourceUrl.startsWith("magnet:")) {
                                            onSelectedPlaybackIdChange(pending.playbackId)
                                            onSelectedPlaybackTypeChange("series")
                                            onSelectedPlaybackTitleChange(pending.playbackTitle)
                                            onTorrentProgressChange(TorrentProgress("Connecting to peers..."))
                                            TorrentService.onStreamReady = { localUrl ->
                                                onTorrentProgressChange(null)
                                                onSelectedVideoUrlChange(localUrl)
                                            }
                                            TorrentService.onStreamError = { error ->
                                                onTorrentProgressChange(null)
                                                if (com.lumera.app.BuildConfig.DEBUG) android.util.Log.e("LumeraTorrent", "Stream error: $error")
                                            }
                                            TorrentService.onStreamProgress = { progress ->
                                                onTorrentProgressChange(progress)
                                            }
                                            val intent = Intent(context, TorrentService::class.java).apply {
                                                putExtra("MAGNET_LINK", sourceUrl)
                                                putExtra("FILE_IDX", streamToPlay.fileIdx ?: -1)
                                                putExtra("FILE_NAME", streamToPlay.behaviorHints?.filename ?: "")
                                            }
                                            context.startService(intent)
                                        } else {
                                            context.stopService(Intent(context, TorrentService::class.java))
                                            onSelectedPlaybackIdChange(pending.playbackId)
                                            onSelectedPlaybackTypeChange("series")
                                            onSelectedPlaybackTitleChange(pending.playbackTitle)
                                            onSelectedVideoUrlChange(sourceUrl)
                                        }

                                                                                 run {
                                             val altId = {
                                                 val ep = playerState.currentEpisodeList.find { it.id == pending.playbackId }
                                                 ep?.imdbId ?: (if (selectedMovieId.startsWith("tt")) selectedMovieId else null)?.let { "$it:${ep?.season ?: 1}:${ep?.episode ?: 1}" }
                                             }()
                                             uiScope.fetchAddonSubtitlesAsync(subtitleRepository, "series", pending.playbackId, streamToPlay, playerState, alternateId = altId)
                                         }
                                    }
                                },
                                onEpisodeSwitchDismissed = { playerState.pendingEpisodeSwitch = null; playerState.isEpisodeSwitchLoading = false },
                                onMagnetSourceSelected = { magnetUrl, sourceFileIdx, sourceFileName, onReady ->
                                    onTorrentProgressChange(TorrentProgress("Connecting to peers..."))
                                    TorrentService.onStreamReady = { localUrl ->
                                                    onTorrentProgressChange(null)
                                        onReady(localUrl)
                                    }
                                    TorrentService.onStreamError = { error ->
                                                    onTorrentProgressChange(null)
                                        if (BuildConfig.DEBUG) Log.e("LumeraTorrent", "Source switch error: $error")
                                    }
                                    TorrentService.onStreamProgress = { progress ->
                                                    onTorrentProgressChange(progress)
                                    }
                                    val intent = Intent(context, TorrentService::class.java).apply {
                                        putExtra("MAGNET_LINK", magnetUrl)
                                        putExtra("FILE_IDX", sourceFileIdx)
                                        putExtra("FILE_NAME", sourceFileName)
                                    }
                                    context.startService(intent)
                                },
                                torrentProgress = torrentProgress,
                                onBack = { sessionResult ->
                                                    onTorrentProgressChange(null)
                                    handlePlayerSessionEnd(
                                        sessionResult = sessionResult,
                                        selectedPlaybackId = selectedPlaybackId,
                                        playbackTrackSelectionStore = playbackTrackSelectionStore,
                                        sourceSelectionStore = sourceSelectionStore,
                                        pendingSourceSelection = playerState.pendingSourceSelection,
                                        onConsumePendingSelection = { playerState.pendingSourceSelection = null },
                                        onResumeHintResolved = { onDetailsResumePlaybackHintChange(it) },
                                        rememberSourceSelection = currentProfile?.rememberSourceSelection ?: true
                                    )
                                    context.stopService(Intent(context, TorrentService::class.java))
                                    if (selectedPlaybackId.startsWith("trailer_")) {
                                        onTrailerReturnTokenIncrement()
                                    }
                                    onNavigateBack()
                                }
                            )
}
