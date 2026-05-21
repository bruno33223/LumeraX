package com.lumera.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.lumera.app.R
import com.lumera.app.data.model.ProfileEntity
import com.lumera.app.ui.profiles.ProfileAssets
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState

enum class NavDestination(
    @DrawableRes val iconRes: Int,
    val labelRes: Int,
    val iconSize: Dp = 20.dp
) {
    Home(R.drawable.home_icon, R.string.nav_home, iconSize = 21.dp),
    Movies(R.drawable.movies_icon, R.string.nav_movies),
    Series(R.drawable.series_icon, R.string.nav_series),
    Watchlist(R.drawable.watchlist_icon, R.string.nav_watchlist),
    Search(R.drawable.search_icon, R.string.nav_search),
    Profile(R.drawable.profile_icon, R.string.nav_profile, iconSize = 18.dp),
    Settings(R.drawable.settings_icon, R.string.nav_settings)
}

@Composable
fun NavDrawer(
    currentDestination: NavDestination,
    currentProfile: ProfileEntity?,
    drawerRequesters: Map<NavDestination, FocusRequester>,
    onNavigate: (NavDestination) -> Unit,
    onClose: () -> Unit,
    content: @Composable () -> Unit
) {
    var isMenuFocused by remember { mutableStateOf(false) }

    val width by animateDpAsState(
        targetValue = if (isMenuFocused) 200.dp else 80.dp,
        label = "NavWidth",
        animationSpec = tween(300)
    )

    // VISIBILITY ANIMATION:
    val extraItemsAlpha by animateFloatAsState(
        targetValue = if (isMenuFocused) 1f else 0f,
        label = "ExtraItemsAlpha",
        animationSpec = tween(300)
    )

    // Standard BackHandler for when the drawer container is focused
    BackHandler(enabled = isMenuFocused) {
        onClose()
    }

    val showStaticMask = currentDestination in listOf(
        NavDestination.Home,
        NavDestination.Movies,
        NavDestination.Series,
        NavDestination.Watchlist
    )

    Box(modifier = Modifier.fillMaxSize()) {

        // LAYER 1: Content
        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }

        // LAYER 2: Static Hero Mask
        val backgroundColor = MaterialTheme.colorScheme.background
        if (showStaticMask && isMenuFocused) {
            Box(
                modifier = Modifier
                    .width(400.dp)
                    .fillMaxHeight()
                    .zIndex(1.1f)
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.0f to backgroundColor.copy(alpha = 0.95f),
                                0.15f to backgroundColor.copy(alpha = 0.90f),
                                0.30f to backgroundColor.copy(alpha = 0.80f),
                                0.45f to backgroundColor.copy(alpha = 0.65f),
                                0.60f to backgroundColor.copy(alpha = 0.45f),
                                0.75f to backgroundColor.copy(alpha = 0.25f),
                                0.90f to backgroundColor.copy(alpha = 0.08f),
                                1.0f to Color.Transparent
                            ),
                            startX = 0f
                        )
                    )
            )
        }

        // LAYER 3: Interactive Drawer Background (solid on focus/expand)
        androidx.compose.animation.AnimatedVisibility(
            visible = isMenuFocused,
            enter = androidx.compose.animation.fadeIn(animationSpec = tween(300)),
            exit = androidx.compose.animation.fadeOut(animationSpec = tween(300)),
            modifier = Modifier.zIndex(1.5f)
        ) {
            Box(
                modifier = Modifier
                    .width(width)
                    .fillMaxHeight()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.0f to backgroundColor.copy(alpha = 0.98f),
                                0.2f to backgroundColor.copy(alpha = 0.96f),
                                0.4f to backgroundColor.copy(alpha = 0.92f),
                                0.6f to backgroundColor.copy(alpha = 0.85f),
                                0.8f to backgroundColor.copy(alpha = 0.70f),
                                0.9f to backgroundColor.copy(alpha = 0.40f),
                                1.0f to Color.Transparent
                            ),
                            startX = 0f
                        )
                    )
            )
        }

        // Noise overlay to reduce gradient banding on budget panels
        com.lumera.app.ui.components.NoiseOverlay(modifier = Modifier.zIndex(1.6f))

        // LAYER 4: Interactive Drawer
        Box(
            modifier = Modifier
                .width(width)
                .fillMaxHeight()
                .zIndex(2f)
                .onFocusChanged { isMenuFocused = it.hasFocus }
                .padding(top = 30.dp, bottom = 30.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .focusGroup(),
                horizontalAlignment = Alignment.Start
            ) {

                // Helper to apply focus logic cleanly
                @Composable
                fun DrawerItem(dest: NavDestination, label: String? = null) {
                    val isSelected = currentDestination == dest

                    SidebarItem(
                        screen = dest,
                        customLabel = label,
                        isSelected = isSelected,
                        isMenuExpanded = isMenuFocused,
                        isDrawerActive = isMenuFocused,
                        onNavigate = onNavigate,
                        modifier = Modifier
                            .focusRequester(drawerRequesters[dest]!!)
                            .onPreviewKeyEvent {
                                if (it.type == KeyEventType.KeyDown) {
                                    if (it.key == Key.DirectionRight || it.key == Key.Back) {
                                        onClose()
                                        true
                                    } else {
                                        false
                                    }
                                } else {
                                    false
                                }
                            }
                    )
                }

                // 1. Profile Avatar (Custom component)
                Column(
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(modifier = Modifier.graphicsLayer { alpha = extraItemsAlpha }) {
                        ProfileAvatarItem(
                            profile = currentProfile,
                            isMenuExpanded = isMenuFocused,
                            onNavigate = { onNavigate(NavDestination.Profile) },
                            modifier = Modifier
                                .focusRequester(drawerRequesters[NavDestination.Profile]!!)
                                .onPreviewKeyEvent {
                                    if (it.type == KeyEventType.KeyDown) {
                                        if (it.key == Key.DirectionRight || it.key == Key.Back) {
                                            onClose()
                                            true
                                        } else {
                                            false
                                        }
                                    } else {
                                        false
                                    }
                                }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 2. Search
                DrawerItem(NavDestination.Search)

                Spacer(modifier = Modifier.weight(1f))

                // Middle Items
                DrawerItem(NavDestination.Home)
                if (currentProfile?.menuMoviesEnabled != false) {
                    Spacer(modifier = Modifier.height(4.dp))
                    DrawerItem(NavDestination.Movies)
                }
                if (currentProfile?.menuSeriesEnabled != false) {
                    Spacer(modifier = Modifier.height(4.dp))
                    DrawerItem(NavDestination.Series)
                }
                if (currentProfile?.menuWatchlistEnabled != false) {
                    Spacer(modifier = Modifier.height(4.dp))
                    DrawerItem(NavDestination.Watchlist)
                }

                Spacer(modifier = Modifier.weight(1f))

                // Bottom Section: Settings & Exit
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.Center
                ) {
                    // Settings (Invisible when collapsed)
                    Box(modifier = Modifier.graphicsLayer { alpha = extraItemsAlpha }) {
                        DrawerItem(NavDestination.Settings)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SidebarItem(
    screen: NavDestination,
    customLabel: String? = null,
    isSelected: Boolean,
    isMenuExpanded: Boolean,
    isDrawerActive: Boolean,
    onNavigate: (NavDestination) -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val accentColor = MaterialTheme.colorScheme.primary

    val showIndicator = if (isDrawerActive) isFocused else isSelected

    val contentColor by animateColorAsState(
        targetValue = if (showIndicator) Color.White else Color(0xFF8E9099),
        label = "contentColor"
    )

    val iconStartPadding = 20.dp

    val indicatorWidth by animateDpAsState(
        targetValue = if (showIndicator) 4.dp else 0.dp,
        label = "IndicatorWidth"
    )

    val textScale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1.0f,
        label = "TextScale"
    )

    val textAlpha by animateFloatAsState(
        targetValue = if (isMenuExpanded) 1f else 0f,
        animationSpec = tween(300),
        label = "TextAlpha"
    )
    val textOffset by animateFloatAsState(
        targetValue = if (isMenuExpanded) 0f else -20f,
        animationSpec = tween(300),
        label = "TextOffset"
    )

    val displayText = customLabel ?: stringResource(screen.labelRes)

    Box(
        modifier = modifier
            .height(50.dp)
            .fillMaxWidth()
    ) {
        Surface(
            onClick = { onNavigate(screen) },
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { isFocused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                pressedContainerColor = Color.Transparent,
                contentColor = contentColor,
                focusedContentColor = Color.White
            )
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Icon(
                    painter = painterResource(id = screen.iconRes),
                    contentDescription = displayText,
                    tint = contentColor,
                    modifier = Modifier
                        .padding(start = iconStartPadding)
                        .size(screen.iconSize)
                )

                Text(
                    text = displayText,
                    fontSize = 13.sp,
                    color = contentColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Visible,
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .graphicsLayer {
                            scaleX = textScale
                            scaleY = textScale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            alpha = textAlpha
                            translationX = textOffset
                        }
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .height(22.dp)
                .width(indicatorWidth)
                .zIndex(10f)
                .background(
                    color = accentColor,
                    shape = RoundedCornerShape(topEnd = 2.dp, bottomEnd = 2.dp)
                )
        )
    }
}

@Composable
fun ProfileAvatarItem(
    profile: ProfileEntity?,
    isMenuExpanded: Boolean,
    isDrawerActive: Boolean = true,
    onNavigate: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val accentColor = MaterialTheme.colorScheme.primary
    val context = LocalContext.current
    
    val showIndicator = if (isDrawerActive) isFocused else false
    
    val contentColor by animateColorAsState(
        targetValue = if (showIndicator) Color.White else Color(0xFF8E9099),
        label = "contentColor"
    )
    
    val borderColor by animateColorAsState(
        targetValue = if (showIndicator) accentColor else Color.Transparent,
        animationSpec = tween(200),
        label = "borderColor"
    )
    
    val avatarScale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1.0f,
        animationSpec = tween(200),
        label = "avatarScale"
    )
    
    val textScale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1.0f,
        label = "TextScale"
    )
    
    // Text only appears when profile item is focused
    val textAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(200),
        label = "TextAlpha"
    )
    
    val textOffset by animateFloatAsState(
        targetValue = if (isFocused) 0f else -20f,
        animationSpec = tween(200),
        label = "TextOffset"
    )
    
    val avatarSource = profile?.let { ProfileAssets.getAvatarSource(it.avatarRef) }
    val displayName = profile?.name ?: stringResource(R.string.nav_profile)
    
    Box(
        modifier = modifier
            .height(50.dp)
            .fillMaxWidth()
    ) {
        Surface(
            onClick = onNavigate,
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged { isFocused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.0f),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                pressedContainerColor = Color.Transparent,
                contentColor = contentColor,
                focusedContentColor = Color.White
            )
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                // Avatar circle with border on focus
                Box(
                    modifier = Modifier
                        .padding(start = 15.dp)
                        .size(30.dp)
                        .graphicsLayer {
                            scaleX = avatarScale
                            scaleY = avatarScale
                        }
                        .clip(CircleShape)
                        .border(2.dp, borderColor, CircleShape)
                        .background(Color(0xFF1A1A1A))
                ) {
                    if (avatarSource != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(avatarSource)
                                .size(100, 100)
                                .crossfade(true)
                                .build(),
                            contentDescription = stringResource(R.string.profile_avatar_content_desc),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                
                // Profile name and Change Profile text
                Column(
                    verticalArrangement = Arrangement.spacedBy((-12).dp),
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .graphicsLayer {
                            scaleX = textScale
                            scaleY = textScale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            alpha = textAlpha
                            translationX = textOffset
                        }
                ) {
                    Text(
                        text = displayName,
                        fontSize = 13.sp,
                        color = contentColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible
                    )
                    Text(
                        text = stringResource(R.string.change_profile),
                        fontSize = 10.sp,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible
                    )
                }
            }
        }
    }
}
