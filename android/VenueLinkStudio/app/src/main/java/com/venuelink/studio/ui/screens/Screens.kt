@file:OptIn(ExperimentalMaterial3Api::class)

package com.venuelink.studio.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import com.venuelink.studio.data.model.Hotspot
import com.venuelink.studio.data.model.HotspotType
import com.venuelink.studio.data.model.ValidationSeverity
import com.venuelink.studio.domain.capture.ArCaptureGuidance
import com.venuelink.studio.domain.capture.CaptureArrowDirection
import com.venuelink.studio.domain.capture.CaptureGuidance
import com.venuelink.studio.domain.capture.CaptureProfile
import com.venuelink.studio.domain.capture.CaptureProfiles
import com.venuelink.studio.ui.components.ArCaptureView
import com.venuelink.studio.ui.components.GlassButton
import com.venuelink.studio.ui.components.GlassCard
import com.venuelink.studio.ui.components.HotspotTypeChip
import com.venuelink.studio.ui.components.LiquidBackground
import com.venuelink.studio.ui.components.PanoramaViewer
import com.venuelink.studio.ui.components.SectionTitle
import com.venuelink.studio.ui.components.ThemePicker
import com.venuelink.studio.ui.components.toColor
import com.venuelink.studio.ui.navigation.Routes
import com.venuelink.studio.viewmodel.StudioViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private object CaptureRuntime {
    var sessionId: String = ""
    var sceneName: String = "Captured Room"
}

@Composable
fun HomeScreen(model: StudioViewModel, nav: NavHostController) {
    val state by model.state.collectAsState()
    LiquidBackground {
        LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Spacer(Modifier.height(24.dp))
                SectionTitle("Portal Studio", "Capture, edit, and publish interactive 360 venue tours.")
                Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassButton("Create New Tour", Modifier.weight(1f), primary = true) { nav.navigate(Routes.CreateTour) }
                    GlassButton("Settings", Modifier.weight(1f)) { nav.navigate(Routes.Settings) }
                }
            }
            items(state.tours, key = { it.id }) { tour ->
                val scenes = state.scenes[tour.id].orEmpty()
                val hotspotCount = scenes.sumOf { state.hotspots[it.id].orEmpty().size }
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(tour.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                    Text("${tour.venueName} - ${tour.venueType}", color = Color(0xFF64748B))
                    Text("${scenes.size} scenes - $hotspotCount points - ${tour.status}", modifier = Modifier.padding(top = 8.dp), fontWeight = FontWeight.Bold)
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton("Edit", Modifier.weight(1f), primary = true) { nav.navigate(Routes.TourEditor) }
                        GlassButton("Preview", Modifier.weight(1f)) { nav.navigate(Routes.Preview) }
                    }
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GlassButton("Export", Modifier.weight(1f)) { nav.navigate(Routes.Export) }
                        GlassButton("Duplicate", Modifier.weight(1f)) { model.duplicateTour(tour.id) }
                        TextButton(onClick = { model.deleteTour(tour.id) }) { Text("Delete") }
                    }
                }
            }
        }
    }
}

