package com.elthon.optilauncher.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The classification drives whether the Settings page offers the keep-alive hint.
 * Getting it wrong in the permissive direction nags Pixel users; getting it wrong in
 * the strict direction hides the one fix that helps on OPPO/OnePlus/Realme.
 */
class OemBackgroundPolicyTest {

    @Test
    fun `oppo oneplus realme are the coloros family`() {
        assertEquals(
            OemBackgroundPolicy.COLOROS_FAMILY,
            OemBackgroundPolicy.classify("OPPO", "OPPO")
        )
        assertEquals(
            OemBackgroundPolicy.COLOROS_FAMILY,
            OemBackgroundPolicy.classify("OnePlus", "OnePlus")
        )
        assertEquals(
            OemBackgroundPolicy.COLOROS_FAMILY,
            OemBackgroundPolicy.classify("realme", "realme")
        )
    }

    @Test
    fun `other aggressive roms get the softer hint`() {
        assertEquals(
            OemBackgroundPolicy.OTHER_AGGRESSIVE,
            OemBackgroundPolicy.classify("Xiaomi", "Redmi")
        )
        assertEquals(
            OemBackgroundPolicy.OTHER_AGGRESSIVE,
            OemBackgroundPolicy.classify("HUAWEI", "HONOR")
        )
        assertEquals(
            OemBackgroundPolicy.OTHER_AGGRESSIVE,
            OemBackgroundPolicy.classify("vivo", "iQOO")
        )
    }

    @Test
    fun `stock android gets no hint`() {
        assertEquals(
            OemBackgroundPolicy.NONE,
            OemBackgroundPolicy.classify("Google", "google")
        )
        assertFalse(OemBackgroundPolicy.NONE.wantsKeepAliveHint)
    }

    @Test
    fun `null and blank manufacturer never crash and never nag`() {
        assertEquals(OemBackgroundPolicy.NONE, OemBackgroundPolicy.classify(null, null))
        assertEquals(OemBackgroundPolicy.NONE, OemBackgroundPolicy.classify("", ""))
    }

    @Test
    fun `coloros and aggressive both want a hint`() {
        assertTrue(OemBackgroundPolicy.COLOROS_FAMILY.wantsKeepAliveHint)
        assertTrue(OemBackgroundPolicy.OTHER_AGGRESSIVE.wantsKeepAliveHint)
    }
}
