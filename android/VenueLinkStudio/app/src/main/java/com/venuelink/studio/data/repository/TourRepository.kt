package com.venuelink.studio.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.net.Uri
import androidx.core.net.toUri
import androidx.core.content.FileProvider
import com.venuelink.studio.data.model.CapturedFrame
import com.venuelink.studio.data.model.ExportAsset
import com.venuelink.studio.data.model.Hotspot
import com.venuelink.studio.data.model.HotspotStyle
import com.venuelink.studio.data.model.HotspotType
import com.venuelink.studio.data.model.StudioState
import com.venuelink.studio.data.model.Tour
import com.venuelink.studio.data.model.TourExport
import com.venuelink.studio.data.model.TourScene
import com.venuelink.studio.data.model.TourStatus
import com.venuelink.studio.data.model.ValidationResult
import com.venuelink.studio.data.model.ValidationSeverity
import com.venuelink.studio.data.model.VenueInfo
import com.venuelink.studio.data.model.defaultThemes
import com.venuelink.studio.data.model.newId
import com.venuelink.studio.domain.export.TourExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import java.io.File
import kotlin.math.abs
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

class TourRepository(private val context: Context) {
    private val storeFile = File(context.filesDir, "studio-state.json")
    private val panoramasDir = File(context.filesDir, "panoramas").apply { mkdirs() }
    private val capturesDir = File(context.filesDir, "captures").apply { mkdirs() }
    private val exportsDir = File(context.filesDir, "exports").apply { mkdirs() }
    private val _state = MutableStateFlow(loadState())
    val state: StateFlow<StudioState> = _state

    private fun loadState(): StudioState {
        return runCatching {
            if (!storeFile.exists()) seedState() else TourExporter.json.decodeFromString<StudioState>(storeFile.readText())
        }.getOrElse { seedState() }
    }

    private fun seedState(): StudioState {
        val demoImage = generateDemoPanorama()
        val tour = Tour(
            name = "Bloom Cafe sample tour",
            venueName = "Bloom Cafe",
            venueType = "Restaurant / Cafe",
            description = "A warm sample venue tour ready for editing.",
            coverImageUri = demoImage,
            themeId = "warm-cafe",
            accentColor = "#D97706",
        )
        val venueInfo = VenueInfo(
            tourId = tour.id,
            venueName = tour.venueName,
            shortDescription = "Capture interactive 360 venue tours.",
            address = "12 Market Lane",
            phone = "+353 1 555 0199",
            email = "hello@bloom.example",
            website = "https://example.com",
            bookingUrl = "https://example.com/book",
            menuUrl = "https://example.com/menu",
            openingHours = "Mon-Sun 8am-6pm",
        )
        val scene = TourScene(tourId = tour.id, name = "Main Room", panoramaUri = demoImage, isDefault = true)
        val hotspots = listOf(
            Hotspot(tourId = tour.id, sceneId = scene.id, label = "Book a table", type = HotspotType.Booking, yaw = -35f, pitch = -4f, buttonText = "Book Now", targetUrl = venueInfo.bookingUrl),
            Hotspot(tourId = tour.id, sceneId = scene.id, label = "View menu", type = HotspotType.Menu, yaw = 22f, pitch = 2f, buttonText = "Open Menu", targetUrl = venueInfo.menuUrl),
            Hotspot(tourId = tour.id, sceneId = scene.id, label = "Private hire", type = HotspotType.Info, yaw = 74f, pitch = 8f, description = "Show customers how this area works for events."),
            Hotspot(tourId = tour.id, sceneId = scene.id, label = "Contact us", type = HotspotType.Contact, yaw = 128f, pitch = -6f, phoneNumber = venueInfo.phone, email = venueInfo.email),
            Hotspot(tourId = tour.id, sceneId = scene.id, label = "Special offer", type = HotspotType.Offer, yaw = -112f, pitch = 4f, description = "Add seasonal offers directly inside the tour.", buttonText = "Claim offer"),
            Hotspot(tourId = tour.id, sceneId = scene.id, label = "Product info", type = HotspotType.Product, yaw = -74f, pitch = -10f, productPrice = "From EUR 12", description = "Highlight services, products, or packages."),
            Hotspot(tourId = tour.id, sceneId = scene.id, label = "Meet the team", type = HotspotType.Gallery, yaw = 160f, pitch = 12f, description = "Use images to introduce your people."),
            Hotspot(tourId = tour.id, sceneId = scene.id, label = "Directions", type = HotspotType.ExternalLink, yaw = 0f, pitch = -18f, buttonText = "Open Maps", targetUrl = "https://maps.google.com"),
        )
        return StudioState(
            tours = listOf(tour),
            activeTourId = tour.id,
            venueInfo = mapOf(tour.id to venueInfo),
            scenes = mapOf(tour.id to listOf(scene)),
            hotspots = mapOf(scene.id to hotspots),
        ).also { saveState(it) }
    }

