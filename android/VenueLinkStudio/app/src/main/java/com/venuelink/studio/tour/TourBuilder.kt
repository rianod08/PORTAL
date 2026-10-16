package com.venuelink.studio.tour

import android.content.Intent
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.venuelink.studio.capture.*
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.*

private val Paper=Color(0xFFF5F6F0)
private val Ink=Color(0xFF162C2B)

@Composable
fun TourBuilderHome(account: PortalAccount, initialTourId: String?, onCapture: (String)->Unit, onExit: ()->Unit, onSignOut: ()->Unit) {
    val context=LocalContext.current;val owner=account.ownerId;val repo=remember(owner){TourRepository(context,account)}
    val drafts by remember(owner){repo.dao.observe(owner)}.collectAsState(initial=emptyList())
    val captures by remember{CaptureDatabase.get(context).captures().observeSessions()}.collectAsState(initial=emptyList())
    val scope=rememberCoroutineScope();var selected by remember{mutableStateOf(initialTourId)};var creating by remember{mutableStateOf(false)};var title by remember{mutableStateOf("")};var message by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)}
    fun work(action:suspend()->Unit){scope.launch{busy=true;try{action();message=""}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message?:"Connection unavailable. Your drafts are saved on this phone."}finally{busy=false}}}
    LaunchedEffect(owner){work{repo.refresh()}}
    if(selected!=null){key(selected){TourEditor(repo,account,selected!!,onBack={selected=null},onCapture=onCapture)};return}
    BackHandler(onBack=onExit)
    Surface(color=Paper,modifier=Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            item{Text("◉ Portal",style=MaterialTheme.typography.headlineLarge,color=Ink);Text("Your tours",style=MaterialTheme.typography.headlineMedium);Text("Capture a room. Build a place to explore.")}
            item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={creating=true}){Text("＋ New tour")};TextButton(enabled=!busy,onClick={work{repo.refresh()}}){Text("Refresh")};TextButton(onClick=onSignOut){Text("Sign out")}}}
            if(busy)item{LinearProgressIndicator(Modifier.fillMaxWidth())}
            if(message.isNotBlank())item{Text(message,color=Color(0xFF974A30));Text("Downloaded tours can still be edited offline.")}
            if(drafts.isEmpty())item{Text("Create a tour or connect once to download your existing tours.")}
            items(drafts.sortedBy{it.decode().title.lowercase()},key={it.id}){draft->val d=draft.decode();Card(Modifier.fillMaxWidth()){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
                Text(d.title,style=MaterialTheme.typography.titleLarge);Text("${d.scenes.size} rooms · ${if(draft.publishedRevision!=null)"Published"else"Private draft"}");Text(draft.message)
                val pending=captures.count{it.ownerId==owner&&it.tourId==draft.id&&it.state in listOf("waiting","uploading","queued","processing")};if(pending>0)Text("$pending room capture(s) processing")
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){Button(onClick={selected=draft.id}){Text("Edit")};TextButton(onClick={selected=draft.id;context.getSharedPreferences("tour-preview",0).edit().putBoolean(draft.id,true).apply()}){Text("Preview")};TextButton(enabled=draft.publishedRevision!=null,onClick={share(context,"${account.baseUrl}/v/${draft.slug}/tour")}){Text("Share")}}
            }}}
            item{TextButton(onClick=onExit){Text("Capture & saved photos")}}
        }
    }
    if(creating)AlertDialog(onDismissRequest={creating=false},title={Text("New tour")},text={OutlinedTextField(title,{title=it},label={Text("Venue / tour name")},singleLine=true)},confirmButton={TextButton(enabled=title.isNotBlank()&&!busy,onClick={work{selected=repo.create(title.trim());title="";creating=false}}){Text("Create")}},dismissButton={TextButton(onClick={creating=false}){Text("Cancel")}})
}

private fun share(context:android.content.Context,url:String){context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,url),"Share tour"))}

