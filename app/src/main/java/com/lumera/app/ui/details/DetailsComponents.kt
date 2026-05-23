package com.lumera.app.ui.details

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
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.lumera.app.R

/**
 * TV-style icon button that expands to reveal a text label on focus.
 * Mirrors the TopNavItem bubble-expand pattern.
 */
@Composable
fun ExpandableIconButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isActive: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val accentColor = MaterialTheme.colorScheme.primary

    val showText = isFocused

    // Estimate expanded width: icon(18) + padding(12+12) + gap(8) + text
    // ~8dp per uppercase character, minimum 110dp to fit short labels like RESUME/WATCHED
    val expandedWidth = (42 + 8 + (label.uppercase().length * 8)).coerceIn(110, 220).dp

    // Bubble width: icon-only → icon + label
    val bubbleWidth by animateDpAsState(
        targetValue = if (showText) expandedWidth else 42.dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "bubbleWidth"
    )

    // Text fade + slide
    val textAlpha by animateFloatAsState(
        targetValue = if (showText) 1f else 0f,
        animationSpec = tween(200),
        label = "textAlpha"
    )
    val textOffset by animateDpAsState(
        targetValue = if (showText) 0.dp else (-8).dp,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "textOffset"
    )

    // Icon and border colors
    val iconColor by animateColorAsState(
        targetValue = when {
            isFocused -> accentColor
            isActive -> accentColor
            else -> Color.White.copy(alpha = 0.7f)
        },
        animationSpec = tween(200),
        label = "iconColor"
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            isFocused -> accentColor
            isActive -> accentColor.copy(alpha = 0.5f)
            else -> Color.White.copy(alpha = 0.15f)
        },
        animationSpec = tween(200),
        label = "borderColor"
    )
    val bgColor by animateColorAsState(
        targetValue = when {
            isFocused -> accentColor.copy(alpha = 0.15f)
            isActive -> accentColor.copy(alpha = 0.08f)
            else -> Color.White.copy(alpha = 0.07f)
        },
        animationSpec = tween(200),
        label = "bgColor"
    )

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        label = "btnScale"
    )

    Row(
        modifier = modifier
            .width(bubbleWidth)
            .height(42.dp)
            .scale(scale)
            .clip(RoundedCornerShape(21.dp))
            .background(bgColor)
            .border(
                width = if (isFocused) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(21.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
            .padding(start = 12.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = iconColor,
            modifier = Modifier.size(18.dp)
        )

        if (showText) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label.uppercase(),
                color = accentColor,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.graphicsLayer {
                    alpha = textAlpha
                    translationX = textOffset.toPx()
                }
            )
        }
    }
}

@Composable
fun DialogButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isDestructive: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "dlgBtnScale")
    val activeColor = if (isDestructive) Color.Red else MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .height(50.dp)
            .scale(scale)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(0.08f))
            .border(
                1.dp,
                if (isFocused) activeColor else if (isDestructive) activeColor.copy(0.75f) else Color.White.copy(0.2f),
                RoundedCornerShape(8.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = when {
                isFocused -> activeColor
                isDestructive -> activeColor.copy(0.95f)
                else -> Color.White
            }
        )
    }
}

@Composable
fun MetaDot(textColor: Color) {
    Text(
        text = ".",
        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
        color = textColor.copy(alpha = 0.55f)
    )
}

@Composable
fun ImdbBadge() {
    Image(
        painter = painterResource(id = R.drawable.imdb_logo),
        contentDescription = "IMDb",
        modifier = Modifier.height(20.dp)
    )
}

