package com.venuelink.studio.capture

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.view.HapticFeedbackConstants
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.venuelink.studio.data.model.CaptureDot
import com.venuelink.studio.domain.capture.ArCaptureGuidance
import com.venuelink.studio.ui.components.ArCaptureView
import com.venuelink.studio.ui.components.ArFrameSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.abs
import com.venuelink.studio.tour.TourBuilderHome

@Composable
fun CaptureApp(onLegacy: () -> Unit) {
    val context=LocalContext.current
    val account=remember { PortalAccount(context) }
    val dao=remember { CaptureDatabase.get(context).captures() }
    val sessions by dao.observeSessions().collectAsState(initial=emptyList())
    val scope=rememberCoroutineScope()
    var signedIn by remember { mutableStateOf(account.signedIn) }
    var base by remember { mutableStateOf(account.baseUrl) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var roomName by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var tours by remember { mutableStateOf<List<Pair<String,String>>>(emptyList()) }
    var tourId by remember { mutableStateOf("") }
    var active by remember { mutableStateOf<CaptureSession?>(null) }
    var review by remember { mutableStateOf<CaptureSession?>(null) }
    var panorama by remember { mutableStateOf<CaptureSession?>(null) }
    var autoOpen by remember { mutableStateOf<String?>(null) }
    var builder by remember { mutableStateOf(true) }
    var builderTour by remember { mutableStateOf<String?>(null) }
    val currentSessions by rememberUpdatedState(sessions)
    val lifecycle=LocalLifecycleOwner.current
    suspend fun refresh() {
        val data=withContext(Dispatchers.IO){JSONArray(account.call("tours"))}
        tours=(0 until data.length()).map{data.getJSONObject(it).let{t->t.getString("id") to t.getString("title")}}
        if(tours.none{it.first==tourId})tourId=tours.firstOrNull()?.first.orEmpty()
        val cloud=withContext(Dispatchers.IO){JSONArray(account.call("captures"))}
        for(index in 0 until cloud.length()) {val c=cloud.getJSONObject(index);val local=dao.session(c.getString("id"));if(local!=null&&local.ownerId==account.ownerId&&c.getString("status")!="uploading"){CapturePipeline.sync(context,account,local.id);if(c.getString("status") in CapturePipeline.processingStates)CapturePipeline.enqueue(context,local.id)}}
    }
    fun work(block:suspend ()->Unit){scope.launch{busy=true;try{block()}catch(e:Exception){message=e.message?:"Something went wrong."}finally{busy=false}}}
    LaunchedEffect(signedIn,builder){if(signedIn&&!builder)try{refresh()}catch(e:Exception){message=e.message?:"Could not connect. Saved captures are available offline."}}
    LaunchedEffect(signedIn,active?.id){if(signedIn&&active==null)lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){while(true){for(s in currentSessions.filter{it.ownerId==account.ownerId&&(it.state in CapturePipeline.processingStates||(it.state=="ready"&&CapturePipeline.localPanorama(it)==null))})try{CapturePipeline.sync(context,account,s.id)}catch(e:CancellationException){throw e}catch(_:Exception){CapturePipeline.enqueue(context,s.id)};delay(5000)}}}
    LaunchedEffect(autoOpen,sessions){val ready=sessions.firstOrNull{it.id==autoOpen&&it.ownerId==account.ownerId&&it.state=="ready"&&CapturePipeline.localPanorama(it)!=null};if(ready!=null){panorama=ready;review=null;autoOpen=null}}
    fun createPanorama(session:CaptureSession){work{if(session.state=="failed"&&dao.frames(session.id).all{it.uploaded}){withContext(Dispatchers.IO){account.call("captures/${session.id}",JSONObject().put("action","retry"))};dao.status(session.id,"queued","Waiting to build your panorama…");CapturePipeline.enqueue(context,session.id)}else{dao.status(session.id,"waiting","Waiting for a connection");CaptureUploadWorker.enqueue(context,session.id)};review=session;autoOpen=session.id}}
    if(panorama!=null){CapturePanoramaViewer(panorama!!,onBack={panorama=null});return}
    if(active!=null){GuidedRoomCapture(active!!,onDone={val completed=active!!;active=null;review=completed;scope.launch{val latest=dao.session(completed.id)?:completed;if(latest.state=="captured")createPanorama(latest)}});return}
    if(review!=null){CaptureReview(review!!,onBack={review=null;autoOpen=null},onUpload={createPanorama(it)},onView={session->panorama=session;review=null;autoOpen=null});return}
    if(signedIn&&builder){TourBuilderHome(account,initialTourId=builderTour,onCapture={id->tourId=id;roomName="";builderTour=id;builder=false},onExit={builder=false},onSignOut={account.signOut();signedIn=false;builder=true});return}
    Surface(color=Color(0xFFF5F6F0),modifier=Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(22.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item { Text("◉ Portal Capture",style=MaterialTheme.typography.headlineLarge);Text("Every room. Every angle.",color=Color(0xFF62746A)) }
            if(!signedIn) {
                if(com.venuelink.studio.BuildConfig.PORTAL_BASE_URL.isBlank())item { OutlinedTextField(base,{base=it},label={Text("Portal website (https://…)" )},modifier=Modifier.fillMaxWidth(),singleLine=true) }
                item { OutlinedTextField(email,{email=it},label={Text("Email")},modifier=Modifier.fillMaxWidth(),singleLine=true) }
                item { OutlinedTextField(password,{password=it},label={Text("Password")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth()) }
                item { Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){ Button(enabled=!busy,onClick={work{message=withContext(Dispatchers.IO){account.baseUrl=base;account.authenticate(email,password)};signedIn=account.signedIn}}){Text("Sign in")};OutlinedButton(enabled=!busy,onClick={work{message=withContext(Dispatchers.IO){account.baseUrl=base;account.authenticate(email,password,true)};signedIn=account.signedIn}}){Text("Create account")} } }
                item { TextButton(onClick={work{withContext(Dispatchers.IO){account.baseUrl=base;account.call("auth",JSONObject().put("action","recover").put("email",email),false)};message="Check your email for a password reset link."}}){Text("Forgot password?")} }
            } else {
                item { Row { TextButton(onClick={builder=true}){Text("← Tour builder")};TextButton(enabled=!busy,onClick={work{refresh()}}){Text("Refresh")};TextButton(onClick={account.signOut();signedIn=false}){Text("Sign out")} } }
                items(tours,key={it.first}){tour->FilterChip(selected=tourId==tour.first,onClick={tourId=tour.first},label={Text(tour.second)})}
                item { OutlinedTextField(title,{title=it},label={Text("New tour / venue name")},modifier=Modifier.fillMaxWidth());TextButton(enabled=!busy&&title.isNotBlank(),onClick={work{val created=withContext(Dispatchers.IO){JSONObject(account.call("tours",JSONObject().put("title",title)))};tourId=created.getString("id");title="";refresh()}}){Text("Create tour")} }
                item { OutlinedTextField(roomName,{roomName=it},label={Text("Room name")},placeholder={Text("e.g. Main dining room")},modifier=Modifier.fillMaxWidth()) }
                item { Button(modifier=Modifier.fillMaxWidth(),enabled=tourId.isNotBlank()&&roomName.isNotBlank()&&!busy,onClick={work{val s=CaptureSession(UUID.randomUUID().toString(),account.ownerId,tourId,roomName.trim());dao.insertSession(s);active=s}}){Text("Capture this room →")} }
                item { Text("Capture ${CaptureGeometry.ultrawideTargets().size} overlapping photos. Keep the camera lens over one fixed point while turning and tilting. After the last photo, the app uploads and builds your panorama, then opens it here. Originals are saved on your phone first.",style=MaterialTheme.typography.bodyMedium) }
            }
            if(message.isNotBlank())item { Text(message,color=Color(0xFF974A30)) }
            item { HorizontalDivider();Text("Saved captures",style=MaterialTheme.typography.titleLarge) }
            items(sessions.filter{signedIn&&it.ownerId==account.ownerId},key={it.id}) { session ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(7.dp)){Text(session.name,style=MaterialTheme.typography.titleMedium);Text(session.state);if(session.message.isNotBlank())Text(session.message);if(session.state in listOf("waiting","uploading","queued","processing"))LinearProgressIndicator(modifier=Modifier.fillMaxWidth());if(session.state=="ready")Button(onClick={if(CapturePipeline.localPanorama(session)!=null)panorama=session else{review=session;autoOpen=session.id;CapturePipeline.enqueue(context,session.id)}}){Text("View panorama")};TextButton(onClick={review=session}){Text("Review photos / processing")};if(session.state=="capturing")Text("Interrupted captures keep their photos. Start a new capture to reset tracking safely.",style=MaterialTheme.typography.bodySmall)} }
            }
            item { TextButton(onClick=onLegacy){Text("Open archived local tours")} }
        }
    }
}

