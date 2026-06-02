package com.lumera.app

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.hilt.navigation.compose.hiltViewModel
import com.lumera.app.data.update.AppUpdateManager
import com.lumera.app.data.update.UpdateInfo
import com.lumera.app.data.update.UpdateState
import com.lumera.app.data.player.PlaybackTrackSelectionStore
import com.lumera.app.data.torrent.TorrentProgress
import com.lumera.app.data.torrent.TorrentService
import com.lumera.app.data.torrent.TorrServerEngine
import com.lumera.app.data.player.SourceSelectionStore
import com.lumera.app.ui.MainViewModel
import com.lumera.app.ui.components.LumeraBackground
import com.lumera.app.ui.details.DetailsScreen
import com.lumera.app.ui.home.GridViewScreen
import com.lumera.app.ui.home.HomeScreen
import com.lumera.app.ui.watchlist.WatchlistScreen
import com.lumera.app.ui.home.HomeViewModel
import com.lumera.app.data.model.stremio.MetaItem
import com.lumera.app.data.model.stremio.Stream
import com.lumera.app.data.model.stremio.StreamSubtitle
import com.lumera.app.data.model.stremio.MetaVideo
import com.lumera.app.data.repository.AddonRepository
import com.lumera.app.data.repository.IntroRepository
import com.lumera.app.data.repository.SubtitleRepository
import com.lumera.app.data.stream.StreamSortingService
import com.lumera.app.domain.AddonSubtitle
import com.lumera.app.domain.DashboardTab
import com.lumera.app.domain.episodeDisplayTitle
import com.lumera.app.domain.episodePlaybackId
import com.lumera.app.domain.episodeStreamId
import com.lumera.app.domain.findNextEpisode
import com.lumera.app.ui.navigation.NavDestination
import com.lumera.app.ui.navigation.NavDrawer
import com.lumera.app.ui.navigation.TopNavigationBar
import com.lumera.app.ui.player.PlayerScreen
import com.lumera.app.ui.player.PlayerSessionResult
import com.lumera.app.ui.player.base.PlayerSourceOption
import com.lumera.app.ui.player.base.NextEpisodeInfo
import com.lumera.app.ui.player.base.PlaybackSettings
import com.lumera.app.ui.player.base.PlayerSubtitleSource
import com.lumera.app.ui.player.base.SkipSegmentInfo
import com.lumera.app.ui.profiles.ProfileScreen
import com.lumera.app.ui.profiles.ProfileViewModel
import com.lumera.app.ui.search.SearchScreen
import com.lumera.app.ui.settings.SettingsScreen
import com.lumera.app.ui.addons.VoidButton
import com.lumera.app.ui.addons.VoidDialog
import com.lumera.app.ui.theme.DefaultThemes
import com.lumera.app.ui.theme.LocalRoundCorners
import com.lumera.app.ui.theme.LocalHubRoundCorners
import com.lumera.app.ui.theme.LumeraTheme
import com.lumera.app.ui.theme.ThemeManager
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope

import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.media.MediaPlayer
import com.lumera.app.data.local.AddonDao
import com.lumera.app.data.profile.ProfileConfigurationManager
import kotlinx.coroutines.runBlocking

import java.util.Locale
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import javax.inject.Inject
import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts


import kotlinx.coroutines.withContext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.text.font.FontWeight
import com.lumera.app.ui.components.dialogs.PlayerChoiceDialog
import com.lumera.app.ui.components.dialogs.UpdateAvailableDialog
import com.lumera.app.ui.components.dialogs.UpdateDownloadingDialog
import com.lumera.app.ui.components.dialogs.UpdateErrorDialog
import com.lumera.app.ui.player.resolveSubtitleUrl
import com.lumera.app.ui.player.sanitizeSubtitleSourceName
import com.lumera.app.ui.player.subtitleNameFromUrl
import com.lumera.app.ui.player.normalizeSubtitleLanguageTag
import com.lumera.app.ui.player.TORRENT_TRACKERS
import com.lumera.app.ui.player.resolvePlayableSourceUrl
import com.lumera.app.ui.player.sourceDisplayLabel
import com.lumera.app.ui.player.launchExternalPlayer
import com.lumera.app.ui.player.buildSourcePayload
import com.lumera.app.ui.player.buildEmbeddedSubtitlePayload
import com.lumera.app.ui.player.buildEmbeddedSubtitlePayloadItem
import com.lumera.app.ui.player.buildAddonSubtitlePayload
import com.lumera.app.ui.player.buildSubtitlePayload
import com.lumera.app.ui.player.handlePlayerSessionEnd
import com.lumera.app.ui.player.fetchAddonSubtitlesAsync
import com.lumera.app.data.model.PlayerState
import com.lumera.app.data.model.PlayerSubtitlePayload
import com.lumera.app.data.model.PendingSourceSelection
import com.lumera.app.data.model.PendingEpisodeSwitch
import androidx.compose.runtime.Stable


private const val SOURCE_SELECTION_COMMIT_MIN_POSITION_MS = 5_000L
private const val SOURCE_SELECTION_FAILURE_RESET_MAX_POSITION_MS = 1_000L





