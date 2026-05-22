package com.lumera.app.ui.player.base

import android.view.KeyEvent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerticalAlignCenter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.lumera.app.R
import com.lumera.app.data.model.stremio.MetaVideo
import com.lumera.app.data.model.stremio.Stream
import com.lumera.app.ui.details.GlassSidebar
import com.lumera.app.ui.details.GlassSidebarScaffold
import com.lumera.app.ui.details.SidebarState
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val SUBTITLE_OFF_TRACK_ID = "#none"

internal enum class PlayerPanel {
    NONE,
    SOURCES,
    AUDIO,
    SUBTITLES,
    EPISODES
}

internal data class PanelItem(
    val id: String,
    val title: String,
    val subtitle: String? = null
)

internal data class SubtitleLanguageGroup(
    val key: String,
    val displayName: String,
    val tracks: List<PlayerTrackOption>,
    val isOffGroup: Boolean = false
)

internal data class AudioLanguageGroup(
    val key: String,
    val displayName: String,
    val tracks: List<PlayerTrackOption>
)

@Composable
internal fun BoxScope.PlayerSourceSidebar(
    visible: Boolean,
    title: String,
    sources: List<PlayerSourceOption>,
    currentSourceId: String?,
    onClose: () -> Unit,
    onSelectSource: (String) -> Unit
) {
    val sourceStreams = remember(sources) {
        sources.map { source ->
            Stream(
                name = source.name,
                title = source.title ?: source.label,
                description = source.description,
                url = source.url,
                addonTransportUrl = source.id
            )
        }
    }

    val sidebarState = if (visible) {
        SidebarState.Sources(
            streamTitle = title,
            streams = sourceStreams,
            selectedStreamId = currentSourceId
        )
    } else {
        SidebarState.Closed
    }

    GlassSidebar(
        state = sidebarState,
        onEpisodeSelected = {},
        onSourceSelected = { stream ->
            val sourceId = stream.addonTransportUrl ?: stream.url ?: return@GlassSidebar
            onSelectSource(sourceId)
        },
        onBack = onClose,
        onDismiss = onClose
    )
}

@Composable
internal fun BoxScope.PlayerEpisodeSidebar(
    visible: Boolean,
    episodes: List<MetaVideo>,
    currentPlaybackId: String?,
    onClose: () -> Unit,
    onEpisodeSelected: (MetaVideo) -> Unit
) {
    val sidebarState = if (visible) {
        SidebarState.Episodes(episodes)
    } else {
        SidebarState.Closed
    }

    GlassSidebar(
        state = sidebarState,
        currentEpisodeId = currentPlaybackId,
        onEpisodeSelected = onEpisodeSelected,
        onSourceSelected = {},
        onBack = onClose,
        onDismiss = onClose
    )
}

@Composable
internal fun BoxScope.EpisodeSwitchSourceSidebar(
    visible: Boolean,
    title: String,
    sources: List<PlayerSourceOption>?,
    onClose: () -> Unit,
    onSelectSource: (String) -> Unit
) {
    val sourceStreams = remember(sources) {
        sources?.map { source ->
            Stream(
                name = source.name,
                title = source.title ?: source.label,
                description = source.description,
                url = source.url,
                addonTransportUrl = source.url
            )
        }
    }

    val sidebarState = if (visible) {
        SidebarState.Sources(
            streamTitle = title,
            streams = sourceStreams,
            selectedStreamId = null
        )
    } else {
        SidebarState.Closed
    }

    GlassSidebar(
        state = sidebarState,
        onEpisodeSelected = {},
        onSourceSelected = { stream ->
            val url = stream.url ?: return@GlassSidebar
            onSelectSource(url)
        },
        onBack = onClose,
        onDismiss = onClose
    )
}

