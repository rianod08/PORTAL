package com.venuelink.studio.capture

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.work.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/** Connects durable uploads, server stitching, and an account-owned offline result. */
object CapturePipeline {
    private val lock = Mutex()
    private val client = OkHttpClient.Builder().readTimeout(90, TimeUnit.SECONDS).build()
    val processingStates = setOf("queued", "processing")

    fun localPanorama(session: CaptureSession): File? =
        session.panoramaPath?.let(::File)?.takeIf { it.isFile && it.length() > 0 }

    suspend fun sync(context: Context, account: PortalAccount, id: String): String =
        withContext(Dispatchers.IO) {
            lock.withLock {
                val dao = CaptureDatabase.get(context).captures()
                val local = dao.session(id) ?: return@withLock "missing"
                check(account.signedIn && local.ownerId == account.ownerId) { "Sign in to the account that made this capture." }
                if (local.state == "ready" && localPanorama(local) != null) return@withLock "ready"
                val remote = JSONObject(account.call("captures/$id"))
                check(remote.getString("owner_id") == local.ownerId) { "Capture account does not match." }
                val state = remote.getString("status")
                if (state == "uploading") return@withLock state // Keep the uploader's precise progress.
                if (state == "ready") {
                    dao.status(id, "ready", "Panorama ready. Saving it on this phone…")
                    val path = remote.getJSONObject("result").getString("path")
                    val signed = JSONObject(account.call("media?path=${Uri.encode(path)}"))
                    val folder = File(context.filesDir, "capture-panoramas/${local.ownerId}").apply { mkdirs() }
                    val output = File(folder, "$id.jpg")
                    val pending = File(folder, "$id.part")
                    try {
                        client.newCall(Request.Builder().url(signed.getString("url")).build()).execute().use { response ->
                            check(response.isSuccessful) { "Panorama download interrupted. It will retry." }
                            val body = response.body ?: error("Panorama download was empty.")
                            body.byteStream().use { input -> pending.outputStream().use { input.copyTo(it) } }
                        }
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(pending.path, bounds)
                        check(bounds.outWidth > 0 && bounds.outWidth == bounds.outHeight * 2) { "The downloaded panorama is incomplete." }
                        check(account.signedIn && account.ownerId == local.ownerId) { "Sign in again to view this panorama." }
                        check(pending.renameTo(output)) { "Could not save the panorama. Please retry." }
                        dao.panorama(id, output.path)
                        com.venuelink.studio.tour.TourRepository.enqueue(context,local.tourId)
                    } finally { pending.delete() }
                } else {
                    val error = remote.optString("error").takeUnless { it == "null" }.orEmpty()
                    val message = when (state) {
                        "queued" -> "All photos uploaded. Waiting to build your panorama…"
                        "processing" -> "Building your 360° panorama… You can leave this screen."
                        "needs-retake", "failed" -> error.ifBlank { "The panorama could not be built. Review your photos and try again." }
                        "cancelled" -> "Processing cancelled. Your original photos are still saved."
                        else -> error
                    }
                    dao.status(id, state, message)
                }
                state
            }
        }

    fun enqueue(context: Context, id: String) {
        val request = OneTimeWorkRequestBuilder<CaptureProcessingWorker>()
            .setInputData(workDataOf("sessionId" to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.LINEAR, 15, TimeUnit.SECONDS).build()
        WorkManager.getInstance(context).enqueueUniqueWork("panorama-$id", ExistingWorkPolicy.KEEP, request)
    }
}

class CaptureProcessingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getString("sessionId") ?: return Result.failure()
        val dao = CaptureDatabase.get(applicationContext).captures()
        val local = dao.session(id) ?: return Result.failure()
        val account = PortalAccount(applicationContext)
        if (!account.signedIn || local.ownerId != account.ownerId) return Result.failure()
        return try {
            val state = CapturePipeline.sync(applicationContext, account, id)
            if (state in CapturePipeline.processingStates) Result.retry() else Result.success()
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) {
            val latest = dao.session(id) ?: return Result.failure()
            dao.status(id, latest.state, "Waiting for a connection. Your saved photos are safe; processing will resume.")
            Result.retry()
        }
    }
}
