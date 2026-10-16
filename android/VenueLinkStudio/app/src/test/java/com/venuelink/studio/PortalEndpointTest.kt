package com.venuelink.studio

import com.venuelink.studio.capture.PortalEndpoint
import org.junit.Assert.assertEquals
import org.junit.Test

class PortalEndpointTest {
    @Test fun hostedBuildReplacesOldLanAddressWithoutChangingAccountData() {
        assertEquals("https://portal.example.com", PortalEndpoint.address("http://192.168.0.149:3000", "https://portal.example.com/"))
    }
    @Test fun freshInstallUsesBundledServer() {
        assertEquals("https://portal.example.com", PortalEndpoint.address("", "https://portal.example.com"))
    }
    @Test fun developmentBuildKeepsItsManualServer() {
        assertEquals("http://10.0.2.2:3000", PortalEndpoint.address("http://10.0.2.2:3000", ""))
    }
}