@Composable
private fun TourEditor(repo:TourRepository,account:PortalAccount,id:String,onBack:()->Unit,onCapture:(String)->Unit) {
    val context=LocalContext.current;val scope=rememberCoroutineScope();val draft by remember(id){repo.dao.observeDraft(id,account.ownerId)}.collectAsState(initial=null)
    var tab by remember{mutableStateOf("Rooms")};var roomId by remember{mutableStateOf("")};var preview by remember{mutableStateOf(context.getSharedPreferences("tour-preview",0).getBoolean(id,false))}
    var message by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var editing by remember{mutableStateOf<TourPoint?>(null)};var viewing by remember{mutableStateOf<TourPoint?>(null)}
    var reposition by remember{mutableStateOf<String?>(null)};var returnTo by remember{mutableStateOf<String?>(null)};var publishDialog by remember{mutableStateOf(false)};var overwriteDialog by remember{mutableStateOf(false)};var removing by remember{mutableStateOf<TourRoom?>(null)}
    val history=remember{TourHistory()};var historyTick by remember{mutableIntStateOf(0)}
    val canUndo=historyTick.let{history.canUndo};val canRedo=historyTick.let{history.canRedo}
    val d=draft?.decode();val room=d?.scenes?.find{it.id==roomId}?:d?.scenes?.find{it.id==d.defaultSceneId}?:d?.scenes?.firstOrNull()
    var view by remember(room?.id){mutableStateOf(room?.initialView?:TourView())}
    fun work(action:suspend()->Unit){scope.launch{busy=true;try{action();message=""}catch(e:CancellationException){throw e}catch(e:Exception){message=e.message?:"Unable to connect. Your draft is saved locally."}finally{busy=false}}}
    fun change(transform:(TourDocument)->TourDocument){scope.launch{try{repo.edit(id){old->history.record(old);transform(old)};historyTick++}catch(e:Exception){message=e.message.orEmpty()}}}
    fun savePoint(point:TourPoint,source:String=room!!.id){change{doc->doc.room(doc.scenes.first{it.id==source}.let{s->s.copy(points=if(s.points.any{it.id==point.id})s.points.map{if(it.id==point.id)point else it}else s.points+point)})}}
    LaunchedEffect(id){context.getSharedPreferences("tour-preview",0).edit().remove(id).apply();work{repo.sync(id)}}
    LaunchedEffect(id){while(true){delay(5000);if(account.signedIn)try{repo.sync(id)}catch(e:CancellationException){throw e}catch(_:Exception){/* Local drafts remain editable while offline. */}}}
    BackHandler{if(viewing!=null)viewing=null else if(editing!=null)editing=null else if(preview)preview=false else onBack()}
    val importPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)work{val path=repo.addMedia(id,uri,"panorama");change{doc->val new=TourRoom(name="New room",assetPath=path);doc.copy(scenes=doc.scenes+new,defaultSceneId=doc.defaultSceneId.ifBlank{new.id})}}}
    if(d==null){Surface(Modifier.fillMaxSize(),color=Paper){Column(Modifier.statusBarsPadding().padding(24.dp)){TextButton(onClick=onBack){Text("← Tours")};Text(message.ifBlank{"Opening your saved tour…"})}};return}
    val accent=runCatching{Color(android.graphics.Color.parseColor(d.branding.accentColor))}.getOrDefault(Color(0xFFBCE867))
    val dark=preview&&d.branding.colorScheme=="dark"
    MaterialTheme(colorScheme=if(preview){if(dark)darkColorScheme(primary=accent,secondary=accent)else lightColorScheme(primary=accent,secondary=accent)}else MaterialTheme.colorScheme){Surface(Modifier.fillMaxSize(),color=if(dark)Ink else Paper,contentColor=if(dark)Color.White else Ink){Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
        if(preview&&d.branding.logoAssetPath!=null)TourLogo(repo,id,d.branding.logoAssetPath)
        Row(verticalAlignment=Alignment.CenterVertically){TextButton(onClick={if(preview)preview=false else onBack()}){Text(if(preview)"← Editor"else"← Tours")};Text(d.title,modifier=Modifier.weight(1f),maxLines=1,style=MaterialTheme.typography.titleLarge);TextButton(onClick={if(!preview)roomId=d.defaultSceneId;preview=!preview;tab="Editor"}){Text(if(preview)"Edit"else"Preview")}}
        if(!preview){Text(draft!!.message,style=MaterialTheme.typography.bodySmall);LazyRow(horizontalArrangement=Arrangement.spacedBy(6.dp)){item{TextButton(enabled=canUndo,onClick={scope.launch{repo.edit(id){history.undo(it)?:it};historyTick++}}){Text("Undo")}};item{TextButton(enabled=canRedo,onClick={scope.launch{repo.edit(id){history.redo(it)?:it};historyTick++}}){Text("Redo")}};item{TextButton(enabled=!busy,onClick={work{repo.sync(id)}}){Text("Sync")}};item{Button(enabled=!busy,onClick={publishDialog=true}){Text(if(draft!!.publishedRevision!=null)"Update live tour"else"Publish")}};item{TextButton(enabled=draft!!.publishedRevision!=null,onClick={share(context,"${account.baseUrl}/v/${draft!!.slug}/tour")}){Text("Share")}}}
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(listOf("Rooms","Editor","Connections","Style")){name->FilterChip(selected=tab==name,onClick={tab=name},label={Text(name)})}}
        }
        if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())
        if(message.isNotBlank())Text(message,color=if(dark)accent else Color(0xFF974A30),style=MaterialTheme.typography.bodySmall)
        if(draft!!.conflict)Column{Text("Another device changed this tour. Your edits are kept.");Row{TextButton(onClick={work{repo.resolve(id,false)}}){Text("Load server draft")};TextButton(onClick={overwriteDialog=true}){Text("Replace server draft")}}}
        if(preview||tab=="Editor"){
            if(room==null){Text("Add a captured room or import a panorama to start.");Button(onClick={tab="Rooms";preview=false}){Text("Add rooms")}}
            else{
                LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){items(d.scenes,key={it.id}){r->FilterChip(selected=r.id==room.id,onClick={roomId=r.id;reposition=null;returnTo=null},label={Text(r.name)})}}
                key(preview,room.id){TourSphere(repo,id,room,accent,Modifier.weight(1f).fillMaxWidth(),onView={view=it},onPoint={point->if(preview){if(point.kind=="scene"){roomId=point.targetSceneId.orEmpty()}else viewing=point}else editing=point},editing=!preview)}
                if(!preview){Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    Button(modifier=Modifier.weight(1f),onClick={when{reposition!=null->{room.points.find{it.id==reposition}?.let{savePoint(it.copy(yaw=view.yaw,pitch=view.pitch))};reposition=null};returnTo!=null->{savePoint(TourPoint(label="Back to ${d.scenes.find{it.id==returnTo}?.name.orEmpty()}",kind="scene",yaw=view.yaw,pitch=view.pitch,targetSceneId=returnTo));returnTo=null};else->editing=TourPoint(yaw=view.yaw,pitch=view.pitch)}}){Text(when{reposition!=null->"Place point here";returnTo!=null->"Place return link";else->"＋ Add point"})}
                    TextButton(onClick={val saved=view;change{doc->doc.room(doc.scenes.first{it.id==room.id}.copy(initialView=saved))}}){Text("Set start view")}
                };if(reposition!=null||returnTo!=null)TextButton(onClick={reposition=null;returnTo=null}){Text("Cancel placement")}
                    LazyRow{items(room.points,key={it.id}){point->TextButton(onClick={editing=point}){Text("${if(point.enabled)"●"else"○"} ${point.label}")}}}
                }
            }
        }else when(tab){
            "Rooms"->LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(10.dp)){
                item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={onCapture(id)}){Text("Capture room")};OutlinedButton(onClick={importPicker.launch(arrayOf("image/jpeg","image/png","image/webp"))}){Text("Import 360°")}}}
                if(d.unreachable().isNotEmpty())item{Text("Not linked from the entrance: ${d.unreachable().joinToString{it.name}}",color=Color(0xFF974A30))}
                items(d.scenes,key={it.id}){r->var name by remember(r.id,r.name){mutableStateOf(r.name)};Card{Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
                    Text(if(d.defaultSceneId==r.id)"ENTRANCE"else"ROOM",style=MaterialTheme.typography.labelSmall);OutlinedTextField(name,{name=it},label={Text("Room name")},singleLine=true,modifier=Modifier.fillMaxWidth());Row{TextButton(enabled=name.isNotBlank(),onClick={change{doc->doc.room(doc.scenes.first{it.id==r.id}.copy(name=name.trim()))}}){Text("Rename")};TextButton(onClick={roomId=r.id;tab="Editor"}){Text("Edit 360°")};TextButton(onClick={change{it.copy(defaultSceneId=r.id)}}){Text("Start here")}}
                    Row{val index=d.scenes.indexOf(r);TextButton(enabled=index>0,onClick={change{doc->val rooms=doc.scenes.toMutableList();val n=rooms.indexOfFirst{it.id==r.id};if(n>0){rooms[n]=rooms[n-1];rooms[n-1]=r};doc.copy(scenes=rooms)}}){Text("Move up")};TextButton(enabled=index<d.scenes.lastIndex,onClick={change{doc->val rooms=doc.scenes.toMutableList();val n=rooms.indexOfFirst{it.id==r.id};if(n<rooms.lastIndex){rooms[n]=rooms[n+1];rooms[n+1]=r};doc.copy(scenes=rooms)}}){Text("Move down")};TextButton(onClick={removing=r}){Text("Remove")}}
                }}}
                item{if(draft!!.recoveryDocument!=null)TextButton(onClick={work{repo.restoreRecovery(id)}}){Text("Restore recovery draft")};if(draft!!.publishedRevision!=null)TextButton(onClick={work{repo.publish(id,true)}}){Text("Unpublish tour")}}
            }
            "Connections"->ConnectionMap(d,Modifier.weight(1f),onPosition={node,pos->change{it.copy(mapPositions=it.mapPositions+(node to pos))}},onRoom={roomId=it;tab="Editor"},onPoint={source,p->roomId=source;editing=p;tab="Editor"})
            "Style"->TourStyle(d,repo,id,onChange={next->change{latest->latest.copy(title=next.title,description=next.description,branding=next.branding)}},onError={message=it})
        }
    }}}
    editing?.let{point->PointEditor(point,d.scenes,room?.id.orEmpty(),repo,id,onDismiss={editing=null},onSave={savePoint(it);editing=null},onDelete={change{doc->doc.room(doc.scenes.first{it.id==room!!.id}.let{s->s.copy(points=s.points.filter{it.id!=point.id})})};editing=null},onDuplicate={savePoint(point.copy(id=tourId(),label="${point.label} copy",yaw=(point.yaw+5)%360));editing=null},onReposition={savePoint(it);reposition=it.id;editing=null},onReturn={p->val source=room!!.id;savePoint(p,source);roomId=p.targetSceneId!!;returnTo=source;editing=null})}
    viewing?.let{PointCard(it,repo,id,onDismiss={viewing=null})}
    if(removing!=null)AlertDialog(onDismissRequest={removing=null},title={Text("Remove ${removing!!.name}?")},text={Text("Room links pointing here will also be removed. Your original photos stay saved.")},confirmButton={TextButton(onClick={val remove=removing!!.id;change{it.remove(remove)};removing=null}){Text("Remove room")}},dismissButton={TextButton(onClick={removing=null}){Text("Cancel")}})
    if(overwriteDialog)AlertDialog(onDismissRequest={overwriteDialog=false},title={Text("Replace the server draft?")},text={Text("Your phone draft will replace the latest draft from the other device. A recovery copy stays on this phone. The live tour will not change.")},confirmButton={TextButton(onClick={overwriteDialog=false;work{repo.resolve(id,true)}}){Text("Replace draft")}},dismissButton={TextButton(onClick={overwriteDialog=false}){Text("Cancel")}})
    if(publishDialog)AlertDialog(onDismissRequest={publishDialog=false},title={Text("Publish ${d.title}?")},text={Text("${d.scenes.size} rooms and ${d.scenes.sumOf{it.points.count{p->p.enabled}}} visible points will be available to anyone with the link.${if(d.unreachable().isNotEmpty())" Some rooms have no doorway path; visitors can still use the room list."else""}")},confirmButton={TextButton(enabled=!busy,onClick={publishDialog=false;work{val url=repo.publish(id);share(context,url)}}){Text("Publish & share")}},dismissButton={TextButton(onClick={publishDialog=false}){Text("Cancel")}})
}

