package com.venuelink.studio.capture

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import com.venuelink.studio.tour.*

@Entity(tableName = "capture_sessions")
data class CaptureSession(
    @PrimaryKey val id: String,
    val ownerId: String,
    val tourId: String,
    val name: String,
    val expectedFrames: Int = 0,
    val state: String = "capturing",
    val message: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val panoramaPath: String? = null,
)
@Entity(tableName = "capture_frames", foreignKeys = [ForeignKey(entity = CaptureSession::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE)], indices = [Index("sessionId"), Index(value = ["sessionId", "targetId"], unique = true)])
data class CaptureFrame(
    @PrimaryKey val id: String,
    val sessionId: String,
    val targetId: String,
    val path: String,
    val metadata: String,
    val uploadUrl: String? = null,
    val uploadToken: String? = null,
    val uploaded: Boolean = false,
)
@Dao
interface CaptureDao {
    @Query("SELECT * FROM capture_sessions ORDER BY createdAt DESC") fun observeSessions(): Flow<List<CaptureSession>>
    @Query("SELECT * FROM capture_sessions WHERE id=:id") suspend fun session(id: String): CaptureSession?
    @Query("SELECT * FROM capture_sessions WHERE id=:id") fun observeSession(id: String): Flow<CaptureSession?>
    @Insert(onConflict = OnConflictStrategy.ABORT) suspend fun insertSession(session: CaptureSession)
    @Query("UPDATE capture_sessions SET state=:state, message=:message WHERE id=:id") suspend fun status(id: String, state: String, message: String = "")
    @Query("UPDATE capture_sessions SET expectedFrames=:count WHERE id=:id") suspend fun targetCount(id: String, count: Int)
    @Query("UPDATE capture_sessions SET panoramaPath=:path, state='ready', message='Your panorama is ready. Saved on this phone.' WHERE id=:id") suspend fun panorama(id: String, path: String)
    @Query("SELECT * FROM capture_frames WHERE sessionId=:id ORDER BY rowid") suspend fun frames(id: String): List<CaptureFrame>
    @Query("SELECT * FROM capture_frames WHERE sessionId=:id ORDER BY rowid") fun observeFrames(id: String): Flow<List<CaptureFrame>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveFrame(frame: CaptureFrame)
    @Query("DELETE FROM capture_frames WHERE id=:id") suspend fun deleteFrame(id: String)
    @Query("DELETE FROM capture_sessions WHERE id=:id") suspend fun deleteSession(id: String)
}
@Database(entities = [CaptureSession::class, CaptureFrame::class, TourDraft::class, TourAsset::class], version = 3, exportSchema = false)
abstract class CaptureDatabase : RoomDatabase() {
    abstract fun captures(): CaptureDao
    abstract fun tours(): TourDao
    companion object {
        @Volatile private var instance: CaptureDatabase? = null
        val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE capture_sessions ADD COLUMN panoramaPath TEXT")
            }
        }
        val Migration2To3 = object : Migration(2,3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS tour_drafts (id TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, slug TEXT NOT NULL, document TEXT NOT NULL, baseRevision INTEGER NOT NULL, publishedRevision INTEGER, dirty INTEGER NOT NULL, conflict INTEGER NOT NULL, message TEXT NOT NULL, editVersion INTEGER NOT NULL, serverDocument TEXT NOT NULL, recoveryDocument TEXT)")
                db.execSQL("CREATE TABLE IF NOT EXISTS tour_assets (id TEXT NOT NULL PRIMARY KEY, ownerId TEXT NOT NULL, tourId TEXT NOT NULL, localPath TEXT NOT NULL, mimeType TEXT NOT NULL, kind TEXT NOT NULL, remotePath TEXT, uploadUrl TEXT, uploaded INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_tour_assets_ownerId_tourId ON tour_assets(ownerId,tourId)")
            }
        }
        fun get(context: Context): CaptureDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, CaptureDatabase::class.java, "portal-capture.db").addMigrations(Migration1To2,Migration2To3).build().also { instance = it }
        }
    }
}
