package com.venuelink.studio

import android.graphics.Bitmap
import android.graphics.Color
import android.media.*
import android.net.Uri
import android.os.Bundle
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import com.venuelink.studio.capture.*
import com.venuelink.studio.tour.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in live test; uses the signed-in phone account and leaves an unpublished test tour. */
@RunWith(AndroidJUnit4::class)
class TourEditorIntegrationTest {
    @Test fun resumesMediaAfterAnInterruptedSixMegabyteChunk(): Unit = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tourIntegration")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val account=PortalAccount(context);assertTrue(account.signedIn)
        val repo=TourRepository(context,account);val id=repo.create("Portal upload resume verification")
        val image=File(context.cacheDir,"resume-test.png")
        val pixels=IntArray(2400*1200);val random=java.util.Random(44)
        for(i in pixels.indices)pixels[i]=random.nextInt() or (0xff shl 24)
        Bitmap.createBitmap(pixels,2400,1200,Bitmap.Config.ARGB_8888).apply{image.outputStream().use{compress(Bitmap.CompressFormat.PNG,100,it)};recycle()}
        val ref=repo.addMedia(id,Uri.fromFile(image),"panorama")
        val asset=repo.dao.assets(account.ownerId,id).single();val file=File(asset.localPath)
        assertTrue(file.length()>6*1024*1024)
        val signed=JSONObject(account.call("media",JSONObject().put("tourId",id).put("assetId",asset.id).put("mediaType","panorama").put("mimeType","image/png").put("size",file.length())))
        val client=OkHttpClient();val headers=mapOf("Tus-Resumable" to "1.0.0","x-signature" to signed.getString("token"),"Authorization" to "Bearer ${signed.getString("uploadAuthorization")}")
        fun request(url:String)=Request.Builder().url(url).apply{headers.forEach{(key,value)->header(key,value)}}
        val metadata=mapOf("bucketName" to "portal-media","objectName" to signed.getString("path"),"contentType" to "image/png").entries.joinToString(","){it.key+" "+Base64.encodeToString(it.value.toByteArray(),Base64.NO_WRAP)}
        val upload=client.newCall(request(signed.getString("storageUrl")+"/storage/v1/upload/resumable").header("Upload-Length",file.length().toString()).header("Upload-Metadata",metadata).post(ByteArray(0).toRequestBody()).build()).execute().use{assertTrue(it.isSuccessful);it.header("Location")!!}
        val first=file.inputStream().use{input->ByteArray(6*1024*1024).also{var n=0;while(n<it.size)n+=input.read(it,n,it.size-n)}}
        client.newCall(request(upload).header("Upload-Offset","0").patch(first.toRequestBody("application/offset+octet-stream".toMediaType())).build()).execute().use{assertTrue(it.isSuccessful);assertEquals((6*1024*1024).toString(),it.header("Upload-Offset"))}
        // The app is interrupted here: only durable metadata survives in the next repository instance.
        repo.dao.asset(asset.copy(remotePath=signed.getString("path"),uploadUrl=upload))
        repo.edit(id){it.copy(defaultSceneId="resume",scenes=listOf(TourRoom("resume","Resumed panorama",ref)))}
        WorkManager.getInstance(context).cancelUniqueWork("tour-sync-$id").result.get()
        val restarted=TourRepository(context,PortalAccount(context));restarted.sync(id)
        val completed=restarted.dao.assets(account.ownerId,id).single()
        assertTrue(completed.uploaded);assertNull(completed.uploadUrl)
        assertFalse(restarted.dao.draft(id,account.ownerId)!!.dirty)
        val download=JSONObject(account.call("media?path=${Uri.encode(completed.remotePath)}"))
        val bytes=client.newCall(Request.Builder().url(download.getString("url")).build()).execute().use{assertTrue(it.isSuccessful);it.body!!.bytes()}
        assertArrayEquals(file.readBytes(),bytes)
        WorkManager.getInstance(context).cancelUniqueWork("tour-sync-$id").result.get()
        image.delete()
    }

    @Test fun buildOfflineSyncRichMediaConflictPublishAndUnpublish() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tourIntegration")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val account=PortalAccount(context);assertTrue("Sign in before running the live test",account.signedIn)
        val originalBase=account.baseUrl;val repo=TourRepository(context,account)
        val source=CaptureDatabase.get(context).captures().session("eba9d80f-7651-4557-ac63-0283bb3b7029")!!
        val panorama=CapturePipeline.localPanorama(source)!!
        val id=repo.create("Portal editor verification")
        val work=WorkManager.getInstance(context)
        fun cancelSync(){work.cancelUniqueWork("tour-sync-$id").result.get()}
        fun publicTour(slug:String): Pair<Int,JSONObject?> = OkHttpClient().newCall(Request.Builder().url("$originalBase/api/v1/public/$slug").build()).execute().use{it.code to it.body?.string()?.let{body->runCatching{JSONObject(body)}.getOrNull()}}
        try {
            val image=File(context.cacheDir,"editor-test-image.png")
            Bitmap.createBitmap(96,96,Bitmap.Config.ARGB_8888).apply{eraseColor(Color.rgb(90,160,70));image.outputStream().use{compress(Bitmap.CompressFormat.PNG,100,it)};recycle()}
            val clip=File(context.cacheDir,"editor-test-video.mp4");video(clip)
            val pano=repo.addMedia(id,Uri.fromFile(panorama),"panorama")
            val picture=repo.addMedia(id,Uri.fromFile(image),"image")
            val logo=repo.addMedia(id,Uri.fromFile(image),"logo")
            val movie=repo.addMedia(id,Uri.fromFile(clip),"video")
            val a=TourRoom("entrance","Latest v (test)",pano,points=listOf(TourPoint("to-second","Next room","scene",20f,0f,targetSceneId="second"),TourPoint("photo","Photo","image",-20f,0f,assetPath=picture),TourPoint("movie","Video","video",0f,20f,assetPath=movie),TourPoint("hidden","Hidden point","info",enabled=false)))
            val b=TourRoom("second","Second room (test)",pano,points=listOf(TourPoint("back","Back","scene",targetSceneId="entrance")))
            repo.edit(id){it.copy(defaultSceneId=a.id,scenes=listOf(a,b),branding=TourBranding("#38BDF8","dark",logo),mapPositions=mapOf(a.id to MapPosition(.1f,.2f),b.id to MapPosition(.7f,.5f)))}
            cancelSync()
            // An unreachable endpoint exercises a real failed sync, without disabling phone networking.
            account.baseUrl="http://127.0.0.1:1"
            repo.edit(id){it.copy(description="Offline change survives")};cancelSync()
            assertTrue(runCatching{repo.sync(id)}.isFailure)
            assertEquals("Offline change survives",TourRepository(context,account).dao.draft(id,account.ownerId)!!.decode().description)
            assertNull(repo.dao.draft(id,"another-account"))
            account.baseUrl=originalBase
            repo.sync(id)
            var saved=repo.dao.draft(id,account.ownerId)!!
            assertFalse(saved.dirty);assertFalse(saved.conflict);assertTrue(saved.decode().assets().none{it.startsWith("local:")})
            val unchangedRevision=saved.baseRevision;repo.sync(id)
            assertEquals("Polling must not create new revisions",unchangedRevision,repo.dao.draft(id,account.ownerId)!!.baseRevision)
            assertTrue(repo.dao.assets(account.ownerId,id).all{it.uploaded})
            assertEquals(2,saved.decode().scenes.size);assertTrue(saved.decode().unreachable().isEmpty())
            assertEquals(404,publicTour(saved.slug).first)
            // Change the server independently, then verify local edits are never overwritten.
            repo.edit(id){it.copy(description="Keep these local edits")};cancelSync()
            val remote=JSONObject(account.call("tours/$id"))
            val remoteDocument=remote.getJSONObject("document").put("description","Other device edit")
            account.call("tours/$id",JSONObject().put("revision",remote.getInt("revision")).put("document",remoteDocument),method="PUT")
            repo.sync(id);saved=repo.dao.draft(id,account.ownerId)!!
            assertTrue(saved.conflict);assertEquals("Keep these local edits",saved.decode().description)
            repo.resolve(id,false);cancelSync()
            assertEquals("Other device edit",repo.dao.draft(id,account.ownerId)!!.decode().description)
            assertNotNull(repo.dao.draft(id,account.ownerId)!!.recoveryDocument)
            repo.restoreRecovery(id);cancelSync();repo.sync(id)
            repo.publish(id);saved=repo.dao.draft(id,account.ownerId)!!
            var published=publicTour(saved.slug);assertEquals(200,published.first)
            val document=published.second!!.getJSONObject("document")
            assertEquals("#38BDF8",document.getJSONObject("branding").getString("accentColor"))
            assertTrue(document.getJSONObject("branding").getString("logoUrl").startsWith("http"))
            val points=document.getJSONArray("scenes").getJSONObject(0).getJSONArray("points")
            assertEquals(3,points.length())
            for(i in 0 until points.length()){val p=points.getJSONObject(i);if(p.optString("kind") in listOf("image","video"))assertTrue(p.getString("mediaUrl").startsWith("http"))}
            repo.edit(id){it.copy(description="Private draft after publish")};cancelSync();repo.sync(id)
            published=publicTour(saved.slug);assertEquals("Keep these local edits",published.second!!.getJSONObject("document").getString("description"))
            repo.publish(id,true);assertEquals(404,publicTour(saved.slug).first)
            assertNotNull(repo.file(id,repo.dao.draft(id,account.ownerId)!!.decode().scenes.first().assetPath))
            InstrumentationRegistry.getInstrumentation().sendStatus(0,Bundle().apply{putString("stream","\nTOUR_TEST_ID=$id\nAll rich editor integration checks passed.\n")})
        } finally {
            account.baseUrl=originalBase
            cancelSync()
            runCatching{repo.publish(id,true)}
        }
    }

    private fun video(file:File) {
        val codec=MediaCodec.createEncoderByType("video/avc")
        val format=MediaFormat.createVideoFormat("video/avc",128,128).apply{setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);setInteger(MediaFormat.KEY_BIT_RATE,64000);setInteger(MediaFormat.KEY_FRAME_RATE,10);setInteger(MediaFormat.KEY_I_FRAME_INTERVAL,1)}
        val muxer=MediaMuxer(file.path,MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var started=false;var track=-1;var frame=0;var done=false
        try {
            codec.configure(format,null,null,MediaCodec.CONFIGURE_FLAG_ENCODE);codec.start()
            val info=MediaCodec.BufferInfo();val deadline=System.currentTimeMillis()+15000
            while(!done&&System.currentTimeMillis()<deadline){
                if(frame<=10){val input=codec.dequeueInputBuffer(10000);if(input>=0){val bytes=ByteArray(128*128*3/2){if(it<128*128)(70+frame*8).toByte()else 128.toByte()};codec.getInputBuffer(input)!!.apply{clear();put(bytes)};codec.queueInputBuffer(input,0,bytes.size,frame*100000L,if(frame==10)MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0);frame++}}
                val out=codec.dequeueOutputBuffer(info,10000)
                if(out==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){track=muxer.addTrack(codec.outputFormat);muxer.start();started=true}
                else if(out>=0){if(info.size>0&&info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG==0){val buffer=codec.getOutputBuffer(out)!!;buffer.position(info.offset);buffer.limit(info.offset+info.size);muxer.writeSampleData(track,buffer,info)};done=info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM!=0;codec.releaseOutputBuffer(out,false)}
            }
            check(done){"Video test encoder timed out"}
        }finally{runCatching{codec.stop()};codec.release();if(started)muxer.stop();muxer.release()}
    }
}
