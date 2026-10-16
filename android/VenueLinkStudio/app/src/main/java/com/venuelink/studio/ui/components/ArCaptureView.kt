package com.venuelink.studio.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.view.Surface
import android.util.Log
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import com.venuelink.studio.capture.CaptureGeometry
import com.venuelink.studio.data.media.YuvJpegConverter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

data class ArFrameSnapshot(
    val tracking: Boolean,
    val trackingFailureReason: String = "",
    val poseMatrix: FloatArray,
    val timestampNanos: Long,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val projectionMatrix: FloatArray = FloatArray(16),
    val intrinsics: FloatArray = FloatArray(4),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
)

data class ArCaptureResult(
    val file: File,
    val cameraPoseMatrix: FloatArray,
    val intrinsics: FloatArray,
    val width: Int,
    val height: Int,
    val timestampNanos: Long,
)

class ArCaptureView(context: Context, private val useUltrawideStill: Boolean = false) : FrameLayout(context) {
    var onFrame: (ArFrameSnapshot) -> Unit = {}
    var onStatus: (String) -> Unit = {}
    var onCaptureResult: (Result<ArCaptureResult>) -> Unit = {}

    private val renderer = ArRenderer()
    private val sensorPoseBridge = SensorPoseBridge(context)
    private val surfaceView = GLSurfaceView(context)
    private var session: Session? = null
    private var installRequested = true
    private var resumed = false
    private val ultrawideCamera = if (useUltrawideStill) UltrawideCamera(context) else null
    private var switchingCamera = false

