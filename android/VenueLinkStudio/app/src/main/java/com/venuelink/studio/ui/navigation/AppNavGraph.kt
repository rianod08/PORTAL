package com.venuelink.studio.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.compose.BackHandler
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.venuelink.studio.ui.screens.CaptureMethodScreen
import com.venuelink.studio.ui.screens.CaptureReviewScreen
import com.venuelink.studio.ui.screens.CreateTourScreen
import com.venuelink.studio.ui.screens.ExportScreen
import com.venuelink.studio.ui.screens.GuidedCaptureScreen
import com.venuelink.studio.ui.screens.HomeScreen
import com.venuelink.studio.ui.screens.Import360Screen
import com.venuelink.studio.ui.screens.PreviewScreen
import com.venuelink.studio.ui.screens.ProcessingScreen
import com.venuelink.studio.ui.screens.PublishScreen
import com.venuelink.studio.ui.screens.SettingsScreen
import com.venuelink.studio.ui.screens.StyleEditorScreen
import com.venuelink.studio.ui.screens.TourEditorScreen
import com.venuelink.studio.ui.screens.VenueDetailsScreen
import com.venuelink.studio.viewmodel.StudioViewModel

@Composable
fun AppNavGraph(model: StudioViewModel) {
    var legacy by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    if (!legacy) {
        com.venuelink.studio.capture.CaptureApp(onLegacy = { legacy = true })
        return
    }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    BackHandler(enabled = entry?.destination?.route == Routes.Home) { legacy = false }
    NavHost(navController = nav, startDestination = Routes.Home) {
        composable(Routes.Home) { HomeScreen(model, nav) }
        composable(Routes.CreateTour) { CreateTourScreen(model, nav) }
        composable(Routes.VenueDetails) { VenueDetailsScreen(model, nav) }
        composable(Routes.CaptureMethod) { CaptureMethodScreen(model, nav) }
        composable(Routes.Import360) { Import360Screen(model, nav) }
        composable(Routes.GuidedCapture) { GuidedCaptureScreen(model, nav) }
        composable(Routes.CaptureReview) { CaptureReviewScreen(model, nav) }
        composable(Routes.Processing) { ProcessingScreen(model, nav) }
        composable(Routes.TourEditor) { TourEditorScreen(model, nav) }
        composable(Routes.StyleEditor) { StyleEditorScreen(model, nav) }
        composable(Routes.Preview) { PreviewScreen(model, nav) }
        composable(Routes.Export) { ExportScreen(model, nav) }
        composable(Routes.Publish) { PublishScreen(model, nav) }
        composable(Routes.Settings) { SettingsScreen(model, nav) }
    }
}
