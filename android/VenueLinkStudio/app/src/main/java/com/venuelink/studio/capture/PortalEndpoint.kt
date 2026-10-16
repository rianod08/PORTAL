package com.venuelink.studio.capture

/** A hosted build always uses its bundled server, including existing background jobs. */
object PortalEndpoint {
    fun address(saved: String, bundled: String): String = bundled.trim().trimEnd('/').ifBlank { saved }
}
