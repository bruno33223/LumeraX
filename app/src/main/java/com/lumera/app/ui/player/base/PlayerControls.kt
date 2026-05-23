package com.lumera.app.ui.player.base

import android.view.KeyEvent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.IconButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.util.concurrent.TimeUnit

@Composable
fun PlayerControlsOverlay(
    currentPositionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    showSourceControl: Boolean,
    showAudioControl: Boolean,
    showSubtitleControl: Boolean,
    playPauseFocusRequester: FocusRequester,
    seekBarFocusRequester: FocusRequester,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onShowSourcesPanel: () -> Unit,
    onShowAudioPanel: () -> Unit,
    onShowSubtitlePanel: () -> Unit,
    onToggleResizeMode: () -> Unit,
    showEpisodesControl: Boolean = false,
    onShowEpisodesPanel: () -> Unit = {},
    onResetHideTimer: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(196.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.0f to Color.Transparent,
                            0.15f to Color.Black.copy(alpha = 0.05f),
                            0.3f to Color.Black.copy(alpha = 0.14f),
                            0.45f to Color.Black.copy(alpha = 0.28f),
                            0.6f to Color.Black.copy(alpha = 0.44f),
                            0.75f to Color.Black.copy(alpha = 0.60f),
                            0.88f to Color.Black.copy(alpha = 0.74f),
                            1.0f to Color.Black.copy(alpha = 0.82f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 28.dp, vertical = 20.dp)
        ) {
            FocusableSeekBar(
                currentPosition = currentPositionMs,
                duration = durationMs,
                onSeekBy = onSeekBy,
                onFocused = onResetHideTimer,
                focusRequester = seekBarFocusRequester,
                downFocusRequester = playPauseFocusRequester
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ControlButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        onClick = onPlayPause,
                        focusRequester = playPauseFocusRequester,
                        onFocused = onResetHideTimer,
                        upFocusRequester = seekBarFocusRequester,
                        buttonSize = 54.dp,
                        iconSize = 29.dp
                    )

                    if (showSubtitleControl) {
                        ControlButton(
                            icon = Icons.Default.ClosedCaption,
                            contentDescription = "Subtitles",
                            onClick = onShowSubtitlePanel,
                            onFocused = onResetHideTimer,
                            buttonSize = 36.dp,
                            iconSize = 18.dp
                        )
                    }

                    if (showAudioControl) {
                        ControlButton(
                            icon = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Audio tracks",
                            onClick = onShowAudioPanel,
                            onFocused = onResetHideTimer,
                            buttonSize = 36.dp,
                            iconSize = 20.dp
                        )
                    }

                    if (showEpisodesControl) {
                        ControlButton(
                            icon = Icons.Default.VideoLibrary,
                            contentDescription = "Episodes",
                            onClick = onShowEpisodesPanel,
                            onFocused = onResetHideTimer,
                            buttonSize = 36.dp,
                            iconSize = 18.dp
                        )
                    }

                    ControlButton(
                        icon = Icons.Default.AspectRatio,
                        contentDescription = "Aspect Ratio / Resize Mode",
                        onClick = onToggleResizeMode,
                        onFocused = onResetHideTimer,
                        buttonSize = 36.dp,
                        iconSize = 18.dp
                    )

                    if (showSourceControl || showAudioControl || showSubtitleControl) {
                        ControlButton(
                            icon = Icons.Default.SwapHoriz,
                            contentDescription = "More sources",
                            onClick = onShowSourcesPanel,
                            onFocused = onResetHideTimer,
                            buttonSize = 36.dp,
                            iconSize = 18.dp
                        )
                    }
                }

                Text(
                    text = "${formatTime(currentPositionMs)} \u2022 ${formatTime(durationMs)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.95f)
                )
            }
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    focusRequester: FocusRequester? = null,
    onFocused: (() -> Unit)? = null,
    upFocusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null,
    buttonSize: Dp = 40.dp,
    iconSize: Dp = 20.dp
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(buttonSize)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .focusProperties {
                if (upFocusRequester != null) up = upFocusRequester
                if (downFocusRequester != null) down = downFocusRequester
            }
            .onFocusChanged {
                if (it.isFocused) onFocused?.invoke()
            },
        colors = IconButtonDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = Color.White,
            contentColor = Color.White,
            focusedContentColor = Color.Black
        ),
        shape = IconButtonDefaults.shape(shape = CircleShape)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun FocusableSeekBar(
    currentPosition: Long,
    duration: Long,
    onSeekBy: (Long) -> Unit,
    onFocused: () -> Unit,
    focusRequester: FocusRequester? = null,
    downFocusRequester: FocusRequester? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .then(
                if (focusRequester != null) Modifier.focusRequester(focusRequester)
                else Modifier
            )
            .focusProperties {
                if (downFocusRequester != null) down = downFocusRequester
            }
            .onFocusChanged {
                if (it.isFocused) onFocused()
            }
            .onKeyEvent { keyEvent ->
                if (keyEvent.nativeKeyEvent.action != KeyEvent.ACTION_DOWN) {
                    return@onKeyEvent false
                }
                when (keyEvent.nativeKeyEvent.keyCode) {
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        onFocused()
                        onSeekBy(adaptiveSeekDeltaMs(keyEvent.nativeKeyEvent.repeatCount))
                        true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        onFocused()
                        onSeekBy(-adaptiveSeekDeltaMs(keyEvent.nativeKeyEvent.repeatCount))
                        true
                    }
                    else -> false
                }
            }
            .focusable(interactionSource = interactionSource)
    ) {
        ProgressBar(
            currentPosition = currentPosition,
            duration = duration,
            isFocused = isFocused
        )
    }
}

@Composable
fun ProgressBar(
    currentPosition: Long,
    duration: Long,
    isFocused: Boolean = false
) {
    val progress = if (duration > 0) {
        (currentPosition.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(100),
        label = "progress"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
    ) {
        val trackHeight = if (isFocused) 5.dp else 4.dp
        val thumbSize = if (isFocused) 12.dp else 9.dp
        val clampedProgress = animatedProgress.coerceIn(0f, 1f)
        val thumbOffset = (maxWidth - thumbSize) * clampedProgress

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.34f))
        )

        val primaryColor = MaterialTheme.colorScheme.primary

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxWidth(clampedProgress)
                .height(trackHeight)
                .clip(RoundedCornerShape(999.dp))
                .background(primaryColor)
        )

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .size(thumbSize)
                .clip(CircleShape)
                .background(primaryColor)
        )
    }
}

internal fun adaptiveSeekDeltaMs(repeatCount: Int): Long {
    return when {
        repeatCount >= 8 -> 30_000L
        repeatCount >= 3 -> 20_000L
        else -> 10_000L
    }
}

internal fun formatTime(millis: Long): String {
    if (millis <= 0L) return "0:00"

    val hours = TimeUnit.MILLISECONDS.toHours(millis)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60

    return if (hours > 0L) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