@Composable
fun CreateTourScreen(model: StudioViewModel, nav: NavHostController) {
    var tourName by remember { mutableStateOf("") }
    var venueName by remember { mutableStateOf("") }
    var venueType by remember { mutableStateOf("Restaurant / Cafe") }
    var error by remember { mutableStateOf("") }
    FormScaffold("Create tour", nav) {
        OutlinedTextField(tourName, { tourName = it }, label = { Text("Tour name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(venueName, { venueName = it }, label = { Text("Venue name") }, modifier = Modifier.fillMaxWidth())
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(venueTypes) { type -> AssistChip(onClick = { venueType = type }, label = { Text(type) }) }
        }
        if (error.isNotBlank()) Text(error, color = Color(0xFFB91C1C), fontWeight = FontWeight.Bold)
        GlassButton("Continue", Modifier.fillMaxWidth(), primary = true) {
            val result = model.createTour(tourName, venueName, venueType)
            if (result.isValid) nav.navigate(Routes.VenueDetails) else error = result.message.orEmpty()
        }
    }
}

@Composable
fun VenueDetailsScreen(model: StudioViewModel, nav: NavHostController) {
    val tour = model.activeTour() ?: return MissingTour(nav)
    var info by remember(tour.id) { mutableStateOf(model.venueInfo(tour.id)) }
    var error by remember { mutableStateOf("") }
    FormScaffold("Venue details", nav) {
        OutlinedTextField(info.venueName, { info = info.copy(venueName = it) }, label = { Text("Venue name") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(info.shortDescription, { info = info.copy(shortDescription = it) }, label = { Text("Short description") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(info.address, { info = info.copy(address = it) }, label = { Text("Address") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(info.phone, { info = info.copy(phone = it) }, label = { Text("Phone") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(info.email, { info = info.copy(email = it) }, label = { Text("Email") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(info.website, { info = info.copy(website = it) }, label = { Text("Website") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(info.bookingUrl, { info = info.copy(bookingUrl = it) }, label = { Text("Booking URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(info.menuUrl, { info = info.copy(menuUrl = it) }, label = { Text("Menu URL") }, modifier = Modifier.fillMaxWidth())
        if (error.isNotBlank()) Text(error, color = Color(0xFFB91C1C), fontWeight = FontWeight.Bold)
        GlassButton("Choose capture method", Modifier.fillMaxWidth(), primary = true) {
            val result = model.updateVenueInfo(tour.id) { info }
            if (result.isValid) nav.navigate(Routes.CaptureMethod) else error = result.message.orEmpty()
        }
    }
}

@Composable
fun CaptureMethodScreen(model: StudioViewModel, nav: NavHostController) {
    val tour = model.activeTour() ?: return MissingTour(nav)
    FormScaffold("Archived local tours", nav) {
        Text("For a new guided room capture, return to the Portal Capture home screen. These tools are for archived local tours.")
        OptionCard("Import 360 image", "Use an existing 2:1 equirectangular 360 image.") { nav.navigate(Routes.Import360) }
        OptionCard("Use demo scene", "Start with a sample scene for testing.") {
            model.createDemoScene(tour.id)
            nav.navigate(Routes.TourEditor)
        }
    }
}

@Composable
fun Import360Screen(model: StudioViewModel, nav: NavHostController) {
    val tour = model.activeTour() ?: return MissingTour(nav)
    var message by remember { mutableStateOf("Choose a 2:1 equirectangular panorama.") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) model.importImageAsScene(tour.id, uri) { result ->
            message = result.message.orEmpty()
            nav.navigate(Routes.TourEditor)
        }
    }
    FormScaffold("Import 360 image", nav) {
        Text(message, color = Color(0xFF475569))
        GlassButton("Select image", Modifier.fillMaxWidth(), primary = true) {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
        Text("Non-2:1 images are allowed for testing, but may look stretched in the viewer.")
    }
}

@Composable
fun GuidedCaptureScreen(model: StudioViewModel, nav: NavHostController) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var arView by remember { mutableStateOf<ArCaptureView?>(null) }
    var latestFrame by remember { mutableStateOf<com.venuelink.studio.ui.components.ArFrameSnapshot?>(null) }
    var captureOriginPose by remember { mutableStateOf<FloatArray?>(null) }
    var capturedDotIds by remember { mutableStateOf(emptySet<String>()) }
    var skippedDotIds by remember { mutableStateOf(emptySet<String>()) }
    var lastCapturedDotId by remember { mutableStateOf<String?>(null) }
    var captureInProgress by remember { mutableStateOf(false) }
    var holdProgress by remember { mutableStateOf(0f) }
    var message by remember { mutableStateOf("Starting AR guided capture...") }
    val tour = model.activeTour() ?: return MissingTour(nav)
    val sceneName = remember { "Captured Room" }
    val sessionId = remember(tour.id) {
        model.createCaptureSessionId(tour.id, sceneName).also {
            CaptureRuntime.sessionId = it
            CaptureRuntime.sceneName = sceneName
        }
    }
    val dots = remember { CaptureGuidance.orderDotsForCapture(CaptureProfiles.getCaptureDots(CaptureProfile.Standard)) }
    val activeDot = CaptureGuidance.activeDotForCapture(dots, capturedDotIds, skippedDotIds)
    val originPose = captureOriginPose
    val frame = latestFrame
    val projection = if (activeDot != null && originPose != null && frame != null && frame.tracking) {
        ArCaptureGuidance.projectActiveTarget(
            dot = activeDot,
            originCameraPoseMatrix = originPose,
            currentCameraPoseMatrix = frame.poseMatrix,
            viewportWidth = frame.viewportWidth.toFloat(),
            viewportHeight = frame.viewportHeight.toFloat(),
        )
    } else {
        null
    }
    val aimed = projection?.isAimed == true
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPermission = it }

    fun captureActiveDot(manual: Boolean) {
        val dot = activeDot
        val captureView = arView
        if (dot == null) {
            nav.navigate(Routes.CaptureReview)
            return
        }
        if (!manual && !aimed) return
        if (captureView == null || latestFrame?.tracking != true) {
            message = "AR tracking is still starting. Move slowly around the room."
            return
        }
        if (captureInProgress) return
        captureInProgress = true
        message = if (manual && !aimed) "Capturing current view for this dot." else "Capturing..."
        val file = File(context.cacheDir, "capture-${sessionId}-${dot.id}.jpg")
        captureView.captureNextFrame(file)
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) permission.launch(Manifest.permission.CAMERA)
    }
    DisposableEffect(arView, hasPermission) {
        val view = arView
        if (hasPermission && view != null) {
            view.onHostResume()
            onDispose { view.close() }
        } else {
            onDispose { }
        }
    }
    LaunchedEffect(activeDot?.id, aimed, captureInProgress) {
        if (activeDot != null && aimed && !captureInProgress) {
            message = "Hold still."
            val holdStart = System.currentTimeMillis()
            while (!captureInProgress && CaptureGuidance.holdProgress(holdStart, System.currentTimeMillis()) < 1f) {
                holdProgress = CaptureGuidance.holdProgress(holdStart, System.currentTimeMillis())
                delay(50)
            }
            if (!captureInProgress) captureActiveDot(manual = false)
        } else {
            holdProgress = 0f
        }
    }
    LaunchedEffect(activeDot?.id, projection?.arrow, aimed, captureInProgress, capturedDotIds.size, skippedDotIds.size, latestFrame?.tracking) {
        if (!captureInProgress) {
            message = when {
                activeDot == null -> "All dots captured. Review your frames."
                latestFrame?.tracking != true -> "Move slowly so AR tracking can lock on."
                aimed -> "Hold still."
                projection?.arrow != null -> projection.arrow.label
                projection?.isVisible == true -> "Point your phone at the blue dot."
                else -> "Move your phone until the target appears."
            }
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    ArCaptureView(ctx).also { view ->
                        arView = view
                    }
                },
                update = { view ->
                    view.onFrame = { snapshot ->
                        latestFrame = snapshot
                        if (captureOriginPose == null && snapshot.tracking) {
                            captureOriginPose = snapshot.poseMatrix.copyOf()
                        }
                    }
                    view.onStatus = { status ->
                        if (!captureInProgress) message = status
                    }
                    view.onCaptureResult = { result ->
                        result.onSuccess { capture ->
                            val capturedDot = activeDot
                            if (capturedDot != null) {
                                model.saveCapturedFrame(
                                    sessionId = sessionId,
                                    tourId = tour.id,
                                    dotId = capturedDot.id,
                                    file = capture.file,
                                    yaw = capturedDot.yaw,
                                    pitch = capturedDot.pitch,
                                    roll = 0f,
                                    cameraPoseMatrix = capture.cameraPoseMatrix.toList(),
                                    targetWorldDirection = projection?.worldDirection?.toList().orEmpty(),
                                )
                                capturedDotIds = capturedDotIds + capturedDot.id
                                skippedDotIds = skippedDotIds - capturedDot.id
                                lastCapturedDotId = capturedDot.id
                                message = "Captured. Move to the next dot."
                            }
                            captureInProgress = false
                        }.onFailure {
                            captureInProgress = false
                            message = "Capture failed. Hold still and try again."
                        }
                    }
                },
            )
        } else {
            Text("Camera permission is required for AR guided capture. You can still import a 360 image instead.", color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
        }
        Column(Modifier.align(Alignment.TopCenter).padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${capturedDotIds.size} / ${dots.size} captured", color = Color.White, fontWeight = FontWeight.ExtraBold)
            Text(message, color = Color.White)
            Text(if (latestFrame?.tracking == true) "AR tracking live" else "Waiting for AR tracking", color = Color.White.copy(alpha = 0.75f), style = MaterialTheme.typography.bodySmall)
        }
        Canvas(Modifier.fillMaxSize()) {
            val point = projection?.screenPoint
            if (activeDot != null && point != null) {
                val color = if (aimed) Color(0xFF38BDF8) else Color(0xFF0EA5E9)
                drawCircle(color.copy(alpha = 0.25f), radius = 58f, center = point)
                drawCircle(color, radius = 20f, center = point)
                drawCircle(Color.White.copy(alpha = 0.86f), radius = 36f, center = point, style = Stroke(width = 4f))
                if (holdProgress > 0f) drawCircle(Color(0xFF67E8F9), radius = 48f, center = point, style = Stroke(width = 7f * holdProgress.coerceAtLeast(0.25f)))
            }
            drawLine(Color.White.copy(alpha = 0.72f), Offset(size.width / 2f - 28f, size.height / 2f), Offset(size.width / 2f + 28f, size.height / 2f), strokeWidth = 3f)
            drawLine(Color.White.copy(alpha = 0.72f), Offset(size.width / 2f, size.height / 2f - 28f), Offset(size.width / 2f, size.height / 2f + 28f), strokeWidth = 3f)
        }
        projection?.arrow?.let { arrow ->
            OffscreenCaptureArrow(arrow)
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Capture now", primary = true) { captureActiveDot(manual = true) }
                GlassButton("Recalibrate") {
                    val trackedFrame = latestFrame
                    if (trackedFrame?.tracking == true) {
                        captureOriginPose = trackedFrame.poseMatrix.copyOf()
                        holdProgress = 0f
                        arView?.recalibrateRequestFrame()
                        message = "Recalibrated. The next dot is anchored from here."
                    } else {
                        message = "Wait for AR tracking, then recalibrate."
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Skip dot") {
                    activeDot?.let {
                        skippedDotIds = skippedDotIds + it.id
                        message = "Skipped. Move to the next dot."
                    }
                }
                GlassButton("Retake last") {
                    val last = lastCapturedDotId
                    if (last == null) {
                        message = "No captured dot to retake yet."
                    } else {
                        capturedDotIds = capturedDotIds - last
                        model.discardCapturedFrameForDot(sessionId, last)
                        lastCapturedDotId = null
                        message = "Line up with that dot again."
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassButton("Review frames") {
                    if (model.capturedFrames(sessionId).isEmpty()) message = "Capture at least one frame first." else nav.navigate(Routes.CaptureReview)
                }
                GlassButton("Import instead") { nav.navigate(Routes.Import360) }
            }
        }
    }
}

@Composable
private fun OffscreenCaptureArrow(direction: CaptureArrowDirection) {
    val alignment = when (direction) {
        CaptureArrowDirection.Left -> Alignment.CenterStart
        CaptureArrowDirection.Right -> Alignment.CenterEnd
        CaptureArrowDirection.Up -> Alignment.TopCenter
        CaptureArrowDirection.Down -> Alignment.BottomCenter
    }
    val padding = when (direction) {
        CaptureArrowDirection.Left -> Modifier.padding(start = 20.dp)
        CaptureArrowDirection.Right -> Modifier.padding(end = 20.dp)
        CaptureArrowDirection.Up -> Modifier.padding(top = 96.dp)
        CaptureArrowDirection.Down -> Modifier.padding(bottom = 176.dp)
    }
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.align(alignment).then(padding).clip(RoundedCornerShape(28.dp)).background(Color.Black.copy(alpha = 0.42f)).padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(direction.symbol, color = Color(0xFF67E8F9), fontSize = 42.sp, fontWeight = FontWeight.ExtraBold)
            Text(direction.label, color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun CaptureReviewScreen(model: StudioViewModel, nav: NavHostController) {
    val state by model.state.collectAsState()
    val frames = remember(state.capturedFrames, CaptureRuntime.sessionId) { model.capturedFrames(CaptureRuntime.sessionId) }
    var message by remember { mutableStateOf("") }
    FormScaffold("Capture review", nav) {
        Text("${frames.size} frames captured. Weak frames can be retaken by going back to capture.")
        frames.take(12).forEachIndexed { index, frame ->
            Text("Frame ${index + 1}: yaw ${frame.yaw.roundToInt()}, pitch ${frame.pitch.roundToInt()}, quality ${(frame.qualityScore * 100).roundToInt()}%")
        }
        if (message.isNotBlank()) Text(message, color = Color(0xFFB91C1C), fontWeight = FontWeight.Bold)
        GlassButton("Build 360 image", Modifier.fillMaxWidth(), primary = true) {
            if (!CaptureGuidance.canProcessCapturedFrames(frames.size)) message = "Capture at least one frame before processing." else nav.navigate(Routes.Processing)
        }
        GlassButton("Retake", Modifier.fillMaxWidth()) { nav.navigate(Routes.GuidedCapture) }
    }
}

@Composable
fun ProcessingScreen(model: StudioViewModel, nav: NavHostController) {
    val state by model.state.collectAsState()
    val tour = model.activeTour() ?: return MissingTour(nav)
    var processing by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Ready to build your 360 image.") }
    var generatedSceneId by remember { mutableStateOf<String?>(null) }
    val generatedScene = state.scenes[tour.id].orEmpty().firstOrNull { it.id == generatedSceneId }
    val theme = model.themeFor(tour)
    LaunchedEffect(tour.id) {
        val frames = model.capturedFrames(CaptureRuntime.sessionId)
        if (CaptureRuntime.sessionId.isBlank() || !CaptureGuidance.canProcessCapturedFrames(frames.size)) {
            message = "Capture at least one frame before processing."
        } else if (!processing) {
            processing = true
            message = "Stitching your 360 photo..."
            model.processCaptureSession(tour.id, CaptureRuntime.sessionId, CaptureRuntime.sceneName) { scene ->
                generatedSceneId = scene?.id
                message = if (scene != null) "Stitched 360 photo is ready." else "Panorama processing failed. Try importing a 360 image."
                processing = false
            }
        }
    }
    FormScaffold("Building your 360 image", nav) {
        listOf("Loading frames", "Aligning capture points", "Blending overlaps", "Filling the full 360 canvas", "Saving stitched photo").forEach {
            Text(it, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
        }
        Text(message)
        if (generatedScene != null) {
            PanoramaViewer(
                scene = generatedScene,
                hotspots = emptyList(),
                theme = theme,
                modifier = Modifier.fillMaxWidth().height(360.dp),
            )
            GlassButton("Open stitched 360 preview", Modifier.fillMaxWidth(), primary = true) { nav.navigate(Routes.Preview) }
            GlassButton("Open editor", Modifier.fillMaxWidth()) { nav.navigate(Routes.TourEditor) }
        } else {
            GlassButton("Open editor", Modifier.fillMaxWidth(), primary = true) { nav.navigate(Routes.TourEditor) }
        }
        GlassButton("Retake capture", Modifier.fillMaxWidth()) { nav.navigate(Routes.GuidedCapture) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TourEditorScreen(model: StudioViewModel, nav: NavHostController) {
    val state by model.state.collectAsState()
    val tour = model.activeTour() ?: return MissingTour(nav)
    val scene = model.activeScene(tour.id)
    val theme = model.themeFor(tour)
    val hotspots = scene?.let { state.hotspots[it.id].orEmpty() }.orEmpty()
    var addType by remember { mutableStateOf(HotspotType.Info) }
    var selected by remember { mutableStateOf<Hotspot?>(null) }
    var listOpen by remember { mutableStateOf(false) }
    LiquidBackground(theme) {
        Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { nav.navigate(Routes.Home) }) { Text("Back") }
                Column(Modifier.weight(1f)) {
                    Text(tour.name, fontWeight = FontWeight.ExtraBold, color = theme.textColor.toColor())
                    Text(scene?.name ?: "No scene", color = theme.secondaryTextColor.toColor())
                }
                TextButton(onClick = { nav.navigate(Routes.Preview) }) { Text("Preview") }
                TextButton(onClick = { nav.navigate(Routes.Export) }) { Text("Export") }
            }
            PanoramaViewer(
                scene = scene,
                hotspots = hotspots,
                theme = theme,
                editMode = true,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onTapPanorama = { yaw, pitch -> if (scene != null) selected = model.createHotspot(scene.id, yaw, pitch, addType) },
                onHotspotClick = { selected = it },
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf(HotspotType.Info, HotspotType.Booking, HotspotType.Menu, HotspotType.ExternalLink, HotspotType.Contact, HotspotType.Offer, HotspotType.Product, HotspotType.ScenePortal)) { type ->
                    itemChip(type.name, selected = addType == type) { addType = type }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassButton("Hotspot List", Modifier.weight(1f)) { listOpen = true }
                GlassButton("Style", Modifier.weight(1f)) { nav.navigate(Routes.StyleEditor) }
                GlassButton("Venue Info", Modifier.weight(1f)) { nav.navigate(Routes.VenueDetails) }
            }
        }
    }
    selected?.let { hotspot ->
        HotspotEditorSheet(hotspot, onDismiss = { selected = null }, onSave = { patch -> model.updateHotspot(hotspot.id) { patch }; selected = null }, onDelete = { model.deleteHotspot(hotspot.id); selected = null }, onDuplicate = { model.duplicateHotspot(hotspot.id); selected = null })
    }
    if (listOpen) {
        ModalBottomSheet(onDismissRequest = { listOpen = false }) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Interactive points", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                hotspots.forEach { h ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${h.label} - ${h.type}", Modifier.weight(1f))
                        Switch(checked = h.isEnabled, onCheckedChange = { model.toggleHotspot(h.id) })
                        TextButton(onClick = { selected = h; listOpen = false }) { Text("Edit") }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HotspotEditorSheet(hotspot: Hotspot, onDismiss: () -> Unit, onSave: (Hotspot) -> Unit, onDelete: () -> Unit, onDuplicate: () -> Unit) {
    var draft by remember(hotspot.id) { mutableStateOf(hotspot) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text("Edit point", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    items(HotspotType.entries) { type -> itemChip(type.name, draft.type == type) { draft = draft.copy(type = type) } }
                }
                OutlinedTextField(draft.label, { draft = draft.copy(label = it) }, label = { Text("Label") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.description, { draft = draft.copy(description = it) }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.buttonText, { draft = draft.copy(buttonText = it) }, label = { Text("Button text") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.targetUrl.orEmpty(), { draft = draft.copy(targetUrl = it) }, label = { Text("Target URL") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(draft.colour, { draft = draft.copy(colour = it) }, label = { Text("Colour") }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Enabled", Modifier.weight(1f)); Switch(draft.isEnabled, { draft = draft.copy(isEnabled = it) }) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 24.dp)) {
                    GlassButton("Save", Modifier.weight(1f), primary = true) { onSave(draft) }
                    GlassButton("Duplicate", Modifier.weight(1f)) { onDuplicate() }
                    TextButton(onClick = onDelete) { Text("Delete") }
                }
            }
        }
    }
}

@Composable
fun StyleEditorScreen(model: StudioViewModel, nav: NavHostController) {
    val state by model.state.collectAsState()
    val tour = model.activeTour() ?: return MissingTour(nav)
    val theme = model.themeFor(tour)
    FormScaffold("Style editor", nav) {
        ThemePicker(state.themes, tour.themeId) { model.applyTheme(tour.id, it) }
        Text("Accent colour", fontWeight = FontWeight.Bold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(listOf("#38BDF8", "#A3E635", "#F59E0B", "#F5C451", "#111827", "#EC4899")) { colour ->
                Box(Modifier.size(52.dp).clip(CircleShape).background(colour.toColor()).clickable { model.updateAccentColor(tour.id, colour) })
            }
        }
        GlassCard(Modifier.fillMaxWidth(), dark = theme.isDark) {
            Text("Live preview", color = theme.textColor.toColor(), fontWeight = FontWeight.ExtraBold)
            Text("Sample booking card and glass controls update instantly.", color = theme.secondaryTextColor.toColor())
            Spacer(Modifier.height(10.dp))
            GlassButton("Book Now", primary = true) {}
        }
        GlassButton("Back to editor", Modifier.fillMaxWidth(), primary = true) { nav.navigate(Routes.TourEditor) }
    }
}

@Composable
fun PreviewScreen(model: StudioViewModel, nav: NavHostController) {
    val state by model.state.collectAsState()
    val tour = model.activeTour() ?: return MissingTour(nav)
    val scene = model.activeScene(tour.id)
    val theme = model.themeFor(tour)
    val hotspots = scene?.let { state.hotspots[it.id].orEmpty() }.orEmpty()
    var card by remember { mutableStateOf<Hotspot?>(null) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        PanoramaViewer(scene, hotspots, theme, Modifier.fillMaxSize(), onHotspotClick = { card = it })
        Row(Modifier.align(Alignment.TopCenter).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassButton("Back to editor") { nav.navigate(Routes.TourEditor) }
        }
        Row(Modifier.align(Alignment.BottomCenter).padding(18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Info", "Book", "Menu", "Contact", "Share").forEach { label -> Button(onClick = { }) { Text(label) } }
        }
    }
    card?.let {
        ModalBottomSheet(onDismissRequest = { card = null }) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(it.label, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                Text(it.description.ifBlank { "Customer action: ${it.type}" })
                GlassButton(it.buttonText.ifBlank { "Continue" }, Modifier.fillMaxWidth(), primary = true) { card = null }
            }
        }
    }
}

@Composable
fun ExportScreen(model: StudioViewModel, nav: NavHostController) {
    val state by model.state.collectAsState()
    val tour = model.activeTour() ?: return MissingTour(nav)
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var json by remember { mutableStateOf("") }
    FormScaffold("Export tour", nav) {
        val scenes = state.scenes[tour.id].orEmpty()
        val hotspots = scenes.sumOf { state.hotspots[it.id].orEmpty().size }
        Text("${tour.name}\n${scenes.size} scenes\n$hotspots interactive points\nTheme: ${model.themeFor(tour).name}")
        GlassButton("Export JSON", Modifier.fillMaxWidth(), primary = true) { json = model.exportJson(tour.id) }
        GlassButton("Copy JSON", Modifier.fillMaxWidth()) {
            json = model.exportJson(tour.id)
            clipboard.setText(AnnotatedString(json))
        }
        GlassButton("Share JSON", Modifier.fillMaxWidth()) {
            json = model.exportJson(tour.id)
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_TEXT, json), "Share tour JSON"))
        }
        GlassButton("Export ZIP bundle", Modifier.fillMaxWidth()) { json = "ZIP bundle export is coming soon. JSON export is ready now." }
        if (json.isNotBlank()) Text(json.take(1200), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun PublishScreen(model: StudioViewModel, nav: NavHostController) {
    val tour = model.activeTour() ?: return MissingTour(nav)
    var endpoint by remember { mutableStateOf("") }
    var apiKey by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("No website endpoint configured yet. Export the tour locally instead.") }
    FormScaffold("Publish", nav) {
        OutlinedTextField(endpoint, { endpoint = it }, label = { Text("VenueLink API base URL") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API key optional") }, modifier = Modifier.fillMaxWidth())
        Text(message)
        GlassButton("Publish", Modifier.fillMaxWidth(), primary = true) {
            model.configurePublishEndpoint(endpoint, apiKey)
            message = model.publish(tour.id).message.orEmpty()
        }
        GlassButton("Export instead", Modifier.fillMaxWidth()) { nav.navigate(Routes.Export) }
    }
}

@Composable
fun SettingsScreen(model: StudioViewModel, nav: NavHostController) {
    FormScaffold("Settings", nav) {
        Text("App theme mode: System")
        Text("Default capture profile: Standard")
        Text("Default hotspot style: Glass dot")
        GlassButton("Reset demo data", Modifier.fillMaxWidth()) { model.resetDemoData() }
        GlassButton("Clear all local tours", Modifier.fillMaxWidth()) { model.clearAll() }
        Text("About Portal Studio\nCapture interactive 360 venue tours.")
    }
}

@Composable
private fun FormScaffold(title: String, nav: NavHostController, content: @Composable ColumnScope.() -> Unit) {
    LiquidBackground {
        LazyColumn(Modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { nav.popBackStack() }) { Text("Back") }
                    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                }
                GlassCard(Modifier.fillMaxWidth()) { content() }
            }
        }
    }
}

@Composable
private fun OptionCard(title: String, body: String, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(title, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleLarge)
        Text(body, color = Color(0xFF64748B), modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun MissingTour(nav: NavHostController) {
    LiquidBackground {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            GlassCard { Text("No tour selected."); GlassButton("Go home") { nav.navigate(Routes.Home) } }
        }
    }
}

@Composable
private fun itemChip(text: String, selected: Boolean, onClick: () -> Unit) {
    AssistChip(onClick = onClick, label = { Text(if (selected) "$text *" else text) })
}

private val venueTypes = listOf(
    "Restaurant / Cafe", "Bar / Nightlife", "Gym / Fitness", "Salon / Beauty", "Clinic / Wellness",
    "Event Venue", "Wedding Venue", "Retail Showroom", "Studio / Creative Space", "Other",
)
