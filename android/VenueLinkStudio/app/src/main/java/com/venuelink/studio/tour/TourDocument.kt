package com.venuelink.studio.tour

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import kotlin.math.*

val tourJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
fun tourId() = UUID.randomUUID().toString()
@Serializable data class TourView(val yaw: Float = 0f, val pitch: Float = 0f, val fov: Float = 95f)
@Serializable data class TourPoint(val id: String = tourId(), val label: String = "New point", val kind: String = "info", val yaw: Float = 0f, val pitch: Float = 0f, val description: String = "", val url: String? = null, val assetPath: String? = null, val targetSceneId: String? = null, val enabled: Boolean = true)
@Serializable data class TourRoom(val id: String = tourId(), val name: String, val assetPath: String, val initialView: TourView = TourView(), val points: List<TourPoint> = emptyList())
@Serializable data class TourBranding(val accentColor: String = "#BCE867", val colorScheme: String = "dark", val logoAssetPath: String? = null)
@Serializable data class MapPosition(val x: Float, val y: Float)
@Serializable data class TourDocument(val version: Int = 1, val title: String, val description: String = "", val defaultSceneId: String = "", val scenes: List<TourRoom> = emptyList(), val branding: TourBranding = TourBranding(), val mapPositions: Map<String, MapPosition> = emptyMap(), val handledCaptureIds: List<String> = emptyList()) {
    fun room(room: TourRoom) = copy(scenes = scenes.map { if(it.id==room.id) room else it })
    fun remove(id: String): TourDocument = copy(scenes=scenes.filter{it.id!=id}.map{it.copy(points=it.points.filter{p->p.targetSceneId!=id})},defaultSceneId=if(defaultSceneId==id)scenes.firstOrNull{it.id!=id}?.id.orEmpty() else defaultSceneId,mapPositions=mapPositions-id,handledCaptureIds=(handledCaptureIds+id).distinct())
    fun assets() = (listOfNotNull(branding.logoAssetPath)+scenes.flatMap{listOf(it.assetPath)+it.points.mapNotNull{p->p.assetPath}}).filter{it.isNotBlank()}.distinct()
    fun replaceAsset(local: String, remote: String) = copy(branding=branding.copy(logoAssetPath=if(branding.logoAssetPath==local)remote else branding.logoAssetPath),scenes=scenes.map{s->s.copy(assetPath=if(s.assetPath==local)remote else s.assetPath,points=s.points.map{p->if(p.assetPath==local)p.copy(assetPath=remote)else p})})
    fun unreachable(): List<TourRoom> {
        val visited=mutableSetOf<String>();val queue=ArrayDeque<String>();queue.add(defaultSceneId)
        while(queue.isNotEmpty()){val id=queue.removeFirst();if(visited.add(id))scenes.find{it.id==id}?.points?.filter{it.enabled&&it.kind=="scene"}?.mapNotNull{it.targetSceneId}?.forEach(queue::add)}
        return scenes.filter{it.id !in visited}
    }
    fun publicationErrors(): List<String> = buildList {
        if(title.isBlank())add("Name your tour.")
        if(scenes.isEmpty())add("Add at least one room.")
        if(scenes.none{it.id==defaultSceneId})add("Choose an entrance room.")
        for(s in scenes){if(s.name.isBlank())add("Name every room.");if(s.assetPath.isBlank())add("${s.name} needs a panorama.");for(p in s.points.filter{it.enabled}){
            if(p.label.isBlank())add("Name every point.")
            if(p.kind=="scene"&&scenes.none{it.id==p.targetSceneId})add("${p.label} needs a destination.")
            if(p.kind in listOf("image","video")&&p.assetPath.isNullOrBlank()&&p.url.isNullOrBlank())add("${p.label} needs media.")
            if(p.kind in listOf("external","booking","menu","contact")&&p.url.isNullOrBlank())add("${p.label} needs a link.")
            if(!p.url.isNullOrBlank()&&!safeTourUrl(p.url,p.kind=="contact"))add("${p.label} has an invalid link.")
        }}
    }
}
fun safeTourUrl(value: String, contact: Boolean = false) = runCatching { java.net.URI(value).scheme?.lowercase() in if(contact)listOf("http","https","mailto","tel")else listOf("http","https") }.getOrDefault(false)

/** Matches the native perspective ray shader; rejects points behind the camera. */
object TourProjection {
    fun point(yaw: Float, pitch: Float, view: TourView, width: Float, height: Float): Pair<Float,Float>? {
        val y=Math.toRadians((yaw-view.yaw).toDouble());val p=Math.toRadians(pitch.toDouble());val tilt=Math.toRadians(view.pitch.toDouble())
        val x=sin(y)*cos(p);val up=sin(p)*cos(tilt)-cos(y)*cos(p)*sin(tilt);val depth=sin(p)*sin(tilt)+cos(y)*cos(p)*cos(tilt)
        if(depth<=.001||width<=0||height<=0)return null
        val tangent=tan(Math.toRadians(view.fov/2.0));val nx=x/depth/tangent/(width/height);val ny=up/depth/tangent
        if(abs(nx)>1.1||abs(ny)>1.1)return null
        return ((nx+1)*width/2).toFloat() to ((1-ny)*height/2).toFloat()
    }
}

class TourHistory {
    private val undo=ArrayDeque<TourDocument>();private val redo=ArrayDeque<TourDocument>()
    val canUndo get()=undo.isNotEmpty();val canRedo get()=redo.isNotEmpty()
    fun record(before: TourDocument){undo.addLast(before);if(undo.size>50)undo.removeFirst();redo.clear()}
    fun undo(current: TourDocument): TourDocument?=if(undo.isEmpty())null else undo.removeLast().also{redo.addLast(current)}
    fun redo(current: TourDocument): TourDocument?=if(redo.isEmpty())null else redo.removeLast().also{undo.addLast(current)}
}