@Composable
private fun TourSphere(repo:TourRepository,id:String,room:TourRoom,accent:Color,modifier:Modifier,onView:(TourView)->Unit,onPoint:(TourPoint)->Unit,editing:Boolean) {
    var file by remember(room.assetPath){mutableStateOf<File?>(null)};var view by remember(room.id){mutableStateOf(room.initialView)};var error by remember{mutableStateOf("")}
    val lifecycle=LocalLifecycleOwner.current;val latestView by rememberUpdatedState(onView)
    val context=LocalContext.current
    // Create before AndroidView's layout pass. Writing Compose state in its factory
    // can invalidate that pass and leave a newly attached SurfaceView without a frame.
    val sphere=remember(context,room.id,file?.path){file?.let{PhotosphereView(context,it,initialView=room.initialView,onViewChanged={next->view=next;latestView(next)}){error=it}}}
    LaunchedEffect(room.id){latestView(room.initialView)}
    LaunchedEffect(room.assetPath){while(file==null){file=repo.file(id,room.assetPath);if(file==null)delay(1000)}}
    DisposableEffect(sphere,lifecycle){val target=sphere;val observer=LifecycleEventObserver{_,event->when(event){Lifecycle.Event.ON_RESUME->target?.onResume();Lifecycle.Event.ON_PAUSE->target?.onPause();else->Unit}};lifecycle.lifecycle.addObserver(observer);target?.onResume();onDispose{lifecycle.lifecycle.removeObserver(observer);target?.onPause()}}
    BoxWithConstraints(modifier.background(Color.Black)) {
        val width=constraints.maxWidth.toFloat();val height=constraints.maxHeight.toFloat()
        val half=with(LocalDensity.current){24.dp.roundToPx()}
        if(sphere!=null)key(room.id,file!!.path){AndroidView(modifier=Modifier.fillMaxSize(),factory={sphere})}else Text("Downloading room for offline viewing…",color=Color.White,modifier=Modifier.align(Alignment.Center).padding(20.dp))
        for(p in room.points.filter{it.enabled||editing})TourProjection.point(p.yaw,p.pitch,view,width,height)?.let{(x,y)->Box(modifier=Modifier.offset{IntOffset(x.toInt()-half,y.toInt()-half)}.size(48.dp).clickable{onPoint(p)}.semantics{contentDescription=p.label},contentAlignment=Alignment.Center){Box(Modifier.size(24.dp).background(if(p.enabled)accent else Color.Gray,CircleShape),contentAlignment=Alignment.Center){Text(if(p.kind=="scene")"↗"else"＋",color=Ink,fontSize=7.sp,lineHeight=10.sp,maxLines=1)}}}
        if(editing)Text("＋",color=Color.White,style=MaterialTheme.typography.headlineLarge,modifier=Modifier.align(Alignment.Center))
        if(error.isNotBlank())Text(error,color=Color.White,modifier=Modifier.align(Alignment.Center))
        Row(Modifier.align(Alignment.BottomEnd).padding(8.dp)){Button(onClick={sphere?.zoom(.8f)}){Text("＋")};Button(onClick={sphere?.reset()}){Text("Reset")};Button(onClick={sphere?.zoom(1.25f)}){Text("−")}}
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PointEditor(original:TourPoint,rooms:List<TourRoom>,source:String,repo:TourRepository,tour:String,onDismiss:()->Unit,onSave:(TourPoint)->Unit,onDelete:()->Unit,onDuplicate:()->Unit,onReposition:(TourPoint)->Unit,onReturn:(TourPoint)->Unit) {
    var point by remember(original.id){mutableStateOf(original)};var error by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};val scope=rememberCoroutineScope()
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{busy=true;try{point=point.copy(assetPath=repo.addMedia(tour,uri,point.kind),url=null)}catch(e:Exception){error=e.message.orEmpty()}finally{busy=false}}}
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){LazyColumn(Modifier.fillMaxWidth().fillMaxHeight(.9f).imePadding().navigationBarsPadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
        item{Text("Edit point",style=MaterialTheme.typography.headlineSmall);LazyRow{items(listOf("info","scene","image","video","external")){kind->FilterChip(selected=point.kind==kind,onClick={point=point.copy(kind=kind,assetPath=null,url=null,targetSceneId=null)},label={Text(when(kind){"scene"->"Room link";"info"->"Text";else->kind.replaceFirstChar{it.uppercase()}})})}}}
        item{OutlinedTextField(point.label,{point=point.copy(label=it)},label={Text("Label")},modifier=Modifier.fillMaxWidth());OutlinedTextField(point.description,{point=point.copy(description=it)},label={Text("Description")},modifier=Modifier.fillMaxWidth())}
        if(point.kind=="scene")items(rooms.filter{it.id!=source},key={it.id}){room->FilterChip(selected=point.targetSceneId==room.id,onClick={point=point.copy(targetSceneId=room.id)},label={Text(room.name)})}
        if(point.kind in listOf("image","video"))item{Button(enabled=!busy,onClick={picker.launch(if(point.kind=="video")arrayOf("video/mp4")else arrayOf("image/jpeg","image/png","image/webp"))}){Text(if(point.assetPath==null)"Choose ${point.kind}"else"Replace ${point.kind}")};if(point.assetPath!=null)Text("File attached · available in offline preview");Text("Up to 50 MB. Videos: H.264 MP4 with AAC audio.")}
        if(point.kind in listOf("image","video","external","booking","menu","contact"))item{OutlinedTextField(point.url.orEmpty(),{point=point.copy(url=it.takeIf{v->v.isNotBlank()})},label={Text("Web link (optional for uploaded media)")},modifier=Modifier.fillMaxWidth())}
        item{Row(verticalAlignment=Alignment.CenterVertically){Text("Visible to visitors",Modifier.weight(1f));Switch(point.enabled,{point=point.copy(enabled=it)})};if(error.isNotBlank())Text(error,color=Color(0xFF974A30));if(busy)LinearProgressIndicator(Modifier.fillMaxWidth())}
        item{val valid=point.label.isNotBlank()&&(point.kind!="scene"||point.targetSceneId!=null)&&(point.url.isNullOrBlank()||safeTourUrl(point.url!!,point.kind=="contact"));Row{Button(enabled=valid&&!busy,onClick={onSave(point)}){Text("Save point")};TextButton(enabled=valid&&!busy,onClick={onReposition(point)}){Text("Reposition")}};if(point.kind=="scene")TextButton(enabled=valid,onClick={onReturn(point)}){Text("Save & place return link")};Row{TextButton(onClick=onDuplicate){Text("Duplicate")};TextButton(onClick=onDelete){Text("Delete")};TextButton(onClick=onDismiss){Text("Cancel")}}}
    }}
}

