package com.venuelink.studio

import com.venuelink.studio.tour.*
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Test

class TourEditorTest {
    private fun fixture()=TourDocument(title="Venue",defaultSceneId="a",scenes=listOf(TourRoom("a","Entrance","a.jpg",points=listOf(TourPoint("link","Dining","scene",targetSceneId="b"))),TourRoom("b","Dining","b.jpg")),handledCaptureIds=listOf("a","b"))
    @Test fun seamPointIsVisibleAndBehindPointIsHidden(){val p=TourProjection.point(-179f,0f,TourView(179f,0f,95f),1000f,1000f);assertNotNull(p);assertTrue(p!!.first>500);assertNull(TourProjection.point(180f,0f,TourView(),1000f,1000f))}
    @Test fun ceilingAndFloorUseTheSameCameraFrame(){for(pitch in listOf(-89f,89f)){val p=TourProjection.point(20f,pitch,TourView(20f,pitch,95f),1000f,1000f)!!;assertEquals(500f,p.first,.01f);assertEquals(500f,p.second,.01f)}}
    @Test fun positiveYawIsRightAndPositivePitchIsUp(){val right=TourProjection.point(20f,0f,TourView(),1000f,1000f)!!;val up=TourProjection.point(0f,20f,TourView(),1000f,1000f)!!;assertTrue(right.first>500);assertTrue(up.second<500)}
    @Test fun removalCleansIncomingLinksAndDoesNotForgetTheCapture(){val d=fixture().remove("b");assertTrue(d.scenes.first().points.isEmpty());assertTrue("b" in d.handledCaptureIds);assertEquals("b",fixture().remove("a").defaultSceneId)}
    @Test fun directedLinksAndHiddenLinksDetermineReachability(){assertTrue(fixture().unreachable().isEmpty());val d=fixture();assertEquals(listOf("b"),d.room(d.scenes.first().copy(points=d.scenes.first().points.map{it.copy(enabled=false)})).unreachable().map{it.id})}
    @Test fun historyKeepsFiftyActionsAndClearsRedoOnNewEdit(){val h=TourHistory();var d=fixture();repeat(60){h.record(d);d=d.copy(title="Edit $it")};var undone=0;while(h.canUndo){d=h.undo(d)!!;undone++};assertEquals(50,undone);assertTrue(h.canRedo);h.record(d);assertFalse(h.canRedo)}
    @Test fun mediaResolutionPreservesBrandingMapAndOtherPoints(){val d=fixture().copy(branding=TourBranding(logoAssetPath="local:logo"),mapPositions=mapOf("a" to MapPosition(.2f,.4f)));val loaded=tourJson.decodeFromString<TourDocument>(tourJson.encodeToString(d.replaceAsset("local:logo","owner/tour/logo.png")));assertEquals("owner/tour/logo.png",loaded.branding.logoAssetPath);assertEquals(.2f,loaded.mapPositions["a"]!!.x);assertEquals("b",loaded.scenes.first().points.first().targetSceneId)}
    @Test fun oldDocumentsKeepSavedViewsAndUseDefaultsForNewFields(){val d=tourJson.decodeFromString<TourDocument>("""{"version":1,"title":"Old","description":"","defaultSceneId":"a","scenes":[{"id":"a","name":"Old room","assetPath":"a.jpg","initialView":{"yaw":42,"pitch":5,"fov":80},"points":[]}]}""");assertEquals(80f,d.scenes.first().initialView.fov);assertEquals(95f,TourRoom(name="New",assetPath="x").initialView.fov);assertTrue(d.handledCaptureIds.isEmpty())}
    @Test fun publicationRequiresDestinationsMediaAndSafeLinks(){val d=fixture();assertTrue(d.publicationErrors().isEmpty());assertTrue(d.room(d.scenes.first().copy(points=listOf(TourPoint(label="Video",kind="video")))).publicationErrors().isNotEmpty());assertFalse(safeTourUrl("javascript:alert(1)"));assertTrue(safeTourUrl("https://example.com"))}
}
