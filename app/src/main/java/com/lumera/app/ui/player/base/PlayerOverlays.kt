package com.lumera.app.ui.player.base

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import coil.compose.AsyncImage
import com.lumera.app.R
import com.lumera.app.data.torrent.TorrentProgress
import kotlinx.coroutines.delay

private val seriesEpisodePattern = Regex(
    pattern = """^\s*[Ss]\s*(\d+)\s*[:x]?\s*[Ee]\s*(\d+)\s*-\s*(.+)$"""
)

// Subtitle Color Presets
internal val PLAYER_TEXT_COLORS = listOf(
    "White" to 0xFFFFFFFF.toInt(),
    "Gray" to 0xFFBDBDBD.toInt(),
    "Yellow" to 0xFFFFEB3B.toInt(),
    "Cyan" to 0xFF00BCD4.toInt(),
    "Red" to 0xFFF44336.toInt(),
    "Orange" to 0xFFFF9800.toInt(),
    "Green" to 0xFF8BC34A.toInt()
)

internal val PLAYER_BACKGROUND_COLORS = listOf(
    "None" to 0x00000000,
    "Black" to 0xFF000000.toInt(),
    "Semi" to 0x80000000.toInt(),
    "Dark" to 0xFF212121.toInt()
)

@Composable
fun NextEpisodeButton(
    info: NextEpisodeInfo,
    countdownSeconds: Int,
    onPlayNow: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "nextEpScale")
    val accentColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Column(
        modifier = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .height(40.dp)
                .scale(scale)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(0.07f))
                .border(
                    if (isFocused) 2.dp else 1.dp,
                    if (isFocused) accentColor else Color.White.copy(0.15f),
                    RoundedCornerShape(8.dp)
                )
                .clickable(interactionSource = interactionSource, indication = null) { onPlayNow() }
                .focusRequester(focusRequester)
                .focusable(interactionSource = interactionSource)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.SkipNext,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "NEXT EPISODE IN $countdownSeconds...",
                color = if (isFocused) accentColor else Color.White.copy(0.8f),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Press back to cancel",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(0.5f)
        )
    }
}

@Composable
fun PlayNextEpisodeButton(
    onPlayNext: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "playNextScale")
    val accentColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Row(
        modifier = Modifier
            .padding(32.dp)
            .height(40.dp)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(0.07f))
            .border(
                if (isFocused) 2.dp else 1.dp,
                if (isFocused) accentColor else Color.White.copy(0.15f),
                RoundedCornerShape(8.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onPlayNext() }
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.SkipNext,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "PLAY NEXT EPISODE",
            color = if (isFocused) accentColor else Color.White.copy(0.8f),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1
        )
    }
}

@Composable
fun SkipIntroButton(
    onSkip: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "skipIntroScale")
    val accentColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Row(
        modifier = Modifier
            .padding(32.dp)
            .height(40.dp)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(0.07f))
            .border(
                if (isFocused) 2.dp else 1.dp,
                if (isFocused) accentColor else Color.White.copy(0.15f),
                RoundedCornerShape(8.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onSkip() }
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.SkipNext,
            contentDescription = null,
            tint = accentColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "SKIP INTRO",
            color = if (isFocused) accentColor else Color.White.copy(0.8f),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1
        )
    }
}