    private fun saveState(next: StudioState = _state.value) {
        storeFile.writeText(TourExporter.json.encodeToString(StudioState.serializer(), next))
    }

    private fun update(mutator: (StudioState) -> StudioState) {
        val next = mutator(_state.value)
        _state.value = next
        saveState(next)
    }

    fun getAllTours(): Flow<List<Tour>> = state.map { it.tours.sortedByDescending(Tour::updatedAt) }
    fun getTour(tourId: String): Tour? = _state.value.tours.find { it.id == tourId }
    fun getScenesForTour(tourId: String): Flow<List<TourScene>> = state.map { it.scenes[tourId].orEmpty().sortedBy(TourScene::order) }
    fun getHotspotsForScene(sceneId: String): Flow<List<Hotspot>> = state.map { it.hotspots[sceneId].orEmpty() }
    fun getHotspotsForTour(tourId: String): Flow<List<Hotspot>> = state.map { current ->
        current.scenes[tourId].orEmpty().flatMap { current.hotspots[it.id].orEmpty() }
    }

    fun activeTour(): Tour? = _state.value.activeTourId?.let(::getTour) ?: _state.value.tours.firstOrNull()
    fun activeTourId(): String? = activeTour()?.id
    fun activeScene(tourId: String): TourScene? = _state.value.scenes[tourId].orEmpty().firstOrNull { it.isDefault } ?: _state.value.scenes[tourId].orEmpty().firstOrNull()
    fun venueInfo(tourId: String): VenueInfo = _state.value.venueInfo[tourId] ?: VenueInfo(tourId = tourId)
    fun themeFor(tour: Tour) = _state.value.themes.find { it.id == tour.themeId } ?: defaultThemes().first()

    fun createTour(name: String, venueName: String, venueType: String): Tour {
        val now = System.currentTimeMillis()
        val tour = Tour(name = name.trim(), venueName = venueName.trim(), venueType = venueType, createdAt = now, updatedAt = now)
        val info = VenueInfo(tourId = tour.id, venueName = venueName.trim())
        update {
            it.copy(
                tours = it.tours + tour,
                activeTourId = tour.id,
                venueInfo = it.venueInfo + (tour.id to info),
                scenes = it.scenes + (tour.id to emptyList()),
            )
        }
        return tour
    }

    fun updateTour(tourId: String, patch: Tour.() -> Tour): Tour {
        var result = requireNotNull(getTour(tourId)) { "Tour not found" }.patch().copy(updatedAt = System.currentTimeMillis())
        update { state -> state.copy(tours = state.tours.map { if (it.id == tourId) result else it }) }
        return result
    }

    fun deleteTour(tourId: String) {
        update { state ->
            val sceneIds = state.scenes[tourId].orEmpty().map { it.id }.toSet()
            state.copy(
                tours = state.tours.filterNot { it.id == tourId },
                activeTourId = state.tours.firstOrNull { it.id != tourId }?.id,
                venueInfo = state.venueInfo - tourId,
                scenes = state.scenes - tourId,
                hotspots = state.hotspots.filterKeys { it !in sceneIds },
                capturedFrames = state.capturedFrames.filterKeys { it !in sceneIds },
            )
        }
    }