private class GridRestoreState {
    var focusedIndex: Int? = null
    var scrollIndex: Int = 0
    var scrollOffset: Int = 0
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var sourceSelectionStore: SourceSelectionStore
    @Inject
    lateinit var playbackTrackSelectionStore: PlaybackTrackSelectionStore
    @Inject
    lateinit var addonRepository: AddonRepository
    @Inject
    lateinit var subtitleRepository: SubtitleRepository
    @Inject
    lateinit var introRepository: IntroRepository
    @Inject
    lateinit var profileConfigurationManager: ProfileConfigurationManager
    @Inject
    lateinit var appUpdateManager: AppUpdateManager
    @Inject
    lateinit var addonDao: AddonDao
    @Inject
    lateinit var streamSortingService: StreamSortingService

    @Inject
    lateinit var torrServerEngine: TorrServerEngine

    private var splashManager: com.lumera.app.ui.splash.SplashManager? = null
    private val _splashFinished = mutableStateOf(false)

    override fun onStop() {
        super.onStop()
        lifecycleScope.launch(Dispatchers.IO) {
            profileConfigurationManager.saveActiveRuntimeState()
        }
    }

    override fun onDestroy() {
        splashManager?.dismiss()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            torrServerEngine.stop()
        }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (splashManager == null) {
            outState.putBoolean(KEY_SPLASH_SHOWN, true)
        }
    }



    @Composable
    private fun LocaleWrapper(language: String?, content: @Composable () -> Unit) {
        val context = LocalContext.current
        val configuration = LocalConfiguration.current
        
        val locale = remember(language) {
            if (language.isNullOrBlank()) Locale.getDefault()
            else {
                val parts = language.split("-")
                if (parts.size > 1) Locale(parts[0], parts[1].removePrefix("r"))
                else Locale(language)
            }
        }

        val localizedConfiguration = remember(locale) {
            Configuration(configuration).apply {
                setLocale(locale)
                setLayoutDirection(locale)
            }
        }

        // CorreÃ§Ã£o CirÃºrgica: Retorna um ContextWrapper com base na Activity (context), 
        // mas servindo os resources traduzidos (configContext). Isso impede o crash do HiltViewModelFactory.
        val localizedContext = remember(locale, context) {
            val configContext = context.createConfigurationContext(localizedConfiguration)
            object : android.content.ContextWrapper(context) {
                override fun getResources() = configContext.resources
            }
        }

        CompositionLocalProvider(
            LocalConfiguration provides localizedConfiguration,
            LocalContext provides localizedContext
        ) {
            content()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Sanitize saved state: R8 can obfuscate Parcelable class names, causing
        // BadParcelableException on process-death restore. Clear the bundle if corrupt.
        val safeState = savedInstanceState?.let { bundle ->
            try {
                bundle.keySet() // forces unparcel â€” throws if any class is missing
                bundle
            } catch (_: android.os.BadParcelableException) {
                null
            }
        }
        super.onCreate(safeState)
        window.setFormat(android.graphics.PixelFormat.RGBA_8888)

        // Fix sideload launch bug: pressing Home and returning re-creates the activity
        // instead of resuming it when the APK was installed via adb/sideload.
        if (!isTaskRoot && intent.hasCategory(Intent.CATEGORY_LAUNCHER)
            && Intent.ACTION_MAIN == intent.action) {
            finish()
            return
        }

        val hasSplashShown = safeState?.getBoolean(KEY_SPLASH_SHOWN) == true
        val showSplash = !hasSplashShown

        if (showSplash) {
            splashManager = com.lumera.app.ui.splash.SplashManager(this) {
                _splashFinished.value = true
            }
            splashManager?.prepare()
        } else {
            _splashFinished.value = true
        }

        setContent {

            val mainViewModel = hiltViewModel<MainViewModel>()
            val themeManager = hiltViewModel<ThemeManager>()
            val currentProfile by mainViewModel.activeProfile.collectAsState()
            var sessionProfileId by rememberSaveable { mutableStateOf<Int?>(null) }
            var sessionRestoreAttemptedProfileId by rememberSaveable { mutableStateOf<Int?>(null) }
            var activeView by rememberSaveable { mutableStateOf("menu") }
            var selectedMovieId by rememberSaveable { mutableStateOf("") }
            var selectedMovieType by rememberSaveable { mutableStateOf("movie") }
            var selectedVideoUrl by rememberSaveable { mutableStateOf("") }
            var selectedTrailerAudioUrl by rememberSaveable { mutableStateOf("") }
            var torrentProgress by remember { mutableStateOf<TorrentProgress?>(null) }
            var selectedMovieTitle by rememberSaveable { mutableStateOf("") }
            var selectedMoviePoster by rememberSaveable { mutableStateOf("") }
            var selectedMovieBackground by rememberSaveable { mutableStateOf("") }
            var selectedMovieLogo by rememberSaveable { mutableStateOf("") }
            var selectedAddonBaseUrl by rememberSaveable { mutableStateOf<String?>(null) }
            var detailsResumePlaybackHint by rememberSaveable { mutableStateOf<String?>(null) }
            var trailerReturnToken by rememberSaveable { mutableStateOf(0) }
            var isTrailerLoading by remember { mutableStateOf(false) }
            var showTrailerError by remember { mutableStateOf(false) }
            var selectedPlaybackId by rememberSaveable { mutableStateOf("") }
            var selectedPlaybackType by rememberSaveable { mutableStateOf("movie") }
            var selectedPlaybackTitle by rememberSaveable { mutableStateOf("") }
            var selectedPlaybackPoster by rememberSaveable { mutableStateOf("") }
            var previousView by rememberSaveable { mutableStateOf("menu") }
            var showExitConfirmation by rememberSaveable { mutableStateOf(false) }
            val playerState = remember { PlayerState() }


            LaunchedEffect(currentProfile?.id) {
                val profileId = currentProfile?.id
                if (profileId != null) {
                    sessionProfileId = profileId
                    sessionRestoreAttemptedProfileId = null
                }
            }

            LaunchedEffect(currentProfile, sessionProfileId, sessionRestoreAttemptedProfileId) {
                if (currentProfile != null) return@LaunchedEffect
                val profileIdToRestore = sessionProfileId ?: return@LaunchedEffect
                if (sessionRestoreAttemptedProfileId == profileIdToRestore) return@LaunchedEffect

                sessionRestoreAttemptedProfileId = profileIdToRestore
                mainViewModel.login(profileIdToRestore)
            }

            // Resolve theme from profile's themeId
            val currentTheme by themeManager.currentTheme.collectAsState()

            // Get round corners setting from profile (default true)
            val roundCorners = currentProfile?.roundCorners ?: true
            val hubRoundCorners = currentProfile?.hubRoundCorners ?: true
            
            // Update theme when profile changes
            LaunchedEffect(currentProfile) {
                currentProfile?.let { profile ->
                    themeManager.setCurrentProfile(profile.id, profile.themeId)
                }
            }

            // Signal native splash to resume once first composition is done
            LaunchedEffect(Unit) { splashManager?.onAppReady() }

            // Auto-check for updates on launch
            val updateState by appUpdateManager.state.collectAsState()
            var updateDismissed by rememberSaveable { mutableStateOf(false) }
            val updateScope = rememberCoroutineScope()
            
            LaunchedEffect(Unit) { appUpdateManager.checkForUpdate() }

            LocaleWrapper(language = currentProfile?.appLanguage) {
                LumeraTheme(theme = currentTheme) {
                    CompositionLocalProvider(
                        LocalRoundCorners provides roundCorners,
                        LocalHubRoundCorners provides hubRoundCorners
                    ) {
                    LumeraBackground {
                    if (currentProfile == null) {
                        BackHandler {
                            showExitConfirmation = true
                        }
                        // PROFILE SELECTION / CREATION
                        // Always use VOID theme for profile selection (black & white)
                        LumeraTheme(theme = DefaultThemes.VOID) {
                            val profileViewModel = hiltViewModel<ProfileViewModel>()
                            val profiles by profileViewModel.profiles.collectAsState()

                            ProfileScreen(
                                profiles = profiles,
                                onProfileSelected = {
                                    sessionProfileId = it.id
                                    sessionRestoreAttemptedProfileId = null
                                    mainViewModel.login(it.id)
                                }
                            )
                        }
                    } else {
                        // MAIN APP CONTENT
                        var currentNav by remember { mutableStateOf(NavDestination.Home) }
                        
                        // Grid view state
                        var gridViewTitle by rememberSaveable { mutableStateOf("") }
                        var gridViewItems by remember { mutableStateOf<List<MetaItem>>(emptyList()) }
                        var gridViewConfigId by rememberSaveable { mutableStateOf("") }
                        val gridRestoreState = remember { GridRestoreState() }

                        // Search focus restoration
                        val searchMoviesViewMoreRequester = remember { FocusRequester() }
                        val searchSeriesViewMoreRequester = remember { FocusRequester() }
                        val searchResultsRequester = remember { FocusRequester() }
                        val searchDiscoverRequester = remember { FocusRequester() }
                        var searchFocusTarget by remember { mutableStateOf<String?>(null) }
                        var searchLastFocusedId by remember { mutableStateOf<String?>(null) }

                        // Track where we came from for proper back navigation
                        val uiScope = rememberCoroutineScope()

                        // Focus Traffic Control
                        val drawerRequesters = remember { NavDestination.values().associateWith { FocusRequester() } }
                        val homeEntryRequester = remember { FocusRequester() }
                        val searchEntryRequester = remember { FocusRequester() }
                        val discoverEntryRequester = remember { FocusRequester() }
                        val settingsEntryRequester = remember { FocusRequester() }
                        val watchlistEntryRequester = remember { FocusRequester() }

                        // STATE CHANGE TRIGGER:
                        LaunchedEffect(currentNav, activeView) {
                            if (activeView != "menu") return@LaunchedEffect
                            when(currentNav) {
                                // HomeScreen requests focus itself once data is ready.
                                // Avoid requesting early into the loading placeholder, which can
                                // cause a brief nav -> content -> nav -> content flicker.
                                NavDestination.Home, NavDestination.Movies, NavDestination.Series -> Unit
                                NavDestination.Search -> {
                                    delay(200) // Increased for stability
                                    val target = searchFocusTarget
                                    if (target != null) {
                                        searchFocusTarget = null
                                        when (target) {
                                            "movies" -> searchMoviesViewMoreRequester.requestFocus()
                                            "series" -> searchSeriesViewMoreRequester.requestFocus()
                                            "poster" -> searchResultsRequester.requestFocus()
                                            "discover" -> searchDiscoverRequester.requestFocus()
                                        }
                                    } else {
                                        searchEntryRequester.requestFocus()
                                    }
                                }
                                NavDestination.Settings -> {
                                    delay(200) // Increased for stability
                                    settingsEntryRequester.requestFocus()
                                }
                                NavDestination.Watchlist -> {
                                    delay(200)
                                    watchlistEntryRequester.requestFocus()
                                }
                                else -> Unit
                            }
                        }


                        // Focus restoration after navPosition change (Crossfade animation)
                        val navPosition = currentProfile?.navPosition ?: "left"
                        LaunchedEffect(navPosition) {
                            if (activeView == "menu" && currentNav == NavDestination.Settings) {
                                delay(450) // Wait for Crossfade (400ms) + buffer
                                settingsEntryRequester.requestFocus()
                            }
                        }


                            // CONDITIONAL NAVIGATION RENDERING (no animation)
                            val view = activeView
                            if (view == "menu") {
                                // Back handler for main menu
                                BackHandler {
                                    showExitConfirmation = true
                                }
                                var settingsContentFocused by remember { mutableStateOf(false) }

                                // Shared content composable
                                // Shared navigation handler
                                val handleNavigate: (NavDestination) -> Unit = { destination ->
                                    if (currentNav == destination) {
                                        // Already here - just focus content
                                        when(destination) {
                                            NavDestination.Home, NavDestination.Movies, NavDestination.Series -> homeEntryRequester.requestFocus()
                                            NavDestination.Search -> searchEntryRequester.requestFocus()
                                            NavDestination.Settings -> settingsEntryRequester.requestFocus()
                                            NavDestination.Watchlist -> watchlistEntryRequester.requestFocus()
                                            else -> {}
                                        }
                                    } else {
                                        if (currentNav == NavDestination.Search) searchFocusTarget = null
                                        if (currentNav == NavDestination.Settings) settingsContentFocused = false
                                        currentNav = destination
                                    }
                                }

                                // Shared enter content handler
                                val handleEnterContent: () -> Unit = {
                                    when(currentNav) {
                                        NavDestination.Home, NavDestination.Movies, NavDestination.Series -> homeEntryRequester.requestFocus()
                                        NavDestination.Search -> searchEntryRequester.requestFocus()
                                        NavDestination.Settings -> settingsEntryRequester.requestFocus()
                                        NavDestination.Watchlist -> watchlistEntryRequester.requestFocus()
                                        else -> {}
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .onPreviewKeyEvent { event ->
                                            if (settingsContentFocused) return@onPreviewKeyEvent false
                                            if (event.key == Key.Back && event.type == KeyEventType.KeyDown) {
                                                showExitConfirmation = true
                                                true
                                            } else {
                                                false
                                            }
                                        }
                                ) {
                                Crossfade(targetState = navPosition, animationSpec = tween(400), label = "NavSwitcher") { position ->
                                if (position == "top") {
                                    TopNavigationBar(
                                        currentDestination = currentNav,
                                        currentProfile = currentProfile,
                                        topNavRequesters = drawerRequesters,
                                        onNavigate = handleNavigate,
                                        onEnterContent = handleEnterContent,
                                        onLogout = {
                                            sessionProfileId = null
                                            sessionRestoreAttemptedProfileId = null

                                            activeView = "menu"
                                            themeManager.resetTheme()
                                            mainViewModel.logout()
                                        },
                                        onExit = { finishAndRemoveTask() },
                                        content = {
                                            com.lumera.app.ui.navigation.MainDashboardContent(
                                                currentNav = currentNav,
                                                currentProfile = currentProfile,
                                                homeEntryRequester = homeEntryRequester,
                                                searchEntryRequester = searchEntryRequester,
                                                discoverEntryRequester = discoverEntryRequester,
                                                watchlistEntryRequester = watchlistEntryRequester,
                                                settingsEntryRequester = settingsEntryRequester,
                                                drawerRequesters = drawerRequesters,
                                                onMovieClick = { movie ->
                                                    selectedMovieId = movie.id
                                                    selectedMovieType = movie.type
                                                    selectedMovieTitle = movie.name
                                                    selectedMoviePoster = movie.poster ?: ""
                                                    selectedMovieBackground = movie.background ?: ""
                                                    selectedMovieLogo = movie.logo ?: ""
                                                    selectedAddonBaseUrl = movie.addonBaseUrl
                                                    detailsResumePlaybackHint = null
                                                    selectedPlaybackId = movie.id
                                                    selectedPlaybackType = movie.type
                                                    selectedPlaybackTitle = movie.name
                                                    selectedPlaybackPoster = movie.poster ?: ""
                                                    previousView = "menu"
                                                    activeView = "details"
                                                },
                                                onViewMore = { title, items, configId ->
                                                    gridViewTitle = title
                                                    gridViewItems = items
                                                    gridViewConfigId = configId
                                                    activeView = "grid"
                                                },
                                                onSearchDiscoverClick = { movie ->
                                                    selectedMovieId = movie.id
                                                    selectedMovieType = movie.type
                                                    selectedMovieTitle = movie.name
                                                    selectedMoviePoster = movie.poster ?: ""
                                                    selectedMovieBackground = movie.background ?: ""
                                                    selectedMovieLogo = movie.logo ?: ""
                                                    selectedAddonBaseUrl = movie.addonBaseUrl
                                                    detailsResumePlaybackHint = null
                                                    selectedPlaybackId = movie.id
                                                    selectedPlaybackType = movie.type
                                                    selectedPlaybackTitle = movie.name
                                                    selectedPlaybackPoster = movie.poster ?: ""
                                                    searchFocusTarget = "discover"
                                                    previousView = "menu"
                                                    activeView = "details"
                                                },
                                                searchFocusTarget = searchFocusTarget,
                                                onSearchFocusTargetChange = { searchFocusTarget = it },
                                                searchLastFocusedId = searchLastFocusedId,
                                                onSearchLastFocusedIdChange = { searchLastFocusedId = it },
                                                searchMoviesViewMoreRequester = searchMoviesViewMoreRequester,
                                                searchSeriesViewMoreRequester = searchSeriesViewMoreRequester,
                                                searchResultsRequester = searchResultsRequester,
                                                searchDiscoverRequester = searchDiscoverRequester,
                                                onDashboardChanged = {},
                                                onSettingsContentFocusChanged = { settingsContentFocused = it },
                                                onNavigate = { currentNav = it },
                                                onLogout = {
                                                    sessionProfileId = null
                                                    sessionRestoreAttemptedProfileId = null
                                                    activeView = "menu"
                                                    themeManager.resetTheme()
                                                    mainViewModel.logout()
                                                }
                                            )
                                        }
                                    )
                                } else { // position == "left"
                                    NavDrawer(
                                        currentDestination = currentNav,
                                        currentProfile = currentProfile,
                                        drawerRequesters = drawerRequesters,
                                        onNavigate = handleNavigate,
                                        onClose = handleEnterContent,
                                        content = {
                                            com.lumera.app.ui.navigation.MainDashboardContent(
                                                discoverEntryRequester = discoverEntryRequester,
                                                currentNav = currentNav,
                                                currentProfile = currentProfile,
                                                homeEntryRequester = homeEntryRequester,
                                                searchEntryRequester = searchEntryRequester,
                                                watchlistEntryRequester = watchlistEntryRequester,
                                                settingsEntryRequester = settingsEntryRequester,
                                                drawerRequesters = drawerRequesters,
                                                onMovieClick = { movie ->
                                                    selectedMovieId = movie.id
                                                    selectedMovieType = movie.type
                                                    selectedMovieTitle = movie.name
                                                    selectedMoviePoster = movie.poster ?: ""
                                                    selectedMovieBackground = movie.background ?: ""
                                                    selectedMovieLogo = movie.logo ?: ""
                                                    selectedAddonBaseUrl = movie.addonBaseUrl
                                                    detailsResumePlaybackHint = null
                                                    selectedPlaybackId = movie.id
                                                    selectedPlaybackType = movie.type
                                                    selectedPlaybackTitle = movie.name
                                                    selectedPlaybackPoster = movie.poster ?: ""
                                                    previousView = "menu"
                                                    activeView = "details"
                                                },
                                                onViewMore = { title, items, configId ->
                                                    gridViewTitle = title
                                                    gridViewItems = items
                                                    gridViewConfigId = configId
                                                    activeView = "grid"
                                                },
                                                onSearchDiscoverClick = { movie ->
                                                    selectedMovieId = movie.id
                                                    selectedMovieType = movie.type
                                                    selectedMovieTitle = movie.name
                                                    selectedMoviePoster = movie.poster ?: ""
                                                    selectedMovieBackground = movie.background ?: ""
                                                    selectedMovieLogo = movie.logo ?: ""
                                                    selectedAddonBaseUrl = movie.addonBaseUrl
                                                    detailsResumePlaybackHint = null
                                                    selectedPlaybackId = movie.id
                                                    selectedPlaybackType = movie.type
                                                    selectedPlaybackTitle = movie.name
                                                    selectedPlaybackPoster = movie.poster ?: ""
                                                    searchFocusTarget = "discover"
                                                    previousView = "menu"
                                                    activeView = "details"
                                                },
                                                searchFocusTarget = searchFocusTarget,
                                                onSearchFocusTargetChange = { searchFocusTarget = it },
                                                searchLastFocusedId = searchLastFocusedId,
                                                onSearchLastFocusedIdChange = { searchLastFocusedId = it },
                                                searchMoviesViewMoreRequester = searchMoviesViewMoreRequester,
                                                searchSeriesViewMoreRequester = searchSeriesViewMoreRequester,
                                                searchResultsRequester = searchResultsRequester,
                                                searchDiscoverRequester = searchDiscoverRequester,
                                                onDashboardChanged = {},
                                                onSettingsContentFocusChanged = { settingsContentFocused = it },
                                                onNavigate = { currentNav = it },
                                                onLogout = {
                                                    sessionProfileId = null
                                                    sessionRestoreAttemptedProfileId = null
                                                    activeView = "menu"
                                                    themeManager.resetTheme()
                                                    mainViewModel.logout()
                                                }
                                            )
                                        }
                                    )
                                }
                                } // Crossfade end
                                } // Double-back Box end
                        } else if (view == "grid") {
                            val gridVm = hiltViewModel<HomeViewModel>()
                            GridViewScreen(
                                title = gridViewTitle,
                                items = gridViewItems,
                                lastFocusedIndex = gridRestoreState.focusedIndex,
                                onFocusChange = { gridRestoreState.focusedIndex = it },
                                onMovieClick = { movie ->
                                    selectedMovieId = movie.id
                                    selectedMovieType = movie.type
                                    selectedMovieTitle = movie.name
                                    selectedMoviePoster = movie.poster ?: ""
                                    selectedMovieBackground = movie.background ?: ""
                                    selectedMovieLogo = movie.logo ?: ""
                                    selectedAddonBaseUrl = movie.addonBaseUrl
                                    detailsResumePlaybackHint = null
                                    selectedPlaybackId = movie.id
                                    selectedPlaybackType = movie.type
                                    selectedPlaybackTitle = movie.name
                                    selectedPlaybackPoster = movie.poster ?: ""
                                    previousView = "grid"
                                    activeView = "details"
                                },
                                onBack = { 
                                    gridRestoreState.focusedIndex = null  // Reset for next time
                                    gridRestoreState.scrollIndex = 0  // Reset scroll position
                                    gridRestoreState.scrollOffset = 0
                                    activeView = "menu"
                                },
                                onLoadMore = {
                                    if (gridViewConfigId.isNotEmpty()) {
                                        gridVm.loadMoreItems(gridViewConfigId)
                                    }
                                },
                                initialScrollIndex = gridRestoreState.scrollIndex,
                                initialScrollOffset = gridRestoreState.scrollOffset,
                                onScrollPositionChange = { index, offset ->
                                    gridRestoreState.scrollIndex = index
                                    gridRestoreState.scrollOffset = offset
                                },
                                watchedIds = gridVm.state.collectAsState().value.watchedIds
                            )
                            // Sync gridViewItems when ViewModel state updates (after loadMoreItems)
                            val vmState by gridVm.state.collectAsState()
                            LaunchedEffect(vmState.rows) {
                                if (gridViewConfigId.isNotEmpty()) {
                                    val updatedRow = vmState.rows.find { it.configId == gridViewConfigId }
                                    if (updatedRow != null && updatedRow.items.size > gridViewItems.size) {
                                        gridViewItems = updatedRow.items
                                    }
                                }
                            }
                        } else if (view == "details" || (view == "player" && selectedPlaybackId.startsWith("trailer_"))) {
                            val onPlayClick: (String, String, String, String, String, String, com.lumera.app.data.model.stremio.Stream, List<com.lumera.app.domain.AddonSubtitle>, List<com.lumera.app.data.model.stremio.Stream>, List<com.lumera.app.data.model.stremio.MetaVideo>) -> Unit = { url, playbackId, playbackType, playbackTitle, seriesTitle, logo, stream, addonSubtitles, availableStreams, episodes ->
                                val resolvedPlaybackTitle = playbackTitle.ifBlank { selectedMovieTitle }
                                val resolvedSeriesTitle = seriesTitle.ifBlank { selectedMovieTitle }
                                val isSeriesPlayback = playbackType.equals("series", ignoreCase = true) ||
                                    playbackType.equals("tv", ignoreCase = true)
                                if (isSeriesPlayback && resolvedSeriesTitle.isNotBlank()) {
                                    selectedMovieTitle = resolvedSeriesTitle
                                }
                                if (logo.isNotBlank()) selectedMovieLogo = logo
                                playerState.currentEpisodeList = episodes
                                playerState.currentStream = stream

                                val sourcePayloadInput = if (availableStreams.isNotEmpty()) availableStreams else listOf(stream)
                                val sourcePayload = buildSourcePayload(streams = sourcePayloadInput, selectedStream = stream)
                                playerState.pendingSourceSelection = PendingSourceSelection(
                                    playbackId = playbackId,
                                    launchedStream = stream,
                                    candidateStreams = sourcePayloadInput
                                )
                                if (url.startsWith("magnet:")) {
                                    uiScope.launch {
                                        mainViewModel.persistActiveProfileState()
                                        selectedPlaybackId = playbackId
                                        selectedPlaybackType = playbackType
                                        selectedPlaybackTitle = resolvedPlaybackTitle
                                        selectedPlaybackPoster = selectedMoviePoster
                                        selectedTrailerAudioUrl = ""

                                        playerState.selectedPlayerSubtitles = buildSubtitlePayload(stream, addonSubtitles)
                                        playerState.selectedPlayerSources = sourcePayload
                                        selectedVideoUrl = ""
                                        torrentProgress = TorrentProgress("Connecting to peers...")
                                        activeView = "player"

                                        run {
                                             val altId = if (playbackType == "movie") null else {
                                                 val ep = episodes.find { it.id == playbackId }
                                                 ep?.imdbId ?: (if (selectedMovieId.startsWith("tt")) selectedMovieId else null)?.let { "$it:${ep?.season ?: 1}:${ep?.episode ?: 1}" }
                                             }
                                             uiScope.fetchAddonSubtitlesAsync(subtitleRepository, playbackType, playbackId, stream, playerState, alternateId = altId)
                                         }

                                        TorrentService.onStreamReady = { localUrl ->
                                            torrentProgress = null
                                            selectedVideoUrl = localUrl
                                        }
                                        TorrentService.onStreamError = { error ->
                                             torrentProgress = TorrentProgress(status = "Error: $error", isError = true)
                                             if (BuildConfig.DEBUG) Log.e("LumeraTorrent", "Stream error: $error")
                                         }
                                        TorrentService.onStreamProgress = { progress ->
                                            torrentProgress = progress
                                        }
                                        val intent = Intent(this@MainActivity, TorrentService::class.java).apply {
                                            putExtra("MAGNET_LINK", url)
                                            putExtra("FILE_IDX", stream.fileIdx ?: -1)
                                            putExtra("FILE_NAME", stream.behaviorHints?.filename ?: "")
                                        }
                                        startService(intent)
                                    }
                                } else {
                                    stopService(Intent(this@MainActivity, TorrentService::class.java))
                                    uiScope.launch {
                                        mainViewModel.persistActiveProfileState()
                                        selectedPlaybackId = playbackId
                                        selectedPlaybackType = playbackType
                                        selectedPlaybackTitle = resolvedPlaybackTitle
                                        selectedPlaybackPoster = selectedMoviePoster
                                        selectedTrailerAudioUrl = ""

                                        playerState.selectedPlayerSubtitles = buildSubtitlePayload(stream, addonSubtitles)
                                        playerState.selectedPlayerSources = sourcePayload
                                        selectedVideoUrl = url
                                        when (currentProfile?.playerPreference) {
                                            "external" -> launchExternalPlayer(this@MainActivity, url)
                                            "ask" -> playerState.showPlayerChoiceDialog = true
                                            else -> activeView = "player"
                                        }

                                        run {
                                             val altId = if (playbackType == "movie") null else {
                                                 val ep = episodes.find { it.id == playbackId }
                                                 ep?.imdbId ?: (if (selectedMovieId.startsWith("tt")) selectedMovieId else null)?.let { "$it:${ep?.season ?: 1}:${ep?.episode ?: 1}" }
                                             }
                                             uiScope.fetchAddonSubtitlesAsync(subtitleRepository, playbackType, playbackId, stream, playerState, alternateId = altId)
                                         }
                                    }
                                }
                            }

                            com.lumera.app.ui.details.DetailsNavGraph(
                                selectedMovieType = selectedMovieType,
                                selectedMovieId = selectedMovieId,
                                selectedAddonBaseUrl = selectedAddonBaseUrl,
                                detailsResumePlaybackHint = detailsResumePlaybackHint,
                                currentProfile = currentProfile,
                                trailerReturnToken = trailerReturnToken,
                                isTrailerLoading = isTrailerLoading,
                                onPosterResolved = { selectedMoviePoster = it },
                                onPlayClick = onPlayClick,
                                onTrailerClick = { youtubeKey, trailerName ->
                                    isTrailerLoading = true
                                    uiScope.launch {
                                        val extractor = com.lumera.app.data.trailer.YouTubeExtractor()
                                        val source = extractor.extractPlaybackSource(youtubeKey)
                                        isTrailerLoading = false
                                        if (source != null) {
                                            selectedVideoUrl = source.videoUrl
                                            selectedTrailerAudioUrl = source.audioUrl ?: ""
                                            selectedPlaybackId = "trailer_$youtubeKey"
                                            selectedPlaybackType = selectedMovieType
                                            selectedPlaybackTitle = trailerName
                                            selectedPlaybackPoster = selectedMoviePoster
                                            playerState.selectedPlayerSubtitles = emptyList()
                                            playerState.selectedPlayerSources = emptyList()
                                            activeView = "player"
                                        } else {
                                            showTrailerError = true
                                        }
                                    }
                                },
                                onBack = { activeView = previousView }
                            )
                        }
                        if (view == "player") {
                            if (selectedVideoUrl.isBlank() && torrentProgress == null) {
                                LaunchedEffect(Unit) { activeView = "details" }
                            } else {
                                com.lumera.app.ui.player.PlayerRoute(
                                    uiScope = uiScope,
                                    playerState = playerState,
                                    currentProfile = currentProfile,
                                    selectedPlaybackId = selectedPlaybackId,
                                    selectedMovieId = selectedMovieId,
                                    selectedPlaybackType = selectedPlaybackType,
                                    selectedPlaybackTitle = selectedPlaybackTitle,
                                    selectedMovieTitle = selectedMovieTitle,
                                    selectedMovieLogo = selectedMovieLogo,
                                    selectedPlaybackPoster = selectedPlaybackPoster,
                                    selectedVideoUrl = selectedVideoUrl,
                                    selectedTrailerAudioUrl = selectedTrailerAudioUrl,
                                    torrentProgress = torrentProgress,
                                    onTorrentProgressChange = { torrentProgress = it },
                                    onSelectedVideoUrlChange = { selectedVideoUrl = it },
                                    onSelectedPlaybackIdChange = { selectedPlaybackId = it },
                                    onSelectedPlaybackTypeChange = { selectedPlaybackType = it },
                                    onSelectedPlaybackTitleChange = { selectedPlaybackTitle = it },
                                    onDetailsResumePlaybackHintChange = { detailsResumePlaybackHint = it },
                                    onTrailerReturnTokenIncrement = { trailerReturnToken++ },
                                    onNavigateBack = { activeView = "details" },
                                    playbackTrackSelectionStore = playbackTrackSelectionStore,
                                    sourceSelectionStore = sourceSelectionStore,
                                    introRepository = introRepository,
                                    addonRepository = addonRepository,
                                    subtitleRepository = subtitleRepository,
                                    streamSortingService = streamSortingService
                                )
                            }
                        }
                    }

                    // Player choice dialog (shown when playerPreference == "ask")
                    if (playerState.showPlayerChoiceDialog && selectedVideoUrl.isNotBlank()) {
                        PlayerChoiceDialog(
                            onInternal = {
                                playerState.showPlayerChoiceDialog = false
                                activeView = "player"
                            },
                            onExternal = {
                                playerState.showPlayerChoiceDialog = false
                                launchExternalPlayer(this@MainActivity, selectedVideoUrl)
                            },
                            onDismiss = {
                                playerState.showPlayerChoiceDialog = false
                            }
                        )
                    }

                    if (showTrailerError) {
                        Dialog(onDismissRequest = { showTrailerError = false }) {
                            Box(
                                modifier = Modifier
                                    .width(380.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.background)
                                    .border(1.dp, Color.White.copy(0.1f), RoundedCornerShape(16.dp))
                                    .padding(24.dp)
                            ) {
                                androidx.compose.foundation.layout.Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "Trailer Unavailable",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = Color.White,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(24.dp))
                                    Row(modifier = Modifier.fillMaxWidth()) {
                                        VoidButton(
                                            text = "Dismiss",
                                            onClick = { showTrailerError = false },
                                            isPrimary = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Update dialogs (auto-shown after splash)
                    if (_splashFinished.value && !updateDismissed && appUpdateManager.isPopupEnabled) {
                        when (val state = updateState) {
                            is UpdateState.UpdateAvailable -> {
                                UpdateAvailableDialog(
                                    info = state.info,
                                    onUpdate = {
                                        updateScope.launch { appUpdateManager.downloadAndInstall(state.info.apkUrl) }
                                    },
                                    onDismiss = { updateDismissed = true },
                                    onDontShowAgain = {
                                        appUpdateManager.setPopupEnabled(false)
                                        updateDismissed = true
                                    }
                                )
                            }
                            is UpdateState.Downloading -> {
                                UpdateDownloadingDialog(
                                    progress = state.progress,
                                    downloadedMb = state.downloadedMb,
                                    totalMb = state.totalMb
                                )
                            }
                            is UpdateState.Error -> {
                                UpdateErrorDialog(
                                    message = state.message,
                                    onRetry = {
                                        appUpdateManager.resetState()
                                        updateScope.launch { appUpdateManager.checkForUpdate() }
                                    },
                                    onDismiss = {
                                        appUpdateManager.resetState()
                                        updateDismissed = true
                                    }
                                )
                            }
                            else -> {}
                        }
                    }

                    // EXIT POPUP
                    if (showExitConfirmation) {
                        Dialog(onDismissRequest = { showExitConfirmation = false }) {
                            Box(modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).padding(24.dp)) {
                                Column {
                                    Text("Deseja sair do Lumera?", color = Color.White)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.End, 
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        TextButton(onClick = { showExitConfirmation = false }) { Text("Cancelar") }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        TextButton(onClick = { finishAndRemoveTask() }) { Text("Sair") }
                                    }
                                }
                            }
                        }
                    }

                    } // closes LumeraBackground
                } // closes CompositionLocalProvider
            } // closes LumeraTheme
            } // closes LocaleWrapper
        } // closes setContent

        // Attach native splash overlay on top of Compose content — renders immediately
        if (showSplash) {
            splashManager?.attachOverlay()
            lifecycleScope.launch(Dispatchers.IO) {
                val profileId = profileConfigurationManager.getLastActiveProfileId()
                val splashEnabled = if (profileId != null) {
                    addonDao.getProfileById(profileId)?.splashEnabled ?: true
                } else {
                    true
                }
                if (!splashEnabled) {
                    withContext(Dispatchers.Main) {
                        splashManager?.dismiss()
                    }
                }
            }
        }
    } // closes onCreate

    companion object {
        private const val SPLASH_PAUSE_MS = 4500
        private const val KEY_SPLASH_SHOWN = "splash_shown"
    }
} // closes MainActivity


