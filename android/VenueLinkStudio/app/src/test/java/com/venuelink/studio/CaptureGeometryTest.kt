package com.venuelink.studio
import com.venuelink.studio.capture.CaptureGeometry
import org.junit.Assert.*
import org.junit.Test
class CaptureGeometryTest {
    private val identity=floatArrayOf(1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f,0f,0f,0f,0f,1f)
    @Test fun ultrawidePatternHasFifteenViewsAcrossThreeRings(){val targets=CaptureGeometry.ultrawideTargets();assertEquals(15,targets.size);assertEquals(15,targets.map{it.id}.toSet().size);assertEquals(7,targets.count{it.pitch==0f});assertEquals(4,targets.count{it.pitch==55f});assertEquals(4,targets.count{it.pitch== -55f});assertEquals(0f,targets.first().yaw,.001f)}
    @Test fun targetsCoverPolesAndBeginNearTheCameraHeading(){val a=CaptureGeometry.targets(1920,1080,1200f,1200f);val b=CaptureGeometry.targets(1920,1080,1200f,1200f);assertEquals(a.map{it.id},b.map{it.id});assertTrue(a.any{it.pitch==90f});assertTrue(a.any{it.pitch== -90f});assertTrue(a.size in 8..200);assertEquals(a.size,a.map{it.id}.toSet().size);assertTrue(kotlin.math.abs(a.first().yaw)<=25f)}
    @Test fun narrowCameraNeedsMoreOverlapTargets(){assertTrue(CaptureGeometry.targets(1920,1080,1600f,1600f).size>CaptureGeometry.targets(1920,1080,800f,800f).size)}
    @Test fun movementAndRotationAreMeasured(){val moved=identity.copyOf();moved[12]=.3f;assertEquals(.3f,CaptureGeometry.translation(identity,moved),.001f);assertEquals(0f,CaptureGeometry.rotationDegrees(identity,identity),.001f);val turned=identity.copyOf();turned[0]=0f;turned[2]= -1f;turned[8]=1f;turned[10]=0f;assertEquals(90f,CaptureGeometry.rotationDegrees(identity,turned),.001f)}
    @Test fun cameraMustRotateAroundLensInsteadOfBody(){val close=identity.copyOf();close[12]=.1f;assertTrue(CaptureGeometry.isNearStartingPoint(identity,close));val swung=identity.copyOf();swung[12]=.69f;assertFalse(CaptureGeometry.isNearStartingPoint(identity,swung))}
    @Test fun savedPoseNeverHidesActualCameraMovement(){val raw=identity.copyOf();raw[0]=0f;raw[2]= -1f;raw[8]=1f;raw[10]=0f;raw[12]=1.2f;raw[13]=.8f;val saved=CaptureGeometry.rotationalPanoramaPose(identity,raw);assertArrayEquals(raw,saved,.001f);assertNotSame(raw,saved)}
}
