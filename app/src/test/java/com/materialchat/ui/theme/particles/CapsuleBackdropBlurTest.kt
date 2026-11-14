package com.materialchat.ui.theme.particles

import com.materialchat.ui.theme.particles.CapsuleBackdropBlurSupport.Status
import org.junit.Assert.assertEquals
import org.junit.Test

/** Status resolution of the region backdrop-blur probe (mirrors essentials' IslandBackdropBlurSupport). */
class CapsuleBackdropBlurTest {
    @Test fun statusOrdersOsThenApiThenWindowSetting() {
        val s = CapsuleBackdropBlurSupport
        assertEquals(Status.UnsupportedOs, s.status(30, apiPresent = true, windowBlurEnabled = true))
        assertEquals(Status.UnsupportedOs, s.status(26, apiPresent = false, windowBlurEnabled = false))
        assertEquals(Status.ApiUnavailable, s.status(31, apiPresent = false, windowBlurEnabled = true))
        assertEquals(Status.ApiUnavailable, s.status(35, apiPresent = false, windowBlurEnabled = false))
        assertEquals(Status.WindowBlurDisabled, s.status(31, apiPresent = true, windowBlurEnabled = false))
        assertEquals(Status.Available, s.status(33, apiPresent = true, windowBlurEnabled = true))
    }
}
