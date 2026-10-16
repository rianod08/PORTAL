package com.venuelink.studio.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.venuelink.studio.ui.navigation.AppNavGraph
import com.venuelink.studio.ui.theme.VenueLinkStudioTheme
import com.venuelink.studio.viewmodel.StudioViewModel
import com.venuelink.studio.viewmodel.StudioViewModelFactory

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as VenueLinkStudioApp
        setContent {
            val model: StudioViewModel = viewModel(factory = StudioViewModelFactory(app.tourRepository, app.mediaRepository, app.exportRepository, app.publishRepository))
            VenueLinkStudioTheme {
                AppNavGraph(model)
            }
        }
    }
}
