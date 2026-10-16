package com.venuelink.studio.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Size
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.atan

/** A still from the physical back ultrawide lens, while ARCore's main camera is paused. */
internal class UltrawideCamera(private val context: Context) {
    private val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    data class Lens(val id: String, val size: Size, val previewSize: Size, val focalMm: Float, val sensorWidthMm: Float, val sensorHeightMm: Float, val realtimeTimestamps: Boolean, val calibrated: FloatArray?, val activeArray: Rect) {
        val horizontalFovDegrees: Double get() = Math.toDegrees(2 * atan(sensorWidthMm / (2.0 * focalMm)))
    }

    fun lens(): Lens? = manager.cameraIdList.mapNotNull { id ->
        val info = manager.getCameraCharacteristics(id)
        if (info.get(CameraCharacteristics.LENS_FACING) != CameraCharacteristics.LENS_FACING_BACK) return@mapNotNull null
        val focal = info.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.minOrNull() ?: return@mapNotNull null
        val sensor = info.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE) ?: return@mapNotNull null
        val sizes = info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.JPEG) ?: return@mapNotNull null
        val size = sizes.filter { it.width.toLong() * it.height <= 20_000_000 && abs(it.width.toDouble() / it.height - sensor.width / sensor.height) < 0.03 }
            .maxByOrNull { it.width.toLong() * it.height } ?: return@mapNotNull null
        val previewSizes = info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.getOutputSizes(ImageFormat.YUV_420_888) ?: return@mapNotNull null
        val previewSize = previewSizes.filter { it.width.toLong() * it.height <= 1_280L * 960L }
            .minByOrNull { abs(it.width - 640) + abs(it.height - 480) }
            ?: previewSizes.minByOrNull { it.width.toLong() * it.height } ?: return@mapNotNull null
        val active = info.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: Rect(0, 0, size.width, size.height)
        val calibration = info.get(CameraCharacteristics.LENS_INTRINSIC_CALIBRATION)?.copyOf()?.also {
            it[2] -= active.left; it[3] -= active.top
        }
        Lens(id, size, previewSize, focal, sensor.width, sensor.height,
            info.get(CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE) == CameraCharacteristics.SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME,
            calibration, active)
    }.maxByOrNull { it.horizontalFovDegrees }?.takeIf { it.horizontalFovDegrees >= 90.0 }

    @SuppressLint("MissingPermission")
    fun capture(file: File, poseAtShutter: (Long) -> Result<FloatArray>, onComplete: (Result<ArCaptureResult>) -> Unit) {
        val lens = try { lens() } catch (error: Exception) { onComplete(Result.failure(error)); return }
        if (lens == null) {
            onComplete(Result.failure(IllegalStateException("This phone does not expose a back ultrawide camera.")))
            return
        }
        val thread = HandlerThread("PortalUltrawideCapture").apply { start() }
        val handler = Handler(thread.looper)
        var reader: ImageReader? = null
        var previewReader: ImageReader? = null
        var device: CameraDevice? = null
        var session: CameraCaptureSession? = null
        var shutterPose: FloatArray? = null
        val finished = AtomicBoolean(false)

        fun finish(result: Result<ArCaptureResult>) {
            if (!finished.compareAndSet(false, true)) return
            try { session?.close() } catch (_: Exception) {}
            try { device?.close() } catch (_: Exception) {}
            try { reader?.close() } catch (_: Exception) {}
            try { previewReader?.close() } catch (_: Exception) {}
            handler.removeCallbacksAndMessages(null)
            thread.quitSafely()
            onComplete(result)
        }

        handler.postDelayed({ finish(Result.failure(IllegalStateException("Ultrawide camera timed out."))) }, 10_000)
        try {
            reader = ImageReader.newInstance(lens.size.width, lens.size.height, ImageFormat.JPEG, 2).also { imageReader ->
                imageReader.setOnImageAvailableListener({ source ->
                    try {
                        if (finished.get()) { source.acquireLatestImage()?.close(); return@setOnImageAvailableListener }
                        source.acquireNextImage()?.use { image ->
                            val pose = shutterPose ?: throw IllegalStateException("No verified camera pose at the shutter. Retake this dot.")
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                            file.outputStream().use { it.write(bytes) }
                            val intrinsics = CameraCalibration.jpegIntrinsics(lens.calibrated,
                                lens.activeArray.width(), lens.activeArray.height(), image.width, image.height,
                                lens.focalMm, lens.sensorWidthMm, lens.sensorHeightMm)
                            finish(Result.success(ArCaptureResult(file, pose.copyOf(), intrinsics, image.width, image.height, image.timestamp)))
                        }
                    } catch (error: Exception) { finish(Result.failure(error)) }
                }, handler)
            }
            previewReader = ImageReader.newInstance(lens.previewSize.width, lens.previewSize.height, ImageFormat.YUV_420_888, 2).also { imageReader ->
                imageReader.setOnImageAvailableListener({ source -> source.acquireLatestImage()?.close() }, handler)
            }
            manager.openCamera(lens.id, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    if (finished.get()) { camera.close(); return }
                    device = camera
                    try {
                        camera.createCaptureSession(listOf(previewReader!!.surface, reader!!.surface), object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(cameraSession: CameraCaptureSession) {
                                if (finished.get()) { cameraSession.close(); return }
                                session = cameraSession
                                try {
                                    val startedAt = SystemClock.elapsedRealtimeNanos()
                                    var previewFrames = 0
                                    var stillSubmitted = false
                                    fun takeStill() {
                                        if (finished.get() || stillSubmitted) return
                                        stillSubmitted = true
                                        val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                                            addTarget(reader!!.surface)
                                            set(CaptureRequest.JPEG_ORIENTATION, 0)
                                            set(CaptureRequest.SCALER_CROP_REGION, lens.activeArray)
                                            set(CaptureRequest.JPEG_QUALITY, 95.toByte())
                                            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                        }.build()
                                        cameraSession.capture(request, object : CameraCaptureSession.CaptureCallback() {
                                            override fun onCaptureStarted(session: CameraCaptureSession, request: CaptureRequest, timestamp: Long, frameNumber: Long) {
                                                val clockTime = if (lens.realtimeTimestamps) timestamp else SystemClock.elapsedRealtimeNanos()
                                                val pose = try { poseAtShutter(clockTime) } catch (error: Exception) { Result.failure(error) }
                                                pose.onSuccess { shutterPose = it.copyOf() }
                                                    .onFailure { finish(Result.failure(it)) }
                                            }

                                            override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: android.hardware.camera2.CaptureFailure) {
                                                finish(Result.failure(IllegalStateException("Ultrawide exposure failed. Retake this dot.")))
                                            }
                                        }, handler)
                                    }
                                    val previewRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                                        addTarget(previewReader!!.surface)
                                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
                                        set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
                                    }.build()
                                    cameraSession.setRepeatingRequest(previewRequest, object : CameraCaptureSession.CaptureCallback() {
                                        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: android.hardware.camera2.TotalCaptureResult) {
                                            if (finished.get() || stillSubmitted) return
                                            previewFrames++
                                            val ae = result.get(CaptureResult.CONTROL_AE_STATE)
                                            val af = result.get(CaptureResult.CONTROL_AF_STATE)
                                            val exposureReady = ae == null || ae == CaptureResult.CONTROL_AE_STATE_INACTIVE ||
                                                ae == CaptureResult.CONTROL_AE_STATE_CONVERGED || ae == CaptureResult.CONTROL_AE_STATE_LOCKED ||
                                                ae == CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED
                                            val focusReady = af == null || af == CaptureResult.CONTROL_AF_STATE_INACTIVE ||
                                                af == CaptureResult.CONTROL_AF_STATE_PASSIVE_FOCUSED || af == CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                                            if (previewFrames >= 3 && exposureReady && focusReady) {
                                                try { takeStill() } catch (error: Exception) { finish(Result.failure(error)) }
                                            } else if (SystemClock.elapsedRealtimeNanos() - startedAt > 3_000_000_000L) {
                                                finish(Result.failure(IllegalStateException("The ultrawide camera could not settle focus and exposure. Add light and retake this dot.")))
                                            }
                                        }
                                    }, handler)
                                } catch (error: Exception) { finish(Result.failure(error)) }
                            }
                            override fun onConfigureFailed(cameraSession: CameraCaptureSession) {
                                finish(Result.failure(IllegalStateException("Ultrawide camera could not be configured.")))
                            }
                        }, handler)
                    } catch (error: Exception) { finish(Result.failure(error)) }
                }
                override fun onDisconnected(camera: CameraDevice) {
                    finish(Result.failure(IllegalStateException("Ultrawide camera disconnected.")))
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    finish(Result.failure(CameraAccessException(error, "Ultrawide camera error $error")))
                }
            }, handler)
        } catch (error: Exception) { finish(Result.failure(error)) }
    }
}
