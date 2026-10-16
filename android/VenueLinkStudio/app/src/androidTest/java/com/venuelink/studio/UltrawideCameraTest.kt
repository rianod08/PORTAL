package com.venuelink.studio

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.venuelink.studio.ui.components.UltrawideCamera
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class UltrawideCameraTest {
    @Test fun capturesJpegUsingTheWidestBackLens() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val camera = UltrawideCamera(context)
        val lens = camera.lens()
        assertNotNull("A back ultrawide lens is required for 12-photo mode", lens)
        assertTrue(lens!!.horizontalFovDegrees >= 90.0)
        val output = File(context.cacheDir, "ultrawide-camera-test.jpg")
        val done = CountDownLatch(1)
        var captured: Result<com.venuelink.studio.ui.components.ArCaptureResult>? = null
        camera.capture(output, poseAtShutter = { Result.success(FloatArray(16).also { for (index in 0..3) it[index * 5] = 1f }) }) {
            captured = it
            done.countDown()
        }
        assertTrue("Ultrawide still did not finish", done.await(20, TimeUnit.SECONDS))
        val result = captured!!.getOrThrow()
        assertTrue(output.length() > 10_000)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(output.path, bounds)
        assertEquals(result.width, bounds.outWidth)
        assertEquals(result.height, bounds.outHeight)
        assertTrue(result.width > 1000 && result.height > 1000)
        output.delete()
    }
}
