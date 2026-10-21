package com.elthon.optilauncher.core.panel

import com.elthon.optilauncher.core.launch.DeviceCaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultitaskCapabilityTest {

    @Test
    fun `split is ready wherever the platform supports it`() {
        val assessment = MultitaskCapability.assess(
            MultitaskMode.SPLIT,
            DeviceCaps(supportsSplitScreen = true, supportsFreeform = false)
        )
        assertTrue(assessment.usable)
        assertEquals(MultitaskAvailability.READY, assessment.availability)
        assertTrue(assessment.canPair)
        assertFalse(assessment.canFloat)
    }

    @Test
    fun `float is blocked rather than downgraded when freeform is absent`() {
        val assessment = MultitaskCapability.assess(
            MultitaskMode.FLOAT,
            DeviceCaps(supportsFreeform = false)
        )
        assertFalse(assessment.usable)
        assertEquals(MultitaskAvailability.BLOCKED, assessment.availability)
        assertTrue(assessment.note.contains("nothing was launched", ignoreCase = true))
        assertFalse(assessment.note.contains("full-screen", ignoreCase = true))
    }

    @Test
    fun `float is ready when the device declares freeform`() {
        val assessment = MultitaskCapability.assess(
            MultitaskMode.FLOAT,
            DeviceCaps(supportsFreeform = true)
        )
        assertTrue(assessment.usable)
        assertEquals(MultitaskAvailability.READY, assessment.availability)
        assertTrue(assessment.canFloat)
    }

    @Test
    fun `triple is partial without freeform and says the third is manual`() {
        val assessment = MultitaskCapability.assess(
            MultitaskMode.TRIPLE,
            DeviceCaps(supportsSplitScreen = true, supportsFreeform = false)
        )
        assertTrue(assessment.usable)
        assertEquals(MultitaskAvailability.PARTIAL, assessment.availability)
        assertTrue(assessment.note.contains("cannot float", ignoreCase = true))
        assertTrue(assessment.note.contains("Recents", ignoreCase = true))
    }

    @Test
    fun `triple is ready only with both split and freeform`() {
        val assessment = MultitaskCapability.assess(
            MultitaskMode.TRIPLE,
            DeviceCaps(supportsSplitScreen = true, supportsFreeform = true)
        )
        assertTrue(assessment.usable)
        assertEquals(MultitaskAvailability.READY, assessment.availability)
    }

    @Test
    fun `low ram is a warning, not a silent block`() {
        val assessment = MultitaskCapability.assess(
            MultitaskMode.SPLIT,
            DeviceCaps(supportsSplitScreen = true, isLowRam = true)
        )
        assertTrue(assessment.usable)
        assertEquals(MultitaskAvailability.READY, assessment.availability)
        assertTrue(assessment.lowRam)
        assertTrue(assessment.note.contains("low-memory", ignoreCase = true))
    }
}