@Composable
internal fun BoxScope.AudioSelectionSidePanel(
    visible: Boolean,
    title: String,
    audioTracks: List<PlayerTrackOption>,
    selectedAudioId: String?,
    onClose: () -> Unit,
    onSelectTrack: (String?) -> Unit
) {
    if (!visible) return

    val languageGroups = remember(audioTracks) {
        buildAudioLanguageGroups(audioTracks)
    }
    val selectedGroupKeyFromTrack = remember(languageGroups, selectedAudioId) {
        resolveSelectedAudioLanguageKey(
            groups = languageGroups,
            selectedAudioId = selectedAudioId
        )
    }

    var selectedLanguageKey by remember(visible, selectedGroupKeyFromTrack, languageGroups) {
        mutableStateOf(
            selectedGroupKeyFromTrack ?: languageGroups.firstOrNull()?.key
        )
    }
    var optimisticSelectedAudioId by remember(visible) {
        mutableStateOf<String?>(null)
    }
    val effectiveSelectedAudioId = optimisticSelectedAudioId ?: selectedAudioId

    val selectedLanguageGroup = languageGroups.firstOrNull { it.key == selectedLanguageKey }
        ?: languageGroups.firstOrNull()
    val selectedLanguageTracks = selectedLanguageGroup?.tracks.orEmpty()
    val selectedTrackIndex = remember(selectedLanguageTracks, effectiveSelectedAudioId) {
        val index = selectedLanguageTracks.indexOfFirst { track -> track.id == effectiveSelectedAudioId }
        if (index >= 0) index else 0
    }
    val selectedLanguageIndex = remember(languageGroups, selectedLanguageKey) {
        val index = languageGroups.indexOfFirst { group -> group.key == selectedLanguageKey }
        if (index >= 0) index else 0
    }
    val languageGroupsStructureKey = remember(languageGroups) {
        languageGroups.joinToString(separator = "|") { group ->
            "${group.key}:${group.tracks.size}"
        }
    }

    val languageFocusRequesters = remember(languageGroups.size) {
        List(languageGroups.size) { FocusRequester() }
    }
    val trackFocusRequesters = remember(selectedLanguageTracks.size) {
        List(selectedLanguageTracks.size) { FocusRequester() }
    }
    val selectedTrackFocusRequester = trackFocusRequesters.getOrNull(selectedTrackIndex)
    val panelScope = rememberCoroutineScope()
    val languageListState = rememberLazyListState()
    val trackListState = rememberLazyListState()
    val selectedTrack = remember(audioTracks, effectiveSelectedAudioId) {
        audioTracks.firstOrNull { track -> track.id == effectiveSelectedAudioId }
            ?: audioTracks.firstOrNull { track -> track.selected }
    }
    val currentSelectionText = remember(selectedTrack, selectedLanguageGroup) {
        when {
            selectedTrack == null -> "Current: None"
            else -> {
                val language = selectedLanguageGroup?.displayName?.takeIf { it.isNotBlank() } ?: "Unknown"
                val format = selectedTrack.audioFormat
                if (format != null) "Current: $language - $format" else "Current: $language"
            }
        }
    }
    var lastFocusedLanguageIndex by remember(visible, languageGroupsStructureKey) {
        mutableIntStateOf(selectedLanguageIndex)
    }

    LaunchedEffect(visible, languageGroupsStructureKey) {
        if (!visible || languageGroups.isEmpty()) return@LaunchedEffect

        val targetKey = selectedGroupKeyFromTrack
            ?: selectedLanguageKey
            ?: languageGroups.firstOrNull()?.key
            ?: return@LaunchedEffect
        selectedLanguageKey = targetKey

        val targetIndex = languageGroups.indexOfFirst { group -> group.key == targetKey }
            .takeIf { it >= 0 }
            ?: 0
        lastFocusedLanguageIndex = targetIndex

        runCatching { languageListState.scrollToItem(targetIndex) }
        withFrameNanos { }
        runCatching { languageFocusRequesters[targetIndex].requestFocus() }
    }

    LaunchedEffect(selectedAudioId, languageGroups) {
        val key = resolveSelectedAudioLanguageKey(languageGroups, selectedAudioId) ?: return@LaunchedEffect
        selectedLanguageKey = key
    }
    LaunchedEffect(selectedAudioId) {
        if (
            selectedAudioId != null &&
            optimisticSelectedAudioId != null &&
            selectedAudioId == optimisticSelectedAudioId
        ) {
            optimisticSelectedAudioId = null
        }
    }

    val moveFocusToSelectedTrack = remember(
        selectedLanguageTracks,
        selectedTrackIndex,
        selectedTrackFocusRequester
    ) {
        {
            if (selectedLanguageTracks.isEmpty() || selectedTrackFocusRequester == null) {
                true
            } else {
                panelScope.launch {
                    runCatching { trackListState.scrollToItem(selectedTrackIndex) }
                    withFrameNanos { }
                    runCatching { selectedTrackFocusRequester.requestFocus() }
                }
                true
            }
        }
    }
    val requestAudioSelection: (String?) -> Unit = remember(
        panelScope,
        effectiveSelectedAudioId,
        onSelectTrack
    ) {
        { targetTrackId ->
            if (targetTrackId != null && targetTrackId != effectiveSelectedAudioId) {
                optimisticSelectedAudioId = targetTrackId
                panelScope.launch {
                    onSelectTrack(targetTrackId)
                }
            }
        }
    }

    GlassSidebarScaffold(
        visible = visible,
        onDismiss = onClose,
        panelWidth = 500.dp,
        panelPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 14.dp),
        overlayAlpha = 0.45f,
        enter = EnterTransition.None,
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(durationMillis = 180)
        ) + fadeOut(animationSpec = tween(durationMillis = 120))
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = currentSelectionText,
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Languages",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(0.46f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Tracks",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(0.54f)
                )
            }

            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .weight(0.46f)
                        .fillMaxHeight()
                ) {
                    LazyColumn(
                        state = languageListState,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        itemsIndexed(
                            items = languageGroups,
                            key = { _, group -> group.key }
                        ) { index, group ->
                            AudioLanguageListItem(
                                group = group,
                                selectedLanguage = group.key == selectedLanguageGroup?.key,
                                activeTrackInGroup = group.tracks.any { it.id == effectiveSelectedAudioId },
                                rightFocusRequester = selectedTrackFocusRequester,
                                onMoveRight = moveFocusToSelectedTrack,
                                focusRequester = languageFocusRequesters[index],
                                onFocused = {
                                    lastFocusedLanguageIndex = index
                                },
                                onClick = {
                                    selectedLanguageKey = group.key
                                    val topTrack = group.tracks.firstOrNull() ?: return@AudioLanguageListItem
                                    requestAudioSelection(topTrack.id)
                                }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.3f))
                )

                Box(
                    modifier = Modifier
                        .weight(0.54f)
                        .fillMaxHeight()
                ) {
                    if (selectedLanguageTracks.isEmpty()) {
                        Text(
                            text = "No audio tracks available",
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.TopStart)
                        )
                    } else {
                        LazyColumn(
                            state = trackListState,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            itemsIndexed(
                                items = selectedLanguageTracks,
                                key = { _, track -> track.id }
                            ) { index, track ->
                                AudioVariantListItem(
                                    track = track,
                                    selected = track.id == effectiveSelectedAudioId,
                                    leftFocusRequester = languageFocusRequesters.getOrNull(lastFocusedLanguageIndex),
                                    focusRequester = trackFocusRequesters.getOrNull(index),
                                    onClick = { requestAudioSelection(track.id) }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AudioLanguageListItem(
    group: AudioLanguageGroup,
    selectedLanguage: Boolean,
    activeTrackInGroup: Boolean,
    onClick: () -> Unit,
    onMoveRight: (() -> Boolean)? = null,
    rightFocusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val targetBackground = when {
        isFocused -> Color.White.copy(alpha = 0.95f)
        selectedLanguage -> MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
        else -> Color.Transparent
    }

    val background by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = tween(durationMillis = 120),
        label = "audioLanguageBackground"
    )
    val textColor by animateColorAsState(
        targetValue = if (isFocused) Color.Black else Color.White.copy(alpha = 0.98f),
        animationSpec = tween(durationMillis = 120),
        label = "audioLanguageText"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selectedLanguage && !isFocused) Color.White.copy(alpha = 0.38f) else Color.Transparent,
        animationSpec = tween(durationMillis = 120),
        label = "audioLanguageBorder"
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.01f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "audioLanguageScale"
    )

    val labelText = buildString {
        if (activeTrackInGroup) append("\u2022 ")
        append(group.displayName)
    }

    Box(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                if (rightFocusRequester != null) right = rightFocusRequester
            }
            .onFocusChanged { focusState ->
                if (focusState.isFocused) onFocused()
            }
            .onPreviewKeyEvent { keyEvent ->
                if (
                    keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                ) {
                    onMoveRight?.invoke() == true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = labelText,
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AudioVariantListItem(
    track: PlayerTrackOption,
    selected: Boolean,
    onClick: () -> Unit,
    leftFocusRequester: FocusRequester? = null,
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val targetBackground = when {
        isFocused -> Color.White.copy(alpha = 0.93f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.44f)
        else -> Color.Transparent
    }

    val background by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = tween(durationMillis = 120),
        label = "audioVariantBackground"
    )
    val textColor by animateColorAsState(
        targetValue = when {
            isFocused -> Color.Black
            else -> Color.White.copy(alpha = if (selected) 1f else 0.92f)
        },
        animationSpec = tween(durationMillis = 120),
        label = "audioVariantText"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
            selected -> Color.White.copy(alpha = 0.3f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 120),
        label = "audioVariantBorder"
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.01f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "audioVariantScale"
    )

    val formatChip = remember(track.audioFormat) {
        track.audioFormat
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    val labelText = buildString {
        if (selected) append("\u2022 ")
        append(track.label)
    }

    Box(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                if (leftFocusRequester != null) left = leftFocusRequester
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = labelText,
                style = MaterialTheme.typography.titleMedium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (formatChip != null) {
                SubtitleMetaChip(
                    text = formatChip,
                    inverted = isFocused
                )
            }
        }
    }
}

@Composable
internal fun BoxScope.SubtitleSelectionSidePanel(
    visible: Boolean,
    title: String,
    subtitleTracks: List<PlayerTrackOption>,
    selectedSubtitleId: String?,
    onClose: () -> Unit,
    onSelectTrack: (String?) -> Unit,
    onShowOffsetBar: () -> Unit = {},
    onShowSizeBar: () -> Unit = {},
    onShowDelayBar: () -> Unit = {},
    onShowColorBar: () -> Unit = {}
) {
    if (!visible) return

    val languageGroups = remember(subtitleTracks) {
        buildSubtitleLanguageGroups(subtitleTracks)
    }
    val selectedGroupKeyFromTrack = remember(languageGroups, selectedSubtitleId) {
        resolveSelectedSubtitleLanguageKey(
            groups = languageGroups,
            selectedSubtitleId = selectedSubtitleId
        )
    }

    var selectedLanguageKey by remember(visible, selectedGroupKeyFromTrack, languageGroups) {
        mutableStateOf(
            selectedGroupKeyFromTrack ?: languageGroups.firstOrNull()?.key
        )
    }
    var optimisticSelectedSubtitleId by remember(visible) {
        mutableStateOf<String?>(null)
    }
    val effectiveSelectedSubtitleId = optimisticSelectedSubtitleId ?: selectedSubtitleId

    val selectedLanguageGroup = languageGroups.firstOrNull { it.key == selectedLanguageKey }
        ?: languageGroups.firstOrNull()
    val selectedLanguageTracks = if (selectedLanguageGroup?.isOffGroup == true) {
        emptyList()
    } else {
        selectedLanguageGroup?.tracks.orEmpty()
    }
    val selectedTrackIndex = remember(selectedLanguageTracks, effectiveSelectedSubtitleId) {
        val index = selectedLanguageTracks.indexOfFirst { track -> track.id == effectiveSelectedSubtitleId }
        if (index >= 0) index else 0
    }
    val selectedLanguageIndex = remember(languageGroups, selectedLanguageKey) {
        val index = languageGroups.indexOfFirst { group -> group.key == selectedLanguageKey }
        if (index >= 0) index else 0
    }
    val languageGroupsStructureKey = remember(languageGroups) {
        languageGroups.joinToString(separator = "|") { group ->
            "${group.key}:${group.tracks.size}"
        }
    }

    val languageFocusRequesters = remember(languageGroups.size) {
        List(languageGroups.size) { FocusRequester() }
    }
    val trackFocusRequesters = remember(selectedLanguageTracks.size) {
        List(selectedLanguageTracks.size) { FocusRequester() }
    }
    val selectedTrackFocusRequester = trackFocusRequesters.getOrNull(selectedTrackIndex)
    val panelScope = rememberCoroutineScope()
    val languageListState = rememberLazyListState()
    val trackListState = rememberLazyListState()
    val selectedTrack = remember(subtitleTracks, effectiveSelectedSubtitleId) {
        subtitleTracks.firstOrNull { track -> track.id == effectiveSelectedSubtitleId }
            ?: subtitleTracks.firstOrNull { track -> track.selected }
    }
    val currentSelectionText = remember(selectedTrack, selectedLanguageGroup) {
        when {
            selectedTrack == null || isSubtitleOffTrack(selectedTrack) -> "Current: Off"
            else -> {
                val language = selectedLanguageGroup?.displayName?.takeIf { it.isNotBlank() } ?: "Unknown"
                "Current: $language - ${selectedTrack.label}"
            }
        }
    }
    var lastFocusedLanguageIndex by remember(visible, languageGroupsStructureKey) {
        mutableIntStateOf(selectedLanguageIndex)
    }

    LaunchedEffect(visible, languageGroupsStructureKey) {
        if (!visible || languageGroups.isEmpty()) return@LaunchedEffect

        val targetKey = selectedGroupKeyFromTrack
            ?: selectedLanguageKey
            ?: languageGroups.firstOrNull()?.key
            ?: return@LaunchedEffect
        selectedLanguageKey = targetKey

        val targetIndex = languageGroups.indexOfFirst { group -> group.key == targetKey }
            .takeIf { it >= 0 }
            ?: 0
        lastFocusedLanguageIndex = targetIndex

        runCatching { languageListState.scrollToItem(targetIndex) }
        withFrameNanos { }
        runCatching { languageFocusRequesters[targetIndex].requestFocus() }
    }

    LaunchedEffect(selectedSubtitleId, languageGroups) {
        val key = resolveSelectedSubtitleLanguageKey(languageGroups, selectedSubtitleId) ?: return@LaunchedEffect
        selectedLanguageKey = key
    }
    LaunchedEffect(selectedSubtitleId) {
        if (
            selectedSubtitleId != null &&
            optimisticSelectedSubtitleId != null &&
            selectedSubtitleId == optimisticSelectedSubtitleId
        ) {
            optimisticSelectedSubtitleId = null
        }
    }

    val moveFocusToSelectedSubtitle = remember(
        selectedLanguageTracks,
        selectedTrackIndex,
        selectedTrackFocusRequester
    ) {
        {
            if (selectedLanguageTracks.isEmpty() || selectedTrackFocusRequester == null) {
                true
            } else {
                panelScope.launch {
                    runCatching { trackListState.scrollToItem(selectedTrackIndex) }
                    withFrameNanos { }
                    runCatching { selectedTrackFocusRequester.requestFocus() }
                }
                true
            }
        }
    }
    val requestSubtitleSelection: (String?) -> Unit = remember(
        panelScope,
        effectiveSelectedSubtitleId,
        onSelectTrack
    ) {
        { targetTrackId ->
            val resolvedTargetId = targetTrackId ?: SUBTITLE_OFF_TRACK_ID
            if (resolvedTargetId != effectiveSelectedSubtitleId) {
                optimisticSelectedSubtitleId = resolvedTargetId
                panelScope.launch {
                    onSelectTrack(targetTrackId)
                }
            }
        }
    }

    GlassSidebarScaffold(
        visible = visible,
        onDismiss = onClose,
        panelWidth = 500.dp,
        panelPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 14.dp),
        overlayAlpha = 0.45f,
        enter = EnterTransition.None,
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(durationMillis = 180)
        ) + fadeOut(animationSpec = tween(durationMillis = 120))
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onShowOffsetBar,
                    colors = IconButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.VerticalAlignCenter,
                        contentDescription = "Subtitle offset",
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onShowSizeBar,
                    colors = IconButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.FormatSize,
                        contentDescription = "Subtitle size",
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onShowDelayBar,
                    colors = IconButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = "Subtitle delay",
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                IconButton(
                    onClick = onShowColorBar,
                    colors = IconButtonDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.palette_icon),
                        contentDescription = "Subtitle color",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = currentSelectionText,
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Languages",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(0.46f)
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = "Subtitles",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(0.54f)
                )
            }

            Row(
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .weight(0.46f)
                        .fillMaxHeight()
                ) {
                    LazyColumn(
                        state = languageListState,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxHeight()
                    ) {
                        itemsIndexed(
                            items = languageGroups,
                            key = { _, group -> group.key }
                        ) { index, group ->
                            SubtitleLanguageListItem(
                                group = group,
                                selectedLanguage = group.key == selectedLanguageGroup?.key,
                                activeTrackInGroup = group.tracks.any { it.id == effectiveSelectedSubtitleId },
                                rightFocusRequester = selectedTrackFocusRequester,
                                onMoveRight = moveFocusToSelectedSubtitle,
                                focusRequester = languageFocusRequesters[index],
                                onFocused = {
                                    lastFocusedLanguageIndex = index
                                },
                                onClick = {
                                    selectedLanguageKey = group.key
                                    val topTrack = group.tracks.firstOrNull() ?: return@SubtitleLanguageListItem
                                    requestSubtitleSelection(topTrack.id)
                                }
                            )
                        }
                        item {
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(Color.White.copy(alpha = 0.3f))
                )

                Box(
                    modifier = Modifier
                        .weight(0.54f)
                        .fillMaxHeight()
                ) {
                    if (selectedLanguageGroup?.isOffGroup == true) {
                        Text(
                            text = "Subtitles are off",
                            color = Color.White.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.TopStart)
                        )
                    } else if (selectedLanguageTracks.isEmpty()) {
                        Text(
                            text = "No subtitles available",
                            color = Color.White.copy(alpha = 0.75f),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.align(Alignment.TopStart)
                        )
                    } else {
                        LazyColumn(
                            state = trackListState,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            itemsIndexed(
                                items = selectedLanguageTracks,
                                key = { _, track -> track.id }
                            ) { index, track ->
                                SubtitleVariantListItem(
                                    track = track,
                                    selected = track.id == effectiveSelectedSubtitleId,
                                    leftFocusRequester = languageFocusRequesters.getOrNull(lastFocusedLanguageIndex),
                                    focusRequester = trackFocusRequesters.getOrNull(index),
                                    enabled = track.supported,
                                    onClick = { requestSubtitleSelection(track.id) }
                                )
                            }
                            item {
                                Spacer(modifier = Modifier.height(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleLanguageListItem(
    group: SubtitleLanguageGroup,
    selectedLanguage: Boolean,
    activeTrackInGroup: Boolean,
    onClick: () -> Unit,
    onMoveRight: (() -> Boolean)? = null,
    rightFocusRequester: FocusRequester? = null,
    onFocused: () -> Unit = {},
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val targetBackground = when {
        isFocused -> Color.White.copy(alpha = 0.95f)
        selectedLanguage -> MaterialTheme.colorScheme.primary.copy(alpha = 0.42f)
        else -> Color.Transparent
    }

    val background by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = tween(durationMillis = 120),
        label = "subtitleLanguageBackground"
    )
    val textColor by animateColorAsState(
        targetValue = if (isFocused) Color.Black else Color.White.copy(alpha = 0.98f),
        animationSpec = tween(durationMillis = 120),
        label = "subtitleLanguageText"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selectedLanguage && !isFocused) Color.White.copy(alpha = 0.38f) else Color.Transparent,
        animationSpec = tween(durationMillis = 120),
        label = "subtitleLanguageBorder"
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.01f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "subtitleLanguageScale"
    )

    val labelText = buildString {
        if (activeTrackInGroup) append("\u2022 ")
        append(group.displayName)
    }

    Box(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                if (rightFocusRequester != null) right = rightFocusRequester
            }
            .onFocusChanged { focusState ->
                if (focusState.isFocused) onFocused()
            }
            .onPreviewKeyEvent { keyEvent ->
                if (
                    keyEvent.nativeKeyEvent.action == KeyEvent.ACTION_DOWN &&
                    keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                ) {
                    onMoveRight?.invoke() == true
                } else {
                    false
                }
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Text(
            text = labelText,
            style = MaterialTheme.typography.titleMedium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SubtitleVariantListItem(
    track: PlayerTrackOption,
    selected: Boolean,
    onClick: () -> Unit,
    leftFocusRequester: FocusRequester? = null,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val targetBackground = when {
        !enabled -> Color.White.copy(alpha = 0.06f)
        isFocused -> Color.White.copy(alpha = 0.93f)
        selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.44f)
        else -> Color.Transparent
    }

    val background by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = tween(durationMillis = 120),
        label = "subtitleVariantBackground"
    )
    val textColor by animateColorAsState(
        targetValue = when {
            !enabled -> Color.White.copy(alpha = 0.48f)
            isFocused -> Color.Black
            else -> Color.White.copy(alpha = if (selected) 1f else 0.92f)
        },
        animationSpec = tween(durationMillis = 120),
        label = "subtitleVariantText"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused && enabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
            selected -> Color.White.copy(alpha = 0.3f)
            else -> Color.Transparent
        },
        animationSpec = tween(durationMillis = 120),
        label = "subtitleVariantBorder"
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused && enabled) 1.01f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "subtitleVariantScale"
    )
    val formatChip = remember(track.subtitleFormat) {
        track.subtitleFormat
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.uppercase(Locale.ROOT)
    }
    val chips = remember(track) { buildSubtitleTrackChips(track) }
    val displayLabel = remember(track.label, track.language, track.id) {
        buildSubtitleVariantDisplayLabel(track)
    }

    val labelText = buildString {
        if (selected) append("\u2022 ")
        append(displayLabel)
    }

    Box(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(4.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .focusProperties {
                if (leftFocusRequester != null) left = leftFocusRequester
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = labelText,
                    style = MaterialTheme.typography.titleMedium,
                    color = textColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (formatChip != null) {
                    SubtitleMetaChip(
                        text = formatChip,
                        inverted = isFocused
                    )
                }
            }
            if (chips.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.forEach { chip ->
                        SubtitleMetaChip(
                            text = chip,
                            inverted = isFocused
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.SelectionSidePanel(
    visible: Boolean,
    title: String,
    items: List<PanelItem>,
    selectedId: String?,
    onClose: () -> Unit,
    onSelect: (PanelItem) -> Unit
) {
    if (!visible) return

    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(visible, items) {
        if (!visible || items.isEmpty()) return@LaunchedEffect
        delay(120)
        runCatching { firstItemFocusRequester.requestFocus() }
    }

    GlassSidebarScaffold(
        visible = visible,
        onDismiss = onClose,
        panelWidth = 500.dp,
        overlayAlpha = 0.45f,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(durationMillis = 220)
        ) + fadeIn(animationSpec = tween(durationMillis = 180)),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(durationMillis = 180)
        ) + fadeOut(animationSpec = tween(durationMillis = 120))
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxHeight()
            ) {
                itemsIndexed(items) { index, item ->
                    PanelListItem(
                        item = item,
                        selected = item.id == selectedId,
                        focusRequester = if (index == 0) firstItemFocusRequester else null,
                        onClick = { onSelect(item) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun PanelListItem(
    item: PanelItem,
    selected: Boolean,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    val targetBackground = when {
        isFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.38f)
        selected -> Color.White.copy(alpha = 0.18f)
        else -> Color.White.copy(alpha = 0.06f)
    }

    val background by animateColorAsState(
        targetValue = targetBackground,
        animationSpec = tween(durationMillis = 120),
        label = "panelItemBackground"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent,
        animationSpec = tween(durationMillis = 120),
        label = "panelItemBorder"
    )
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.02f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "panelItemScale"
    )

    Box(
        modifier = Modifier
            .scale(scale)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = item.title,
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!item.subtitle.isNullOrBlank()) {
                    Text(
                        text = item.subtitle,
                        color = Color.White.copy(alpha = 0.72f),
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
internal fun SubtitleMetaChip(
    text: String,
    inverted: Boolean
) {
    val background = if (inverted) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.14f)
    val contentColor = if (inverted) Color.Black.copy(alpha = 0.82f) else Color.White.copy(alpha = 0.9f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

internal fun buildSubtitleTrackChips(track: PlayerTrackOption): List<String> {
    if (isSubtitleOffTrack(track)) return emptyList()

    val chips = mutableListOf<String>()
    chips += if (track.isExternal) "Add-on" else "Embedded"

    if (!track.supported) {
        chips += "Unsupported"
    }

    return chips.distinct()
}

internal fun buildSubtitleVariantDisplayLabel(track: PlayerTrackOption): String {
    val baseLabel = track.label.trim().ifBlank { "Subtitle" }
    if (isSubtitleOffTrack(track)) return baseLabel

    if (!isGenericSubtitleDescriptorLabel(baseLabel)) return baseLabel

    val languageName = subtitleLanguageDisplayName(
        groupKey = subtitleLanguageKey(track.language),
        rawLanguage = track.language
    ).takeIf { displayName ->
        displayName.isNotBlank() &&
            !displayName.equals("Unknown", ignoreCase = true) &&
            !displayName.equals("Off", ignoreCase = true)
    } ?: return baseLabel

    if (labelAlreadyContainsLanguage(baseLabel, languageName, track.language)) return baseLabel

    return "$languageName [$baseLabel]"
}

internal fun isGenericSubtitleDescriptorLabel(rawLabel: String): Boolean {
    val normalized = rawLabel
        .lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    return normalized in setOf(
        "cc",
        "cc1",
        "cc2",
        "cc3",
        "cc4",
        "sdh",
        "forced",
        "caption",
        "captions",
        "closed caption",
        "closed captions",
        "hearing impaired"
    )
}

internal fun labelAlreadyContainsLanguage(
    label: String,
    displayLanguage: String,
    rawLanguage: String?
): Boolean {
    val normalizedLabel = label.lowercase(Locale.ROOT)
    if (normalizedLabel.contains(displayLanguage.lowercase(Locale.ROOT))) return true

    val primaryLanguageCode = rawLanguage
        ?.trim()
        ?.replace('_', '-')
        ?.substringBefore('-')
        ?.lowercase(Locale.ROOT)
        ?.takeIf { it.isNotEmpty() }
        ?: return false

    return Regex("""\b$primaryLanguageCode\b""").containsMatchIn(normalizedLabel)
}

internal fun buildSubtitleLanguageGroups(
    subtitleTracks: List<PlayerTrackOption>
): List<SubtitleLanguageGroup> {
    if (subtitleTracks.isEmpty()) return emptyList()

    val groups = mutableListOf<SubtitleLanguageGroup>()
    val offTrack = subtitleTracks.firstOrNull { isSubtitleOffTrack(it) }

    if (offTrack != null) {
        groups += SubtitleLanguageGroup(
            key = "__off__",
            displayName = offTrack.label.ifBlank { "Off" },
            tracks = listOf(offTrack),
            isOffGroup = true
        )
    }

    val groupedByLanguage = linkedMapOf<String, MutableList<PlayerTrackOption>>()
    subtitleTracks
        .filterNot { track -> offTrack != null && track.id == offTrack.id }
        .forEach { track ->
            val languageKey = subtitleLanguageKey(track.language)
            groupedByLanguage.getOrPut(languageKey) { mutableListOf() }.add(track)
        }

    val sortedLanguageGroups = groupedByLanguage.map { (languageKey, tracks) ->
        SubtitleLanguageGroup(
            key = languageKey,
            displayName = subtitleLanguageDisplayName(
                groupKey = languageKey,
                rawLanguage = tracks.firstOrNull()?.language
            ),
            tracks = tracks.toList()
        )
    }.let { groupsToSort ->
        val collator = Collator.getInstance(Locale.getDefault()).apply {
            strength = Collator.PRIMARY
        }
        groupsToSort.sortedWith { a, b ->
            val aUnknown = a.key == "und"
            val bUnknown = b.key == "und"
            if (aUnknown != bUnknown) {
                return@sortedWith if (aUnknown) 1 else -1
            }
            val byName = collator.compare(a.displayName, b.displayName)
            if (byName != 0) byName else a.key.compareTo(b.key)
        }
    }

    groups += sortedLanguageGroups

    return groups
}

internal fun resolveSelectedSubtitleLanguageKey(
    groups: List<SubtitleLanguageGroup>,
    selectedSubtitleId: String?
): String? {
    if (groups.isEmpty()) return null

    val selectedTrackIdFromOptions = groups
        .asSequence()
        .flatMap { group -> group.tracks.asSequence() }
        .firstOrNull { track -> track.selected }
        ?.id

    val resolvedSelectedId = selectedSubtitleId
        ?: selectedTrackIdFromOptions
        ?: SUBTITLE_OFF_TRACK_ID
    return groups.firstOrNull { group ->
        group.tracks.any { track -> track.id == resolvedSelectedId }
    }?.key ?: groups.firstOrNull()?.key
}

internal fun subtitleLanguageKey(language: String?): String = normalizeLanguageToIso2(language)

internal fun subtitleLanguageDisplayName(groupKey: String, rawLanguage: String?): String {
    if (groupKey == "__off__") return "Off"
    if (groupKey == "und") return "Unknown"
    if (groupKey.contains('-')) {
        val canonicalDisplay = Locale.forLanguageTag(groupKey).displayName
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals(groupKey, ignoreCase = true) }
        if (canonicalDisplay != null) return canonicalDisplay
    }

    if (groupKey.length in 2..3) {
        val canonicalDisplay = Locale.forLanguageTag(groupKey).displayLanguage
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals(groupKey, ignoreCase = true) }
        if (canonicalDisplay != null) return canonicalDisplay
    }

    val normalizedTag = rawLanguage
        ?.trim()
        ?.replace('_', '-')
        ?.takeIf { it.isNotEmpty() }

    if (groupKey == "und" && normalizedTag == null) {
        return "Unknown"
    }

    val tag = normalizedTag ?: groupKey
    val localeLanguage = Locale.forLanguageTag(tag).displayLanguage
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    return localeLanguage ?: tag
}

internal fun isSubtitleOffTrack(track: PlayerTrackOption): Boolean {
    return track.id == SUBTITLE_OFF_TRACK_ID ||
        (track.language.isNullOrBlank() && track.label.equals("off", ignoreCase = true))
}

internal fun buildAudioLanguageGroups(
    audioTracks: List<PlayerTrackOption>
): List<AudioLanguageGroup> {
    if (audioTracks.isEmpty()) return emptyList()

    val groupedByLanguage = linkedMapOf<String, MutableList<PlayerTrackOption>>()
    audioTracks.forEach { track ->
        val languageKey = subtitleLanguageKey(track.language)
        groupedByLanguage.getOrPut(languageKey) { mutableListOf() }.add(track)
    }

    return groupedByLanguage.map { (languageKey, tracks) ->
        AudioLanguageGroup(
            key = languageKey,
            displayName = subtitleLanguageDisplayName(
                groupKey = languageKey,
                rawLanguage = tracks.firstOrNull()?.language
            ),
            tracks = tracks.toList()
        )
    }.let { groupsToSort ->
        val collator = Collator.getInstance(Locale.getDefault()).apply {
            strength = Collator.PRIMARY
        }
        groupsToSort.sortedWith { a, b ->
            val aUnknown = a.key == "und"
            val bUnknown = b.key == "und"
            if (aUnknown != bUnknown) {
                return@sortedWith if (aUnknown) 1 else -1
            }
            val byName = collator.compare(a.displayName, b.displayName)
            if (byName != 0) byName else a.key.compareTo(b.key)
        }
    }
}

internal fun resolveSelectedAudioLanguageKey(
    groups: List<AudioLanguageGroup>,
    selectedAudioId: String?
): String? {
    if (groups.isEmpty()) return null

    val selectedTrackIdFromOptions = groups
        .asSequence()
        .flatMap { group -> group.tracks.asSequence() }
        .firstOrNull { track -> track.selected }
        ?.id

    val resolvedSelectedId = selectedAudioId
        ?: selectedTrackIdFromOptions
        ?: return groups.firstOrNull()?.key

    return groups.firstOrNull { group ->
        group.tracks.any { track -> track.id == resolvedSelectedId }
    }?.key ?: groups.firstOrNull()?.key
}