    fun duplicateTour(tourId: String): Tour {
        val source = requireNotNull(getTour(tourId))
        val copy = source.copy(id = newId(), name = "${source.name} copy", status = TourStatus.Draft, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
        val oldScenes = _state.value.scenes[tourId].orEmpty()
        val sceneMap = oldScenes.associate { it.id to newId() }
        val newScenes = oldScenes.map { it.copy(id = sceneMap.getValue(it.id), tourId = copy.id) }
        val newHotspots = oldScenes.flatMap { scene ->
            _state.value.hotspots[scene.id].orEmpty().map { it.copy(id = newId(), tourId = copy.id, sceneId = sceneMap.getValue(scene.id)) }
        }
        update {
            it.copy(
                tours = it.tours + copy,
                activeTourId = copy.id,
                venueInfo = it.venueInfo + (copy.id to venueInfo(tourId).copy(id = newId(), tourId = copy.id)),
                scenes = it.scenes + (copy.id to newScenes),
                hotspots = it.hotspots + newScenes.associate { scene -> scene.id to newHotspots.filter { hotspot -> hotspot.sceneId == scene.id } },
            )
        }
        return copy
    }

    fun saveTour(tour: Tour) = update { state -> state.copy(tours = state.tours.map { if (it.id == tour.id) tour else it }) }
    fun markTourReady(tourId: String) = updateTour(tourId) { copy(status = TourStatus.Ready) }
    fun markTourPublished(tourId: String) = updateTour(tourId) { copy(status = TourStatus.Published) }

    fun updateVenueInfo(tourId: String, patch: VenueInfo.() -> VenueInfo): VenueInfo {
        val updated = venueInfo(tourId).patch()
        update { it.copy(venueInfo = it.venueInfo + (tourId to updated)) }
        return updated
    }

    fun validateVenueInfo(info: VenueInfo): ValidationResult {
        if (info.venueName.isBlank()) return ValidationResult(false, "Venue name is required.", ValidationSeverity.Error)
        if (info.email.isNotBlank() && !info.email.contains("@")) return ValidationResult(false, "Enter a valid email or leave it blank.", ValidationSeverity.Error)
        val urlFields = listOf(info.website, info.bookingUrl, info.menuUrl).filter { it.isNotBlank() }
        if (urlFields.any { !isValidUrl(it) }) return ValidationResult(false, "URLs must start with http:// or https://", ValidationSeverity.Error)
        return ValidationResult(true, "Venue info looks good.")
    }

    fun createScene(tourId: String, name: String, panoramaUri: String): TourScene {
        val existing = _state.value.scenes[tourId].orEmpty()
        val scene = TourScene(tourId = tourId, name = name.ifBlank { "New Room" }, panoramaUri = panoramaUri, order = existing.size, isDefault = existing.isEmpty())
        update { it.copy(scenes = it.scenes + (tourId to (existing + scene))) }
        return scene
    }

    fun createCaptureSessionId(tourId: String, sceneName: String): String =
        "$tourId-${sceneName.ifBlank { "Captured Room" }.replace("\\s+".toRegex(), "-")}-${System.currentTimeMillis()}"

    fun saveCapturedFrame(
        sessionId: String,
        tourId: String,
        dotId: String,
        sourceFile: File,
        yaw: Float,
        pitch: Float,
        roll: Float,
        cameraPoseMatrix: List<Float> = emptyList(),
        targetWorldDirection: List<Float> = emptyList(),
    ): CapturedFrame {
        val target = File(capturesDir, "frame-${sessionId.takeLast(10)}-${dotId.take(8)}.jpg")
        sourceFile.copyTo(target, overwrite = true)
        val frame = CapturedFrame(
            tourId = tourId,
            sceneId = sessionId,
            dotId = dotId,
            imageUri = target.toUri().toString(),
            yaw = yaw,
            pitch = pitch,
            roll = roll,
            qualityScore = analyzeFrameQuality(target),
            blurScore = analyzeFrameQuality(target),
            exposureScore = 0.82f,
            cameraPoseMatrix = cameraPoseMatrix,
            targetWorldDirection = targetWorldDirection,
        )
        update { state ->
            val withoutOldFrameForDot = state.capturedFrames[sessionId].orEmpty().filterNot { it.dotId == dotId }
            state.copy(capturedFrames = state.capturedFrames + (sessionId to (withoutOldFrameForDot + frame)))
        }
        return frame
    }

    fun getCapturedFrames(sessionId: String): List<CapturedFrame> = _state.value.capturedFrames[sessionId].orEmpty()

    fun discardCapturedFrameForDot(sessionId: String, dotId: String) {
        update { state ->
            state.copy(capturedFrames = state.capturedFrames + (sessionId to state.capturedFrames[sessionId].orEmpty().filterNot { it.dotId == dotId }))
        }
    }

    suspend fun processCaptureSession(tourId: String, sessionId: String, sceneName: String): TourScene = withContext(Dispatchers.IO) {
        val frames = getCapturedFrames(sessionId)
        require(frames.isNotEmpty()) { "Capture at least one frame before processing." }
        val panoramaUri = generateEquirectangularPanorama(frames)
        createScene(tourId, sceneName.ifBlank { "Captured Room" }, panoramaUri).also { scene ->
            setDefaultScene(tourId, scene.id)
            updateTour(tourId) { copy(coverImageUri = coverImageUri ?: panoramaUri) }
        }
    }

    fun generateEquirectangularPanorama(frames: List<CapturedFrame>): String {
        val width = 2048
        val height = 1024
        val pixelCount = width * height
        val red = FloatArray(pixelCount)
        val green = FloatArray(pixelCount)
        val blue = FloatArray(pixelCount)
        val weights = FloatArray(pixelCount)
        val averageColor = averageFrameColor(frames)
        seedPanoramaBackground(red, green, blue, weights, width, height, averageColor)

        frames.sortedBy { it.capturedAt }.forEach { frame ->
            val file = File(Uri.parse(frame.imageUri).path.orEmpty())
            val decoded = decodeFrameForStitching(file) ?: return@forEach
            val patchWidth = if (abs(frame.pitch) > 65f) (width * 0.34f).roundToInt() else (width * 0.24f).roundToInt()
            val patchHeight = if (abs(frame.pitch) > 65f) (height * 0.32f).roundToInt() else (height * 0.36f).roundToInt()
            val source = centerCropForPatch(decoded, patchWidth.toFloat() / patchHeight.toFloat())
            val cropped = Bitmap.createBitmap(decoded, source.left, source.top, source.width(), source.height())
            val patch = Bitmap.createScaledBitmap(cropped, patchWidth, patchHeight, true)
            cropped.recycle()
            val centerX = (((frame.yaw + 180f) / 360f) * width).roundToInt()
            val centerY = (((90f - frame.pitch.coerceIn(-89f, 89f)) / 180f) * height).roundToInt()
            blendPatchIntoPanorama(
                patch = patch,
                centerX = centerX,
                centerY = centerY,
                panoramaWidth = width,
                panoramaHeight = height,
                red = red,
                green = green,
                blue = blue,
                weights = weights,
                frameWeight = frame.qualityScore.coerceIn(0.35f, 1f),
            )
            patch.recycle()
            decoded.recycle()
        }

        val bitmap = composeWeightedPanorama(red, green, blue, weights, width, height)
        val file = File(panoramasDir, "captured-panorama-${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bitmap.recycle()
        return file.toUri().toString()
    }

    private fun seedPanoramaBackground(
        red: FloatArray,
        green: FloatArray,
        blue: FloatArray,
        weights: FloatArray,
        width: Int,
        height: Int,
        averageColor: Int,
    ) {
        val baseR = Color.red(averageColor).toFloat()
        val baseG = Color.green(averageColor).toFloat()
        val baseB = Color.blue(averageColor).toFloat()
        for (y in 0 until height) {
            val vertical = y.toFloat() / max(1, height - 1).toFloat()
            val shade = 0.72f + 0.34f * sin(vertical * PI).toFloat()
            for (x in 0 until width) {
                val index = y * width + x
                red[index] = baseR * shade
                green[index] = baseG * shade
                blue[index] = baseB * shade
                weights[index] = 0.08f
            }
        }
    }

    private fun blendPatchIntoPanorama(
        patch: Bitmap,
        centerX: Int,
        centerY: Int,
        panoramaWidth: Int,
        panoramaHeight: Int,
        red: FloatArray,
        green: FloatArray,
        blue: FloatArray,
        weights: FloatArray,
        frameWeight: Float,
    ) {
        val pixels = IntArray(patch.width)
        val left = centerX - patch.width / 2
        val top = centerY - patch.height / 2
        for (py in 0 until patch.height) {
            val targetY = top + py
            if (targetY !in 0 until panoramaHeight) continue
            patch.getPixels(pixels, 0, patch.width, 0, py, patch.width, 1)
            val yBlend = raisedCosineWeight(py, patch.height)
            for (px in 0 until patch.width) {
                val targetX = wrapX(left + px, panoramaWidth)
                val xBlend = raisedCosineWeight(px, patch.width)
                val weight = (0.12f + xBlend * yBlend * 0.88f) * frameWeight
                val color = pixels[px]
                val index = targetY * panoramaWidth + targetX
                red[index] += Color.red(color) * weight
                green[index] += Color.green(color) * weight
                blue[index] += Color.blue(color) * weight
                weights[index] += weight
            }
        }
    }

    private fun composeWeightedPanorama(red: FloatArray, green: FloatArray, blue: FloatArray, weights: FloatArray, width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        for (index in pixels.indices) {
            val weight = weights[index].coerceAtLeast(0.001f)
            pixels[index] = Color.rgb(
                (red[index] / weight).roundToInt().coerceIn(0, 255),
                (green[index] / weight).roundToInt().coerceIn(0, 255),
                (blue[index] / weight).roundToInt().coerceIn(0, 255),
            )
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        return bitmap
    }

    private fun averageFrameColor(frames: List<CapturedFrame>): Int {
        var red = 32L
        var green = 38L
        var blue = 48L
        var samples = 1L
        frames.take(8).forEach { frame ->
            val file = File(Uri.parse(frame.imageUri).path.orEmpty())
            val bitmap = decodeFrameForStitching(file, maxDimension = 96) ?: return@forEach
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            pixels.forEachIndexed { index, color ->
                if (index % 19 == 0) {
                    red += Color.red(color)
                    green += Color.green(color)
                    blue += Color.blue(color)
                    samples += 1
                }
            }
            bitmap.recycle()
        }
        return Color.rgb((red / samples).toInt(), (green / samples).toInt(), (blue / samples).toInt())
    }

    private fun decodeFrameForStitching(file: File, maxDimension: Int = 1280): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) sample *= 2
        return BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun centerCropForPatch(bitmap: Bitmap, aspect: Float): Rect {
        val bitmapAspect = bitmap.width.toFloat() / bitmap.height.toFloat()
        val edgeInsetX = (bitmap.width * 0.08f).roundToInt()
        val edgeInsetY = (bitmap.height * 0.08f).roundToInt()
        val safeWidth = bitmap.width - edgeInsetX * 2
        val safeHeight = bitmap.height - edgeInsetY * 2
        return if (bitmapAspect > aspect) {
            val cropWidth = (safeHeight * aspect).roundToInt().coerceAtMost(safeWidth)
            val left = edgeInsetX + (safeWidth - cropWidth) / 2
            Rect(left, edgeInsetY, left + cropWidth, edgeInsetY + safeHeight)
        } else {
            val cropHeight = (safeWidth / aspect).roundToInt().coerceAtMost(safeHeight)
            val top = edgeInsetY + (safeHeight - cropHeight) / 2
            Rect(edgeInsetX, top, edgeInsetX + safeWidth, top + cropHeight)
        }
    }

    private fun raisedCosineWeight(index: Int, size: Int): Float =
        sin(((index + 0.5f) / max(1f, size.toFloat())) * PI).toFloat().coerceIn(0f, 1f)

    private fun wrapX(x: Int, width: Int): Int {
        val wrapped = x % width
        return if (wrapped < 0) wrapped + width else wrapped
    }

    private fun analyzeFrameQuality(file: File): Float {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return 0f
        return if (bounds.outWidth < 900 || bounds.outHeight < 700) 0.55f else 0.86f
    }

    fun updateScene(sceneId: String, patch: TourScene.() -> TourScene): TourScene {
        var updated: TourScene? = null
        update { state ->
            state.copy(scenes = state.scenes.mapValues { (_, scenes) ->
                scenes.map { scene ->
                    if (scene.id == sceneId) scene.patch().copy(updatedAt = System.currentTimeMillis()).also { updated = it } else scene
                }
            })
        }
        return requireNotNull(updated)
    }

    fun deleteScene(sceneId: String) {
        update { state ->
            val nextScenes = state.scenes.mapValues { (_, scenes) -> scenes.filterNot { it.id == sceneId } }
            state.copy(scenes = nextScenes, hotspots = state.hotspots - sceneId)
        }
    }

    fun setDefaultScene(tourId: String, sceneId: String) {
        update { state ->
            state.copy(scenes = state.scenes + (tourId to state.scenes[tourId].orEmpty().map { it.copy(isDefault = it.id == sceneId) }))
        }
    }

    fun createHotspot(sceneId: String, yaw: Float, pitch: Float, type: HotspotType): Hotspot {
        val scene = _state.value.scenes.values.flatten().first { it.id == sceneId }
        val info = venueInfo(scene.tourId)
        val hotspot = Hotspot(
            tourId = scene.tourId,
            sceneId = sceneId,
            label = defaultHotspotLabel(type),
            type = type,
            yaw = yaw,
            pitch = pitch,
            colour = getTour(scene.tourId)?.accentColor ?: "#38BDF8",
            targetUrl = when (type) {
                HotspotType.Booking -> info.bookingUrl
                HotspotType.Menu -> info.menuUrl
                HotspotType.ExternalLink -> info.website
                else -> null
            }?.ifBlank { null },
            phoneNumber = if (type == HotspotType.Call || type == HotspotType.Contact) info.phone else null,
            email = if (type == HotspotType.Email || type == HotspotType.Contact) info.email else null,
            buttonText = when (type) {
                HotspotType.Booking -> "Book Now"
                HotspotType.Menu -> "Open Menu"
                HotspotType.ExternalLink -> "Open Link"
                else -> ""
            },
        )
        update { state -> state.copy(hotspots = state.hotspots + (sceneId to (state.hotspots[sceneId].orEmpty() + hotspot))) }
        return hotspot
    }

    fun updateHotspot(hotspotId: String, patch: Hotspot.() -> Hotspot): Hotspot {
        var updated: Hotspot? = null
        update { state ->
            state.copy(hotspots = state.hotspots.mapValues { (_, list) ->
                list.map { if (it.id == hotspotId) it.patch().copy(updatedAt = System.currentTimeMillis()).also { next -> updated = next } else it }
            })
        }
        return requireNotNull(updated)
    }

    fun deleteHotspot(hotspotId: String) = update { state -> state.copy(hotspots = state.hotspots.mapValues { it.value.filterNot { h -> h.id == hotspotId } }) }
    fun duplicateHotspot(hotspotId: String): Hotspot {
        val source = _state.value.hotspots.values.flatten().first { it.id == hotspotId }
        val copy = source.copy(id = newId(), label = "${source.label} copy", yaw = source.yaw + 8f, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
        update { state -> state.copy(hotspots = state.hotspots + (copy.sceneId to (state.hotspots[copy.sceneId].orEmpty() + copy))) }
        return copy
    }
    fun moveHotspot(hotspotId: String, yaw: Float, pitch: Float) = updateHotspot(hotspotId) { copy(yaw = yaw, pitch = pitch) }
    fun toggleHotspot(hotspotId: String) = updateHotspot(hotspotId) { copy(isEnabled = !isEnabled) }

    fun applyTheme(tourId: String, themeId: String): Tour {
        val theme = _state.value.themes.first { it.id == themeId }
        return updateTour(tourId) { copy(themeId = themeId, accentColor = theme.accentColor) }
    }
    fun updateAccentColor(tourId: String, colour: String) = updateTour(tourId) { copy(accentColor = colour) }
    fun updateHotspotStyle(tourId: String, style: HotspotStyle) = getTour(tourId) ?: error("Tour not found")

    suspend fun import360Image(uri: Uri): String = withContext(Dispatchers.IO) {
        copyImageToAppStorage(uri)
    }

    suspend fun validate360Image(uri: Uri): ValidationResult = withContext(Dispatchers.IO) {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            ValidationResult(false, "This image could not be loaded.", ValidationSeverity.Error)
        } else if (!checkEquirectangularRatio(bounds.outWidth, bounds.outHeight)) {
            ValidationResult(true, "This image may not display correctly as a 360 tour. For best results, use a 2:1 equirectangular image.", ValidationSeverity.Warning)
        } else {
            ValidationResult(true, "${bounds.outWidth} x ${bounds.outHeight} 360 image ready.")
        }
    }

    fun checkEquirectangularRatio(width: Int, height: Int): Boolean = height > 0 && abs((width.toFloat() / height.toFloat()) - 2f) < 0.08f

    private fun copyImageToAppStorage(uri: Uri): String {
        val file = File(panoramasDir, "panorama-${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Image failed to open" }
            file.outputStream().use { output -> input.copyTo(output) }
        }
        return file.toUri().toString()
    }

    fun createThumbnailForScene(sceneId: String): String = _state.value.scenes.values.flatten().first { it.id == sceneId }.panoramaUri
    fun removeTourImage(uri: String) { runCatching { File(Uri.parse(uri).path.orEmpty()).delete() } }

    fun exportPayload(tourId: String): TourExport {
        val tour = requireNotNull(getTour(tourId))
        val scenes = _state.value.scenes[tourId].orEmpty()
        val hotspots = scenes.flatMap { _state.value.hotspots[it.id].orEmpty() }
        return TourExport(
            tour = tour,
            venueInfo = venueInfo(tourId),
            scenes = scenes,
            hotspots = hotspots,
            theme = themeFor(tour),
            assets = scenes.map { ExportAsset(type = "panorama", localUri = it.panoramaUri, fileName = "scene-${it.id}.jpg", mimeType = "image/jpeg") },
        )
    }

    fun exportTourJson(tourId: String): String = TourExporter.exportTourJson(exportPayload(tourId))

    fun exportTourBundle(tourId: String): Uri {
        val file = File(exportsDir, "venuelink-${tourId.take(8)}.json")
        file.writeText(exportTourJson(tourId))
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    fun clearAll() {
        update { StudioState() }
    }

    fun resetDemoData() {
        _state.value = seedState()
    }

    private fun generateDemoPanorama(): String {
        val file = File(panoramasDir, "demo-panorama.jpg")
        if (file.exists()) return file.toUri().toString()
        val bitmap = Bitmap.createBitmap(2048, 1024, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), intArrayOf(0xFF0F233C.toInt(), 0xFFECCB9A.toInt(), 0xFFB8E1DE.toInt()), null, Shader.TileMode.CLAMP)
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), paint)
        paint.shader = null
        paint.color = 0x55FFFFFF
        paint.textSize = 58f
        listOf("Entrance", "Main Room", "Bar", "Private Area", "Outdoor").forEachIndexed { index, label ->
            canvas.drawText(label, 160f + index * 370f, 520f + if (index % 2 == 0) -120f else 120f, paint)
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        bitmap.recycle()
        return file.toUri().toString()
    }