    init {
        surfaceView.preserveEGLContextOnPause = true
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.setRenderer(renderer)
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        addView(surfaceView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun onHostResume() {
        resumed = true
        sensorPoseBridge.start()
        startOrResumeSession()
    }

    fun onHostPause() {
        resumed = false
        sensorPoseBridge.stop()
        surfaceView.onPause()
        session?.pause()
    }

    fun close() {
        onHostPause()
        renderer.clearSession()
        session?.close()
        session = null
    }

    fun captureNextFrame(targetFile: File, stillAtShutter: () -> Boolean = { true }) {
        if (!useUltrawideStill) {
            renderer.pendingCapture.set(CaptureRequest(targetFile, System.currentTimeMillis()))
            return
        }
        if (switchingCamera) return
        val pose = renderer.latestPhysicalPose.get()?.copyOf()
        val arSession = session
        if (pose == null || arSession == null) {
            onCaptureResult(Result.failure(IllegalStateException("Wait for AR tracking before capturing.")))
            return
        }
        switchingCamera = true
        onStatus("Keep the phone still until the ultrawide photo is taken…")
        try {
            surfaceView.onPause()
            arSession.pause()
            ultrawideCamera!!.capture(targetFile, poseAtShutter = { timestampNanos ->
                val atExposure = sensorPoseBridge.estimateAt(timestampNanos)?.physical
                    ?: return@capture Result.failure(IllegalStateException("Could not verify the phone direction at the shutter. Retake this dot."))
                if (!stillAtShutter() || CaptureGeometry.rotationDegrees(pose, atExposure) > 3f) {
                    Result.failure(IllegalStateException("The phone moved while the ultrawide camera opened. Keep it still until capture finishes, then retake this dot."))
                } else {
                    Result.success(atExposure)
                }
            }) { result ->
                post {
                    switchingCamera = false
                    if (resumed) {
                        try {
                            surfaceView.onResume()
                            arSession.resume()
                        } catch (error: Exception) {
                            onCaptureResult(Result.failure(error))
                            return@post
                        }
                    }
                    onCaptureResult(result)
                }
            }
        } catch (error: Exception) {
            switchingCamera = false
            if (resumed) try { surfaceView.onResume(); arSession.resume() } catch (_: Exception) {}
            onCaptureResult(Result.failure(error))
        }
    }

    fun recalibrateRequestFrame() {
        renderer.forceNextFrame = true
    }

    private fun startOrResumeSession() {
        if (!resumed) return
        val activity = context.findActivity()
        if (activity == null) {
            onStatus("Guided AR capture needs an Activity context.")
            return
        }
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            onStatus("Camera permission is required for AR guided capture.")
            return
        }
        val availability = ArCoreApk.getInstance().checkAvailability(context)
        if (availability.isTransient) {
            onStatus("Checking AR support...")
            postDelayed({ startOrResumeSession() }, 220)
            return
        }
        if (!availability.isSupported) {
            onStatus("This phone does not support AR guided capture. Import a 360 image instead.")
            return
        }
        try {
            if (session == null) {
                when (ArCoreApk.getInstance().requestInstall(activity, installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = false
                        onStatus("Install or update Google Play Services for AR, then return here.")
                        return
                    }
                    ArCoreApk.InstallStatus.INSTALLED -> Unit
                }
                session = Session(context).also { arSession ->
                    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    cameraManager.cameraIdList.forEach { cameraId ->
                        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                        Log.i("PortalCameraConfig", "Camera2 cameraId=$cameraId facing=${characteristics.get(CameraCharacteristics.LENS_FACING)} focalLengths=${characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.contentToString()} sensorSize=${characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)} physicalIds=${characteristics.physicalCameraIds}")
                    }
                    val supportedConfigs = arSession.getSupportedCameraConfigs(com.google.ar.core.CameraConfigFilter(arSession))
                    supportedConfigs.forEach { cameraConfig ->
                        val characteristics = cameraManager.getCameraCharacteristics(cameraConfig.cameraId)
                        val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.contentToString()
                        val sensorSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
                        Log.i("PortalCameraConfig", "ARCore cameraId=${cameraConfig.cameraId} imageSize=${cameraConfig.imageSize} focalLengths=$focalLengths sensorSize=$sensorSize")
                    }
                    supportedConfigs
                        .maxByOrNull { it.imageSize.width * it.imageSize.height }
                        ?.let {
                            Log.i("PortalCameraConfig", "Selected ARCore cameraId=${it.cameraId} imageSize=${it.imageSize}")
                            arSession.cameraConfig = it
                        }
                    val config = Config(arSession).apply {
                        updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                        focusMode = Config.FocusMode.AUTO
                    }
                    arSession.configure(config)
                    surfaceView.queueEvent { renderer.attachSession(arSession) }
                }
            }
            surfaceView.onResume()
            session?.resume()
            onStatus("Move your phone until the target is centered.")
        } catch (exception: UnavailableUserDeclinedInstallationException) {
            onStatus("AR guided capture needs Google Play Services for AR. Import a 360 image instead.")
        } catch (exception: UnavailableDeviceNotCompatibleException) {
            onStatus("This phone does not support AR guided capture. Import a 360 image instead.")
        } catch (exception: UnavailableArcoreNotInstalledException) {
            onStatus("Google Play Services for AR is not installed.")
        } catch (exception: UnavailableApkTooOldException) {
            onStatus("Google Play Services for AR needs an update.")
        } catch (exception: UnavailableSdkTooOldException) {
            onStatus("This build needs a newer ARCore SDK.")
        } catch (exception: CameraNotAvailableException) {
            onStatus("The camera is not available. Close other camera apps and try again.")
        } catch (exception: Exception) {
            onStatus("AR guided capture could not start. Import a 360 image instead.")
        }
    }

    private inner class ArRenderer : GLSurfaceView.Renderer {
        val latestPhysicalPose = AtomicReference<FloatArray?>()
        private var session: Session? = null
        var forceNextFrame: Boolean = false
        val pendingCapture = AtomicReference<CaptureRequest?>()
        private var textureId = 0
        private var program = 0
        private var positionAttrib = 0
        private var texCoordAttrib = 0
        private var textureUniform = 0
        private var viewportWidth = 1
        private var viewportHeight = 1
        private val quadCoords = floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)
        private val texCoords = floatArrayOf(0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f)
        private val quadBuffer = directFloatBuffer(quadCoords)
        private val texBuffer = directFloatBuffer(texCoords)
        private var lastGeometryTimestamp = 0L
        private var postedFirstCameraFrame = false

        fun attachSession(arSession: Session) {
            session = arSession
            if (textureId != 0) {
                arSession.setCameraTextureName(textureId)
                arSession.setDisplayGeometry(displayRotation(), viewportWidth, viewportHeight)
            }
        }

        fun clearSession() {
            session = null
        }

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            textureId = createCameraTexture()
            program = createProgram(VertexShader, FragmentShader)
            positionAttrib = GLES20.glGetAttribLocation(program, "a_Position")
            texCoordAttrib = GLES20.glGetAttribLocation(program, "a_TexCoord")
            textureUniform = GLES20.glGetUniformLocation(program, "u_Texture")
            session?.setCameraTextureName(textureId)
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            viewportWidth = width.coerceAtLeast(1)
            viewportHeight = height.coerceAtLeast(1)
            session?.setDisplayGeometry(displayRotation(), viewportWidth, viewportHeight)
        }

