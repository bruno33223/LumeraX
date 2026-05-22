package com.lumera.app.ui.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.BackHandler
import com.lumera.app.ui.components.dialogs.ParentalPinDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import com.lumera.app.ui.theme.LocalRoundCorners
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.lumera.app.domain.AddonSubtitle
import com.lumera.app.domain.episodePlaybackId
import com.lumera.app.domain.episodeStreamId
import com.lumera.app.domain.episodeDisplayTitle
import com.lumera.app.data.model.stremio.MetaVideo
import com.lumera.app.data.model.stremio.Stream
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import kotlinx.coroutines.delay
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import com.lumera.app.R
import com.lumera.app.ui.home.DpadRepeatGate
import com.lumera.app.ui.home.FocusPivotSpec
import com.lumera.app.data.tmdb.TmdbCastInfo
import com.lumera.app.data.tmdb.TmdbCompanyInfo
import com.lumera.app.data.tmdb.TmdbMetaPreview
import com.lumera.app.data.tmdb.TmdbVideoInfo



@Composable
fun DetailsScreen(
    type: String,
    id: String,
    addonBaseUrl: String? = null,
    resumePlaybackHint: String? = null,
    autoSelectSource: Boolean = false,
    rememberSourceSelection: Boolean = true,
    onPlayClick: (String, String, String, String, String, String, Stream, List<AddonSubtitle>, List<Stream>, List<MetaVideo>) -> Unit,
    onNavigateToDetails: (type: String, id: String) -> Unit = { _, _ -> },
    onNavigateToCastDetail: (personId: Int, personName: String) -> Unit = { _, _ -> },
    onNavigateToStudioDetail: (entityId: Int, entityKind: String, entityName: String, sourceType: String) -> Unit = { _, _, _, _ -> },
    onPosterResolved: (poster: String) -> Unit = {},
    onTrailerClick: (youtubeKey: String, trailerName: String) -> Unit = { _, _ -> },
    isTrailerLoading: Boolean = false,
    trailerReturnToken: Int = 0,
    viewModel: DetailsViewModel = hiltViewModel(key = "details_${type}_${id}")
) {
    LaunchedEffect(type, id) { viewModel.loadDetails(type, id, addonBaseUrl) }

    val state by viewModel.state.collectAsState()
    val movie = state.meta
    val streamId = state.resolvedId ?: movie?.id ?: id // Resolved IMDb ID for stream/subtitle requests
    // Check contentKey to prevent stale content from the previous item flashing for one frame.
    // contentKey is set when meta loads and matches "$type:$id" of the navigation params.
    val isCurrentMovie = movie != null && !state.isLoading && state.contentKey == "$type:$id"
    val showMovieContent = isCurrentMovie

    LaunchedEffect(showMovieContent) {
        if (showMovieContent) {
            movie?.poster?.let { onPosterResolved(it) }
        }
    }
    val sidebarState = if (isCurrentMovie) state.sidebarState else SidebarState.Closed

    val accentColor = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground
    val lifecycleOwner = LocalLifecycleOwner.current

    var showClearProgressDialog by remember { mutableStateOf(false) }
    var pendingPlaybackId by remember(type, id) { mutableStateOf(id) }
    var pendingPlaybackType by remember(type, id) { mutableStateOf(type) }
    var pendingPlaybackTitle by remember(type, id) { mutableStateOf("") }
    val autoPlayStream = state.autoPlayStream
    val addonSubtitles = state.addonSubtitles
    val availableStreams = state.availableStreams

    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(autoPlayStream) {
        val stream = autoPlayStream ?: return@LaunchedEffect
        
        val urlToPlay = resolvePlayableUrl(stream)
        if (!urlToPlay.isNullOrEmpty()) {
            val playbackId = pendingPlaybackId.ifBlank { movie?.id ?: id }
            val playbackType = pendingPlaybackType.ifBlank { movie?.type ?: type }
            val playbackTitle = pendingPlaybackTitle.ifBlank { movie?.name ?: "" }
            onPlayClick(
                urlToPlay,
                playbackId,
                playbackType,
                playbackTitle,
                movie?.name ?: "",
                movie?.logo ?: "",
                stream,
                addonSubtitles,
                availableStreams,
                movie?.videos ?: emptyList()
            )
        }
        viewModel.consumeAutoPlayStream()
    }

    // Dynamic BG Palette
    val animatedBgColor = DynamicBackgroundLayer(
        posterUrl = movie?.poster ?: movie?.background,
        fallbackColor = bg
    )

    var isContentUnlocked by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    LaunchedEffect(state.contentKey) {
        isContentUnlocked = false
    }

    val contentAgeRating = remember(state.tmdbEnrichment?.ageRating) {
        parseAgeRating(state.tmdbEnrichment?.ageRating)
    }
    val isParentalLocked = remember(state.parentalAgeLimit, state.parentalPin, contentAgeRating, isContentUnlocked) {
        state.parentalAgeLimit > 0 && state.parentalPin.isNotEmpty() && contentAgeRating > state.parentalAgeLimit && !isContentUnlocked
    }

    val checkLockAndExecute: (() -> Unit) -> Unit = { action ->
        if (isParentalLocked) {
            pendingAction = action
            showPinDialog = true
        } else {
            action()
        }
    }

    var isLifecycleResumed by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshResumeState()
                isLifecycleResumed = true
            } else if (event == Lifecycle.Event.ON_PAUSE) {
                isLifecycleResumed = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isAnyOverlayActive = state.isLoadingStreams || isTrailerLoading || sidebarState !is SidebarState.Closed || showClearProgressDialog || showPinDialog

    BackHandler(enabled = sidebarState !is SidebarState.Closed) {
        viewModel.goBackInSidebar()
    }

    val firstButtonFocusRequester = remember { FocusRequester() }
    val episodesButtonFocusRequester = remember { FocusRequester() }
    val restoreFocusRequester = remember { FocusRequester() }

    // Track the previous sidebar state so we can restore focus to the
    // episodes button when the episodes sidebar closes.
    var previousSidebarState by remember { mutableStateOf<SidebarState>(SidebarState.Closed) }
    LaunchedEffect(sidebarState) {
        if (sidebarState is SidebarState.Closed && previousSidebarState is SidebarState.Episodes) {
            kotlinx.coroutines.delay(50)
            runCatching { episodesButtonFocusRequester.requestFocus() }
        }
        previousSidebarState = sidebarState
    }
    var restoreRowKey by rememberSaveable { mutableStateOf<String?>(null) }
    var restoreIndex by rememberSaveable { mutableStateOf(-1) }
    val listState = rememberLazyListState()

    val tmdbPending = state.tmdbEnabled && state.tmdbLoading
    val contentReady = showMovieContent && !tmdbPending

    // Track whether focus is inside the hero area (any button).
    // While hero has focus, suppress vertical pivot scrolling (viewport stays fixed,
    // just like the hero carousel on the home screen).
    var heroHasFocus by remember { mutableStateOf(false) }

    // Vertical pivot for smooth row-to-row scrolling (matches home screen SimpleLayout)
    val density = LocalDensity.current
    @OptIn(ExperimentalFoundationApi::class)
    val verticalPivot = remember(density) {
        val pivotPx = with(density) { 71.dp.toPx() }
        FocusPivotSpec(
            customOffset = pivotPx,
            skipScrollProvider = { heroHasFocus },
            stiffnessProvider = { Spring.StiffnessLow }
        )
    }

    // When focus returns to the hero from a row, animate back to the top
    LaunchedEffect(heroHasFocus) {
        if (heroHasFocus && (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)) {
            listState.animateScrollToItem(0)
        }
    }

    LaunchedEffect(contentReady) {
        if (contentReady) {
            if (restoreRowKey != null) {
                // Back navigation from Jetpack Nav: restore focus
                runCatching { restoreFocusRequester.requestFocus() }
                restoreRowKey = null
                restoreIndex = -1
            } else {
                // First load: focus hero button
                runCatching { firstButtonFocusRequester.requestFocus() }
            }
        }
    }

    // Restore focus when returning from trailer playback
    LaunchedEffect(trailerReturnToken) {
        if (trailerReturnToken > 0 && restoreRowKey != null) {
            runCatching { restoreFocusRequester.requestFocus() }
            restoreRowKey = null
            restoreIndex = -1
        }
    }

    // Smooth content reveal: animate alpha from 0→1 when content becomes ready
    val contentAlpha by animateFloatAsState(
        targetValue = if (contentReady) 1f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "content_reveal"
    )

    Box(modifier = Modifier.fillMaxSize().background(animatedBgColor)) {
        // Loading sweep — solid bg with subtle light sweep while data loads
        if (!contentReady) {
            com.lumera.app.ui.components.DetailsLoadingSweep()
        }
        if (showMovieContent && !tmdbPending) {
            val currentMovie = requireNotNull(movie)
            val bgImage = currentMovie.background ?: currentMovie.poster
            Box(modifier = Modifier.alpha(contentAlpha)) {
            AsyncImage(
                model = bgImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.6f)
            )

            // Background Trailer Player
            TrailerBackgroundPlayer(
                trailerKey = state.tmdbTrailer?.key,
                isLifecycleResumed = isLifecycleResumed,
                isOverlayActive = isAnyOverlayActive,
                isParentalLocked = isParentalLocked
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.0f to animatedBgColor,
                                0.1f to animatedBgColor.copy(alpha = 0.95f),
                                0.2f to animatedBgColor.copy(alpha = 0.85f),
                                0.3f to animatedBgColor.copy(alpha = 0.72f),
                                0.4f to animatedBgColor.copy(alpha = 0.58f),
                                0.55f to animatedBgColor.copy(alpha = 0.38f),
                                0.7f to animatedBgColor.copy(alpha = 0.20f),
                                0.85f to animatedBgColor.copy(alpha = 0.08f),
                                1.0f to Color.Transparent
                            ),
                            startX = 0f,
                            endX = 1500f
                        )
                    )
            )
            com.lumera.app.ui.components.NoiseOverlay()

            val enrichment = state.tmdbEnrichment
            val hasEnrichment = enrichment != null

            @OptIn(ExperimentalFoundationApi::class)
            CompositionLocalProvider(LocalBringIntoViewSpec provides verticalPivot) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 0.dp, bottom = 0.dp),
                verticalArrangement = Arrangement.spacedBy(15.dp)
            ) {
            // ── Hero item (fixed height, scroll-suppressed) ──
            item(key = "hero") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(500.dp)
                    .padding(start = 48.dp, end = 48.dp, top = 60.dp, bottom = 24.dp)
                    .onFocusChanged { heroHasFocus = it.hasFocus },
                verticalArrangement = Arrangement.Bottom
            ) {
                val titleStyle = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp,
                    lineHeight = 34.sp
                )

                if (!currentMovie.logo.isNullOrEmpty()) {
                    SubcomposeAsyncImage(
                        model = currentMovie.logo,
                        contentDescription = currentMovie.name,
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.BottomStart,
                        modifier = Modifier
                            .widthIn(max = 450.dp)
                            .heightIn(max = 90.dp),
                        error = {
                            Text(
                                text = currentMovie.name,
                                style = titleStyle,
                                color = textColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                softWrap = true
                            )
                        }
                    )
                } else {
                    Text(
                        text = currentMovie.name,
                        style = titleStyle,
                        color = textColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        softWrap = true
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                val typeLabel = currentMovie.type.replaceFirstChar { it.uppercase() }
                val genreLabel = currentMovie.genres
                    ?.firstOrNull()
                    ?.replaceFirstChar { it.uppercase() }
                    ?: "Unknown"
                val yearLabel = extractPrimaryYear(currentMovie.releaseInfo)

                val ageRating = enrichment?.ageRating
                val runtimeMin = enrichment?.runtimeMinutes
                    ?: currentMovie.runtime?.filter { it.isDigit() }?.toIntOrNull()

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = typeLabel,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = textColor.copy(alpha = 0.95f)
                    )
                    MetaDot(textColor)
                    Text(
                        text = genreLabel,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = textColor.copy(alpha = 0.95f)
                    )
                    MetaDot(textColor)
                    Text(
                        text = yearLabel,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                        color = textColor.copy(alpha = 0.95f)
                    )

                    ageRating?.let {
                        MetaDot(textColor)
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = textColor.copy(alpha = 0.7f),
                            modifier = Modifier
                                .border(1.dp, textColor.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        )
                    }

                    runtimeMin?.let {
                        MetaDot(textColor)
                        val hours = it / 60
                        val mins = it % 60
                        val display = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
                        Text(
                            text = display,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = textColor.copy(alpha = 0.95f)
                        )
                    }

                    currentMovie.imdbRating?.takeIf { it.isNotBlank() }?.let { rating ->
                        Spacer(modifier = Modifier.width(4.dp))
                        ImdbBadge()
                        Text(
                            text = rating,
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = textColor.copy(alpha = 0.95f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = currentMovie.description ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    color = textColor,
                    maxLines = 5
                )
                Spacer(modifier = Modifier.height(32.dp))

                val firstEpisode = remember(currentMovie.id, currentMovie.videos) {
                    findFirstEpisode(currentMovie.videos)
                }
                val hintedResumePlaybackId = remember(type, id, resumePlaybackHint) {
                    when (type) {
                        "series" -> resumePlaybackHint?.takeIf { playbackIdBelongsToSeries(id, it) }
                        else -> resumePlaybackHint?.takeIf { it == id }
                    }
                }
                val resumePlaybackId = hintedResumePlaybackId ?: state.resumePlaybackId
                val resumeEpisode = remember(currentMovie.id, currentMovie.videos, resumePlaybackId) {
                    if (type == "series") {
                        resolveEpisodeForPlaybackId(currentMovie.id, currentMovie.videos, resumePlaybackId)
                    } else {
                        null
                    }
                }
                val parsedResumeSeasonEpisode = remember(resumePlaybackId) {
                    parseSeasonEpisodeFromPlaybackId(resumePlaybackId)
                }
                val firstEpisodeSeason = firstEpisode?.season?.takeIf { it > 0 } ?: 1
                val firstEpisodeNumber = firstEpisode?.episode?.takeIf { it > 0 } ?: 1

                // No onNavigateDown — Compose's default DOWN navigation
                // handles hero→row transitions reliably after disposal/recomposition.

                val isInWatchlist by viewModel.isInWatchlist.collectAsState()

                if (type == "series") {
                    val playLabel = if (resumePlaybackId != null) {
                        val resumeSeason = resumeEpisode?.season?.takeIf { it > 0 } ?: parsedResumeSeasonEpisode?.first
                        val resumeNumber = resumeEpisode?.episode?.takeIf { it > 0 } ?: parsedResumeSeasonEpisode?.second
                        if (resumeSeason != null && resumeNumber != null) {
                            "Resume S${resumeSeason} E${resumeNumber}"
                        } else {
                            "Resume"
                        }
                    } else {
                        "Play S${firstEpisodeSeason} E${firstEpisodeNumber}"
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                // Redirect focus to the Play button before the system
                                // resolves the Down target, so the below-hero content
                                // always receives focus from the same horizontal position.
                                firstButtonFocusRequester.requestFocus()
                            }
                            false
                        }
                    ) {
                        ExpandableIconButton(
                            label = playLabel,
                            icon = Icons.Default.PlayArrow,
                            modifier = Modifier.focusRequester(firstButtonFocusRequester),
                            onClick = {
                                checkLockAndExecute {
                                    val ep = resumeEpisode ?: firstEpisode ?: return@checkLockAndExecute
                                    val trackId = resumePlaybackId ?: episodePlaybackId(streamId, ep)
                                    val epStreamId = episodeStreamId(streamId, ep)
                                    val epTitle = when {
                                        resumePlaybackId != null && resumeEpisode != null -> episodeDisplayTitle(resumeEpisode)
                                        resumePlaybackId != null && parsedResumeSeasonEpisode != null ->
                                            "S${parsedResumeSeasonEpisode.first}:E${parsedResumeSeasonEpisode.second} - ${currentMovie.name}"
                                        resumePlaybackId != null -> currentMovie.name
                                        else -> episodeDisplayTitle(ep)
                                    }
                                    pendingPlaybackId = trackId
                                    pendingPlaybackType = type
                                    pendingPlaybackTitle = epTitle
                                    viewModel.loadStreams(
                                        type = type,
                                        id = epStreamId,
                                        displayTitle = epTitle,
                                        sourceSelectionId = trackId,
                                        forceSourcePicker = false,
                                        autoSelectSource = autoSelectSource,
                                        rememberSourceSelection = rememberSourceSelection
                                    )
                                }
                            }
                        )

                        ExpandableIconButton(
                            label = stringResource(id = R.string.details_play_options_choose_source),
                            icon = Icons.Default.Settings,
                            onClick = {
                                checkLockAndExecute {
                                    val ep = resumeEpisode ?: firstEpisode ?: return@checkLockAndExecute
                                    val trackId = resumePlaybackId ?: episodePlaybackId(streamId, ep)
                                    val epStreamId = episodeStreamId(streamId, ep)
                                    val epTitle = when {
                                        resumePlaybackId != null && resumeEpisode != null -> episodeDisplayTitle(resumeEpisode)
                                        resumePlaybackId != null && parsedResumeSeasonEpisode != null ->
                                            "S${parsedResumeSeasonEpisode.first}:E${parsedResumeSeasonEpisode.second} - ${currentMovie.name}"
                                        resumePlaybackId != null -> currentMovie.name
                                        else -> episodeDisplayTitle(ep)
                                    }
                                    pendingPlaybackId = trackId
                                    pendingPlaybackType = type
                                    pendingPlaybackTitle = epTitle
                                    viewModel.loadStreams(
                                        type = type,
                                        id = epStreamId,
                                        displayTitle = epTitle,
                                        sourceSelectionId = trackId,
                                        forceSourcePicker = true,
                                        autoSelectSource = autoSelectSource,
                                        rememberSourceSelection = rememberSourceSelection
                                    )
                                }
                            }
                        )

                        ExpandableIconButton(
                            label = "Episodes",
                            icon = Icons.AutoMirrored.Filled.List,
                            modifier = Modifier.focusRequester(episodesButtonFocusRequester),
                            onClick = { viewModel.openEpisodes() }
                        )

                        val seriesTrailer = state.tmdbTrailer
                        if (seriesTrailer != null) {
                            ExpandableIconButton(
                                label = "Trailer",
                                icon = Icons.Default.Videocam,
                                onClick = { checkLockAndExecute { onTrailerClick(seriesTrailer.key, seriesTrailer.name) } }
                            )
                        }

                        ExpandableIconButton(
                            label = if (isInWatchlist) "Watchlisted" else "Add to watchlist",
                            icon = if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            isActive = isInWatchlist,
                            onClick = { viewModel.toggleWatchlist() }
                        )

                        if (resumePlaybackId != null) {
                            ExpandableIconButton(
                                label = "Clear Progress",
                                icon = Icons.Default.Close,
                                onClick = { showClearProgressDialog = true }
                            )
                        }
                    }
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                                firstButtonFocusRequester.requestFocus()
                            }
                            false
                        }
                    ) {
                        ExpandableIconButton(
                            label = if (resumePlaybackId != null) "Resume" else "Play Movie",
                            icon = Icons.Default.PlayArrow,
                            modifier = Modifier.focusRequester(firstButtonFocusRequester),
                            onClick = {
                                checkLockAndExecute {
                                    pendingPlaybackId = streamId
                                    pendingPlaybackType = type
                                    pendingPlaybackTitle = currentMovie.name
                                    viewModel.loadStreams(
                                        type = type,
                                        id = streamId,
                                        displayTitle = currentMovie.name,
                                        forceSourcePicker = false,
                                        autoSelectSource = autoSelectSource,
                                        rememberSourceSelection = rememberSourceSelection
                                    )
                                }
                            }
                        )

                        ExpandableIconButton(
                            label = stringResource(id = R.string.details_play_options_choose_source),
                            icon = Icons.Default.Settings,
                            onClick = {
                                checkLockAndExecute {
                                    pendingPlaybackId = streamId
                                    pendingPlaybackType = type
                                    pendingPlaybackTitle = currentMovie.name
                                    viewModel.loadStreams(
                                        type = type,
                                        id = streamId,
                                        displayTitle = currentMovie.name,
                                        forceSourcePicker = true,
                                        autoSelectSource = autoSelectSource,
                                        rememberSourceSelection = rememberSourceSelection
                                    )
                                }
                            }
                        )

                        val movieTrailer = state.tmdbTrailer
                        if (movieTrailer != null) {
                            ExpandableIconButton(
                                label = "Trailer",
                                icon = Icons.Default.Videocam,
                                onClick = { checkLockAndExecute { onTrailerClick(movieTrailer.key, movieTrailer.name) } }
                            )
                        }

                        ExpandableIconButton(
                            label = if (state.isMovieWatched) "Watched" else "Mark as watched",
                            icon = if (state.isMovieWatched) Icons.Default.Check else Icons.Default.Add,
                            isActive = state.isMovieWatched,
                            onClick = { viewModel.toggleMovieWatched() }
                        )

                        ExpandableIconButton(
                            label = if (isInWatchlist) "Watchlisted" else "Add to watchlist",
                            icon = if (isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            isActive = isInWatchlist,
                            onClick = { viewModel.toggleWatchlist() }
                        )

                        if (resumePlaybackId != null) {
                            ExpandableIconButton(
                                label = "Clear Progress",
                                icon = Icons.Default.Close,
                                onClick = { showClearProgressDialog = true }
                            )
                        }

                        // Works around a Compose focus-tree bug where requestFocus()
                        // silently fails when a container has a single focusable child.
                        if (resumePlaybackId == null && !isInWatchlist) {
                            Spacer(modifier = Modifier
                                .size(0.dp)
                                .onFocusChanged {
                                    if (it.isFocused) firstButtonFocusRequester.requestFocus()
                                }
                                .focusable()
                            )
                        }
                    }
                }
            }
            } // hero item

            // ── TMDB Enrichment Sections ──
            if (hasEnrichment) {
                val castMembers = enrichment?.castMembers.orEmpty()
                val directorMembers = enrichment?.directorMembers.orEmpty()
                val writerMembers = enrichment?.writerMembers.orEmpty()
                val companies = enrichment?.productionCompanies.orEmpty()
                val networks = enrichment?.networks.orEmpty()
                val tmdbRecommendations = state.tmdbRecommendations
                val tmdbCollection = state.tmdbCollection

                val leadingCrew = directorMembers + writerMembers
                // Modifier applied to the first TMDB section so Up always returns to the Play button
                var firstSectionClaimed = false
                fun firstSectionModifier(): Modifier {
                    if (firstSectionClaimed) return Modifier
                    firstSectionClaimed = true
                    return Modifier.onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp) {
                            firstButtonFocusRequester.requestFocus()
                            true
                        } else false
                    }
                }

                if (castMembers.isNotEmpty() || leadingCrew.isNotEmpty()) {
                    item(key = "tmdb_cast") {
                        val title = if (leadingCrew.isNotEmpty() && castMembers.isNotEmpty()) "Director & Cast"
                            else if (leadingCrew.isNotEmpty()) "Director"
                            else "Cast"
                        Column(modifier = firstSectionModifier().padding(top = 28.dp)) {
                            SectionHeader(title, textColor, Modifier.padding(start = 48.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            CastRow(
                                leadingCrew, castMembers, accentColor, textColor,
                                onPersonClick = { personId, personName, rowIndex ->
                                    restoreRowKey = "tmdb_cast"
                                    restoreIndex = rowIndex
                                    onNavigateToCastDetail(personId, personName)
                                },
                                restoreIndex = if (restoreRowKey == "tmdb_cast") restoreIndex else -1,
                                restoreFocusRequester = if (restoreRowKey == "tmdb_cast") restoreFocusRequester else null
                            )
                        }
                    }
                }



                val networkCompanies = networks.map { TmdbCompanyInfo(name = it.name, logo = it.logo, tmdbId = it.tmdbId) }
                val isTvShow = type == "series"

                // TV shows: Networks first, then Production. Movies: Production first, then Networks.
                val firstStudios = if (isTvShow) networkCompanies else companies
                val firstLabel = if (isTvShow) "Network" else "Production"
                val secondStudios = if (isTvShow) companies else networkCompanies
                val secondLabel = if (isTvShow) "Production" else "Network"

                val firstStudioKind = if (isTvShow) "network" else "company"
                val secondStudioKind = if (isTvShow) "company" else "network"

                if (firstStudios.isNotEmpty()) {
                    item(key = "tmdb_studios_first") {
                        Column(modifier = firstSectionModifier().padding(top = 28.dp)) {
                            SectionHeader(firstLabel, textColor, Modifier.padding(start = 48.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            StudioRow(
                                firstStudios, textColor, accentColor,
                                onStudioClick = { studioId, studioName ->
                                    restoreRowKey = "tmdb_studios_first"
                                    restoreIndex = firstStudios.indexOfFirst { it.tmdbId == studioId }
                                    onNavigateToStudioDetail(studioId, firstStudioKind, studioName, type)
                                },
                                restoreIndex = if (restoreRowKey == "tmdb_studios_first") restoreIndex else -1,
                                restoreFocusRequester = if (restoreRowKey == "tmdb_studios_first") restoreFocusRequester else null
                            )
                        }
                    }
                }

                if (secondStudios.isNotEmpty()) {
                    item(key = "tmdb_studios_second") {
                        Column(modifier = Modifier.padding(top = 28.dp)) {
                            SectionHeader(secondLabel, textColor, Modifier.padding(start = 48.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            StudioRow(
                                secondStudios, textColor, accentColor,
                                onStudioClick = { studioId, studioName ->
                                    restoreRowKey = "tmdb_studios_second"
                                    restoreIndex = secondStudios.indexOfFirst { it.tmdbId == studioId }
                                    onNavigateToStudioDetail(studioId, secondStudioKind, studioName, type)
                                },
                                restoreIndex = if (restoreRowKey == "tmdb_studios_second") restoreIndex else -1,
                                restoreFocusRequester = if (restoreRowKey == "tmdb_studios_second") restoreFocusRequester else null
                            )
                        }
                    }
                }

                if (tmdbRecommendations.isNotEmpty()) {
                    item(key = "tmdb_recs") {
                        Column(modifier = Modifier.padding(top = 28.dp)) {
                            SectionHeader("More Like This", textColor, Modifier.padding(start = 48.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            RecommendationRow(
                                        tmdbRecommendations, accentColor,
                                        rowKey = "tmdb_recs",
                                        onItemClick = { navType, navId, rowKey, index ->
                                            restoreRowKey = rowKey
                                            restoreIndex = index
                                            onNavigateToDetails(navType, navId)
                                        },
                                        restoreIndex = if (restoreRowKey == "tmdb_recs") restoreIndex else -1,
                                        restoreFocusRequester = if (restoreRowKey == "tmdb_recs") restoreFocusRequester else null
                                    )
                        }
                    }
                }

                val collectionName = state.tmdbCollectionName
                if (tmdbCollection.isNotEmpty() && collectionName != null) {
                    item(key = "tmdb_collection") {
                        Column(modifier = Modifier.padding(top = 28.dp)) {
                            SectionHeader(collectionName, textColor, Modifier.padding(start = 48.dp))
                            Spacer(modifier = Modifier.height(10.dp))
                            RecommendationRow(
                                            tmdbCollection, accentColor,
                                            rowKey = "tmdb_collection",
                                            onItemClick = { navType, navId, rowKey, index ->
                                                restoreRowKey = rowKey
                                                restoreIndex = index
                                                onNavigateToDetails(navType, navId)
                                            },
                                            restoreIndex = if (restoreRowKey == "tmdb_collection") restoreIndex else -1,
                                            restoreFocusRequester = if (restoreRowKey == "tmdb_collection") restoreFocusRequester else null
                                        )
                        }
                    }
                }

                item(key = "tmdb_spacer") { Spacer(modifier = Modifier.height(48.dp)) }
            }
            } // LazyColumn
            } // CompositionLocalProvider verticalPivot

            val showIndicator by remember {
                derivedStateOf {
                    listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 150
                }
            }
            AnimatedVisibility(
                visible = showIndicator,
                enter = fadeIn(animationSpec = tween(300)),
                exit = fadeOut(animationSpec = tween(300)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            ) {
                if (state.tmdbEnabled) {
                    val infiniteTransition = rememberInfiniteTransition(label = "arrow_bounce")
                    val offsetY by infiniteTransition.animateFloat(
                        initialValue = 0f,
                        targetValue = 8f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 600, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "offset"
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.graphicsLayer { translationY = offsetY }
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Scroll down for more",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.details_sync_tmdb_instruction),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
            } // contentAlpha Box
        }

        GlassSidebar(
            state = sidebarState,
            episodeProgressMap = state.episodeProgressMap,
            episodeEnrichmentMap = state.episodeEnrichmentMap,
            onToggleWatched = { episode -> viewModel.toggleEpisodeWatched(episode) },
            onDismiss = { viewModel.closeSidebar() },
            onBack = { viewModel.goBackInSidebar() },
            onEpisodeSelected = { episode ->
                checkLockAndExecute {
                    val trackId = episodePlaybackId(streamId, episode)
                    val epStreamId = episodeStreamId(streamId, episode)
                    val epTitle = episodeDisplayTitle(episode)
                    pendingPlaybackId = trackId
                    pendingPlaybackType = type
                    pendingPlaybackTitle = epTitle
                    viewModel.loadStreams(type, epStreamId, epTitle, sourceSelectionId = trackId, autoSelectSource = autoSelectSource, rememberSourceSelection = rememberSourceSelection)
                }
            },
            onSourceSelected = { stream ->
                val playbackId = pendingPlaybackId.ifBlank { movie?.id ?: id }
                val playbackType = pendingPlaybackType.ifBlank { movie?.type ?: type }
                viewModel.selectStreamAndPlay(playbackType, playbackId, stream)
            }
        )

        // Centered loading spinner for auto-resolve paths (remembered source, auto-select)
        if ((state.isLoadingStreams && sidebarState is SidebarState.Closed) || isTrailerLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accentColor)
            }
        }

        // Clear progress confirmation dialog
        if (showClearProgressDialog) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showClearProgressDialog = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 400.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(bg)
                            .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Clear Progress",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = textColor
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "This will remove all watch progress for this title, including on Trakt. This action cannot be undone.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = textColor.copy(0.7f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(Modifier.height(20.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                        ) {
                            val cancelFocusRequester = remember { FocusRequester() }

                            DialogButton(
                                text = "Cancel",
                                modifier = Modifier.width(120.dp).focusRequester(cancelFocusRequester),
                                onClick = { showClearProgressDialog = false }
                            )
                            DialogButton(
                                text = "Clear",
                                isDestructive = true,
                                modifier = Modifier.width(120.dp),
                                onClick = {
                                    showClearProgressDialog = false
                                    viewModel.confirmClearProgress()
                                }
                            )

                            LaunchedEffect(Unit) {
                                kotlinx.coroutines.delay(200)
                                runCatching { cancelFocusRequester.requestFocus() }
                            }
                        }
                    }
                }
            }
        }

        if (showPinDialog) {
            ParentalPinDialog(
                title = stringResource(id = R.string.parental_enter_pin),
                subtitle = stringResource(id = R.string.parental_enter_pin_desc),
                errorMessage = pinError,
                onPinSubmitted = { enteredPin ->
                    if (enteredPin == state.parentalPin) {
                        isContentUnlocked = true
                        showPinDialog = false
                        pinError = null
                        pendingAction?.invoke()
                        pendingAction = null
                    } else {
                        pinError = context.getString(R.string.parental_pin_incorrect)
                    }
                },
                onDismiss = {
                    showPinDialog = false
                    pinError = null
                    pendingAction = null
                }
            )
        }
    }
}
