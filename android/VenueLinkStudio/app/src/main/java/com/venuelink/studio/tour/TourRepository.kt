package com.venuelink.studio.tour

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Base64
import androidx.work.*
import com.venuelink.studio.capture.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

class TourRepository(private val context: Context, private val account: PortalAccount) {
    private val owner=account.ownerId
    val dao=CaptureDatabase.get(context).tours()
    private fun guard(){check(owner.isNotBlank()&&account.signedIn&&account.ownerId==owner){"Sign in to the account that owns this tour."}}
    private suspend fun row(id: String)=dao.draft(id,owner)?:error("Download this tour while connected first.")

    suspend fun edit(id: String, transform: (TourDocument)->TourDocument) {
        val changed=mutations.withLock {guard();val old=row(id);val before=old.decode();val next=transform(before)
            if(next==before)false else{dao.save(old.copy(document=tourJson.encodeToString(next),dirty=true,editVersion=old.editVersion+1,message=if(old.conflict)"Resolve the other-device conflict to sync." else "Saved on phone · waiting to sync"));true}}
        if(changed)enqueue(context,id)
    }
    suspend fun create(title: String): String = withContext(Dispatchers.IO){guard();val remote=JSONObject(account.call("tours",JSONObject().put("title",title)));accept(remote);remote.getString("id")}
    private suspend fun accept(remote: JSONObject) = mutations.withLock {
        guard();check(remote.getString("owner_id")==owner)
        val id=remote.getString("id");val old=dao.draft(id,owner);val document=remote.getJSONObject("document").toString();val revision=remote.getInt("revision")
        val published=if(remote.isNull("published_revision"))null else remote.getInt("published_revision")
        if(old==null||!old.dirty)dao.save(TourDraft(id,owner,remote.getString("slug"),document,revision,published,serverDocument=document,recoveryDocument=old?.recoveryDocument,editVersion=old?.editVersion?:0))
        else dao.save(old.copy(publishedRevision=published,serverDocument=document,conflict=old.conflict||old.baseRevision!=revision,message=if(old.baseRevision!=revision)"This tour changed on another device. Your edits are safe." else old.message))
    }
    suspend fun refresh() = withContext(Dispatchers.IO) {
        guard();val remote=JSONArray(account.call("tours"));for(i in 0 until remote.length()){val r=remote.getJSONObject(i);accept(r);if(dao.draft(r.getString("id"),owner)?.dirty==true)enqueue(context,r.getString("id"))}
        val captures=JSONArray(account.call("captures"));for(i in 0 until captures.length()){val c=captures.getJSONObject(i);if(c.optString("status")=="ready"&&dao.draft(c.getString("tour_id"),owner)!=null)importCapture(c)}
    }
    private suspend fun importCapture(c: JSONObject) {
        val id=c.getString("id");val tour=c.getString("tour_id");val path=c.getJSONObject("result").getString("path")
        val local=CaptureDatabase.get(context).captures().session(id)
        CapturePipeline.localPanorama(local?:CaptureSession(id,owner,tour,c.getString("name")))?.let{file->dao.asset(TourAsset(assetKey(path),owner,tour,file.path,"image/jpeg","panorama",path,uploaded=true))}
        edit(tour){d->if(id in d.handledCaptureIds)d else d.copy(scenes=if(d.scenes.any{it.id==id})d.scenes else d.scenes+TourRoom(id,c.getString("name"),path),defaultSceneId=d.defaultSceneId.ifBlank{id},handledCaptureIds=(d.handledCaptureIds+id).distinct())}
    }
    suspend fun sync(id: String) = withContext(Dispatchers.IO) {
        network.withLock {
            guard();accept(JSONObject(account.call("tours/$id")))
            val captures=JSONArray(account.call("captures"));for(i in 0 until captures.length()){val c=captures.getJSONObject(i);if(c.optString("status")=="ready"&&c.getString("tour_id")==id)importCapture(c)}
            if(row(id).conflict)return@withLock
            for(asset in dao.assets(owner,id).filter{!it.uploaded&&"local:${it.id}" in row(id).decode().assets()}) upload(asset)
            mutations.withLock {
                val old=row(id);val before=old.decode();var d=before
                for(a in dao.assets(owner,id).filter{it.uploaded&&it.remotePath!=null})d=d.replaceAsset("local:${a.id}",a.remotePath!!)
                val encoded=tourJson.encodeToString(d)
                if(d!=before)dao.save(old.copy(document=encoded,dirty=true,editVersion=old.editVersion+1))
            }
            val snapshot=row(id)
            if(snapshot.dirty) {
                try {
                    val result=JSONObject(account.call("tours/$id",JSONObject().put("revision",snapshot.baseRevision).put("document",JSONObject(snapshot.document)),method="PUT"))
                    mutations.withLock {guard();val latest=row(id);val dirty=latest.editVersion!=snapshot.editVersion;dao.save(latest.copy(baseRevision=result.getInt("revision"),serverDocument=snapshot.document,dirty=dirty,conflict=false,message=if(dirty)"Saved on phone · waiting to sync" else "All changes saved"))}
                } catch(e:PortalApiError){if(e.status!=409)throw e;mutations.withLock{val latest=row(id);dao.save(latest.copy(conflict=true,message="This tour changed on another device. Your edits are safe."))};return@withLock}
            }
            cache(id)
        }
    }
    suspend fun resolve(id: String, replaceServer: Boolean) = withContext(Dispatchers.IO){
        guard();val remote=JSONObject(account.call("tours/$id"));mutations.withLock{val old=row(id);dao.save(old.copy(document=if(replaceServer)old.document else remote.getJSONObject("document").toString(),serverDocument=remote.getJSONObject("document").toString(),baseRevision=remote.getInt("revision"),dirty=replaceServer,conflict=false,recoveryDocument=old.document,editVersion=old.editVersion+1,message="Previous edits kept in recovery"))};enqueue(context,id)
    }
    suspend fun restoreRecovery(id: String){edit(id){rowDocument->rowDocument};mutations.withLock{val old=row(id);old.recoveryDocument?.let{dao.save(old.copy(document=it,dirty=true,editVersion=old.editVersion+1,message="Recovery draft restored"))}};enqueue(context,id)}
    suspend fun publish(id: String, unpublish: Boolean=false): String = withContext(Dispatchers.IO){
        sync(id);val latest=row(id);check(!latest.dirty&&!latest.conflict){"Finish syncing or resolve the conflict before publishing."}
        if(!unpublish)check(latest.decode().publicationErrors().isEmpty()){latest.decode().publicationErrors().joinToString("\n")}
        val result=JSONObject(account.call("tours/$id/publish",JSONObject().put("revision",latest.baseRevision).put("action",if(unpublish)"unpublish"else"publish")))
        mutations.withLock{val current=row(id);dao.save(current.copy(publishedRevision=if(result.isNull("published_revision"))null else result.getInt("published_revision"),message=if(unpublish)"Tour unpublished" else "Tour published"))}
        "${account.baseUrl}/v/${latest.slug}/tour"
    }
    suspend fun file(id: String,path: String?): File? {
        if(path.isNullOrBlank())return null
        return dao.assets(owner,id).firstOrNull{it.remotePath==path||"local:${it.id}"==path}?.localPath?.let(::File)?.takeIf{it.isFile}
    }
    suspend fun addMedia(tour: String,uri: Uri,kind: String): String = withContext(Dispatchers.IO) {
        guard();val id=tourId();val folder=File(context.filesDir,"tour-media/$owner/$tour").apply{mkdirs()};val file=File(folder,id)
        try {
            context.contentResolver.openInputStream(uri)?.use{input->file.outputStream().use{output->val buffer=ByteArray(65536);var total=0L;while(true){val n=input.read(buffer);if(n<0)break;total+=n;require(total<=50L*1024*1024){"Choose a file up to 50 MB."};output.write(buffer,0,n)}}}?:error("Could not read this file.")
            val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true};BitmapFactory.decodeFile(file.path,bounds)
            val mime=if(bounds.outWidth>0)bounds.outMimeType else "video/mp4"
            require(mime in if(kind=="video")listOf("video/mp4") else listOf("image/jpeg","image/png","image/webp")){"Choose JPEG, PNG, WebP, or H.264 MP4."}
            if(kind=="panorama")require(kotlin.math.abs(bounds.outWidth.toDouble()/bounds.outHeight-2)<.02){"Choose a full 360° panorama with a 2:1 aspect ratio."}
            if(kind=="video"){
                val header=ByteArray(12);val count=file.inputStream().use{it.read(header)};require(count>=12&&String(header,4,4)=="ftyp"){"Choose an MP4 video."}
                val extractor=MediaExtractor();try{extractor.setDataSource(file.path);var video=false;for(i in 0 until extractor.trackCount){val track=extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME).orEmpty();if(track.startsWith("video/")){require(track=="video/avc"){"Choose an H.264 MP4 video. HEVC needs conversion first."};video=true};if(track.startsWith("audio/"))require(track=="audio/mp4a-latm"){"Choose an MP4 with AAC audio."}};require(video){"No video track found."}}finally{extractor.release()}
            }
            dao.asset(TourAsset(id,owner,tour,file.path,mime,kind));"local:$id"
        }catch(e:Exception){file.delete();throw e}
    }
    private suspend fun upload(initial: TourAsset) {
        guard();val file=File(initial.localPath);check(file.isFile){"An upload file is missing. Choose it again."}
        val signed=JSONObject(account.call("media",JSONObject().put("tourId",initial.tourId).put("mediaType",initial.kind).put("mimeType",initial.mimeType).put("size",file.length()).put("assetId",initial.id)))
        val path=signed.getString("path");var asset=initial.copy(remotePath=path);dao.asset(asset)
        // A completed upload may outlive a process crash before the local checkpoint.
        if(runCatching{account.call("media?path=${Uri.encode(path)}")}.isSuccess){dao.asset(asset.copy(uploaded=true,uploadUrl=null));return}
        val headers=mapOf("Tus-Resumable" to "1.0.0","x-signature" to signed.getString("token"),"Authorization" to "Bearer ${signed.getString("uploadAuthorization")}")
        fun builder(url: String)=Request.Builder().url(url).apply{headers.forEach{(k,v)->header(k,v)}}
        var upload=asset.uploadUrl;var offset=0L
        if(upload!=null)client.newCall(builder(upload).head().build()).execute().use{if(it.isSuccessful)offset=it.header("Upload-Offset")?.toLongOrNull()?:0 else upload=null}
        if(upload==null){val metadata=mapOf("bucketName" to "portal-media","objectName" to path,"contentType" to asset.mimeType).entries.joinToString(","){it.key+" "+Base64.encodeToString(it.value.toByteArray(),Base64.NO_WRAP)}
            client.newCall(builder(signed.getString("storageUrl")+"/storage/v1/upload/resumable").header("Upload-Length",file.length().toString()).header("Upload-Metadata",metadata).post(ByteArray(0).toRequestBody()).build()).execute().use{check(it.isSuccessful){"Could not start media upload (${it.code})."};upload=it.header("Location")?:error("Missing upload location")}
            asset=asset.copy(uploadUrl=upload);dao.asset(asset)
        }
        java.io.RandomAccessFile(file,"r").use{input->while(offset<file.length()){currentCoroutineContext().ensureActive();guard();input.seek(offset);val bytes=ByteArray(minOf(6L*1024*1024,file.length()-offset).toInt());input.readFully(bytes)
            client.newCall(builder(upload!!).header("Upload-Offset",offset.toString()).patch(bytes.toRequestBody("application/offset+octet-stream".toMediaType())).build()).execute().use{check(it.isSuccessful){"Media upload interrupted (${it.code})."};val next=it.header("Upload-Offset")?.toLongOrNull()?:error("Missing upload offset");check(next>offset&&next<=file.length());offset=next}
            mutations.withLock{val old=row(asset.tourId);dao.save(old.copy(message="Uploading media · ${offset*100/file.length()}%"))}
        }}
        guard();dao.asset(asset.copy(uploaded=true,uploadUrl=null))
    }
    private suspend fun cache(id: String) {
        for(path in row(id).decode().assets().filter{!it.startsWith("local:")}){
            if(file(id,path)!=null)continue
            guard();val signed=JSONObject(account.call("media?path=${Uri.encode(path)}"));val folder=File(context.filesDir,"tour-media/$owner/$id").apply{mkdirs()};val key=assetKey(path);val pending=File(folder,"$key.part");val output=File(folder,key)
            try{client.newCall(Request.Builder().url(signed.getString("url")).build()).execute().use{r->check(r.isSuccessful){"Could not download a tour file."};r.body!!.byteStream().use{input->pending.outputStream().use{input.copyTo(it)}}};guard();check(pending.renameTo(output));dao.asset(TourAsset(key,owner,id,output.path,if(path.endsWith(".mp4"))"video/mp4"else"image/jpeg","cached",path,uploaded=true))}finally{pending.delete()}
        }
    }
    companion object {
        private val mutations=Mutex();private val network=Mutex()
        private val client=OkHttpClient.Builder().readTimeout(90,TimeUnit.SECONDS).writeTimeout(90,TimeUnit.SECONDS).build()
        private fun assetKey(path: String)=MessageDigest.getInstance("SHA-256").digest(path.toByteArray()).joinToString(""){"%02x".format(it)}
        fun enqueue(context: Context,id: String){val request=OneTimeWorkRequestBuilder<TourSyncWorker>().setInputData(workDataOf("tourId" to id,"ownerId" to PortalAccount(context).ownerId)).setInitialDelay(1,TimeUnit.SECONDS).setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).setBackoffCriteria(BackoffPolicy.LINEAR,15,TimeUnit.SECONDS).build();WorkManager.getInstance(context).enqueueUniqueWork("tour-sync-$id",ExistingWorkPolicy.KEEP,request)}
    }
}

class TourSyncWorker(context: Context,params:WorkerParameters): CoroutineWorker(context,params){
    override suspend fun doWork(): Result {
        val id=inputData.getString("tourId")?:return Result.failure();val account=PortalAccount(applicationContext);if(!account.signedIn||inputData.getString("ownerId")!=account.ownerId)return Result.failure()
        val repo=TourRepository(applicationContext,account)
        return try{repo.sync(id);val draft=repo.dao.draft(id,account.ownerId);if(draft?.dirty==true&&!draft.conflict)Result.retry()else Result.success()}catch(e:CancellationException){throw e}catch(_:Exception){Result.retry()}
    }
}
