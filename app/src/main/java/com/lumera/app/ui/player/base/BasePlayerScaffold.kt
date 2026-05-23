package com.lumera.app.ui.player.base

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.res.painterResource
import com.lumera.app.R
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lumera.app.data.model.stremio.MetaVideo
import com.lumera.app.data.model.stremio.Stream
import com.lumera.app.data.torrent.TorrentProgress
import com.lumera.app.ui.details.GlassSidebar
import com.lumera.app.ui.details.GlassSidebarScaffold
import com.lumera.app.ui.details.SidebarState
import java.text.Collator
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import coil.compose.AsyncImage

private const val CONTROLS_AUTO_HIDE_MS = 3_000L
private const val SEEK_OVERLAY_AUTO_HIDE_MS = 1_500L
private const val PAUSE_OVERLAY_IDLE_MS = 10_000L

@Composable
fun BasePlayerScaffold(
    playbackController: PlayerPlaybackController,
    renderSurface: PlayerRenderSurface,
    title: String,
    mediaType: String,
    seriesTitle: String? = null,
    logoUrl: String? = null,
    onBack: () -> Unit,
    skipSegmentInfo: SkipSegmentInfo? = null,
    nextEpisodeInfo: NextEpisodeInfo? = null,
    onAutoplayNextEpisode: ((currentSourceUrl: String?) -> Unit)? = null,
    autoplayEnabled: Boolean = false,
    autoplayThresholdMode: String = "percentage",
    autoplayThresholdPercent: Int = 95,
    autoplayThresholdSeconds: Int = 30,
    episodes: List<MetaVideo> = emptyList(),
    currentPlaybackId: String? = null,
    onEpisodeSelected: ((episode: MetaVideo, currentSourceUrl: String?) -> Unit)? = null,
    episodeSwitchSources: List<PlayerSourceOption>? = null,
    isEpisodeSwitchLoading: Boolean = false,
    episodeSwitchTitle: String? = null,
    onEpisodeSwitchSourceSelected: ((sourceUrl: String) -> Unit)? = null,
    onEpisodeSwitchDismissed: (() -> Unit)? = null,
    torrentProgress: TorrentProgress? = null,
    isTrailer: Boolean = false,
    modifier: Modifier = Modifier
) {
    val uiState by playbackController.uiState.collectAsState()
    val sources by playbackController.sourceOptions.collectAsState()
    val audioTracks by playbackController.audioTracks.collectAsState()
    val subtitleTracks by playbackController.subtitleTracks.collectAsState()

    val currentSourceUrl = sources.firstOrNull { it.id == uiState.currentSourceId }?.url

    val containerFocusRequester = remember { FocusRequester() }
    val playPauseFocusRequester = remember { FocusRequester() }
    val seekBarFocusRequester = remember { FocusRequester() }
    val nextEpisodeFocusRequester = remember { FocusRequester() }
    val skipIntroFocusRequester = remember { FocusRequester() }
    val playNextFocusRequester = remember { FocusRequester() }

    var activePanel by remember { mutableStateOf(PlayerPanel.NONE) }
    var showControls by remember { mutableStateOf(true) }
    var showSeekOverlay by remember { mutableStateOf(false) }
    var showPauseOverlay by remember { mutableStateOf(false) }
    var showSubtitleOffsetBar by remember { mutableStateOf(false) }
    var showSubtitleSizeBar by remember { mutableStateOf(false) }
    var showSubtitleDelayBar by remember { mutableStateOf(false) }
    var showSubtitleColorBar by remember { mutableStateOf(false) }
    var pendingPreviewSeekPosition by remember { mutableStateOf<Long?>(null) }
    var hideControlsSignal by remember { mutableIntStateOf(0) }
    var hideSeekOverlaySignal by remember { mutableIntStateOf(0) }
    var interactionSignal by remember { mutableIntStateOf(0) }

    var consumeNextBackHandler by remember { mutableStateOf(false) }

    val hasError = !uiState.errorMessage.isNullOrBlank()

    // --- Autoplay next episode (reset when playback controller changes, i.e. new episode) ---
    var autoplayCancelled by remember(playbackController) { mutableStateOf(false) }
    var countdownSeconds by remember(playbackController) { mutableIntStateOf(10) }
    var countdownActive by remember(playbackController) { mutableStateOf(false) }

    val isNearCompletion = remember(uiState.positionMs, uiState.durationMs, skipSegmentInfo, autoplayThresholdMode, autoplayThresholdPercent, autoplayThresholdSeconds, hasError) {
        if (hasError) return@remember false
        val duration = uiState.durationMs
        val position = uiState.positionMs
        if (duration <= 0L) false
        else {
            // Priority 1: Outro timestamp from IntroDB
            val outroStart = skipSegmentInfo?.outroStartMs
            if (outroStart != null && outroStart > 0) {
                position >= outroStart
            } else if (autoplayThresholdMode == "introdb") {
                // Only IntroDB mode — no fallback threshold
                false
            } else {
                // Configurable threshold fallback
                if (autoplayThresholdMode == "time") {
                    val remaining = duration - position
                    remaining <= autoplayThresholdSeconds * 1000L
                } else {
                    val ratio = position.toDouble() / duration.toDouble()
                    ratio >= autoplayThresholdPercent / 100.0
                }
            }
        }
    }

    // Skip intro visibility — never show during error
    val showSkipIntro = remember(uiState.positionMs, skipSegmentInfo, hasError) {
        if (hasError) return@remember false
        val info = skipSegmentInfo ?: return@remember false
        val start = info.introStartMs ?: return@remember false
        val end = info.introEndMs ?: return@remember false
        start > 0 && end > start && uiState.positionMs in start..end
    }

    val shouldShowNextEpisode = autoplayEnabled &&
        isNearCompletion &&
        nextEpisodeInfo != null &&
        onAutoplayNextEpisode != null &&
        !autoplayCancelled &&
        uiState.errorMessage.isNullOrBlank()

    val overlayVisible = countdownActive && nextEpisodeInfo != null && !autoplayCancelled
    val showPlayNextButton = uiState.isEnded &&
        (!autoplayEnabled || autoplayCancelled || !countdownActive) &&
        nextEpisodeInfo != null &&
        onAutoplayNextEpisode != null

    // Start countdown when near completion
    LaunchedEffect(shouldShowNextEpisode) {
        if (shouldShowNextEpisode) {
            countdownSeconds = 10
            countdownActive = true
        } else {
            countdownActive = false
        }
    }

    // Countdown timer — pauses when video is paused or buffering
    LaunchedEffect(countdownActive, uiState.isPlaying) {
        if (!countdownActive || !uiState.isPlaying) return@LaunchedEffect
        while (countdownSeconds > 0) {
            delay(1_000L)
            if (!countdownActive) return@LaunchedEffect
            countdownSeconds--
        }
        // Countdown finished
        if (countdownActive && !autoplayCancelled) {
            onAutoplayNextEpisode?.invoke(currentSourceUrl)
        }
    }

    // Auto-trigger on STATE_ENDED if overlay is showing
    LaunchedEffect(uiState.isEnded) {
        if (uiState.isEnded && countdownActive && !autoplayCancelled) {
            onAutoplayNextEpisode?.invoke(currentSourceUrl)
        }
    }

    // Re-focus buttons when controls hide
    LaunchedEffect(showControls, overlayVisible, showPlayNextButton) {
        if (!showControls) {
            when {
                overlayVisible -> runCatching { nextEpisodeFocusRequester.requestFocus() }
                showPlayNextButton -> runCatching { playNextFocusRequester.requestFocus() }
                showSkipIntro -> runCatching { skipIntroFocusRequester.requestFocus() }
            }
        }
    }

    // Also re-focus skip intro when it first appears and controls are not showing
    LaunchedEffect(showSkipIntro) {
        if (showSkipIntro && !showControls && !overlayVisible) {
            runCatching { skipIntroFocusRequester.requestFocus() }
        }
    }

    val headerInfo = remember(title, mediaType, seriesTitle) {
        resolveHeaderInfo(
            title = title,
            mediaType = mediaType,
            seriesTitle = seriesTitle
        )
    }
    val panelOpen = activePanel != PlayerPanel.NONE
    val displayPositionMs = pendingPreviewSeekPosition ?: uiState.positionMs
    val isPlaybackIntended = uiState.playWhenReady
    val showLoadingOverlay = uiState.errorMessage.isNullOrBlank() &&
        (uiState.isBuffering || !uiState.hasRenderedFirstFrame)
    val canShowPauseOverlay = !isPlaybackIntended &&
        !uiState.isBuffering &&
        uiState.isReady &&
        uiState.hasRenderedFirstFrame &&
        !panelOpen &&
        !showSubtitleOffsetBar &&
        !showSubtitleSizeBar &&
        !showSubtitleDelayBar &&
        !showSubtitleColorBar &&
        uiState.errorMessage.isNullOrBlank()

    fun markInteraction() {
        interactionSignal++
        showPauseOverlay = false
    }

    fun scheduleHideControls() {
        hideControlsSignal++
    }

    fun showControlsTemporarily() {
        showControls = true
        showSeekOverlay = false
        scheduleHideControls()
    }

    fun showSeekOverlayTemporarily() {
        showSeekOverlay = true
        hideSeekOverlaySignal++
    }

    fun closePanel() {
        markInteraction()
        activePanel = PlayerPanel.NONE
        if (showControls) {
            scheduleHideControls()
        }
    }

    fun handleBackAction() {
        when {
            episodeSwitchSources != null || isEpisodeSwitchLoading -> {
                onEpisodeSwitchDismissed?.invoke()
                activePanel = PlayerPanel.EPISODES
                showControls = true
            }
            !uiState.errorMessage.isNullOrBlank() && !panelOpen -> onBack()
            showSubtitleDelayBar -> {
                markInteraction()
                showSubtitleDelayBar = false
            }
            showSubtitleSizeBar -> {
                markInteraction()
                showSubtitleSizeBar = false
            }
            showSubtitleOffsetBar -> {
                markInteraction()
                showSubtitleOffsetBar = false
            }
            showSubtitleColorBar -> {
                markInteraction()
                showSubtitleColorBar = false
            }
            showPauseOverlay -> {
                markInteraction()
                showPauseOverlay = false
                showControls = true
                showSeekOverlay = false
                if (isPlaybackIntended) {
                    scheduleHideControls()
                }
            }
            panelOpen -> closePanel()
            showControls -> {
                markInteraction()
                showControls = false
                showSeekOverlay = false
            }
            overlayVisible -> {
                autoplayCancelled = true
                countdownActive = false
                showControls = true
                scheduleHideControls()
            }
            else -> onBack()
        }
    }

    fun previewSeekBy(deltaMs: Long) {
        val maxDuration = uiState.durationMs.takeIf { it > 0L } ?: Long.MAX_VALUE
        val basePosition = pendingPreviewSeekPosition ?: uiState.positionMs.coerceAtLeast(0L)
        val target = (basePosition + deltaMs)
            .coerceAtLeast(0L)
            .coerceAtMost(maxDuration)
        pendingPreviewSeekPosition = target
        showSeekOverlayTemporarily()
    }

    fun commitPreviewSeek() {
        val target = pendingPreviewSeekPosition ?: return
        playbackController.seekTo(target)
        pendingPreviewSeekPosition = null
        showSeekOverlayTemporarily()
    }

    BackHandler {
        if (consumeNextBackHandler) {
            consumeNextBackHandler = false
        } else {
            handleBackAction()
        }
    }

    val episodeSwitchOpen = episodeSwitchSources != null || isEpisodeSwitchLoading

    LaunchedEffect(showControls, isPlaybackIntended, panelOpen, showSubtitleOffsetBar, showSubtitleSizeBar, showSubtitleDelayBar, showSubtitleColorBar, hideControlsSignal, episodeSwitchOpen) {
        if (!showControls || !isPlaybackIntended || panelOpen || showSubtitleOffsetBar || showSubtitleSizeBar || showSubtitleDelayBar || showSubtitleColorBar) return@LaunchedEffect
        if (!uiState.errorMessage.isNullOrBlank()) return@LaunchedEffect
        if (episodeSwitchOpen) return@LaunchedEffect
        delay(CONTROLS_AUTO_HIDE_MS)
        if (showControls && isPlaybackIntended && !panelOpen && !showSubtitleOffsetBar && !showSubtitleSizeBar && !showSubtitleDelayBar && !showSubtitleColorBar) {
            showControls = false
        }
    }

    LaunchedEffect(showSeekOverlay, showControls, hideSeekOverlaySignal) {
        if (!showSeekOverlay || showControls) return@LaunchedEffect
        delay(SEEK_OVERLAY_AUTO_HIDE_MS)
        if (showSeekOverlay && !showControls) {
            showSeekOverlay = false
        }
    }

    LaunchedEffect(canShowPauseOverlay, interactionSignal) {
        if (!canShowPauseOverlay) {
            showPauseOverlay = false
            return@LaunchedEffect
        }
        delay(PAUSE_OVERLAY_IDLE_MS)
        if (canShowPauseOverlay) {
            showControls = false
            showSeekOverlay = false
            showPauseOverlay = true
        }
    }

    LaunchedEffect(showControls, panelOpen, showSubtitleOffsetBar, showSubtitleSizeBar, showSubtitleDelayBar, showSubtitleColorBar, hasError, episodeSwitchOpen) {
        if (showSubtitleOffsetBar || showSubtitleSizeBar || showSubtitleDelayBar || showSubtitleColorBar) return@LaunchedEffect
        if (overlayVisible || showPlayNextButton || showSkipIntro || hasError) return@LaunchedEffect
        if (episodeSwitchOpen) return@LaunchedEffect

        if (showControls && !panelOpen) {
            delay(250)
            runCatching { playPauseFocusRequester.requestFocus() }
        } else if (!showControls && !panelOpen) {
            runCatching { containerFocusRequester.requestFocus() }
        }
    }

    // Close panels when episode switch source sidebar or loading spinner appears
    LaunchedEffect(episodeSwitchSources, isEpisodeSwitchLoading) {
        if (episodeSwitchSources != null || isEpisodeSwitchLoading) {
            activePanel = PlayerPanel.NONE
            showControls = false
        }
    }

    LaunchedEffect(Unit) {
        runCatching { containerFocusRequester.requestFocus() }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(containerFocusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                if (isBackKey(keyEvent.nativeKeyEvent.keyCode)) {
                    if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN) {
                        handleBackAction()
                        consumeNextBackHandler = true
                    }
                    return@onPreviewKeyEvent true
                }

                if (!isBackKey(keyEvent.nativeKeyEvent.keyCode) &&
                    (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN ||
                        keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP)
                ) {
                    markInteraction()
                }

                if (keyEvent.nativeKeyEvent.keyCode != KeyEvent.KEYCODE_CAPTIONS) {
                    return@onPreviewKeyEvent false
                }

                if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_UP) {
                    return@onPreviewKeyEvent true
                }

                if (!panelOpen && !episodeSwitchOpen && subtitleTracks.isNotEmpty()) {
                    showControls = true
                    activePanel = PlayerPanel.SUBTITLES
                }
                true
            }
            .onKeyEvent { keyEvent ->
                if (!isBackKey(keyEvent.nativeKeyEvent.keyCode) &&
                    (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN ||
                        keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP)
                ) {
                    markInteraction()
                }

                // During error, only let back key through (handled in onPreviewKeyEvent)
                if (hasError) return@onKeyEvent false
                if (panelOpen || episodeSwitchOpen) return@onKeyEvent false

                if (keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_UP) {
                    when (keyEvent.nativeKeyEvent.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT,
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (!showControls) {
                                commitPreviewSeek()
                                return@onKeyEvent true
                            }
                        }
                    }
                    return@onKeyEvent false
                }

                if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                    return@onKeyEvent false
                }

                when (keyEvent.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER -> {
                        if (!showControls) {
                            playbackController.togglePlayPause()
                            showControlsTemporarily()
                            true
                        } else {
                            false
                        }
                    }

                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (!showControls) {
                            previewSeekBy(adaptiveSeekDeltaMs(keyEvent.nativeKeyEvent.repeatCount))
                            true
                        } else {
                            false
                        }
                    }

                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (!showControls) {
                            previewSeekBy(-adaptiveSeekDeltaMs(keyEvent.nativeKeyEvent.repeatCount))
                            true
                        } else {
                            false
                        }
                    }

                    KeyEvent.KEYCODE_DPAD_UP -> {
                        if (!showControls) {
                            showControlsTemporarily()
                            true
                        } else {
                            false
                        }
                    }

                    KeyEvent.KEYCODE_DPAD_DOWN -> {
                        if (!showControls) {
                            showControlsTemporarily()
                            true
                        } else {
                            false
                        }
                    }

                    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                        playbackController.togglePlayPause()
                        showControlsTemporarily()
                        true
                    }

                    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                        playbackController.seekBy(10_000L)
                        if (showControls) scheduleHideControls() else showSeekOverlayTemporarily()
                        true
                    }

                    KeyEvent.KEYCODE_MEDIA_REWIND -> {
                        playbackController.seekBy(-10_000L)
                        if (showControls) scheduleHideControls() else showSeekOverlayTemporarily()
                        true
                    }

                    else -> false
                }
            }
    ) {
        ComposePlayerSurface(
            renderSurface = renderSurface,
            modifier = Modifier.fillMaxSize()
        )

        AnimatedVisibility(
            visible = showPauseOverlay,
            enter = fadeIn(animationSpec = tween(320)),
            exit = fadeOut(animationSpec = tween(220))
        ) {
            PauseBrandOverlay(
                logoUrl = logoUrl,
                primaryText = headerInfo.primaryText
            )
        }

        AnimatedVisibility(
            visible = showLoadingOverlay,
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(120))
        ) {
            LoadingOverlay(torrentProgress = torrentProgress)
        }

        AnimatedVisibility(
            visible = !isTrailer && showControls && !panelOpen && !showSubtitleOffsetBar && !showSubtitleSizeBar && !showSubtitleDelayBar && !showSubtitleColorBar && uiState.errorMessage.isNullOrBlank(),
            enter = slideInVertically(animationSpec = tween(200)) { -it } + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(animationSpec = tween(200)) { -it } + fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 74.dp, end = 36.dp)
        ) {
            PlayerHeader(
                primaryText = headerInfo.primaryText,
                secondaryText = headerInfo.secondaryText,
                durationMs = uiState.durationMs,
                positionMs = displayPositionMs
            )
        }

        AnimatedVisibility(
            visible = showControls && !panelOpen && !showSubtitleOffsetBar && !showSubtitleSizeBar && !showSubtitleDelayBar && !showSubtitleColorBar && uiState.errorMessage.isNullOrBlank(),
            enter = slideInVertically(animationSpec = tween(200)) { it } + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(animationSpec = tween(200)) { it } + fadeOut(animationSpec = tween(200))
        ) {
            PlayerControlsOverlay(
                currentPositionMs = displayPositionMs,
                durationMs = uiState.durationMs,
                isPlaying = isPlaybackIntended,
                showSourceControl = !isTrailer && sources.size > 1,
                showAudioControl = !isTrailer && audioTracks.isNotEmpty(),
                showSubtitleControl = !isTrailer && subtitleTracks.isNotEmpty(),
                playPauseFocusRequester = playPauseFocusRequester,
                seekBarFocusRequester = seekBarFocusRequester,
                onPlayPause = {
                    markInteraction()
                    playbackController.togglePlayPause()
                    showControlsTemporarily()
                },
                onSeekBy = { deltaMs ->
                    markInteraction()
                    pendingPreviewSeekPosition = null
                    playbackController.seekBy(deltaMs)
                    scheduleHideControls()
                },
                onShowSourcesPanel = {
                    markInteraction()
                    activePanel = PlayerPanel.SOURCES
                    showControls = true
                    showSeekOverlay = false
                },
                onShowAudioPanel = {
                    markInteraction()
                    activePanel = PlayerPanel.AUDIO
                    showControls = true
                    showSeekOverlay = false
                },
                onShowSubtitlePanel = {
                    markInteraction()
                    activePanel = PlayerPanel.SUBTITLES
                    showControls = true
                    showSeekOverlay = false
                },
                onToggleResizeMode = {
                    markInteraction()
                    val nextMode = when (uiState.resizeMode) {
                        0 -> 4
                        4 -> 3
                        else -> 0
                    }
                    playbackController.setResizeMode(nextMode)
                    showControlsTemporarily()
                },
                showEpisodesControl = episodes.isNotEmpty() && onEpisodeSelected != null,
                onShowEpisodesPanel = {
                    markInteraction()
                    activePanel = PlayerPanel.EPISODES
                    showControls = true
                    showSeekOverlay = false
                },
                onResetHideTimer = {
                    markInteraction()
                    scheduleHideControls()
                }
            )
        }

        AnimatedVisibility(
            visible = showSeekOverlay && !showControls && !panelOpen && uiState.errorMessage.isNullOrBlank(),
            enter = fadeIn(animationSpec = tween(150)),
            exit = fadeOut(animationSpec = tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            SeekOverlay(
                currentPositionMs = displayPositionMs,
                durationMs = uiState.durationMs
            )
        }

        if (!uiState.errorMessage.isNullOrBlank()) {
            PlayerErrorOverlay(
                errorMessage = uiState.errorMessage.orEmpty(),
                onBack = onBack,
                onSwitchSource = if (sources.size > 1) {
                    {
                        markInteraction()
                        activePanel = PlayerPanel.SOURCES
                        showControls = true
                        showSeekOverlay = false
                    }
                } else null
            )
        }

        PlayerSourceSidebar(
            visible = activePanel == PlayerPanel.SOURCES,
            title = headerInfo.primaryText,
            onClose = { closePanel() },
            sources = sources,
            currentSourceId = uiState.currentSourceId,
            onSelectSource = { sourceId ->
                playbackController.selectSource(sourceId)
                closePanel()
                showSubtitleOffsetBar = false
                showSubtitleSizeBar = false
                showSubtitleDelayBar = false
                showSubtitleColorBar = false
            }
        )

        PlayerEpisodeSidebar(
            visible = activePanel == PlayerPanel.EPISODES && episodeSwitchSources == null && !isEpisodeSwitchLoading,
            episodes = episodes,
            currentPlaybackId = currentPlaybackId,
            onClose = { closePanel() },
            onEpisodeSelected = { episode ->
                closePanel()
                onEpisodeSelected?.invoke(episode, currentSourceUrl)
            }
        )

        EpisodeSwitchSourceSidebar(
            visible = episodeSwitchSources != null,
            title = episodeSwitchTitle ?: "",
            sources = episodeSwitchSources,
            onClose = {
                onEpisodeSwitchDismissed?.invoke()
                activePanel = PlayerPanel.EPISODES
                showControls = true
            },
            onSelectSource = { sourceUrl ->
                onEpisodeSwitchSourceSelected?.invoke(sourceUrl)
            }
        )

        // Centered loading spinner for auto-resolve paths (binge group, auto-select)
        if (isEpisodeSwitchLoading && episodeSwitchSources == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        AudioSelectionSidePanel(
            visible = activePanel == PlayerPanel.AUDIO,
            title = "Audio Tracks",
            audioTracks = audioTracks,
            selectedAudioId = uiState.selectedAudioTrackId,
            onClose = { closePanel() },
            onSelectTrack = { trackId ->
                markInteraction()
                playbackController.selectAudioTrack(trackId)
            }
        )

        SubtitleSelectionSidePanel(
            visible = activePanel == PlayerPanel.SUBTITLES,
            title = "Subtitles",
            subtitleTracks = subtitleTracks,
            selectedSubtitleId = uiState.selectedSubtitleTrackId,
            onClose = { closePanel() },
            onSelectTrack = { trackId ->
                markInteraction()
                playbackController.selectSubtitleTrack(trackId)
            },
            onShowOffsetBar = {
                closePanel()
                showSubtitleOffsetBar = true
            },
            onShowSizeBar = {
                closePanel()
                showSubtitleSizeBar = true
            },
            onShowDelayBar = {
                closePanel()
                showSubtitleDelayBar = true
            },
            onShowColorBar = {
                closePanel()
                showSubtitleColorBar = true
            }
        )

        SubtitleOffsetTopBar(
            visible = showSubtitleOffsetBar,
            offsetPercent = uiState.subtitleVerticalOffsetPercent,
            isPlaying = isPlaybackIntended,
            onPlayPause = {
                markInteraction()
                playbackController.togglePlayPause()
            },
            onDecrement = {
                markInteraction()
                playbackController.setSubtitleVerticalOffset(uiState.subtitleVerticalOffsetPercent - 1)
            },
            onIncrement = {
                markInteraction()
                playbackController.setSubtitleVerticalOffset(uiState.subtitleVerticalOffsetPercent + 1)
            },
            onClose = {
                markInteraction()
                showSubtitleOffsetBar = false
                showControls = true
                scheduleHideControls()
                runCatching { playPauseFocusRequester.requestFocus() }
            }
        )

        SubtitleSizeTopBar(
            visible = showSubtitleSizeBar,
            sizePercent = uiState.subtitleSizePercent,
            isPlaying = isPlaybackIntended,
            onPlayPause = {
                markInteraction()
                playbackController.togglePlayPause()
            },
            onDecrement = {
                markInteraction()
                playbackController.setSubtitleSize(uiState.subtitleSizePercent - 10)
            },
            onIncrement = {
                markInteraction()
                playbackController.setSubtitleSize(uiState.subtitleSizePercent + 10)
            },
            onClose = {
                markInteraction()
                showSubtitleSizeBar = false
                showControls = true
                scheduleHideControls()
                runCatching { playPauseFocusRequester.requestFocus() }
            }
        )

        SubtitleDelayTopBar(
            visible = showSubtitleDelayBar,
            delayMs = uiState.subtitleDelayMs,
            isPlaying = isPlaybackIntended,
            onPlayPause = {
                markInteraction()
                playbackController.togglePlayPause()
            },
            onDecrement = {
                markInteraction()
                playbackController.setSubtitleDelay(uiState.subtitleDelayMs - 100L)
            },
            onIncrement = {
                markInteraction()
                playbackController.setSubtitleDelay(uiState.subtitleDelayMs + 100L)
            },
            onClose = {
                markInteraction()
                showSubtitleDelayBar = false
                showControls = true
                scheduleHideControls()
                runCatching { playPauseFocusRequester.requestFocus() }
            }
        )

        SubtitleColorTopBar(
            visible = showSubtitleColorBar,
            currentTextColor = uiState.subtitleTextColor,
            currentBackgroundColor = uiState.subtitleBackgroundColor,
            isPlaying = isPlaybackIntended,
            onPlayPause = {
                markInteraction()
                playbackController.togglePlayPause()
            },
            onSetTextColor = { color ->
                markInteraction()
                playbackController.setSubtitleTextColor(color)
            },
            onSetBackgroundColor = { color ->
                markInteraction()
                playbackController.setSubtitleBackgroundColor(color)
            },
            onClose = {
                markInteraction()
                showSubtitleColorBar = false
                showControls = true
                scheduleHideControls()
                runCatching { playPauseFocusRequester.requestFocus() }
            }
        )

        // Animate bottom padding so buttons move above controls when visible
        val controlsVisible = showControls && !panelOpen && !showSubtitleOffsetBar && !showSubtitleSizeBar && !showSubtitleDelayBar && !showSubtitleColorBar && uiState.errorMessage.isNullOrBlank()
        val buttonBottomPadding by animateDpAsState(
            targetValue = if (controlsVisible) 120.dp else 32.dp,
            animationSpec = tween(200),
            label = "buttonBottomPadding"
        )

        // Skip intro button
        AnimatedVisibility(
            visible = showSkipIntro && !overlayVisible,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = buttonBottomPadding)
        ) {
            SkipIntroButton(
                onSkip = {
                    skipSegmentInfo?.introEndMs?.let { endMs ->
                        playbackController.seekTo(endMs)
                    }
                },
                focusRequester = skipIntroFocusRequester
            )
        }

        // Next episode countdown button
        AnimatedVisibility(
            visible = countdownActive && nextEpisodeInfo != null && !autoplayCancelled,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = buttonBottomPadding)
        ) {
            nextEpisodeInfo?.let { info ->
                NextEpisodeButton(
                    info = info,
                    countdownSeconds = countdownSeconds,
                    onPlayNow = { onAutoplayNextEpisode?.invoke(currentSourceUrl) },
                    focusRequester = nextEpisodeFocusRequester
                )
            }
        }

        // Play next episode button (shown after ended + user cancelled countdown)
        AnimatedVisibility(
            visible = showPlayNextButton,
            enter = fadeIn(animationSpec = tween(300)),
            exit = fadeOut(animationSpec = tween(200)),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = buttonBottomPadding)
        ) {
            PlayNextEpisodeButton(
                onPlayNext = { onAutoplayNextEpisode?.invoke(currentSourceUrl) },
                focusRequester = playNextFocusRequester
            )
        }
    }
}


private fun isBackKey(keyCode: Int): Boolean {
    return keyCode == android.view.KeyEvent.KEYCODE_BACK || keyCode == android.view.KeyEvent.KEYCODE_ESCAPE
}
