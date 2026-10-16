package com.venuelink.studio

import com.venuelink.studio.data.model.Hotspot
import com.venuelink.studio.data.model.HotspotType
import com.venuelink.studio.data.model.Tour
import com.venuelink.studio.data.model.TourExport
import com.venuelink.studio.data.model.TourScene
import com.venuelink.studio.data.model.VenueInfo
import com.venuelink.studio.data.model.defaultThemes
import com.venuelink.studio.data.repository.isValidUrl
import com.venuelink.studio.domain.export.TourExporter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportAndValidationTest {
    @Test
    fun validatesUrls() {
        assertTrue(isValidUrl("https://example.com"))
        assertFalse(isValidUrl("example.com"))
    }

    @Test
    fun exportsAndImportsTourJson() {
        val tour = Tour(name = "Test", venueName = "Venue", venueType = "Studio")
        val scene = TourScene(tourId = tour.id, name = "Room", panoramaUri = "file://room.jpg")
        val export = TourExport(
            tour = tour,
            venueInfo = VenueInfo(tourId = tour.id, venueName = "Venue"),
            scenes = listOf(scene),
            hotspots = listOf(Hotspot(tourId = tour.id, sceneId = scene.id, label = "Book", type = HotspotType.Booking, yaw = 0f, pitch = 0f)),
            theme = defaultThemes().first(),
            assets = emptyList(),
        )
        val json = TourExporter.exportTourJson(export)
        assertTrue(json.contains("\"tour\""))
        assertTrue(TourExporter.validateTourExport(json).isValid)
        assertTrue(TourExporter.importTourJson(json).isSuccess)
    }
}
