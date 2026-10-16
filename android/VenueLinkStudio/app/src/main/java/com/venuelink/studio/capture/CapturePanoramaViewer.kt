package com.venuelink.studio.capture

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.tan
import com.venuelink.studio.tour.TourView

private const val DefaultPhotosphereFov = 95f

@Composable
fun CapturePanoramaViewer(session: CaptureSession, onBack: () -> Unit) {
    var error by remember(session.id) { mutableStateOf("") }
    val context = LocalContext.current
    val photo = CapturePipeline.localPanorama(session)
    val viewer = remember(context, session.id, photo?.path) {
        photo?.let { PhotosphereView(context, it) { message -> error = message } }
    }
    val lifecycle = LocalLifecycleOwner.current
    BackHandler(onBack = onBack)
    DisposableEffect(viewer, lifecycle) {
        val target = viewer
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> target?.onResume()
                Lifecycle.Event.ON_PAUSE -> target?.onPause()
                else -> Unit
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        if (lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) target?.onResume()
        onDispose { lifecycle.lifecycle.removeObserver(observer); target?.onPause() }
    }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (viewer != null) AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { viewer }
        ) else Text("This panorama has not finished downloading. Go back to retry.", color = Color.White, modifier = Modifier.align(Alignment.Center).padding(24.dp))
        Column(Modifier.align(Alignment.TopStart).statusBarsPadding().padding(16.dp).background(Color.Black.copy(alpha = .65f)).padding(12.dp)) {
            TextButton(onClick = onBack) { Text("← Saved captures", color = Color.White) }
            Text(session.name, color = Color.White, style = MaterialTheme.typography.headlineSmall)
            Text("360° panorama · saved on this phone", color = Color.White)
        }
        if (error.isNotBlank()) Text(error, color = Color.White, modifier = Modifier.align(Alignment.Center).background(Color.Black).padding(24.dp))
        Column(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Drag to look around · pinch to zoom", color = Color.White, modifier = Modifier.background(Color.Black.copy(alpha = .65f)).padding(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { viewer?.zoom(.8f) }) { Text("＋") }
                OutlinedButton(onClick = { viewer?.reset() }) { Text("Reset view", color = Color.White) }
                Button(onClick = { viewer?.zoom(1.25f) }) { Text("−") }
            }
        }
    }
}

/** Perspective rays sample an equirectangular texture, including both poles. */
class PhotosphereView(context: Context, photo: File, private val initialView: TourView = TourView(), private val onViewChanged: (TourView)->Unit = {}, onError: (String) -> Unit) : GLSurfaceView(context) {
    private val sphere = SphereRenderer(photo) { message -> post { onError(message) } }.apply{yaw=initialView.yaw;pitch=initialView.pitch;fov=initialView.fov}
    private var lastX = 0f
    private var lastY = 0f
    private val scale = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean { zoom(1f / detector.scaleFactor); return true }
    })

    init {
        contentDescription = "Interactive 360 panorama. Drag to look around; pinch to zoom."
        keepScreenOn = true
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(sphere)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun currentView() = TourView(sphere.yaw,sphere.pitch,sphere.fov)
    fun zoom(factor: Float) { sphere.fov = (sphere.fov * factor).coerceIn(35f, 110f);onViewChanged(currentView()); requestRender() }
    fun reset() { sphere.yaw = initialView.yaw; sphere.pitch = initialView.pitch; sphere.fov = initialView.fov;onViewChanged(currentView()); requestRender() }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scale.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_MOVE && event.pointerCount == 1 && !scale.isInProgress) {
            sphere.yaw = (sphere.yaw - (event.x - lastX) * sphere.fov / height.coerceAtLeast(1)) % 360f
            sphere.pitch = (sphere.pitch + (event.y - lastY) * sphere.fov / height.coerceAtLeast(1)).coerceIn(-89f, 89f)
            onViewChanged(currentView())
            requestRender()
        }
        lastX = event.x; lastY = event.y
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}

private class SphereRenderer(private val photo: File, private val onError: (String) -> Unit) : GLSurfaceView.Renderer {
    @Volatile var yaw = 0f
    @Volatile var pitch = 0f
    @Volatile var fov = DefaultPhotosphereFov
    private var aspect = 1f
    private var program = 0
    private var texture = 0
    private var position = 0
    private var angles = 0
    private var projection = 0
    private val vertices = ByteBuffer.allocateDirect(8 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        try {
            program = GLES20.glCreateProgram()
            val vertex = shader(GLES20.GL_VERTEX_SHADER, "attribute vec2 aPosition; varying vec2 screen; void main(){screen=aPosition;gl_Position=vec4(aPosition,0.0,1.0);}")
            val fragment = shader(GLES20.GL_FRAGMENT_SHADER, """
                precision highp float;
                varying vec2 screen;
                uniform sampler2D panorama;
                uniform vec2 angles;
                uniform vec2 projection;
                void main(){
                    vec3 d=normalize(vec3(screen.x*projection.x*projection.y,screen.y*projection.y,-1.0));
                    float cy=cos(angles.x),sy=sin(angles.x),cp=cos(angles.y),sp=sin(angles.y);
                    d=vec3(d.x,cp*d.y-sp*d.z,sp*d.y+cp*d.z);
                    d=vec3(cy*d.x-sy*d.z,d.y,sy*d.x+cy*d.z);
                    vec2 uv=vec2(atan(d.x,-d.z)/6.28318530718+0.5,0.5-asin(clamp(d.y,-1.0,1.0))/3.14159265359);
                    gl_FragColor=texture2D(panorama,uv);
                }
            """.trimIndent())
            GLES20.glAttachShader(program, vertex); GLES20.glAttachShader(program, fragment); GLES20.glLinkProgram(program)
            val linked = IntArray(1); GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
            check(linked[0] != 0) { GLES20.glGetProgramInfoLog(program) }
            GLES20.glDeleteShader(vertex); GLES20.glDeleteShader(fragment)
            position = GLES20.glGetAttribLocation(program, "aPosition")
            angles = GLES20.glGetUniformLocation(program, "angles")
            projection = GLES20.glGetUniformLocation(program, "projection")
            val limit = IntArray(1); GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, limit, 0)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(photo.path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > limit[0] || bounds.outHeight / sample > limit[0]) sample *= 2
            val bitmap = BitmapFactory.decodeFile(photo.path, BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }) ?: error("Could not open the panorama.")
            val names = IntArray(1); GLES20.glGenTextures(1, names, 0); texture = names[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0); bitmap.recycle()
        } catch (_: Exception) { program = 0; onError("The 360 viewer could not open this panorama. Go back and try again.") }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height); aspect = width.toFloat() / height.coerceAtLeast(1)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        if (program == 0) return
        GLES20.glUseProgram(program)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0); GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture)
        GLES20.glUniform2f(angles, Math.toRadians(yaw.toDouble()).toFloat(), Math.toRadians(pitch.toDouble()).toFloat())
        GLES20.glUniform2f(projection, aspect, tan(Math.toRadians(fov / 2.0)).toFloat())
        GLES20.glEnableVertexAttribArray(position); GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun shader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type); GLES20.glShaderSource(shader, source); GLES20.glCompileShader(shader)
        val compiled = IntArray(1); GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        check(compiled[0] != 0) { GLES20.glGetShaderInfoLog(shader) }
        return shader
    }
}
