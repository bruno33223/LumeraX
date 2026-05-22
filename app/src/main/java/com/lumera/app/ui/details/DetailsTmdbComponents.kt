package com.lumera.app.ui.details

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lumera.app.data.tmdb.TmdbCastInfo
import com.lumera.app.data.tmdb.TmdbCompanyInfo
import com.lumera.app.data.tmdb.TmdbMetaPreview
import com.lumera.app.ui.home.DpadRepeatGate
import com.lumera.app.ui.home.FocusPivotSpec
import com.lumera.app.ui.theme.LocalRoundCorners

@Composable
fun SectionHeader(title: String, textColor: Color, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        color = textColor.copy(alpha = 0.9f),
        modifier = modifier
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CastRow(
    leadingCrew: List<TmdbCastInfo>,
    cast: List<TmdbCastInfo>,
    accentColor: Color,
    textColor: Color,
    onPersonClick: (personId: Int, personName: String, rowIndex: Int) -> Unit = { _, _, _ -> },
    restoreIndex: Int = -1,
    restoreFocusRequester: FocusRequester? = null
) {
    val rowState = rememberLazyListState()
    val repeatGate = remember { DpadRepeatGate(horizontalRepeatIntervalMs = 150L) }
    val density = LocalDensity.current
    val startPad = 48.dp
    val paddingPx = remember(density) { with(density) { startPad.toPx() } }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val endPadding = (screenWidth - startPad - 96.dp).coerceAtLeast(120.dp)

    val pivotSpec = remember(paddingPx) {
        FocusPivotSpec(
            customOffset = paddingPx,
            stiffnessProvider = { Spring.StiffnessLow }
        )
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides pivotSpec) {
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            contentPadding = PaddingValues(start = startPad, end = endPadding)
        ) {
            val hasDivider = leadingCrew.isNotEmpty() && cast.isNotEmpty()
            val castOffset = leadingCrew.size + if (hasDivider) 1 else 0

            // Leading crew (directors, writers)
            itemsIndexed(leadingCrew, key = { i, it -> "crew_${it.tmdbId ?: i}" }) { index, member ->
                Box(modifier = Modifier.onPreviewKeyEvent {
                    if (repeatGate.shouldConsume(it)) return@onPreviewKeyEvent true
                    if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft && index == 0) true else false
                }) {
                    CastCard(
                        member, accentColor, textColor,
                        modifier = if (restoreFocusRequester != null && index == restoreIndex) Modifier.focusRequester(restoreFocusRequester) else Modifier
                    ) {
                        member.tmdbId?.let { id -> onPersonClick(id, member.name, index) }
                    }
                }
            }

            // Vertical divider between crew and cast
            if (hasDivider) {
                item(key = "cast_divider") {
                    Box(
                        modifier = Modifier.height(110.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(72.dp)
                                .background(Color.White.copy(alpha = 0.3f))
                        )
                    }
                }
            }

            // Regular cast
            itemsIndexed(cast.take(20), key = { i, it -> "cast_${it.tmdbId ?: i}" }) { index, member ->
                val isFirstOverall = leadingCrew.isEmpty() && index == 0
                val flatIndex = castOffset + index
                Box(modifier = Modifier.onPreviewKeyEvent {
                    if (repeatGate.shouldConsume(it)) return@onPreviewKeyEvent true
                    if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft && isFirstOverall) true else false
                }) {
                    CastCard(
                        member, accentColor, textColor,
                        modifier = if (restoreFocusRequester != null && flatIndex == restoreIndex) Modifier.focusRequester(restoreFocusRequester) else Modifier
                    ) {
                        member.tmdbId?.let { id -> onPersonClick(id, member.name, flatIndex) }
                    }
                }
            }
        }
    }
}

