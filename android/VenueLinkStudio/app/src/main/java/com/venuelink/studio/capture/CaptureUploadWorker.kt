package com.venuelink.studio.capture

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import android.util.Base64

class CaptureUploadWorker(context: Context, params: WorkerParameters): CoroutineWorker(context,params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val id = inputData.getString("sessionId") ?: return@withContext Result.failure()
        val dao = CaptureDatabase.get(applicationContext).captures()
        val capture = dao.session(id) ?: return@withContext Result.failure()
        val account = PortalAccount(applicationContext)
        if(!account.signedIn || capture.ownerId != account.ownerId) { dao.status(id,"paused","Sign in to the account that made this capture."); return@withContext Result.failure() }
        try {
            val frames = dao.frames(id)
            require(frames.size >= 8 && frames.size == capture.expectedFrames) { "Complete every target before uploading." }
            dao.status(id,"uploading","Preparing upload")
            val manifest = JSONObject().put("id",id).put("tourId",capture.tourId).put("name",capture.name).put("frames",JSONArray(frames.map{JSONObject(it.metadata)}))
            val remote = JSONObject(account.call("captures",manifest))
            if(remote.getString("status") != "uploading") { CapturePipeline.sync(applicationContext,account,id);CapturePipeline.enqueue(applicationContext,id);return@withContext Result.success() }
            val client=OkHttpClient.Builder().connectTimeout(30,TimeUnit.SECONDS).readTimeout(90,TimeUnit.SECONDS).writeTimeout(90,TimeUnit.SECONDS).build()
            for((index,original) in frames.withIndex()) {
                if(isStopped) return@withContext Result.retry()
                if(original.uploaded) continue
                var frame=original
                val signed=JSONObject(account.call("captures/$id",JSONObject().put("action","upload").put("frameId",frame.id)))
                if(signed.optBoolean("uploaded")) { dao.saveFrame(frame.copy(uploaded=true)); continue }
                val token=signed.getString("token")
                val authorization="Bearer "+signed.getString("uploadAuthorization")
                var upload=frame.uploadUrl
                var offset=0L
                if(upload!=null) {
                    client.newCall(Request.Builder().url(upload).head().header("Tus-Resumable","1.0.0").header("x-signature",token).header("Authorization",authorization).build()).execute().use { response ->
                        if(response.isSuccessful) offset=response.header("Upload-Offset")?.toLongOrNull()?:0 else upload=null
                    }
                }
                val file=File(frame.path);require(file.exists()){ "Original capture photo is missing." }
                if(upload==null) {
                    val metadata=mapOf("bucketName" to "portal-media","objectName" to signed.getString("path"),"contentType" to "image/jpeg").entries.joinToString(","){it.key+" "+Base64.encodeToString(it.value.toByteArray(),Base64.NO_WRAP)}
                    client.newCall(Request.Builder().url(signed.getString("storageUrl")+"/storage/v1/upload/resumable").post(ByteArray(0).toRequestBody()).header("Tus-Resumable","1.0.0").header("x-signature",token).header("Authorization",authorization).header("Upload-Length",file.length().toString()).header("Upload-Metadata",metadata).build()).execute().use { response ->
                        check(response.isSuccessful){"Could not start upload."};upload=response.header("Location")?:error("Missing upload location")
                    }
                    frame=frame.copy(uploadUrl=upload,uploadToken=token);dao.saveFrame(frame)
                }
                java.io.RandomAccessFile(file,"r").use { input ->
                    while(offset<file.length()) {
                        if(isStopped) return@withContext Result.retry()
                        input.seek(offset);val bytes=ByteArray(minOf(6L*1024*1024,file.length()-offset).toInt());input.readFully(bytes)
                        client.newCall(Request.Builder().url(upload!!).patch(bytes.toRequestBody("application/offset+octet-stream".toMediaType())).header("Tus-Resumable","1.0.0").header("x-signature",token).header("Authorization",authorization).header("Upload-Offset",offset.toString()).build()).execute().use { response ->
                            check(response.isSuccessful){"Upload interrupted (${response.code})."};val next=response.header("Upload-Offset")?.toLongOrNull()?:error("Missing upload offset");check(next>offset&&next<=file.length());offset=next
                        }
                    }
                }
                dao.saveFrame(frame.copy(uploaded=true,uploadToken=null,uploadUrl=null));val uploaded=dao.frames(id).count{it.uploaded};dao.status(id,"uploading","Uploaded $uploaded of ${frames.size} photos")
            }
            val result=JSONObject(account.call("captures/$id",JSONObject().put("action","complete")))
            dao.status(id,result.getString("status"),"Uploaded. Cloud processing will continue when the app is closed.")
            CapturePipeline.enqueue(applicationContext,id)
            Result.success()
        } catch(e:CancellationException) { throw e
        } catch(e:Exception) {
            dao.status(id,if(runAttemptCount>=5) "failed" else "waiting",e.message?:"Upload interrupted.")
            if(runAttemptCount>=5) Result.failure() else Result.retry()
        }
    }
    companion object {
        fun enqueue(context:Context,id:String) {
            val request=OneTimeWorkRequestBuilder<CaptureUploadWorker>().setInputData(workDataOf("sessionId" to id)).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).enqueueUniqueWork("capture-$id",ExistingWorkPolicy.KEEP,request)
        }
    }
}
