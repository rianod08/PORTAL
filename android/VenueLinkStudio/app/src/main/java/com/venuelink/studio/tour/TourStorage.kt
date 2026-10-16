package com.venuelink.studio.tour

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName="tour_drafts")
data class TourDraft(@PrimaryKey val id: String, val ownerId: String, val slug: String, val document: String, val baseRevision: Int, val publishedRevision: Int? = null, val dirty: Boolean = false, val conflict: Boolean = false, val message: String = "Saved", val editVersion: Long = 0, val serverDocument: String = document, val recoveryDocument: String? = null) {
    fun decode() = tourJson.decodeFromString<TourDocument>(document)
}
@Entity(tableName="tour_assets",indices=[Index(value=["ownerId","tourId"])])
data class TourAsset(@PrimaryKey val id: String, val ownerId: String, val tourId: String, val localPath: String, val mimeType: String, val kind: String, val remotePath: String? = null, val uploadUrl: String? = null, val uploaded: Boolean = false)
@Dao interface TourDao {
    @Query("SELECT * FROM tour_drafts WHERE ownerId=:owner ORDER BY id") fun observe(owner: String): Flow<List<TourDraft>>
    @Query("SELECT * FROM tour_drafts WHERE id=:id AND ownerId=:owner") suspend fun draft(id: String, owner: String): TourDraft?
    @Query("SELECT * FROM tour_drafts WHERE id=:id AND ownerId=:owner") fun observeDraft(id: String, owner: String): Flow<TourDraft?>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun save(draft: TourDraft)
    @Query("SELECT * FROM tour_assets WHERE ownerId=:owner AND tourId=:tour") suspend fun assets(owner: String,tour: String): List<TourAsset>
    @Insert(onConflict=OnConflictStrategy.REPLACE) suspend fun asset(asset: TourAsset)
}
