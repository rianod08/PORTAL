package com.venuelink.studio.app

import android.app.Application
import com.venuelink.studio.data.repository.ExportRepository
import com.venuelink.studio.data.repository.MediaRepository
import com.venuelink.studio.data.repository.PublishRepository
import com.venuelink.studio.data.repository.TourRepository

class VenueLinkStudioApp : Application() {
    lateinit var tourRepository: TourRepository
        private set
    lateinit var mediaRepository: MediaRepository
        private set
    lateinit var exportRepository: ExportRepository
        private set
    lateinit var publishRepository: PublishRepository
        private set

    override fun onCreate() {
        super.onCreate()
        tourRepository = TourRepository(this)
        mediaRepository = MediaRepository(tourRepository)
        exportRepository = ExportRepository(tourRepository)
        publishRepository = PublishRepository()
    }
}