@Composable
private fun TourStyle(document:TourDocument,repo:TourRepository,tour:String,onChange:(TourDocument)->Unit,onError:(String)->Unit) {
    var title by remember(document.title){mutableStateOf(document.title)};var description by remember(document.description){mutableStateOf(document.description)};var accent by remember(document.branding.accentColor){mutableStateOf(document.branding.accentColor)};val scope=rememberCoroutineScope();var logo by remember{mutableStateOf<File?>(null)}
    LaunchedEffect(document.branding.logoAssetPath){logo=repo.file(tour,document.branding.logoAssetPath)}
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->if(uri!=null)scope.launch{try{val path=repo.addMedia(tour,uri,"logo");onChange(document.copy(branding=document.branding.copy(logoAssetPath=path)))}catch(e:Exception){onError(e.message.orEmpty())}}}
    LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp)){
        item{OutlinedTextField(title,{title=it},label={Text("Tour name")},modifier=Modifier.fillMaxWidth());OutlinedTextField(description,{description=it},label={Text("Description")},modifier=Modifier.fillMaxWidth())}
        item{Text("Venue logo",style=MaterialTheme.typography.titleMedium);if(logo!=null)coil3.compose.AsyncImage(model=logo,contentDescription="Venue logo",modifier=Modifier.size(100.dp));Row{Button(onClick={picker.launch(arrayOf("image/jpeg","image/png","image/webp"))}){Text("Choose logo")};TextButton(onClick={onChange(document.copy(branding=document.branding.copy(logoAssetPath=null)))}){Text("Remove logo")}}}
        item{OutlinedTextField(accent,{accent=it},label={Text("Accent colour (#RRGGBB)")},modifier=Modifier.fillMaxWidth());LazyRow{items(listOf("#BCE867","#38BDF8","#F59E0B","#EC4899","#A78BFA")){color->Button(onClick={accent=color},colors=ButtonDefaults.buttonColors(containerColor=Color(android.graphics.Color.parseColor(color)),contentColor=Ink)){Text("●")}}}}
        item{Row{listOf("light","dark").forEach{scheme->FilterChip(selected=document.branding.colorScheme==scheme,onClick={onChange(document.copy(branding=document.branding.copy(colorScheme=scheme)))},label={Text(scheme.replaceFirstChar{it.uppercase()})})}}}
        item{Button(enabled=title.isNotBlank()&&Regex("#[a-fA-F0-9]{6}").matches(accent),onClick={onChange(document.copy(title=title.trim(),description=description,branding=document.branding.copy(accentColor=accent)))}){Text("Save style")};Text("Use Preview to see your branding as visitors will see it.")}
    }
}