@Composable
private fun CaptureReview(session:CaptureSession,onBack:()->Unit,onUpload:(CaptureSession)->Unit,onView:(CaptureSession)->Unit) {
    val context=LocalContext.current;val dao=remember{CaptureDatabase.get(context).captures()}
    val frames by remember(session.id){dao.observeFrames(session.id)}.collectAsState(initial=emptyList())
    val observed by remember(session.id){dao.observeSession(session.id)}.collectAsState(initial=session)
    val latest=observed?:session
    BackHandler(onBack=onBack)
    Surface(color=Color(0xFFF5F6F0),modifier=Modifier.fillMaxSize()){LazyColumn(Modifier.statusBarsPadding().navigationBarsPadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{TextButton(onClick=onBack){Text("← Saved captures")};Text(session.name,style=MaterialTheme.typography.headlineMedium);Text("${frames.size} / ${latest.expectedFrames} photos saved");Text("${latest.state} · ${latest.message}")}
        item{when{latest.state=="ready"&&CapturePipeline.localPanorama(latest)!=null->Button(onClick={onView(latest)},modifier=Modifier.fillMaxWidth()){Text("View 360° panorama")};latest.state in listOf("waiting","uploading","queued","processing","ready")->Column{LinearProgressIndicator(modifier=Modifier.fillMaxWidth());Text("Your panorama will open here when it is ready. You can also return to Saved captures.")};else->Button(enabled=frames.size>=8&&frames.size==latest.expectedFrames&&latest.state in listOf("captured","failed","paused","capturing"),onClick={onUpload(latest)},modifier=Modifier.fillMaxWidth()){Text("Create panorama")}};if(frames.size!=latest.expectedFrames)Text("This capture is incomplete. Start a new room capture; these original photos remain saved.")}
        items(frames,key={it.id}){frame->Column{coil3.compose.AsyncImage(model=File(frame.path),contentDescription="Captured photo ${frame.targetId}",modifier=Modifier.fillMaxWidth().height(190.dp));Text(frame.targetId,style=MaterialTheme.typography.bodySmall)}}
    }}
}

