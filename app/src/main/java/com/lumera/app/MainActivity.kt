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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.lumera.app.data.backup.DriveBackupManager
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


private const val SOURCE_SELECTION_COMMIT_MIN_POSITION_MS = 5_000L
private const val SOURCE_SELECTION_FAILURE_RESET_MAX_POSITION_MS = 1_000L

private data class PlayerSubtitlePayload(
    val id: String,
    val url: String,
    val name: String,
    val language: String?
)

private data class PendingSourceSelection(
    val playbackId: String,
    val launchedStream: Stream,
    val candidateStreams: List<Stream>
)

data class PendingEpisodeSwitch(
    val playbackId: String,
    val playbackTitle: String,
    val streams: List<Stream>?,
    val addonSubs: List<AddonSubtitle>,
    val playerCurrentSourceUrl: String?
)

@Stable
class PlayerState {
    var selectedPlayerSubtitles by mutableStateOf<List<PlayerSubtitlePayload>>(emptyList())
    var selectedPlayerSources by mutableStateOf<List<PlayerSourceOption>>(emptyList())
    var pendingSourceSelection by mutableStateOf<PendingSourceSelection?>(null)
    var showPlayerChoiceDialog by mutableStateOf(false)
    var currentEpisodeList by mutableStateOf<List<MetaVideo>>(emptyList())
    var currentStream by mutableStateOf<Stream?>(null)
    var pendingEpisodeSwitch by mutableStateOf<PendingEpisodeSwitch?>(null)
    var isEpisodeSwitchLoading by mutableStateOf(false)
}



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
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (splashOverlay == null) {
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

        // Correção Cirúrgica: Retorna um ContextWrapper com base na Activity (context), 
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
                bundle.keySet() // forces unparcel — throws if any class is missing
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

        val splashEnabledInProfile = profileConfigurationManager.getLastActiveProfileId()?.let { id ->
            runBlocking(Dispatchers.IO) { addonDao.getProfileById(id) }
        }?.splashEnabled ?: true
        val showSplash = splashEnabledInProfile && safeState?.getBoolean(KEY_SPLASH_SHOWN) != true
        if (!showSplash) _splashFinished.value = true

        if (showSplash) {
            splashManager = com.lumera.app.ui.splash.SplashManager(this) {
                _splashFinished.value = true
            }
            splashManager?.prepare()
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
            
            var driveOnboardingComplete by rememberSaveable { mutableStateOf(false) }
            val driveLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    val task = com.google.android.gms.auth.api.signin.GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    try {
                        task.getResult(com.google.android.gms.common.api.ApiException::class.java)
                        updateScope.launch {
                            val manager = com.lumera.app.data.backup.DriveBackupManager.getInstance()
                            if (manager.hasBackup(this@MainActivity) != null) {
                                manager.restoreFromDrive(this@MainActivity, addonDao)
                            } else {
                                manager.exportToDrive(this@MainActivity, addonDao)
                            }
                            driveOnboardingComplete = true
                        }
                    } catch (e: Exception) {
                        driveOnboardingComplete = true
                    }
                } else {
                    driveOnboardingComplete = true
                }
            }

            LaunchedEffect(Unit) { appUpdateManager.checkForUpdate() }

            // Silent auto-backup to Google Drive (throttled, non-blocking)
            LaunchedEffect(currentProfile) {
                if (currentProfile != null && DriveBackupManager.isSignedIn(this@MainActivity)) {
                    DriveBackupManager.getInstance().autoBackupIfNeeded(this@MainActivity, addonDao)
                }
            }
            
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
                        if (!driveOnboardingComplete) {
                            // UI de Interceptação Direta e Limpa (KISS)
                            Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.9f)), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Deseja restaurar dados do Google Drive?", color = Color.White, style = MaterialTheme.typography.headlineMedium)
                                    Spacer(Modifier.height(24.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                        VoidButton(text = "Restaurar Backup", isPrimary = true, onClick = {
                                            val gso = DriveBackupManager.getGoogleSignInOptions()
                                            val signInClient = GoogleSignIn.getClient(this@MainActivity, gso)
                                            driveLauncher.launch(signInClient.signInIntent)
                                        })
                                        VoidButton(text = "Continuar Local", onClick = {
                                            driveOnboardingComplete = true
                                        })
                                    }
                                }
                            }
                        } else {
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
                                    if (destination == NavDestination.Exit) {
                                        showExitConfirmation = true
                                    } else if (currentNav == destination) {
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
                                            when (currentNav) {
                                                NavDestination.Home, NavDestination.Movies, NavDestination.Series -> {
                                                    val vm = hiltViewModel<HomeViewModel>()
                                                    val tab = if(currentNav == NavDestination.Home) "home" else if(currentNav == NavDestination.Movies) "movies" else "series"
                                                    val dashboardTab = DashboardTab.fromString(tab)

                                                    key(tab) {
                                                        LaunchedEffect(tab, currentProfile?.id) { vm.loadScreen(tab, currentProfile) }
                                                        HomeScreen(
                                                            tab = dashboardTab,
                                                            viewModel = vm,
                                                            currentProfile = currentProfile,
                                                            entryRequester = homeEntryRequester,
                                                            drawerRequester = drawerRequesters[currentNav]!!,
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
                                                            }
                                                        )
                                                    }
                                                }
                                                NavDestination.Search -> {
                                                    val searchHomeVm = hiltViewModel<HomeViewModel>()
                                                    SearchScreen(
                                                        currentProfile = currentProfile,
                                                        watchedIds = searchHomeVm.state.collectAsState().value.watchedIds,
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
                                                            searchFocusTarget = "poster"
                                                            previousView = "menu"
                                                            activeView = "details"
                                                        },
                                                        onViewMore = { title, items ->
                                                            searchFocusTarget = if (title == "Movies") "movies" else "series"
                                                            gridViewTitle = title
                                                            gridViewItems = items
                                                            gridViewConfigId = ""
                                                            activeView = "grid"
                                                        },
                                                        moviesViewMoreRequester = searchMoviesViewMoreRequester,
                                                        seriesViewMoreRequester = searchSeriesViewMoreRequester,
                                                        resultsRequester = searchResultsRequester,
                                                        discoverRequester = searchDiscoverRequester,
                                                        lastFocusedId = searchLastFocusedId,
                                                        onFocusedIdChange = { searchLastFocusedId = it },
                                                        onDiscoverClick = { movie ->
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
                                                        entryRequester = searchEntryRequester,
                                                        drawerRequester = drawerRequesters[NavDestination.Search]!!
                                                    )
                                                }
                                                NavDestination.Profile -> {
                                                    sessionProfileId = null
                                                    sessionRestoreAttemptedProfileId = null
                                                    activeView = "menu"
                                                    themeManager.resetTheme()
                                                    mainViewModel.logout()
                                                }
                                                NavDestination.Watchlist -> {
                                                    val watchlistHomeVm = hiltViewModel<HomeViewModel>()
                                                    WatchlistScreen(
                                                        currentProfile = currentProfile,
                                                        entryRequester = watchlistEntryRequester,
                                                        drawerRequester = drawerRequesters[NavDestination.Watchlist]!!,
                                                        watchedIds = watchlistHomeVm.state.collectAsState().value.watchedIds,
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
                                                        }
                                                    )
                                                }
                                                NavDestination.Settings -> {
                                                    val homeVm = hiltViewModel<HomeViewModel>()
                                                    SettingsScreen(
                                                        currentProfile = currentProfile,
                                                        onBack = {
                                                            currentNav = NavDestination.Home
                                                            drawerRequesters[NavDestination.Home]?.requestFocus()
                                                        },
                                                        entryRequester = settingsEntryRequester,
                                                        drawerRequester = drawerRequesters[NavDestination.Settings]!!,
                                                        onDashboardChanged = { homeVm.invalidate() },
                                                        onContentFocusChanged = { settingsContentFocused = it }
                                                    )
                                                }
                                                NavDestination.Exit -> { /* App closes */ }
                                            }
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
                                            when (currentNav) {
                                                NavDestination.Home, NavDestination.Movies, NavDestination.Series -> {
                                                    val vm = hiltViewModel<HomeViewModel>()
                                                    val tab = if(currentNav == NavDestination.Home) "home" else if(currentNav == NavDestination.Movies) "movies" else "series"
                                                    val dashboardTab = DashboardTab.fromString(tab)

                                                    key(tab) {
                                                                                                                LaunchedEffect(tab, currentProfile?.id) {
                                                            delay(100)
                                                            vm.loadScreen(tab, currentProfile)
                                                        }
                                                        HomeScreen(
                                                            tab = dashboardTab,
                                                            viewModel = vm,
                                                            currentProfile = currentProfile,
                                                            entryRequester = homeEntryRequester,
                                                            drawerRequester = drawerRequesters[currentNav]!!,
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
                                                            }
                                                        )
                                                    }
                                                }
                                                NavDestination.Search -> {
                                                    val searchHomeVm = hiltViewModel<HomeViewModel>()
                                                    SearchScreen(
                                                        currentProfile = currentProfile,
                                                        watchedIds = searchHomeVm.state.collectAsState().value.watchedIds,
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
                                                            searchFocusTarget = "poster"
                                                            previousView = "menu"
                                                            activeView = "details"
                                                        },
                                                        onViewMore = { title, items ->
                                                            searchFocusTarget = if (title == "Movies") "movies" else "series"
                                                            gridViewTitle = title
                                                            gridViewItems = items
                                                            gridViewConfigId = ""
                                                            activeView = "grid"
                                                        },
                                                        moviesViewMoreRequester = searchMoviesViewMoreRequester,
                                                        seriesViewMoreRequester = searchSeriesViewMoreRequester,
                                                        resultsRequester = searchResultsRequester,
                                                        discoverRequester = searchDiscoverRequester,
                                                        lastFocusedId = searchLastFocusedId,
                                                        onFocusedIdChange = { searchLastFocusedId = it },
                                                        onDiscoverClick = { movie ->
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
                                                        entryRequester = searchEntryRequester,
                                                        drawerRequester = drawerRequesters[NavDestination.Search]!!
                                                    )
                                                }
                                                NavDestination.Profile -> {
                                                    sessionProfileId = null
                                                    sessionRestoreAttemptedProfileId = null
                                                    activeView = "menu"
                                                    themeManager.resetTheme()
                                                    mainViewModel.logout()
                                                }
                                                NavDestination.Watchlist -> {
                                                    val watchlistHomeVm = hiltViewModel<HomeViewModel>()
                                                    WatchlistScreen(
                                                        currentProfile = currentProfile,
                                                        entryRequester = watchlistEntryRequester,
                                                        drawerRequester = drawerRequesters[NavDestination.Watchlist]!!,
                                                        watchedIds = watchlistHomeVm.state.collectAsState().value.watchedIds,
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
                                                        }
                                                    )
                                                }
                                                NavDestination.Settings -> {
                                                    val homeVm = hiltViewModel<HomeViewModel>()
                                                    SettingsScreen(
                                                        currentProfile = currentProfile,
                                                        onBack = {
                                                            currentNav = NavDestination.Home
                                                            drawerRequesters[NavDestination.Home]?.requestFocus()
                                                        },
                                                        entryRequester = settingsEntryRequester,
                                                        drawerRequester = drawerRequesters[NavDestination.Settings]!!,
                                                        onDashboardChanged = { homeVm.invalidate() },
                                                        onContentFocusChanged = { settingsContentFocused = it }
                                                    )
                                                }
                                                NavDestination.Exit -> { /* App closes */ }
                                            }
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
                            val detailsNavController = rememberNavController()
                            val startRoute = "detail/${java.net.URLEncoder.encode(selectedMovieType, "UTF-8")}/${java.net.URLEncoder.encode(selectedMovieId, "UTF-8")}?addon=${java.net.URLEncoder.encode(selectedAddonBaseUrl ?: "", "UTF-8")}&resume=${java.net.URLEncoder.encode(detailsResumePlaybackHint ?: "", "UTF-8")}"

                            // Navigate to initial details when first entering
                            LaunchedEffect(selectedMovieType, selectedMovieId) {
                                val currentRoute = detailsNavController.currentBackStackEntry?.destination?.route
                                if (currentRoute == null || currentRoute == "detail_start") {
                                    detailsNavController.navigate(startRoute) {
                                        popUpTo("detail_start") { inclusive = true }
                                    }
                                }
                            }

                            BackHandler {
                                if (!detailsNavController.popBackStack()) {
                                    activeView = previousView
                                }
                            }

                            // Shared onPlayClick lambda for all detail screens
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

                                        playerState.selectedPlayerSubtitles = buildSubtitlePayload(stream, emptyList())
                                        playerState.selectedPlayerSources = sourcePayload
                                        selectedVideoUrl = ""
                                        torrentProgress = TorrentProgress("Connecting to peers...")
                                        activeView = "player"

                                        uiScope.fetchAddonSubtitlesAsync(subtitleRepository, playbackType, playbackId, stream, playerState)

                                        TorrentService.onStreamReady = { localUrl ->
                                            torrentProgress = null
                                            selectedVideoUrl = localUrl
                                        }
                                        TorrentService.onStreamError = { error ->
                                            torrentProgress = null
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

                                        playerState.selectedPlayerSubtitles = buildSubtitlePayload(stream, emptyList())
                                        playerState.selectedPlayerSources = sourcePayload
                                        selectedVideoUrl = url
                                        when (currentProfile?.playerPreference) {
                                            "external" -> launchExternalPlayer(this@MainActivity, url)
                                            "ask" -> playerState.showPlayerChoiceDialog = true
                                            else -> activeView = "player"
                                        }

                                        uiScope.fetchAddonSubtitlesAsync(subtitleRepository, playbackType, playbackId, stream, playerState)
                                    }
                                }
                            }

                            NavHost(
                                navController = detailsNavController,
                                startDestination = "detail_start",
                            ) {
                                composable("detail_start") { }
                                composable(
                                    "detail/{type}/{id}?addon={addon}&resume={resume}",
                                    arguments = listOf(
                                        navArgument("type") { type = NavType.StringType },
                                        navArgument("id") { type = NavType.StringType },
                                        navArgument("addon") { type = NavType.StringType; defaultValue = "" },
                                        navArgument("resume") { type = NavType.StringType; defaultValue = "" }
                                    )
                                ) { backStackEntry ->
                                    val detailType = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("type") ?: "movie", "UTF-8")
                                    val detailId = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("id") ?: "", "UTF-8")
                                    val detailAddon = backStackEntry.arguments?.getString("addon")?.takeIf { it.isNotEmpty() }
                                    val detailResume = backStackEntry.arguments?.getString("resume")?.takeIf { it.isNotEmpty() }

                                    DetailsScreen(
                                        type = detailType,
                                        id = detailId,
                                        addonBaseUrl = detailAddon,
                                        resumePlaybackHint = detailResume,
                                        autoSelectSource = currentProfile?.autoSelectSource ?: false,
                                        rememberSourceSelection = currentProfile?.rememberSourceSelection ?: true,
                                        onPosterResolved = { selectedMoviePoster = it },
                                        onPlayClick = onPlayClick,
                                        onNavigateToDetails = { navType, navId ->
                                            val route = "detail/${java.net.URLEncoder.encode(navType, "UTF-8")}/${java.net.URLEncoder.encode(navId, "UTF-8")}"
                                            detailsNavController.navigate(route)
                                        },
                                        onNavigateToCastDetail = { castPersonId, castPersonName ->
                                            val route = "cast_detail/$castPersonId/${java.net.URLEncoder.encode(castPersonName, "UTF-8")}"
                                            detailsNavController.navigate(route)
                                        },
                                        onNavigateToStudioDetail = { entityId, entityKind, entityName, sourceType ->
                                            val route = "studio_detail/$entityId/$entityKind/${java.net.URLEncoder.encode(entityName, "UTF-8")}/$sourceType"
                                            detailsNavController.navigate(route)
                                        },
                                        trailerReturnToken = trailerReturnToken,
                                        isTrailerLoading = isTrailerLoading,
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
                                        }
                                    )
                                }
                                composable(
                                    "cast_detail/{personId}/{personName}",
                                    arguments = listOf(
                                        navArgument("personId") { type = NavType.StringType },
                                        navArgument("personName") { type = NavType.StringType }
                                    )
                                ) { backStackEntry ->
                                    val castPersonId = (backStackEntry.arguments?.getString("personId") ?: "0").toIntOrNull() ?: 0
                                    val castPersonName = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("personName") ?: "", "UTF-8")

                                    com.lumera.app.ui.cast.CastDetailScreen(
                                        personId = castPersonId,
                                        personName = castPersonName,
                                        onBackPress = { detailsNavController.popBackStack() },
                                        onNavigateToDetails = { navType, navId ->
                                            val route = "detail/${java.net.URLEncoder.encode(navType, "UTF-8")}/${java.net.URLEncoder.encode(navId, "UTF-8")}"
                                            detailsNavController.navigate(route)
                                        }
                                    )
                                }
                                composable(
                                    "studio_detail/{entityId}/{entityKind}/{entityName}/{sourceType}",
                                    arguments = listOf(
                                        navArgument("entityId") { type = NavType.StringType },
                                        navArgument("entityKind") { type = NavType.StringType },
                                        navArgument("entityName") { type = NavType.StringType },
                                        navArgument("sourceType") { type = NavType.StringType }
                                    )
                                ) { backStackEntry ->
                                    val studioEntityId = (backStackEntry.arguments?.getString("entityId") ?: "0").toIntOrNull() ?: 0
                                    val studioEntityKind = backStackEntry.arguments?.getString("entityKind") ?: "company"
                                    val studioEntityName = java.net.URLDecoder.decode(backStackEntry.arguments?.getString("entityName") ?: "", "UTF-8")
                                    val studioSourceType = backStackEntry.arguments?.getString("sourceType") ?: "movie"

                                    com.lumera.app.ui.studio.StudioDetailScreen(
                                        entityId = studioEntityId,
                                        entityKind = studioEntityKind,
                                        entityName = studioEntityName,
                                        sourceType = studioSourceType,
                                        onBackPress = { detailsNavController.popBackStack() },
                                        onNavigateToDetails = { navType, navId ->
                                            val route = "detail/${java.net.URLEncoder.encode(navType, "UTF-8")}/${java.net.URLEncoder.encode(navId, "UTF-8")}"
                                            detailsNavController.navigate(route)
                                        }
                                    )
                                }
                            }
                        }
                        if (view == "player") {
                            if (selectedVideoUrl.isBlank() && torrentProgress == null) {
                                LaunchedEffect(Unit) { activeView = "details" }
                            } else {
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
                                            onResumeHintResolved = { detailsResumePlaybackHint = it },
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
                                                selectedPlaybackId = nextPlaybackId
                                                selectedPlaybackType = "series"
                                                selectedPlaybackTitle = nextPlaybackTitle
                                                playerState.selectedPlayerSubtitles = subtitlePayload
                                                playerState.selectedPlayerSources = sourcePayload
                                                torrentProgress = TorrentProgress("Connecting to peers...")
                                                TorrentService.onStreamReady = { localUrl ->
                                                    torrentProgress = null
                                                    selectedVideoUrl = localUrl
                                                }
                                                TorrentService.onStreamError = { error ->
                                                    torrentProgress = null
                                                    if (BuildConfig.DEBUG) Log.e("LumeraTorrent", "Stream error: $error")
                                                }
                                                TorrentService.onStreamProgress = { progress ->
                                                    torrentProgress = progress
                                                }
                                                val intent = Intent(this@MainActivity, TorrentService::class.java).apply {
                                                    putExtra("MAGNET_LINK", nextUrl)
                                                    putExtra("FILE_IDX", streamToPlay.fileIdx ?: -1)
                                                    putExtra("FILE_NAME", streamToPlay.behaviorHints?.filename ?: "")
                                                }
                                                startService(intent)
                                            } else {
                                                stopService(Intent(this@MainActivity, TorrentService::class.java))
                                                selectedPlaybackId = nextPlaybackId
                                                selectedPlaybackType = "series"
                                                selectedPlaybackTitle = nextPlaybackTitle
                                                playerState.selectedPlayerSubtitles = subtitlePayload
                                                playerState.selectedPlayerSources = sourcePayload
                                                selectedVideoUrl = nextUrl
                                            }

                                            uiScope.fetchAddonSubtitlesAsync(subtitleRepository, "series", nextStreamId, streamToPlay, playerState)
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
                                                onResumeHintResolved = { detailsResumePlaybackHint = it },
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
                                                selectedPlaybackId = epPlaybackId
                                                selectedPlaybackType = "series"
                                                selectedPlaybackTitle = epTitle
                                                playerState.selectedPlayerSubtitles = subtitlePayload
                                                playerState.selectedPlayerSources = sourcePayload
                                                torrentProgress = TorrentProgress("Connecting to peers...")
                                                TorrentService.onStreamReady = { localUrl ->
                                                    torrentProgress = null
                                                    selectedVideoUrl = localUrl
                                                }
                                                TorrentService.onStreamError = { error ->
                                                    torrentProgress = null
                                                    if (BuildConfig.DEBUG) Log.e("LumeraTorrent", "Stream error: $error")
                                                }
                                                TorrentService.onStreamProgress = { progress ->
                                                    torrentProgress = progress
                                                }
                                                val intent = Intent(this@MainActivity, TorrentService::class.java).apply {
                                                    putExtra("MAGNET_LINK", epUrl)
                                                    putExtra("FILE_IDX", streamToPlay.fileIdx ?: -1)
                                                    putExtra("FILE_NAME", streamToPlay.behaviorHints?.filename ?: "")
                                                }
                                                startService(intent)
                                            } else {
                                                stopService(Intent(this@MainActivity, TorrentService::class.java))
                                                selectedPlaybackId = epPlaybackId
                                                selectedPlaybackType = "series"
                                                selectedPlaybackTitle = epTitle
                                                playerState.selectedPlayerSubtitles = subtitlePayload
                                                playerState.selectedPlayerSources = sourcePayload
                                                selectedVideoUrl = epUrl
                                            }

                                            uiScope.fetchAddonSubtitlesAsync(subtitleRepository, "series", epStreamId, streamToPlay, playerState)
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
                                            onResumeHintResolved = { detailsResumePlaybackHint = it },
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
                                        playerState.pendingEpisodeSwitch = null

                                        if (sourceUrl.startsWith("magnet:")) {
                                            selectedPlaybackId = pending.playbackId
                                            selectedPlaybackType = "series"
                                            selectedPlaybackTitle = pending.playbackTitle
                                            playerState.selectedPlayerSubtitles = subtitlePayload
                                            playerState.selectedPlayerSources = sourcePayload
                                            torrentProgress = TorrentProgress("Connecting to peers...")
                                            TorrentService.onStreamReady = { localUrl ->
                                                torrentProgress = null
                                                selectedVideoUrl = localUrl
                                            }
                                            TorrentService.onStreamError = { error ->
                                                torrentProgress = null
                                                if (BuildConfig.DEBUG) Log.e("LumeraTorrent", "Stream error: $error")
                                            }
                                            TorrentService.onStreamProgress = { progress ->
                                                torrentProgress = progress
                                            }
                                            val intent = Intent(this@MainActivity, TorrentService::class.java).apply {
                                                putExtra("MAGNET_LINK", sourceUrl)
                                                putExtra("FILE_IDX", streamToPlay.fileIdx ?: -1)
                                                putExtra("FILE_NAME", streamToPlay.behaviorHints?.filename ?: "")
                                            }
                                            startService(intent)
                                        } else {
                                            stopService(Intent(this@MainActivity, TorrentService::class.java))
                                            selectedPlaybackId = pending.playbackId
                                            selectedPlaybackType = "series"
                                            selectedPlaybackTitle = pending.playbackTitle
                                            playerState.selectedPlayerSubtitles = subtitlePayload
                                            playerState.selectedPlayerSources = sourcePayload
                                            selectedVideoUrl = sourceUrl
                                        }

                                        // Async addon subtitle fetch for source-switched episode
                                        val vHash = streamToPlay.behaviorHints?.videoHash
                                        val vSize = streamToPlay.behaviorHints?.videoSize
                                        val vFilename = streamToPlay.behaviorHints?.filename
                                        uiScope.launch {
                                            try {
                                                val addonSubs = subtitleRepository.getSubtitles(
                                                    type = "series",
                                                    playbackId = pending.playbackId,
                                                    videoHash = vHash,
                                                    videoSize = vSize,
                                                    filename = vFilename
                                                )
                                                if (addonSubs.isNotEmpty()) {
                                                    playerState.selectedPlayerSubtitles =
                                                        (playerState.selectedPlayerSubtitles + buildAddonSubtitlePayload(addonSubs)).distinctBy { it.id }
                                                }
                                            } catch (e: Exception) {
                                                Log.e("LumeraSubtitles", "Async addon subtitle fetch failed", e)
                                            }
                                        }
                                    }
                                },
                                onEpisodeSwitchDismissed = { playerState.pendingEpisodeSwitch = null; playerState.isEpisodeSwitchLoading = false },
                                onMagnetSourceSelected = { magnetUrl, sourceFileIdx, sourceFileName, onReady ->
                                    torrentProgress = TorrentProgress("Connecting to peers...")
                                    TorrentService.onStreamReady = { localUrl ->
                                        torrentProgress = null
                                        onReady(localUrl)
                                    }
                                    TorrentService.onStreamError = { error ->
                                        torrentProgress = null
                                        if (BuildConfig.DEBUG) Log.e("LumeraTorrent", "Source switch error: $error")
                                    }
                                    TorrentService.onStreamProgress = { progress ->
                                        torrentProgress = progress
                                    }
                                    val intent = Intent(this@MainActivity, TorrentService::class.java).apply {
                                        putExtra("MAGNET_LINK", magnetUrl)
                                        putExtra("FILE_IDX", sourceFileIdx)
                                        putExtra("FILE_NAME", sourceFileName)
                                    }
                                    startService(intent)
                                },
                                torrentProgress = torrentProgress,
                                onBack = { sessionResult ->
                                    torrentProgress = null
                                    handlePlayerSessionEnd(
                                        sessionResult = sessionResult,
                                        selectedPlaybackId = selectedPlaybackId,
                                        playbackTrackSelectionStore = playbackTrackSelectionStore,
                                        sourceSelectionStore = sourceSelectionStore,
                                        pendingSourceSelection = playerState.pendingSourceSelection,
                                        onConsumePendingSelection = { playerState.pendingSourceSelection = null },
                                        onResumeHintResolved = { detailsResumePlaybackHint = it },
                                        rememberSourceSelection = currentProfile?.rememberSourceSelection ?: true
                                    )
                                    stopService(Intent(this@MainActivity, TorrentService::class.java))
                                    if (selectedPlaybackId.startsWith("trailer_")) {
                                        trailerReturnToken++
                                    }
                                    activeView = "details"
                                }
                            )
                            }
                        }
                    // ViewSwitcher end
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
                                            selectedPlaybackType = "series"
                                            selectedPlaybackTitle = pending.playbackTitle
                                            playerState.selectedPlayerSubtitles = subtitlePayload
                                            playerState.selectedPlayerSources = sourcePayload
                                            selectedVideoUrl = sourceUrl
                                        }

                                        // Async addon subtitle fetch for source-switched episode
                                        uiScope.fetchAddonSubtitlesAsync(subtitleRepository, "series", pending.playbackId, streamToPlay, playerState)
                                    }
                                },
                                onEpisodeSwitchDismissed = { playerState.pendingEpisodeSwitch = null; playerState.isEpisodeSwitchLoading = false },
                                onMagnetSourceSelected = { magnetUrl, sourceFileIdx, sourceFileName, onReady ->
                                    torrentProgress = TorrentProgress("Connecting to peers...")
                                    TorrentService.onStreamReady = { localUrl ->
                                        torrentProgress = null
                                        onReady(localUrl)
                                    }
                                    TorrentService.onStreamError = { error ->
                                        torrentProgress = null
                                        if (BuildConfig.DEBUG) Log.e("LumeraTorrent", "Source switch error: $error")
                                    }
                                    TorrentService.onStreamProgress = { progress ->
                                        torrentProgress = progress
                                    }
                                    val intent = Intent(this@MainActivity, TorrentService::class.java).apply {
                                        putExtra("MAGNET_LINK", magnetUrl)
                                        putExtra("FILE_IDX", sourceFileIdx)
                                        putExtra("FILE_NAME", sourceFileName)
                                    }
                                    startService(intent)
                                },
                                torrentProgress = torrentProgress,
                                onBack = { sessionResult ->
                                    torrentProgress = null
                                    handlePlayerSessionEnd(
                                        sessionResult = sessionResult,
                                        selectedPlaybackId = selectedPlaybackId,
                                        playbackTrackSelectionStore = playbackTrackSelectionStore,
                                        sourceSelectionStore = sourceSelectionStore,
                                        pendingSourceSelection = playerState.pendingSourceSelection,
                                        onConsumePendingSelection = { playerState.pendingSourceSelection = null },
                                        onResumeHintResolved = { detailsResumePlaybackHint = it },
                                        rememberSourceSelection = currentProfile?.rememberSourceSelection ?: true
                                    )
                                    stopService(Intent(this@MainActivity, TorrentService::class.java))
                                    if (selectedPlaybackId.startsWith("trailer_")) {
                                        trailerReturnToken++
                                    }
                                    activeView = "details"
                                }
                            )
                            }
                        }
                    // ViewSwitcher end
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
        }
    } // closes onCreate

    companion object {
        private const val SPLASH_PAUSE_MS = 4500
        private const val KEY_SPLASH_SHOWN = "splash_shown"
    }
} // closes MainActivity


