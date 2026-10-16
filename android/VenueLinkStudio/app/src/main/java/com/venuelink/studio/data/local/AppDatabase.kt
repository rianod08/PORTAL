package com.venuelink.studio.data.local

import com.venuelink.studio.data.model.Hotspot
import com.venuelink.studio.data.model.Tour
import com.venuelink.studio.data.model.TourScene
import com.venuelink.studio.data.model.VenueInfo
import kotlinx.coroutines.flow.Flow

interface TourDao {
    fun getAllTours(): Flow<List<Tour>>
    suspend fun saveTour(tour: Tour)
    suspend fun deleteTour(tourId: String)
}

interface HotspotDao {
    fun getHotspotsForScene(sceneId: String): Flow<List<Hotspot>>
    suspend fun saveHotspot(hotspot: Hotspot)
    suspend fun deleteHotspot(hotspotId: String)
}

interface SceneDao {
    fun getScenesForTour(tourId: String): Flow<List<TourScene>>
    suspend fun saveScene(scene: TourScene)
    suspend fun deleteScene(sceneId: String)
}

interface VenueInfoDao {
    fun getVenueInfo(tourId: String): Flow<VenueInfo?>
    suspend fun saveVenueInfo(info: VenueInfo)
}

interface ThemeDao

class AppDatabase private constructor() {
    companion object {
        const val name: String = "venuelink-studio-local-json-store"
    }
}