@Composable
private fun GuidedRoomCapture(session:CaptureSession,onDone:()->Unit) {
    val context=LocalContext.current;val dao=remember{CaptureDatabase.get(context).captures()};val scope=rememberCoroutineScope();val lifecycle=LocalLifecycleOwner.current
    val stillness=remember{GyroscopeStillness(context)};val holdGate=remember{CaptureHoldGate()}
    var permitted by remember{mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){permitted=it}
    var camera by remember{mutableStateOf<ArCaptureView?>(null)};var frame by remember{mutableStateOf<ArFrameSnapshot?>(null)};var origin by remember{mutableStateOf<FloatArray?>(null)}
    var targets by remember{mutableStateOf<List<CaptureDot>>(emptyList())};var captured by remember{mutableStateOf(setOf<String>())};var last by remember{mutableStateOf<CaptureFrame?>(null)}
    var busy by remember{mutableStateOf(false)};var hold by remember{mutableStateOf(0f)};var message by remember{mutableStateOf("Move slowly to initialize tracking.")}
    var interrupted by remember{mutableStateOf(false)}
    val target=targets.firstOrNull{it.id !in captured};val snapshot=frame;val start=origin
    val projection=if(target!=null&&snapshot?.tracking==true&&start!=null)ArCaptureGuidance.projectActiveTarget(target,start,snapshot.poseMatrix,snapshot.viewportWidth.toFloat(),snapshot.viewportHeight.toFloat(),aimThresholdDegrees=5f,projectionMatrix=snapshot.projectionMatrix)else null
    val nearStart=snapshot!=null&&start!=null&&CaptureGeometry.isNearStartingPoint(start,snapshot.poseMatrix)
    val aimed=projection?.isAimed==true&&!interrupted&&nearStart
    fun take(){val view=camera?:return;if(busy||!aimed||target==null||hold<1f||!stillness.isStill())return;busy=true;holdGate.reset();hold=0f;message="Keep still until the ultrawide photo finishes…";view.captureNextFrame(File(context.cacheDir,"${UUID.randomUUID()}.jpg")){stillness.isStill()}}
    LaunchedEffect(Unit){if(!permitted)permission.launch(Manifest.permission.CAMERA)}
    DisposableEffect(camera,permitted,lifecycle){val view=camera;val observer=LifecycleEventObserver{_,event->when(event){Lifecycle.Event.ON_RESUME->if(permitted){stillness.start();view?.onHostResume()};Lifecycle.Event.ON_PAUSE->{stillness.stop();holdGate.reset();hold=0f;view?.onHostPause();if(captured.isNotEmpty())interrupted=true};else->Unit}};lifecycle.lifecycle.addObserver(observer);if(permitted&&lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)){stillness.start();view?.onHostResume()};onDispose{lifecycle.lifecycle.removeObserver(observer);stillness.stop();view?.close()}}
    LaunchedEffect(hold>=1f,target?.id,busy){if(hold>=1f&&!busy)take()}
    Box(Modifier.fillMaxSize().background(Color.Black)){
        if(permitted)AndroidView(modifier=Modifier.fillMaxSize(),factory={ArCaptureView(it, useUltrawideStill = true).also{camera=it}},update={view->
            view.onStatus={message=it}
            view.onFrame={next->
                frame=next
                if(origin==null&&next.tracking&&next.imageWidth>0){origin=CaptureGeometry.origin(next.poseMatrix);targets=CaptureGeometry.ultrawideTargets();scope.launch{dao.targetCount(session.id,targets.size)}}
                val currentTarget=targets.firstOrNull{it.id !in captured};val currentOrigin=origin
                val onDot=if(currentTarget!=null&&currentOrigin!=null&&next.tracking)ArCaptureGuidance.projectActiveTarget(currentTarget,currentOrigin,next.poseMatrix,next.viewportWidth.toFloat(),next.viewportHeight.toFloat(),aimThresholdDegrees=5f,projectionMatrix=next.projectionMatrix).isAimed else false
                val positionOk=currentOrigin!=null&&CaptureGeometry.isNearStartingPoint(currentOrigin,next.poseMatrix)
                hold=holdGate.progress(currentTarget?.id,onDot&&positionOk&&!interrupted&&!busy&&stillness.isStill(),next.timestampNanos)
            }
            view.onCaptureResult={result->result.onSuccess{photo->val dot=target;if(dot!=null)scope.launch{
                try{val saved=withContext(Dispatchers.IO){
                    require(CaptureGeometry.isNearStartingPoint(origin?:photo.cameraPoseMatrix,photo.cameraPoseMatrix)){"The camera moved away from its starting point. Move the lens back and retake this target."}
                    val id=UUID.randomUUID().toString();val folder=File(context.filesDir,"capture-originals/${session.id}").apply{mkdirs()};val output=File(folder,"$id.jpg");photo.file.copyTo(output);photo.file.delete()
                    val bitmap=BitmapFactory.decodeFile(output.path)?:error("Image could not be decoded.");val pixels=IntArray(bitmap.width*bitmap.height);bitmap.getPixels(pixels,0,bitmap.width,0,0,bitmap.width,bitmap.height);bitmap.recycle()
                    val checksum=MessageDigest.getInstance("SHA-256").digest(output.readBytes()).joinToString(""){"%02x".format(it)}
                    val panoramaPose=CaptureGeometry.rotationalPanoramaPose(origin?:photo.cameraPoseMatrix,photo.cameraPoseMatrix)
                    val data=JSONObject().put("id",id).put("sha256",checksum).put("width",photo.width).put("height",photo.height).put("timestampNanos",photo.timestampNanos).put("intrinsics",JSONArray(photo.intrinsics.toList())).put("pose",JSONArray(panoramaPose.toList())).put("observedPose",JSONArray(photo.cameraPoseMatrix.toList()))
                    CaptureFrame(id,session.id,dot.id,output.path,data.toString()).also{dao.saveFrame(it)}
                };last=saved;captured=captured+dot.id;view.performHapticFeedback(HapticFeedbackConstants.CONFIRM);message="Saved. Move to the next target.";if(captured.size==targets.size){dao.status(session.id,"captured","Ready to build your panorama");onDone()}
                }catch(e:Exception){message="Photo was not saved: ${e.message}"}finally{holdGate.reset();hold=0f;busy=false}
            }else busy=false}.onFailure{holdGate.reset();hold=0f;busy=false;message=it.message?:"Capture failed. Try again."}}
        })
        Canvas(Modifier.fillMaxSize()){
            projection?.screenPoint?.let{drawCircle(Color(0xFFBCE867),32f,it);drawCircle(Color.White,40f,it,style=Stroke(3f))}
            drawCircle(Color.White,48f,center,style=Stroke(3f));if(hold>0f)drawArc(Color(0xFFBCE867),-90f,hold*360f,false,topLeft=center-Offset(52f,52f),size=androidx.compose.ui.geometry.Size(104f,104f),style=Stroke(7f))
        }
        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(20.dp).background(Color.Black.copy(alpha=.55f)).padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally){Text(session.name,color=Color.White,style=MaterialTheme.typography.titleLarge);Text("${captured.size} / ${targets.size} photos saved",color=Color(0xFFBCE867));Text(when{interrupted->"Tracking was interrupted. Review saved photos and start a new capture.";busy->"Keep still until the ultrawide photo finishes…";snapshot?.tracking==true&&!nearStart->"Move the lens back over its starting point (within 12 cm). Turn and tilt around the lens, not your body.";snapshot?.tracking!=true->when(snapshot?.trackingFailureReason){"INSUFFICIENT_LIGHT"->"AR needs more light. Point at a brighter part of the room.";"EXCESSIVE_MOTION"->"Move the phone more slowly so tracking can recover.";"INSUFFICIENT_FEATURES"->"Point at textured surfaces, not a blank wall or floor.";else->"Move slowly in a well-lit room to start AR tracking."};target==null&&targets.isNotEmpty()->"All targets complete.";aimed->"Hold steady on the dot for 1 second";projection?.arrow!=null->projection.arrow!!.label;else->message},color=Color.White);if(snapshot?.trackingFailureReason=="MOTION_SENSORS")Text("Motion guidance active · keep turning from one spot",color=Color(0xFFBCE867),style=MaterialTheme.typography.bodySmall);if(targets.isNotEmpty())LinearProgressIndicator(progress={captured.size.toFloat()/targets.size},modifier=Modifier.fillMaxWidth().padding(top=12.dp),color=Color(0xFFBCE867))}
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally){
            Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){
                Button(enabled=hold>=1f&&!busy,onClick={take()}){Text("Capture")}
                OutlinedButton(enabled=last!=null&&!busy&&!interrupted,onClick={val previous=last;if(previous!=null)scope.launch{dao.deleteFrame(previous.id);captured=captured-previous.targetId;last=null;dao.status(session.id,"capturing")}}){Text("Retake last",color=Color.White)}
            }
            if(captured.isEmpty()&&snapshot?.tracking==true)OutlinedButton(onClick={origin=CaptureGeometry.origin(snapshot.poseMatrix);interrupted=false;message="Starting point set. Turn toward the next target."}){Text("Start from here",color=Color.White)}
            Button(enabled=!busy,onClick=onDone){Text("Review & save for later")}
        }
    }
}