        override fun onDrawFrame(gl: GL10?) {
            val arSession = session ?: return
            if (textureId == 0) return
            try {
                arSession.setCameraTextureName(textureId)
                arSession.setDisplayGeometry(displayRotation(), viewportWidth, viewportHeight)
                val frame = arSession.update()
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
                drawCameraBackground(frame)
                val camera = frame.camera
                if (frame.timestamp != 0L && !postedFirstCameraFrame) {
                    postedFirstCameraFrame = true
                    post { onStatus("Camera live. Move slowly so AR tracking can lock on.") }
                }
                val matrix = FloatArray(16)
                camera.pose.toMatrix(matrix, 0)
                val displayMatrix = FloatArray(16)
                camera.displayOrientedPose.toMatrix(displayMatrix, 0)
                val arTracking = camera.trackingState == TrackingState.TRACKING
                if (arTracking) {
                    val androidSensor = FloatArray(16)
                    frame.androidSensorPose.toMatrix(androidSensor, 0)
                    sensorPoseBridge.observeTracked(displayMatrix, matrix, androidSensor, frame.timestamp)
                }
                val motionEstimate = if (!arTracking && frame.timestamp != 0L) sensorPoseBridge.estimate() else null
                val effectiveDisplay = motionEstimate?.display ?: displayMatrix
                val effectivePhysical = motionEstimate?.physical ?: matrix
                if (arTracking || motionEstimate != null) latestPhysicalPose.set(effectivePhysical.copyOf())
                val trackingReason = if (motionEstimate != null) "MOTION_SENSORS" else camera.trackingFailureReason.name
                val projection = FloatArray(16).also { camera.getProjectionMatrix(it, 0, 0.1f, 100f) }
                val imageIntrinsics = camera.imageIntrinsics
                val intrinsics = floatArrayOf(imageIntrinsics.focalLength[0], imageIntrinsics.focalLength[1], imageIntrinsics.principalPoint[0], imageIntrinsics.principalPoint[1])
                val imageWidth = imageIntrinsics.imageDimensions[0]
                val imageHeight = imageIntrinsics.imageDimensions[1]
                val timestamp = frame.timestamp
                val width = viewportWidth
                val height = viewportHeight
                post {
                    onFrame(
                        ArFrameSnapshot(
                            tracking = arTracking || motionEstimate != null,
                            trackingFailureReason = trackingReason,
                            projectionMatrix = projection,
                            intrinsics = intrinsics,
                            imageWidth = imageWidth,
                            imageHeight = imageHeight,
                            poseMatrix = effectiveDisplay,
                            timestampNanos = timestamp,
                            viewportWidth = width,
                            viewportHeight = height,
                        ),
                    )
                }
                if (arTracking || forceNextFrame) {
                    forceNextFrame = false
                    handlePendingCapture(frame, matrix)
                }
            } catch (exception: CameraNotAvailableException) {
                post { onStatus("The AR camera is not available. Try reopening capture.") }
            } catch (exception: Exception) {
                post { onStatus("AR tracking paused. Move slowly and keep the venue well lit.") }
            }
        }