@Composable
private fun ConnectionMap(d:TourDocument,modifier:Modifier,onPosition:(String,MapPosition)->Unit,onRoom:(String)->Unit,onPoint:(String,TourPoint)->Unit) {
    Column(modifier,verticalArrangement=Arrangement.spacedBy(8.dp)){
        Text("Drag room cards to arrange them. Doorway points create the arrows.")
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f).background(Color(0xFFE5EBE2)).border(1.dp,Color.LightGray)){
            val width=constraints.maxWidth.toFloat();val height=constraints.maxHeight.toFloat();val cardWidth=with(LocalDensity.current){90.dp.toPx()};val cardHeight=with(LocalDensity.current){62.dp.toPx()}
            val positions=remember(d.scenes.map{it.id},d.mapPositions){mutableStateMapOf<String,MapPosition>().apply{d.scenes.forEachIndexed{i,r->put(r.id,d.mapPositions[r.id]?:MapPosition(.08f+(i%3)*.3f,.08f+(i/3)*.22f))}}}
            fun center(id:String)=positions[id]?.let{Offset(it.x*(width-cardWidth).coerceAtLeast(1f)+cardWidth/2,it.y*(height-cardHeight).coerceAtLeast(1f)+cardHeight/2)}
            Canvas(Modifier.fillMaxSize()){for(room in d.scenes)for(p in room.points.filter{it.enabled&&it.kind=="scene"}){val a=center(room.id);val b=p.targetSceneId?.let{center(it)};if(a!=null&&b!=null){drawLine(Ink,a,b,5f);val direction=(b-a);val length=direction.getDistance().coerceAtLeast(1f);val tip=a+direction*.65f;val unit=direction/length;val side=Offset(-unit.y,unit.x);drawLine(Ink,tip,tip-unit*22f+side*12f,5f);drawLine(Ink,tip,tip-unit*22f-side*12f,5f)}}}
            for(room in d.scenes){val pos=positions[room.id]!!;Box(Modifier.offset{IntOffset((pos.x*(width-cardWidth).coerceAtLeast(1f)).toInt(),(pos.y*(height-cardHeight).coerceAtLeast(1f)).toInt())}.width(90.dp).height(62.dp).background(if(room.id==d.defaultSceneId)Color(0xFFBCE867)else Color.White).border(1.dp,Ink).pointerInput(room.id,width,height,d.mapPositions){detectDragGestures(onDragEnd={onPosition(room.id,positions[room.id]!!)},onDrag={change,delta->change.consume();val current=positions[room.id]!!;positions[room.id]=MapPosition((current.x+delta.x/(width-cardWidth).coerceAtLeast(1f)).coerceIn(0f,1f),(current.y+delta.y/(height-cardHeight).coerceAtLeast(1f)).coerceIn(0f,1f))})}.clickable{onRoom(room.id)},contentAlignment=Alignment.Center){Text(room.name,modifier=Modifier.padding(6.dp),maxLines=2)}}
        }
        LazyColumn(Modifier.heightIn(max=160.dp)){for(room in d.scenes)items(room.points.filter{it.kind=="scene"},key={it.id}){p->TextButton(onClick={onPoint(room.id,p)}){Text("${room.name} → ${d.scenes.find{it.id==p.targetSceneId}?.name?:"Missing room"} · ${p.label}")}}}
        if(d.unreachable().isNotEmpty())Text("Unlinked: ${d.unreachable().joinToString{it.name}}",color=Color(0xFF974A30))
        Button(onClick={d.scenes.firstOrNull()?.let{onRoom(it.id)}}){Text("Place a doorway link in 360°")}
    }
}

