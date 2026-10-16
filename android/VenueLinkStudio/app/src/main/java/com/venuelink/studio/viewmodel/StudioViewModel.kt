package com.venuelink.studio.viewmodel

import android.net.Uri
import java.io.File
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.venuelink.studio.data.model.Hotspot
import com.venuelink.studio.data.model.HotspotStyle
import com.venuelink.studio.data.model.HotspotType
import com.venuelink.studio.data.model.StudioState
import com.venuelink.studio.data.model.Tour
import com.venuelink.studio.data.model.TourScene
import com.venuelink.studio.data.model.ValidationResult
import com.venuelink.studio.data.model.ValidationSeverity
import com.venuelink.studio.data.model.VenueInfo
import com.venuelink.studio.data.repository.ExportRepository
import com.venuelink.studio.data.repository.MediaRepository
import com.venuelink.studio.data.repository.PublishRepository
import com.venuelink.studio.data.repository.TourRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StudioViewModel(
    private val tourRepository: TourRepository,
    private val mediaRepository: MediaRepository,
    private val exportRepository: ExportRepository,
    private val publishRepository: PublishRepository,
) : ViewModel() {
    val state: StateFlow<StudioState> = tourRepository.state.stateIn(viewModelScope, SharingStarted.Eagerly, tourRepository.state.value)
    var latestMessage: String = ""
        private set

    fun activeTour(): Tour? = tourRepository.activeTour()
    fun activeScene(tourId: String): TourScene? = tourRepository.activeScene(tourId)
    fun venueInfo(tourId: String): VenueInfo = tourRepository.venueInfo(tourId)
    fun themeFor(tour: Tour) = tourRepository.themeFor(tour)

    fun createTour(name: String, venueName: String, venueType: String): ValidationResult {
        if (name.isBlank()) return ValidationResult(false, "Tour name is required.", ValidationSeverity.Error)
        if (venueName.isBlank()) return ValidationResult(false, "Venue name is required.", ValidationSeverity.Error)
        val tour = tourRepository.createTour(name, venueName, venueType)
        latestMessage = "Tour created"
        return ValidationResult(true, tour.id)
    }

    fun selectTour(tourId: String) {
        tourRepository.updateTour(tourId) { copy(updatedAt = System.currentTimeMillis()) }
    }

    fun deleteTour(tourId: String) {
        tourRepository.deleteTour(tourId)
        latestMessage = "Tour deleted"
    }

    fun duplicateTour(tourId: String) {
        tourRepository.duplicateTour(tourId)
        latestMessage = "Tour duplicated"
    }

    fun updateVenueInfo(tourId: String, patch: VenueInfo.() -> VenueInfo): ValidationResult {
        val updated = tourRepository.updateVenueInfo(tourId, patch)
        val validation = tourRepository.validateVenueInfo(updated)
        latestMessage = if (validation.isValid) "Saved" else validation.message.orEmpty()
        return validation
    }

    fun createDemoScene(tourId: String) {
        val tour = tourRepository.getTour(tourId) ?: return
        val uri = tour.coverImageUri ?: return
        tourRepository.createScene(tourId, "Demo Room", uri)
        latestMessage = "Demo scene added"
    }

    fun createCaptureSessionId(tourId: String, sceneName: String): String = tourRepository.createCaptureSessionId(tourId, sceneName)

    fun saveCapturedFrame(
        sessionId: String,
        tourId: String,
        dotId: String,
        file: File,
        yaw: Float,
        pitch: Float,
        roll: Float,
        cameraPoseMatrix: List<Float> = emptyList(),
        targetWorldDirection: List<Float> = emptyList(),
    ) {
        tourRepository.saveCapturedFrame(sessionId, tourId, dotId, file, yaw, pitch, roll, cameraPoseMatrix, targetWorldDirection)
        latestMessage = "Captured"
    }

    fun capturedFrames(sessionId: String) = tourRepository.getCapturedFrames(sessionId)

    fun discardCapturedFrameForDot(sessionId: String, dotId: String) {
        tourRepository.discardCapturedFrameForDot(sessionId, dotId)
    }

    fun processCaptureSession(tourId: String, sessionId: String, sceneName: String, onDone: (TourScene?) -> Unit) {
        viewModelScope.launch {
            runCatching {
                tourRepository.processCaptureSession(tourId, sessionId, sceneName)
            }.onSuccess {
                latestMessage = "360 image built"
                onDone(it)
            }.onFailure {
                latestMessage = "Panorama processing failed"
                onDone(null)
            }
        }
    }

    fun importImageAsScene(tourId: String, uri: Uri, onDone: (ValidationResult) -> Unit) {
        viewModelScope.launch {
            val validation = mediaRepository.validate360Image(uri)
            runCatching {
                val stored = mediaRepository.import360Image(uri)
                tourRepository.createScene(tourId, "Imported Room", stored)
            }.onSuccess {
                latestMessage = "360 image imported"
                onDone(validation)
            }.onFailure {
                onDone(ValidationResult(false, "Image import failed. Try another image.", ValidationSeverity.Error))
            }
        }
    }

    fun addScene(tourId: String, name: String) {
        val uri = tourRepository.activeScene(tourId)?.panoramaUri ?: tourRepository.activeTour()?.coverImageUri ?: return
        tourRepository.createScene(tourId, name, uri)
        latestMessage = "Scene added"
    }

    fun updateScene(sceneId: String, name: String) {
        tourRepository.updateScene(sceneId) { copy(name = name) }
        latestMessage = "Scene saved"
    }

    fun deleteScene(sceneId: String) {
        tourRepository.deleteScene(sceneId)
        latestMessage = "Scene deleted"
    }

    fun setDefaultScene(tourId: String, sceneId: String) = tourRepository.setDefaultScene(tourId, sceneId)

    fun createHotspot(sceneId: String, yaw: Float, pitch: Float, type: HotspotType): Hotspot {
        latestMessage = "Point added"
        return tourRepository.createHotspot(sceneId, yaw, pitch, type)
    }

    fun updateHotspot(hotspotId: String, patch: Hotspot.() -> Hotspot) {
        tourRepository.updateHotspot(hotspotId, patch)
        latestMessage = "Point saved"
    }

    fun moveHotspot(hotspotId: String, yaw: Float, pitch: Float) {
        tourRepository.moveHotspot(hotspotId, yaw, pitch)
        latestMessage = "Point moved"
    }

    fun toggleHotspot(hotspotId: String) = tourRepository.toggleHotspot(hotspotId)
    fun duplicateHotspot(hotspotId: String) = tourRepository.duplicateHotspot(hotspotId)
    fun deleteHotspot(hotspotId: String) {
        tourRepository.deleteHotspot(hotspotId)
        latestMessage = "Point deleted"
    }

    fun applyTheme(tourId: String, themeId: String) {
        tourRepository.applyTheme(tourId, themeId)
        latestMessage = "Theme applied"
    }

    fun updateAccentColor(tourId: String, colour: String) {
        tourRepository.updateAccentColor(tourId, colour)
        latestMessage = "Colour updated"
    }

    fun updateHotspotStyle(tourId: String, style: HotspotStyle) = tourRepository.updateHotspotStyle(tourId, style)

    fun exportJson(tourId: String): String = exportRepository.exportTourJson(tourId)
    fun exportBundle(tourId: String): Uri = exportRepository.exportTourBundle(tourId)
    fun publish(tourId: String): ValidationResult = publishRepository.publishTour(tourId)
    fun configurePublishEndpoint(baseUrl: String, apiKey: String) = publishRepository.configurePublishEndpoint(baseUrl, apiKey)
    fun resetDemoData() = tourRepository.resetDemoData()
    fun clearAll() = tourRepository.clearAll()
}

class StudioViewModelFactory(
    private val tourRepository: TourRepository,
    private val mediaRepository: MediaRepository,
    private val exportRepository: ExportRepository,
    private val publishRepository: PublishRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        StudioViewModel(tourRepository, mediaRepository, exportRepository, publishRepository) as T
}