@Composable
fun DynamicBackgroundLayer(
    posterUrl: String?,
    fallbackColor: Color
): Color {
    val context = androidx.compose.ui.platform.LocalContext.current
    var dynamicBgColor by remember(posterUrl) { mutableStateOf(fallbackColor) }

    LaunchedEffect(posterUrl) {
        if (posterUrl.isNullOrEmpty()) {
            dynamicBgColor = fallbackColor
            return@LaunchedEffect
        }
        val request = coil.request.ImageRequest.Builder(context)
            .data(posterUrl)
            .allowHardware(false)
            .build()
        val imageLoader = coil.ImageLoader(context)
        val result = imageLoader.execute(request)
        if (result is coil.request.SuccessResult) {
            val drawable = result.drawable
            if (drawable is android.graphics.drawable.BitmapDrawable) {
                val bitmap = drawable.bitmap
                val palette = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    androidx.palette.graphics.Palette.from(bitmap).generate()
                }
                val swatch = palette.vibrantSwatch
                    ?: palette.darkVibrantSwatch
                    ?: palette.mutedSwatch
                    ?: palette.darkMutedSwatch
                    ?: palette.lightVibrantSwatch
                    ?: palette.lightMutedSwatch
                    ?: palette.dominantSwatch
                if (swatch != null) {
                    val hsl = FloatArray(3)
                    androidx.core.graphics.ColorUtils.colorToHSL(swatch.rgb, hsl)
                    
                    // Saturation boost for more prominent color theme (if not grayscale)
                    if (hsl[1] > 0.05f) {
                        hsl[1] = hsl[1].coerceAtLeast(0.45f)
                    }
                    
                    // 12% lightness ensures rich colors that are dark enough for white text readability
                    hsl[2] = 0.12f
                    
                    val adjustedColorInt = androidx.core.graphics.ColorUtils.HSLToColor(hsl)
                    val targetColor = Color(adjustedColorInt)
                    
                    // Blend 80% dynamic color + 20% theme background for visible dynamic shift with theme integration
                    dynamicBgColor = Color(
                        red = targetColor.red * 0.8f + fallbackColor.red * 0.2f,
                        green = targetColor.green * 0.8f + fallbackColor.green * 0.2f,
                        blue = targetColor.blue * 0.8f + fallbackColor.blue * 0.2f,
                        alpha = 1f
                    )
                } else {
                    dynamicBgColor = fallbackColor
                }
            }
        } else {
            dynamicBgColor = fallbackColor
        }
    }

    val animatedBgColor by animateColorAsState(
        targetValue = dynamicBgColor,
        animationSpec = tween(durationMillis = 800),
        label = "bgColorAnimation"
    )
    return animatedBgColor
}

@Composable
fun TrailerBackgroundPlayer(
    trailerKey: String?,
    isLifecycleResumed: Boolean,
    isOverlayActive: Boolean,
    isParentalLocked: Boolean
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var trailerUrl by remember(trailerKey) { mutableStateOf<String?>(null) }
    var isTrailerPlaying by remember { mutableStateOf(false) }
    var exoPlayer by remember { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }

    LaunchedEffect(trailerKey) {
        trailerUrl = null
        isTrailerPlaying = false
        if (trailerKey.isNullOrEmpty()) return@LaunchedEffect
        
        kotlinx.coroutines.delay(2000) // 2-second delay of inactivity
        val source = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.lumera.app.data.trailer.YouTubeExtractor().extractPlaybackSource(trailerKey)
        }
        if (source != null) {
            trailerUrl = source.videoUrl
        }
    }

    val shouldPlayTrailer = !trailerUrl.isNullOrEmpty() && isLifecycleResumed && !isOverlayActive && !isParentalLocked

    LaunchedEffect(shouldPlayTrailer, trailerUrl) {
        if (shouldPlayTrailer && !trailerUrl.isNullOrEmpty()) {
            val renderersFactory = androidx.media3.exoplayer.DefaultRenderersFactory(context)
                .setEnableDecoderFallback(true)
            val player = androidx.media3.exoplayer.ExoPlayer.Builder(context, renderersFactory).build().apply {
                setMediaItem(androidx.media3.common.MediaItem.fromUri(trailerUrl!!))
                volume = 0f
                repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                playWhenReady = true
                prepare()
            }
            exoPlayer = player
            player.addListener(object : androidx.media3.common.Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    isTrailerPlaying = isPlaying
                }
            })
        } else {
            isTrailerPlaying = false
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    if (exoPlayer != null) {
        val alphaAnim by animateFloatAsState(
            targetValue = if (isTrailerPlaying) 0.6f else 0f,
            animationSpec = tween(1000),
            label = "trailer_fade"
        )
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    useController = false
                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    player = exoPlayer
                }
            },
            update = { view ->
                view.player = exoPlayer
            },
            modifier = Modifier
                .fillMaxSize()
                .alpha(alphaAnim)
        )
    }
}