@Composable private fun TourLogo(repo:TourRepository,id:String,path:String){var file by remember(path){mutableStateOf<File?>(null)};LaunchedEffect(path){file=repo.file(id,path)};if(file!=null)coil3.compose.AsyncImage(model=file,contentDescription="Venue logo",modifier=Modifier.size(48.dp))}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PointCard(point:TourPoint,repo:TourRepository,tour:String,onDismiss:()->Unit) {
    val context=LocalContext.current;var file by remember(point.id){mutableStateOf<File?>(null)};var video by remember{mutableStateOf<VideoView?>(null)}
    LaunchedEffect(point.id){file=repo.file(tour,point.assetPath)}
    DisposableEffect(Unit){onDispose{video?.stopPlayback()}}
    ModalBottomSheet(onDismissRequest=onDismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)){Column(Modifier.fillMaxWidth().heightIn(max=600.dp).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        Text(point.label,style=MaterialTheme.typography.headlineMedium);Text(point.description)
        if(point.kind=="image")coil3.compose.AsyncImage(model=file?:point.url,contentDescription=point.label,modifier=Modifier.fillMaxWidth().heightIn(max=300.dp))
        if(point.kind=="video"&&(file!=null||point.url!=null))AndroidView(modifier=Modifier.fillMaxWidth().height(260.dp),factory={ctx->VideoView(ctx).apply{video=this;setMediaController(MediaController(ctx).also{it.setAnchorView(this)});setVideoURI(file?.let{Uri.fromFile(it)}?:Uri.parse(point.url));setOnPreparedListener{start()}}})
        if(point.url!=null&&safeTourUrl(point.url,point.kind=="contact"))TextButton(onClick={runCatching{context.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(point.url)))}}){Text("Open link ↗")}
        TextButton(onClick=onDismiss){Text("Close")}
    }}
}