        private fun drawCameraBackground(frame: com.google.ar.core.Frame) {
            if (frame.hasDisplayGeometryChanged() || frame.timestamp != lastGeometryTimestamp) {
                frame.transformCoordinates2d(
                    Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                    quadCoords,
                    Coordinates2d.TEXTURE_NORMALIZED,
                    texCoords,
                )
                texBuffer.clear()
                texBuffer.put(texCoords)
                texBuffer.position(0)
                lastGeometryTimestamp = frame.timestamp
            }
            GLES20.glDisable(GLES20.GL_DEPTH_TEST)
            GLES20.glUseProgram(program)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glUniform1i(textureUniform, 0)
            GLES20.glEnableVertexAttribArray(positionAttrib)
            quadBuffer.position(0)
            GLES20.glVertexAttribPointer(positionAttrib, 2, GLES20.GL_FLOAT, false, 0, quadBuffer)
            GLES20.glEnableVertexAttribArray(texCoordAttrib)
            texBuffer.position(0)
            GLES20.glVertexAttribPointer(texCoordAttrib, 2, GLES20.GL_FLOAT, false, 0, texBuffer)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(positionAttrib)
            GLES20.glDisableVertexAttribArray(texCoordAttrib)
        }

        private fun handlePendingCapture(frame: com.google.ar.core.Frame, poseMatrix: FloatArray) {
            val request = pendingCapture.get() ?: return
            if (System.currentTimeMillis() - request.startedAtMillis > 2_500L) {
                if (pendingCapture.compareAndSet(request, null)) {
                    post { onCaptureResult(Result.failure(IllegalStateException("AR camera frame was not ready. Try again."))) }
                }
                return
            }
            try {
                var imageWidth = 0
                var imageHeight = 0
                var timestamp = 0L
                frame.acquireCameraImage().use { image ->
                    imageWidth = image.width
                    imageHeight = image.height
                    timestamp = image.timestamp
                    YuvJpegConverter.saveImageAsJpeg(image, request.targetFile)
                }
                val intrinsics = frame.camera.imageIntrinsics.let { floatArrayOf(it.focalLength[0], it.focalLength[1], it.principalPoint[0], it.principalPoint[1]) }
                if (pendingCapture.compareAndSet(request, null)) {
                    post { onCaptureResult(Result.success(ArCaptureResult(request.targetFile, poseMatrix.copyOf(), intrinsics, imageWidth, imageHeight, timestamp))) }
                }
            } catch (exception: NotYetAvailableException) {
                // Keep the request alive for the next AR frame.
            } catch (exception: Exception) {
                if (pendingCapture.compareAndSet(request, null)) {
                    post { onCaptureResult(Result.failure(exception)) }
                }
            }
        }
    }

    private fun displayRotation(): Int = display?.rotation ?: Surface.ROTATION_0

    private fun createCameraTexture(): Int {
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        return textures[0]
    }

    private fun directFloatBuffer(values: FloatArray): FloatBuffer =
        ByteBuffer.allocateDirect(values.size * java.lang.Float.BYTES)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(values)
                position(0)
            }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        return GLES20.glCreateProgram().also { program ->
            GLES20.glAttachShader(program, vertex)
            GLES20.glAttachShader(program, fragment)
            GLES20.glLinkProgram(program)
        }
    }

    private fun compileShader(type: Int, source: String): Int =
        GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
        }

    private fun Context.findActivity(): Activity? =
        when (this) {
            is Activity -> this
            is ContextWrapper -> baseContext.findActivity()
            else -> null
        }

    private data class CaptureRequest(val targetFile: File, val startedAtMillis: Long)

    companion object {
        private const val VertexShader = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() {
                gl_Position = a_Position;
                v_TexCoord = a_TexCoord;
            }
        """

        private const val FragmentShader = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES u_Texture;
            varying vec2 v_TexCoord;
            void main() {
                gl_FragColor = texture2D(u_Texture, v_TexCoord);
            }
        """
    }
}