@Composable
fun SeekOverlay(
    currentPositionMs: Long,
    durationMs: Long
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 24.dp)
    ) {
        ProgressBar(
            currentPosition = currentPositionMs,
            duration = durationMs
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatTime(currentPositionMs)} / ${formatTime(durationMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
fun PlayerHeader(
    primaryText: String,
    secondaryText: String?,
    durationMs: Long = 0L,
    positionMs: Long = 0L
) {
    Column(
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = primaryText,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!secondaryText.isNullOrBlank()) {
            Text(
                text = secondaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (durationMs > 0L) {
            val remainingMs = (durationMs - positionMs).coerceAtLeast(0L)
            val endTime = remember(remainingMs / 60_000) {
                val formatter = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
                formatter.format(java.util.Date(System.currentTimeMillis() + remainingMs))
            }
            Text(
                text = "Ends at $endTime",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun PauseBrandOverlay(
    logoUrl: String?,
    primaryText: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.42f))
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 66.dp, end = 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = primaryText,
                    modifier = Modifier
                        .width(360.dp)
                        .height(120.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Text(
                    text = primaryText,
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PlayerErrorOverlay(
    errorMessage: String,
    onBack: () -> Unit,
    onSwitchSource: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null
) {
    val backFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        runCatching { backFocusRequester.requestFocus() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusable(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            PlayerStatusPill(
                text = errorMessage,
                background = Color(0xFF8B1E1E).copy(alpha = 0.85f)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerErrorButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    label = "BACK",
                    onClick = onBack,
                    focusRequester = backFocusRequester
                )
                if (onRetry != null) {
                    PlayerErrorButton(
                        icon = Icons.Default.Refresh,
                        label = "RETRY",
                        onClick = onRetry
                    )
                }
                if (onSwitchSource != null) {
                    PlayerErrorButton(
                        icon = Icons.Default.SwapHoriz,
                        label = "SWITCH SOURCE",
                        onClick = onSwitchSource
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerErrorButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester = remember { FocusRequester() }
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "errorBtnScale")
    val accentColor = MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .height(40.dp)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(0.07f))
            .border(
                if (isFocused) 2.dp else 1.dp,
                if (isFocused) accentColor else Color.White.copy(0.15f),
                RoundedCornerShape(8.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusRequester(focusRequester)
            .focusable(interactionSource = interactionSource)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isFocused) accentColor else Color.White.copy(0.8f),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            label,
            color = if (isFocused) accentColor else Color.White.copy(0.8f),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            maxLines = 1
        )
    }
}

@Composable
private fun PlayerStatusPill(
    text: String,
    background: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun LoadingOverlay(torrentProgress: TorrentProgress? = null) {
    val isError = torrentProgress?.isError == true
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = if (isError) 0.42f else 0.22f)),
        contentAlignment = Alignment.Center
    ) {
        if (isError) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Error",
                    tint = Color(0xFFE57373),
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = torrentProgress?.status ?: "Unknown error starting torrent engine",
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Press BACK to return",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val progress = torrentProgress?.progress
            if (progress != null) {
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    animationSpec = tween(durationMillis = 300),
                    label = "preload"
                )
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.15f),
                        strokeWidth = 4.dp
                    )
                    Text(
                        text = "${(animatedProgress * 100).toInt()}%",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            } else {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (torrentProgress != null) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(top = 80.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = torrentProgress.status,
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (torrentProgress.peers > 0 || torrentProgress.downloadSpeed > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val parts = mutableListOf<String>()
                        if (torrentProgress.downloadSpeed > 0) {
                            parts.add(formatSpeed(torrentProgress.downloadSpeed))
                        }
                        if (torrentProgress.peers > 0) {
                            parts.add("${torrentProgress.peers} peers")
                        }
                        if (torrentProgress.seeds > 0) {
                            parts.add("${torrentProgress.seeds} seeds")
                        }
                        Text(
                            text = parts.joinToString("  \u2022  "),
                            color = Color.White.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1_048_576 -> "${"%.1f".format(bytesPerSec / 1_048_576.0)} MB/s"
        bytesPerSec >= 1_024 -> "${"%.0f".format(bytesPerSec / 1_024.0)} KB/s"
        else -> "$bytesPerSec B/s"
    }
}

@Composable
fun BoxScope.SubtitleOffsetTopBar(
    visible: Boolean,
    offsetPercent: Int,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onClose: () -> Unit
) {
    if (!visible) return

    val playPauseFocus = remember { FocusRequester() }
    val decrementFocus = remember { FocusRequester() }
    val incrementFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { decrementFocus.requestFocus() }
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(140)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 32.dp)
    ) {
        Row(
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onPlayPause,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(44.dp)
                    .focusRequester(playPauseFocus)
                    .focusProperties {
                        left = playPauseFocus
                        right = decrementFocus
                        up = playPauseFocus
                        down = playPauseFocus
                    }
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(26.dp)
                )
            }

            IconButton(
                onClick = onDecrement,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(40.dp)
                    .focusRequester(decrementFocus)
                    .focusProperties {
                        left = playPauseFocus
                        right = incrementFocus
                        up = decrementFocus
                        down = decrementFocus
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = "Decrease offset",
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = "${offsetPercent}%",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(56.dp),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = onIncrement,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(40.dp)
                    .focusRequester(incrementFocus)
                    .focusProperties {
                        left = decrementFocus
                        right = closeFocus
                        up = incrementFocus
                        down = incrementFocus
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Increase offset",
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(40.dp)
                    .focusRequester(closeFocus)
                    .focusProperties {
                        left = incrementFocus
                        right = closeFocus
                        up = closeFocus
                        down = closeFocus
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close offset bar",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun BoxScope.SubtitleSizeTopBar(
    visible: Boolean,
    sizePercent: Int,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onClose: () -> Unit
) {
    if (!visible) return

    val playPauseFocus = remember { FocusRequester() }
    val decrementFocus = remember { FocusRequester() }
    val incrementFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { decrementFocus.requestFocus() }
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(140)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 32.dp)
    ) {
        Row(
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onPlayPause,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(44.dp)
                    .focusRequester(playPauseFocus)
                    .focusProperties {
                        left = playPauseFocus
                        right = decrementFocus
                        up = playPauseFocus
                        down = playPauseFocus
                    }
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(26.dp)
                )
            }

            IconButton(
                onClick = onDecrement,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(40.dp)
                    .focusRequester(decrementFocus)
                    .focusProperties {
                        left = playPauseFocus
                        right = incrementFocus
                        up = decrementFocus
                        down = decrementFocus
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = "Decrease size",
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = "${sizePercent}%",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(56.dp),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = onIncrement,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(40.dp)
                    .focusRequester(incrementFocus)
                    .focusProperties {
                        left = decrementFocus
                        right = closeFocus
                        up = incrementFocus
                        down = incrementFocus
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Increase size",
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(40.dp)
                    .focusRequester(closeFocus)
                    .focusProperties {
                        left = incrementFocus
                        right = closeFocus
                        up = closeFocus
                        down = closeFocus
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close size bar",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun BoxScope.SubtitleDelayTopBar(
    visible: Boolean,
    delayMs: Long,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    onClose: () -> Unit
) {
    if (!visible) return

    val playPauseFocus = remember { FocusRequester() }
    val decrementFocus = remember { FocusRequester() }
    val incrementFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { decrementFocus.requestFocus() }
    }

    val delayText = remember(delayMs) {
        val seconds = delayMs / 1000.0
        when {
            delayMs > 0L -> "+%.1fs".format(seconds)
            delayMs < 0L -> "%.1fs".format(seconds)
            else -> "0.0s"
        }
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(140)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 32.dp)
    ) {
        Row(
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onPlayPause,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(44.dp)
                    .focusRequester(playPauseFocus)
                    .focusProperties {
                        left = playPauseFocus
                        right = decrementFocus
                        up = playPauseFocus
                        down = playPauseFocus
                    }
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(26.dp)
                )
            }

            IconButton(
                onClick = onDecrement,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(40.dp)
                    .focusRequester(decrementFocus)
                    .focusProperties {
                        left = playPauseFocus
                        right = incrementFocus
                        up = decrementFocus
                        down = decrementFocus
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.Remove,
                    contentDescription = "Decrease delay",
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = delayText,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.width(64.dp),
                maxLines = 1,
                overflow = TextOverflow.Clip,
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = onIncrement,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(40.dp)
                    .focusRequester(incrementFocus)
                    .focusProperties {
                        left = decrementFocus
                        right = closeFocus
                        up = incrementFocus
                        down = incrementFocus
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = "Increase delay",
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(40.dp)
                    .focusRequester(closeFocus)
                    .focusProperties {
                        left = incrementFocus
                        right = closeFocus
                        up = closeFocus
                        down = closeFocus
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close delay bar",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
fun BoxScope.SubtitleColorTopBar(
    visible: Boolean,
    currentTextColor: Int,
    currentBackgroundColor: Int,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSetTextColor: (Int) -> Unit,
    onSetBackgroundColor: (Int) -> Unit,
    onClose: () -> Unit
) {
    if (!visible) return

    val playPauseFocus = remember { FocusRequester() }
    val firstTextChipFocus = remember { FocusRequester() }
    val lastTextChipFocus = remember { FocusRequester() }
    val firstBgChipFocus = remember { FocusRequester() }
    val lastBgChipFocus = remember { FocusRequester() }
    val closeFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        withFrameNanos { }
        runCatching { firstTextChipFocus.requestFocus() }
    }

    AnimatedVisibility(
        visible = true,
        enter = fadeIn(animationSpec = tween(180)),
        exit = fadeOut(animationSpec = tween(140)),
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 32.dp)
    ) {
        Row(
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = onPlayPause,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.White.copy(alpha = 0.15f),
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(44.dp)
                    .focusRequester(playPauseFocus)
                    .focusProperties {
                        left = playPauseFocus
                        right = firstTextChipFocus
                        up = playPauseFocus
                        down = playPauseFocus
                    }
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    modifier = Modifier.size(26.dp)
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val textColorCount = PLAYER_TEXT_COLORS.size
                val bgColorCount = PLAYER_BACKGROUND_COLORS.size

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.onPreviewKeyEvent {
                        it.key == Key.DirectionUp && it.type == KeyEventType.KeyDown
                    }
                ) {
                    Text(
                        text = "Text",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(32.dp)
                    )
                    PLAYER_TEXT_COLORS.forEachIndexed { index, (_, colorValue) ->
                        val chipModifier = when (index) {
                            0 -> Modifier
                                .focusRequester(firstTextChipFocus)
                                .focusProperties { left = playPauseFocus }
                            textColorCount - 1 -> Modifier
                                .focusRequester(lastTextChipFocus)
                                .focusProperties { right = closeFocus }
                            else -> Modifier
                        }
                        PlayerSubtitleColorChip(
                            color = Color(colorValue),
                            isSelected = currentTextColor == colorValue,
                            onClick = { onSetTextColor(colorValue) },
                            modifier = chipModifier
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.onPreviewKeyEvent {
                        it.key == Key.DirectionDown && it.type == KeyEventType.KeyDown
                    }
                ) {
                    Text(
                        text = "BG",
                        color = Color.White.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.width(32.dp)
                    )
                    PLAYER_BACKGROUND_COLORS.forEachIndexed { index, (_, colorValue) ->
                        val chipModifier = when (index) {
                            0 -> Modifier
                                .focusRequester(firstBgChipFocus)
                                .focusProperties { left = playPauseFocus }
                            bgColorCount - 1 -> Modifier
                                .focusRequester(lastBgChipFocus)
                                .focusProperties { right = closeFocus }
                            else -> Modifier
                        }
                        PlayerSubtitleColorChip(
                            color = Color(colorValue),
                            isSelected = currentBackgroundColor == colorValue,
                            isTransparent = colorValue == 0x00000000,
                            onClick = { onSetBackgroundColor(colorValue) },
                            modifier = chipModifier
                        )
                    }
                }
            }

            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.colors(
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .size(40.dp)
                    .focusRequester(closeFocus)
                    .focusProperties {
                        left = lastTextChipFocus
                        right = closeFocus
                        up = closeFocus
                        down = closeFocus
                    }
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close color bar",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun PlayerSubtitleColorChip(
    color: Color,
    isSelected: Boolean,
    isTransparent: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "subtitleColorChipScale"
    )

    Box(
        modifier = modifier
            .size(24.dp)
            .scale(scale)
            .clip(CircleShape)
            .then(
                if (isTransparent) {
                    Modifier
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                } else {
                    Modifier.background(color)
                }
            )
            .then(
                if (isSelected) Modifier.border(2.dp, Color.White, CircleShape)
                else if (isFocused) Modifier.border(2.dp, Color.White.copy(alpha = 0.7f), CircleShape)
                else Modifier
            )
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .focusable(interactionSource = interactionSource)
    )
}

internal fun resolveHeaderInfo(
    title: String,
    mediaType: String,
    seriesTitle: String?
): PlayerHeaderInfo {
    val cleanTitle = title.trim().ifBlank { "Untitled" }
    val cleanSeriesTitle = seriesTitle?.trim()?.takeIf { it.isNotBlank() }
    val isSeries = mediaType.equals("series", ignoreCase = true) ||
        mediaType.equals("tv", ignoreCase = true)

    if (!isSeries) {
        return PlayerHeaderInfo(primaryText = cleanTitle, secondaryText = null)
    }

    val normalizedEpisodeLine = normalizeEpisodeLine(cleanTitle)
    val primary = cleanSeriesTitle ?: cleanTitle
    val secondary = when {
        normalizedEpisodeLine != null && cleanSeriesTitle != null -> normalizedEpisodeLine
        cleanSeriesTitle != null && !cleanTitle.equals(cleanSeriesTitle, ignoreCase = true) -> cleanTitle
        else -> null
    }?.takeUnless { candidate ->
        candidate.equals(primary, ignoreCase = true)
    }

    return PlayerHeaderInfo(
        primaryText = primary,
        secondaryText = secondary
    )
}

private fun normalizeEpisodeLine(rawTitle: String): String? {
    val match = seriesEpisodePattern.find(rawTitle.trim()) ?: return null
    val season = match.groupValues[1].toIntOrNull() ?: return null
    val episode = match.groupValues[2].toIntOrNull() ?: return null
    val episodeTitle = match.groupValues[3].trim()
    return "S$season E$episode - $episodeTitle"
}