@Composable
fun CastCard(member: TmdbCastInfo, accentColor: Color, textColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.08f else 1f, label = "castScale")

    // Wider focusable area (96dp) with 80dp visual content centered within.
    // The extra width replaces LazyRow spacing and shifts focus centers so
    // Compose's geometric search picks the correct item when navigating
    // between rows with different card widths (cast 80dp vs trailer 190dp).
    Box(
        modifier = modifier
            .width(96.dp)
            .height(110.dp)
            .scale(scale)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(80.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(0.08f))
                    .border(
                        width = if (isFocused) 2.dp else 0.dp,
                        color = if (isFocused) accentColor else Color.Transparent,
                        shape = CircleShape
                    )
            ) {
                if (member.photo != null) {
                    AsyncImage(
                        model = member.photo,
                        contentDescription = member.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = member.name,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = if (isFocused) Color.White else textColor.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            member.character?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = textColor.copy(alpha = 0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StudioRow(
    studios: List<TmdbCompanyInfo>,
    textColor: Color,
    accentColor: Color,
    onStudioClick: (tmdbId: Int, name: String) -> Unit = { _, _ -> },
    restoreIndex: Int = -1,
    restoreFocusRequester: FocusRequester? = null
) {
    val rowState = rememberLazyListState()
    val repeatGate = remember { DpadRepeatGate(horizontalRepeatIntervalMs = 150L) }
    val density = LocalDensity.current
    val startPad = 48.dp
    val paddingPx = remember(density) { with(density) { startPad.toPx() } }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val endPadding = (screenWidth - startPad - 140.dp).coerceAtLeast(120.dp)

    val pivotSpec = remember(paddingPx) {
        FocusPivotSpec(
            customOffset = paddingPx,
            stiffnessProvider = { Spring.StiffnessLow }
        )
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides pivotSpec) {
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = startPad, end = endPadding)
        ) {
            itemsIndexed(studios, key = { _, it -> "${it.tmdbId}:${it.name}" }) { index, studio ->
                Box(modifier = Modifier.onPreviewKeyEvent {
                    if (repeatGate.shouldConsume(it)) return@onPreviewKeyEvent true
                    if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft && index == 0) true else false
                }) {
                    StudioChip(
                        studio, textColor, accentColor,
                        modifier = if (restoreFocusRequester != null && index == restoreIndex) Modifier.focusRequester(restoreFocusRequester) else Modifier
                    ) {
                        studio.tmdbId?.let { id -> onStudioClick(id, studio.name) }
                    }
                }
            }
        }
    }
}

@Composable
fun StudioChip(studio: TmdbCompanyInfo, textColor: Color, accentColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "studioScale")

    Box(
        modifier = modifier
            .width(140.dp)
            .height(56.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) accentColor else Color.Transparent,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource),
        contentAlignment = Alignment.Center
    ) {
        if (studio.logo != null) {
            AsyncImage(
                model = studio.logo,
                contentDescription = studio.name,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = studio.name,
                style = MaterialTheme.typography.labelMedium,
                color = if (isFocused) accentColor else Color.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecommendationRow(
    items: List<TmdbMetaPreview>,
    accentColor: Color,
    rowKey: String = "",
    onItemClick: (type: String, id: String, rowKey: String, index: Int) -> Unit = { _, _, _, _ -> },
    restoreIndex: Int = -1,
    restoreFocusRequester: FocusRequester? = null
) {
    val rowState = rememberLazyListState()
    val repeatGate = remember { DpadRepeatGate(horizontalRepeatIntervalMs = 150L) }
    val density = LocalDensity.current
    val startPad = 48.dp
    val paddingPx = remember(density) { with(density) { startPad.toPx() } }
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val endPadding = (screenWidth - startPad - 120.dp).coerceAtLeast(120.dp)

    val pivotSpec = remember(paddingPx) {
        FocusPivotSpec(
            customOffset = paddingPx,
            stiffnessProvider = { Spring.StiffnessLow }
        )
    }

    CompositionLocalProvider(LocalBringIntoViewSpec provides pivotSpec) {
        LazyRow(
            state = rowState,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = startPad, end = endPadding)
        ) {
            itemsIndexed(items, key = { _, it -> it.tmdbId }) { index, item ->
                Box(modifier = Modifier.onPreviewKeyEvent {
                    if (repeatGate.shouldConsume(it)) return@onPreviewKeyEvent true
                    if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionLeft && index == 0) true else false
                }) {
                    RecommendationCard(
                        item, accentColor,
                        modifier = if (restoreFocusRequester != null && index == restoreIndex) Modifier.focusRequester(restoreFocusRequester) else Modifier,
                        onClick = {
                            val stremioType = if (item.type == "tv") "series" else item.type
                            onItemClick(stremioType, "tmdb:${item.tmdbId}", rowKey, index)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RecommendationCard(item: TmdbMetaPreview, accentColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    val roundCorners = LocalRoundCorners.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val cardShape = if (roundCorners) RoundedCornerShape(if (isFocused) 16.dp else 12.dp) else RectangleShape
    val scale by animateFloatAsState(if (isFocused) 1.05f else 1f, label = "recScale")

    Box(
        modifier = modifier
            .width(120.dp)
            .height(180.dp)
            .scale(scale)
            .clip(cardShape)
            .background(Color.White.copy(0.06f))
            .border(
                width = if (isFocused) 2.dp else 0.dp,
                color = if (isFocused) accentColor else Color.Transparent,
                shape = cardShape
            )
            .clickable(interactionSource = interactionSource, indication = null) { onClick() }
            .focusable(interactionSource = interactionSource)
    ) {
        if (item.poster != null) {
            AsyncImage(
                model = item.poster,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(cardShape)
            )
        }
    }
}
