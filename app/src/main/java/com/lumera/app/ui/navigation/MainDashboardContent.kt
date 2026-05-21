package com.lumera.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.key
import androidx.compose.ui.focus.FocusRequester
import androidx.hilt.navigation.compose.hiltViewModel
import com.lumera.app.data.model.ProfileEntity
import com.lumera.app.ui.navigation.NavDestination
import com.lumera.app.ui.home.HomeScreen
import com.lumera.app.ui.home.HomeViewModel
import com.lumera.app.ui.search.SearchScreen
import com.lumera.app.ui.watchlist.WatchlistScreen
import com.lumera.app.ui.settings.SettingsScreen
import com.lumera.app.domain.DashboardTab
import com.lumera.app.data.model.stremio.MetaItem

@Composable
fun MainDashboardContent(
    currentNav: NavDestination,
    currentProfile: ProfileEntity?,
    homeEntryRequester: FocusRequester,
    searchEntryRequester: FocusRequester,
    discoverEntryRequester: FocusRequester,
    watchlistEntryRequester: FocusRequester,
    settingsEntryRequester: FocusRequester,
    drawerRequesters: Map<NavDestination, FocusRequester>,
    onMovieClick: (MetaItem) -> Unit,
    onViewMore: (String, List<MetaItem>, String) -> Unit,
    onSearchDiscoverClick: (MetaItem) -> Unit,
    searchFocusTarget: String?,
    onSearchFocusTargetChange: (String?) -> Unit,
    searchLastFocusedId: String?,
    onSearchLastFocusedIdChange: (String?) -> Unit,
    searchMoviesViewMoreRequester: FocusRequester,
    searchSeriesViewMoreRequester: FocusRequester,
    searchResultsRequester: FocusRequester,
    searchDiscoverRequester: FocusRequester,
    onDashboardChanged: () -> Unit,
    onSettingsContentFocusChanged: (Boolean) -> Unit,
    onNavigate: (NavDestination) -> Unit,
    onLogout: () -> Unit
) {
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
                    onMovieClick = onMovieClick,
                    onViewMore = onViewMore
                )
            }
        }
        NavDestination.Search -> {
            val searchHomeVm = hiltViewModel<HomeViewModel>()
            SearchScreen(
                currentProfile = currentProfile,
                watchedIds = searchHomeVm.state.collectAsState().value.watchedIds,
                onMovieClick = { movie ->
                    onSearchFocusTargetChange("poster")
                    onMovieClick(movie)
                },
                onViewMore = { title, items ->
                    onSearchFocusTargetChange(if (title == "Movies") "movies" else "series")
                    onViewMore(title, items, "")
                },
                moviesViewMoreRequester = searchMoviesViewMoreRequester,
                seriesViewMoreRequester = searchSeriesViewMoreRequester,
                resultsRequester = searchResultsRequester,
                discoverRequester = searchDiscoverRequester,
                lastFocusedId = searchLastFocusedId,
                onFocusedIdChange = onSearchLastFocusedIdChange,
                onDiscoverClick = { movie ->
                    onSearchFocusTargetChange("discover")
                    onSearchDiscoverClick(movie)
                },
                entryRequester = searchEntryRequester,
                drawerRequester = drawerRequesters[NavDestination.Search]!!
            )
        }
        NavDestination.Discover -> {
            val searchHomeVm = hiltViewModel<HomeViewModel>()
            SearchScreen(
                currentProfile = currentProfile,
                watchedIds = searchHomeVm.state.collectAsState().value.watchedIds,
                onMovieClick = { movie ->
                    onSearchFocusTargetChange("poster")
                    onMovieClick(movie)
                },
                onViewMore = { title, items ->
                    onSearchFocusTargetChange(if (title == "Movies") "movies" else "series")
                    onViewMore(title, items, "")
                },
                moviesViewMoreRequester = searchMoviesViewMoreRequester,
                seriesViewMoreRequester = searchSeriesViewMoreRequester,
                resultsRequester = searchResultsRequester,
                discoverRequester = searchDiscoverRequester,
                lastFocusedId = searchLastFocusedId,
                onFocusedIdChange = onSearchLastFocusedIdChange,
                onDiscoverClick = { movie ->
                    onSearchFocusTargetChange("discover")
                    onSearchDiscoverClick(movie)
                },
                entryRequester = discoverEntryRequester,
                drawerRequester = drawerRequesters[NavDestination.Discover]!!,
                isDiscoverOnly = true
            )
        }
        NavDestination.Profile -> {
            onLogout()
        }
        NavDestination.Watchlist -> {
            val watchlistHomeVm = hiltViewModel<HomeViewModel>()
            WatchlistScreen(
                currentProfile = currentProfile,
                entryRequester = watchlistEntryRequester,
                drawerRequester = drawerRequesters[NavDestination.Watchlist]!!,
                watchedIds = watchlistHomeVm.state.collectAsState().value.watchedIds,
                onMovieClick = onMovieClick
            )
        }
        NavDestination.Settings -> {
            val homeVm = hiltViewModel<HomeViewModel>()
            SettingsScreen(
                currentProfile = currentProfile,
                onBack = {
                    onNavigate(NavDestination.Home)
                    drawerRequesters[NavDestination.Home]?.requestFocus()
                },
                entryRequester = settingsEntryRequester,
                drawerRequester = drawerRequesters[NavDestination.Settings]!!,
                onDashboardChanged = {
                    homeVm.invalidate()
                    onDashboardChanged()
                },
                onContentFocusChanged = onSettingsContentFocusChanged
            )
        }
        NavDestination.Exit -> { /* App closes */ }
    }
}
