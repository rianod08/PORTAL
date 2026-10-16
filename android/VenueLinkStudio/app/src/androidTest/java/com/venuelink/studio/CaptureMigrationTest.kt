package com.venuelink.studio

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.venuelink.studio.capture.CaptureDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CaptureMigrationTest {
    @Test fun installingPanoramaSupportKeepsOriginalPhotosAndUploadResumeState() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "capture-migration-${UUID.randomUUID()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE capture_sessions (id TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, tourId TEXT NOT NULL, name TEXT NOT NULL, expectedFrames INTEGER NOT NULL, state TEXT NOT NULL, message TEXT NOT NULL, createdAt INTEGER NOT NULL)")
            old.execSQL("CREATE TABLE capture_frames (id TEXT NOT NULL PRIMARY KEY, sessionId TEXT NOT NULL, targetId TEXT NOT NULL, path TEXT NOT NULL, metadata TEXT NOT NULL, uploadUrl TEXT, uploadToken TEXT, uploaded INTEGER NOT NULL, FOREIGN KEY(sessionId) REFERENCES capture_sessions(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
            old.execSQL("CREATE INDEX index_capture_frames_sessionId ON capture_frames(sessionId)")
            old.execSQL("CREATE UNIQUE INDEX index_capture_frames_sessionId_targetId ON capture_frames(sessionId,targetId)")
            old.execSQL("INSERT INTO capture_sessions VALUES ('room','owner','tour','Original room',15,'uploading','Uploaded 1 of 15 photos',1234)")
            old.execSQL("INSERT INTO capture_frames VALUES ('photo','room','ultrawide-0','/saved/original.jpg','{}','https://upload.example/resume','resume-token',0)")
            old.version = 1
        }
        val upgraded = Room.databaseBuilder(context, CaptureDatabase::class.java, name)
            .addMigrations(CaptureDatabase.Migration1To2,CaptureDatabase.Migration2To3).build()
        try {
            val session = upgraded.captures().session("room")!!
            assertEquals("Original room", session.name)
            assertEquals(15, session.expectedFrames)
            assertEquals("uploading", session.state)
            assertNull(session.panoramaPath)
            val photo = upgraded.captures().frames("room").single()
            assertEquals("/saved/original.jpg", photo.path)
            assertEquals("https://upload.example/resume", photo.uploadUrl)
            assertEquals("resume-token", photo.uploadToken)
            assertFalse(photo.uploaded)
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }
}
