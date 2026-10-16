package com.venuelink.studio.ui.components

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.venuelink.studio.data.model.Hotspot
import com.venuelink.studio.data.model.HotspotType
import com.venuelink.studio.data.model.TourScene
import com.venuelink.studio.data.model.TourTheme
import com.venuelink.studio.domain.panorama.SphericalMath

fun String.toColor(fallback: Color = Color(0xFF38BDF8)): Color =
    runCatching { Color(android.graphics.Color.parseColor(this)) }.getOrDefault(fallback)

@Composable
fun LiquidBackground(theme: TourTheme? = null, content: @Composable BoxScope.() -> Unit) {
    val start = theme?.backgroundColor?.toColor() ?: Color(0xFFEEF7FB)
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(start, Color(0xFFEDE9FE), Color(0xFFDDFCF6))))
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.White.copy(alpha = 0.22f), radius = size.width * 0.46f, center = Offset(size.width * 0.1f, size.height * 0.06f))
            drawCircle(Color(0xFF38BDF8).copy(alpha = 0.16f), radius = size.width * 0.38f, center = Offset(size.width * 0.94f, size.height * 0.22f))
            drawCircle(Color(0xFFF5C451).copy(alpha = 0.14f), radius = size.width * 0.5f, center = Offset(size.width * 0.2f, size.height * 0.94f))
        }
        content()
    }
}

@Composable
fun GlassCard(modifier: Modifier = Modifier, dark: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.cardColors(containerColor = if (dark) Color(0xB3111827) else Color(0xCCFFFFFF)),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        content = { Column(Modifier.padding(18.dp), content = content) },
    )
}

@Composable
fun GlassButton(text: String, modifier: Modifier = Modifier, primary: Boolean = false, onClick: () -> Unit) {
    val scale by animateFloatAsState(1f, label = "press")
    if (primary) {
        Button(
            modifier = modifier.height(54.dp),
            shape = RoundedCornerShape(999.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F172A)),
            onClick = onClick,
        ) { Text(text, fontWeight = FontWeight.Bold) }
    } else {
        OutlinedButton(modifier = modifier.height(54.dp), shape = RoundedCornerShape(999.dp), onClick = onClick) {
            Text(text, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun SectionTitle(title: String, body: String? = null) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0F172A))
    if (body != null) Text(body, color = Color(0xFF64748B), modifier = Modifier.padding(top = 6.dp))
}

@Composable
fun HotspotTypeChip(type: HotspotType, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(type.name) })
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThemePicker(themes: List<TourTheme>, selectedId: String, onSelect: (String) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(themes, key = { it.id }) { theme ->
            Column(
                Modifier
                    .width(154.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(theme.cardColor.toColor().copy(alpha = 0.88f))
                    .clickable { onSelect(theme.id) }
                    .padding(14.dp)
                    .animateItem()
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(22.dp).clip(CircleShape).background(theme.backgroundColor.toColor()))
                    Box(Modifier.size(22.dp).clip(CircleShape).background(theme.accentColor.toColor()))
                }
                Text(theme.name, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp), color = theme.textColor.toColor())
                Text(if (selectedId == theme.id) "Selected" else "Tap to apply", color = theme.secondaryTextColor.toColor(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun PanoramaViewer(
    scene: TourScene?,
    hotspots: List<Hotspot>,
    theme: TourTheme,
    modifier: Modifier = Modifier,
    editMode: Boolean = false,
    onTapPanorama: (Float, Float) -> Unit = { _, _ -> },
    onHotspotClick: (Hotspot) -> Unit = {},
    onViewChanged: (Float, Float) -> Unit = { _, _ -> },
) {
    var yaw by remember(scene?.id) { mutableFloatStateOf(0f) }
    var pitch by remember(scene?.id) { mutableFloatStateOf(0f) }
    val fov = 92f
    Box(
        modifier
            .clip(RoundedCornerShape(34.dp))
            .background(Color.Black)
            .pointerInput(scene?.id) {
                detectDragGestures(
                    onDrag = { change, drag ->
                        change.consume()
                        yaw = SphericalMath.normalizeYaw(yaw - drag.x * 0.14f)
                        pitch = SphericalMath.clampPitch(pitch + drag.y * 0.08f)
                        onViewChanged(yaw, pitch)
                    },
                    onDragEnd = { onViewChanged(yaw, pitch) },
                )
            }
            .clickable {
                if (editMode) onTapPanorama(yaw, pitch)
            }
    ) {
        if (scene == null) {
            Text("No 360 scene yet", color = Color.White, modifier = Modifier.align(Alignment.Center))
        } else {
            val normalizedX = ((yaw + 180f) / 360f).coerceIn(0f, 1f)
            AsyncImage(
                model = Uri.parse(scene.panoramaUri),
                contentDescription = "Interactive 360 panorama",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = 1.85f
                        scaleY = 1.2f
                        translationX = (0.5f - normalizedX) * size.width * 0.9f
                        translationY = (pitch / 89f) * size.height * 0.16f
                    },
            )
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(0.28f), Color.Transparent, Color.Black.copy(0.44f)))))
            Text("Drag to look around", color = Color.White, modifier = Modifier.align(Alignment.TopCenter).padding(top = 18.dp))
            Canvas(Modifier.fillMaxSize()) {
                hotspots.filter { it.isEnabled }.forEach { hotspot ->
                    val point = SphericalMath.yawPitchToScreenPoint(hotspot.yaw, hotspot.pitch, size.width, size.height, yaw, pitch, fov)
                    if (point != null) {
                        drawCircle(hotspot.colour.toColor().copy(alpha = 0.28f), radius = 32f, center = point, style = Fill)
                        drawCircle(hotspot.colour.toColor(), radius = 15f, center = point, style = Fill)
                    }
                }
            }
            hotspots.filter { it.isEnabled }.forEach { hotspot ->
                val density = LocalDensity.current
                Box(Modifier.fillMaxSize()) {
                    val point = SphericalMath.yawPitchToScreenPoint(hotspot.yaw, hotspot.pitch, 1000f, 1000f, yaw, pitch, fov)
                    if (point != null) {
                        TextButton(
                            onClick = { onHotspotClick(hotspot) },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = with(density) { (point.x / 1000f * 320).dp }, top = with(density) { (point.y / 1000f * 520).dp })
                        ) {
                            Text(hotspot.label, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
