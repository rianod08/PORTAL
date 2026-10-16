package com.venuelink.studio.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

@Serializable
enum class TourStatus { Draft, Ready, Published }

@Serializable
enum class DotState { Pending, Active, Aiming, Captured, NeedsRetake, Skipped }

@Serializable
enum class HotspotType {
    Info, Booking, Menu, ExternalLink, Contact, Call, Email, Logo, Offer, Product, Video, Gallery, ScenePortal, Custom
}

@Serializable
enum class HotspotStyle { GlassDot, FloatingPill, LogoBadge, PulseRing, MinimalPin, NumberedPoint }

@Serializable
enum class ButtonStyle { RoundedGlass, SolidPill, OutlineGlass, FloatingCapsule }

@Serializable
enum class ValidationSeverity { Info, Warning, Error }

@Serializable
data class ValidationResult(
    val isValid: Boolean,
    val message: String? = null,
    val severity: ValidationSeverity = ValidationSeverity.Info,
)

@Serializable
data class Tour(
    val id: String = newId(),
    val name: String,
    val venueName: String,
    val venueType: String,
    val description: String = "",
    val coverImageUri: String? = null,
    val themeId: String = "opal-glass",
    val accentColor: String = "#38BDF8",
    val status: TourStatus = TourStatus.Draft,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class VenueInfo(
    val id: String = newId(),
    val tourId: String,
    val venueName: String = "",
    val shortDescription: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val website: String = "",
    val instagram: String = "",
    val bookingUrl: String = "",
    val menuUrl: String = "",
    val openingHours: String = "",
)

@Serializable
data class TourScene(
    val id: String = newId(),
    val tourId: String,
    val name: String,
    val description: String = "",
    val panoramaUri: String,
    val thumbnailUri: String? = null,
    val order: Int = 0,
    val isDefault: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class CapturedFrame(
    val id: String = newId(),
    val tourId: String,
    val sceneId: String,
    val dotId: String,
    val imageUri: String,
    val yaw: Float,
    val pitch: Float,
    val roll: Float,
    val qualityScore: Float = 0.8f,
    val blurScore: Float = 0.8f,
    val exposureScore: Float = 0.8f,
    val cameraPoseMatrix: List<Float> = emptyList(),
    val targetWorldDirection: List<Float> = emptyList(),
    val capturedAt: Long = System.currentTimeMillis(),
    val needsRetake: Boolean = false,
)

@Serializable
data class CaptureDot(
    val id: String = newId(),
    val yaw: Float,
    val pitch: Float,
    val state: DotState = DotState.Pending,
    val required: Boolean = true,
    val captureOrder: Int,
)

@Serializable
data class Hotspot(
    val id: String = newId(),
    val tourId: String,
    val sceneId: String,
    val label: String,
    val type: HotspotType,
    val yaw: Float,
    val pitch: Float,
    val icon: String = "point",
    val colour: String = "#38BDF8",
    val description: String = "",
    val buttonText: String = "",
    val targetUrl: String? = null,
    val targetSceneId: String? = null,
    val phoneNumber: String? = null,
    val email: String? = null,
    val imageUri: String? = null,
    val logoUri: String? = null,
    val offerExpiry: String? = null,
    val productPrice: String? = null,
    val isEnabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class TourTheme(
    val id: String,
    val name: String,
    val backgroundColor: String,
    val cardColor: String,
    val accentColor: String,
    val textColor: String,
    val secondaryTextColor: String,
    val hotspotStyle: HotspotStyle = HotspotStyle.GlassDot,
    val buttonStyle: ButtonStyle = ButtonStyle.RoundedGlass,
    val glassIntensity: Float = 0.72f,
    val isDark: Boolean = false,
)

@Serializable
data class ExportAsset(
    val id: String = newId(),
    val type: String,
    val localUri: String,
    val fileName: String,
    val mimeType: String,
)

@Serializable
data class TourExport(
    val version: String = "1.0",
    val exportedAt: Long = System.currentTimeMillis(),
    val tour: Tour,
    val venueInfo: VenueInfo,
    val scenes: List<TourScene>,
    val hotspots: List<Hotspot>,
    val theme: TourTheme,
    val assets: List<ExportAsset>,
)

@Serializable
data class StudioState(
    val tours: List<Tour> = emptyList(),
    val activeTourId: String? = null,
    val venueInfo: Map<String, VenueInfo> = emptyMap(),
    val scenes: Map<String, List<TourScene>> = emptyMap(),
    val hotspots: Map<String, List<Hotspot>> = emptyMap(),
    val capturedFrames: Map<String, List<CapturedFrame>> = emptyMap(),
    val themes: List<TourTheme> = defaultThemes(),
)

fun defaultThemes(): List<TourTheme> = listOf(
    TourTheme("opal-glass", "Opal Glass", "#EEF7FB", "#F8FDFFCC", "#38BDF8", "#0F172A", "#64748B"),
    TourTheme("denim-glass", "Denim Glass", "#071527", "#10284ACC", "#22D3EE", "#F8FAFC", "#B6C7DF", isDark = true),
    TourTheme("warm-cafe", "Warm Cafe", "#F7E8D0", "#FFF4E0D9", "#D97706", "#2B1709", "#7C4A20"),
    TourTheme("luxury-venue", "Luxury Venue", "#09090B", "#191816D9", "#F5C451", "#FFF7DA", "#D6C08A", isDark = true),
    TourTheme("bold-fitness", "Bold Fitness", "#101418", "#1D2730D9", "#A3E635", "#F8FAFC", "#CBD5E1", isDark = true),
    TourTheme("clean-studio", "Clean Studio", "#F7F8FA", "#FFFFFFD9", "#111827", "#111827", "#6B7280"),
)