    private fun defaultHotspotLabel(type: HotspotType): String = when (type) {
        HotspotType.Info -> "Info point"
        HotspotType.Booking -> "Book this space"
        HotspotType.Menu -> "View menu"
        HotspotType.ExternalLink -> "Open link"
        HotspotType.Contact -> "Contact us"
        HotspotType.Call -> "Call"
        HotspotType.Email -> "Email"
        HotspotType.Offer -> "Special offer"
        HotspotType.Product -> "Product info"
        HotspotType.ScenePortal -> "Next room"
        else -> "${type.name} point"
    }
}

fun isValidUrl(url: String): Boolean = url.startsWith("https://") || url.startsWith("http://")

class MediaRepository(private val repository: TourRepository) {
    suspend fun import360Image(uri: Uri): String = repository.import360Image(uri)
    suspend fun validate360Image(uri: Uri): ValidationResult = repository.validate360Image(uri)
    fun checkEquirectangularRatio(width: Int, height: Int): Boolean = repository.checkEquirectangularRatio(width, height)
}

class CaptureRepository

class ExportRepository(private val repository: TourRepository) {
    fun exportTourJson(tourId: String): String = repository.exportTourJson(tourId)
    fun exportTourBundle(tourId: String): Uri = repository.exportTourBundle(tourId)
}

class PublishRepository {
    var endpoint: String = ""
    var apiKey: String = ""
    fun configurePublishEndpoint(baseUrl: String, apiKey: String?) {
        endpoint = baseUrl
        this.apiKey = apiKey.orEmpty()
    }
    fun publishTour(tourId: String): ValidationResult =
        if (endpoint.isBlank()) ValidationResult(false, "No website endpoint configured yet. Export the tour locally instead.", ValidationSeverity.Warning)
        else ValidationResult(true, "Publish payload is ready for $endpoint")
}
