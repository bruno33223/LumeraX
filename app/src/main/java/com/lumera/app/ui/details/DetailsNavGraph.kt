package com.lumera.app.ui.details

import android.content.Intent
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lumera.app.BuildConfig
import com.lumera.app.data.model.ProfileEntity
import com.lumera.app.data.model.stremio.MetaVideo
import com.lumera.app.data.model.stremio.Stream
import com.lumera.app.domain.AddonSubtitle
import kotlinx.coroutines.launch

@Composable
fun DetailsNavGraph(
    selectedMovieType: String,
    selectedMovieId: String,
    selectedAddonBaseUrl: String?,
    detailsResumePlaybackHint: String?,
    currentProfile: ProfileEntity?,
    trailerReturnToken: Int,
    isTrailerLoading: Boolean,
    onPosterResolved: (String) -> Unit,
    onPlayClick: (String, String, String, String, String, String, Stream, List<AddonSubtitle>, List<Stream>, List<MetaVideo>) -> Unit,
    onTrailerClick: (String, String) -> Unit,
    onBack: () -> Unit
) {
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
            onBack()
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
                onPosterResolved = onPosterResolved,
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
                onTrailerClick = onTrailerClick
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
