package com.venuelink.studio

import com.venuelink.studio.ui.components.CameraCalibration
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CameraCalibrationTest {
    @Test fun usesMeasuredS22IntrinsicsInsteadOfPhysicalFocalApproximation() {
        val measured = floatArrayOf(1642.0322f,1640.0105f,2017.1171f,1493.8297f,0f)
        val actual = CameraCalibration.jpegIntrinsics(measured,4032,3024,4032,3024,2.2f,5.6448f,4.2336f)
        assertArrayEquals(measured.copyOf(4),actual,.001f)
    }
    @Test fun scalesPrincipalPointAndAccountsForAspectCrop() {
        val actual = CameraCalibration.jpegIntrinsics(floatArrayOf(1000f,1000f,2000f,1500f),4000,3000,2000,1000,2f,5f,4f)
        assertArrayEquals(floatArrayOf(500f,500f,1000f,500f),actual,.001f)
    }
    @Test fun devicesWithoutCalibrationRetainPhysicalFallback() {
        val actual = CameraCalibration.jpegIntrinsics(null,4000,3000,400,300,2f,5f,3.75f)
        assertArrayEquals(floatArrayOf(160f,160f,200f,150f),actual,.001f)
    }
}
