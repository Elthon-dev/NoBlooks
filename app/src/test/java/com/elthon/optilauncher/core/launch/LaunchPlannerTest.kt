package com.elthon.optilauncher.core.launch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchPlannerTest {

    private val resizable = LaunchTarget("com.cam", "com.cam/.Main", resizable = true)
    private val fixed = LaunchTarget("com.fixed", "com.fixed/.Main", resizable = false)

    @Test
    fun `normal launch uses a fresh launcher task`() {
        val plan = LaunchPlanner.plan(resizable, WindowMode.NORMAL, DeviceCaps())
        assertEquals(WindowMode.NORMAL, plan.mode)
        assertFalse(plan.adjacent)
        assertNull(plan.downgradedFrom)
        assertTrue(plan.flags and IntentFlags.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `split screen is downgraded for non-resizable apps, honestly explained`() {
        val plan = LaunchPlanner.plan(fixed, WindowMode.SPLIT, DeviceCaps(supportsSplitScreen = true))
        assertEquals(WindowMode.NORMAL, plan.mode)
        assertEquals(WindowMode.SPLIT, plan.downgradedFrom)
        assertFalse(plan.adjacent)
        assertNotNull(plan.note)
        assertTrue(plan.note!!.contains("resiz", ignoreCase = true))
    }

    @Test
    fun `split screen is skipped when the device does not support it`() {
        val plan = LaunchPlanner.plan(resizable, WindowMode.SPLIT, DeviceCaps(supportsSplitScreen = false))
        assertEquals(WindowMode.NORMAL, plan.mode)
        assertEquals(WindowMode.SPLIT, plan.downgradedFrom)
    }

    @Test
    fun `split screen requests adjacent placement when possible`() {
        val plan = LaunchPlanner.plan(resizable, WindowMode.SPLIT, DeviceCaps(supportsSplitScreen = true))
        assertEquals(WindowMode.SPLIT, plan.mode)
        assertTrue(plan.adjacent)
        assertTrue(plan.flags and IntentFlags.FLAG_ACTIVITY_LAUNCH_ADJACENT != 0)
    }

    @Test
    fun `freeform never fakes a split — it falls back to a plain launch`() {
        val plan = LaunchPlanner.plan(
            resizable,
            WindowMode.FREEFORM,
            DeviceCaps(supportsSplitScreen = true, supportsFreeform = false)
        )
        assertEquals(WindowMode.NORMAL, plan.mode)
        assertEquals(WindowMode.FREEFORM, plan.downgradedFrom)
        assertFalse(plan.adjacent)
    }

    @Test
    fun `freeform falls back to a normal launch when split is unusable`() {
        val plan = LaunchPlanner.plan(
            fixed,
            WindowMode.FREEFORM,
            DeviceCaps(supportsSplitScreen = true, supportsFreeform = false)
        )
        assertEquals(WindowMode.NORMAL, plan.mode)
        assertEquals(WindowMode.FREEFORM, plan.downgradedFrom)
    }

    @Test
    fun `freeform is honoured when the device advertises it`() {
        val plan = LaunchPlanner.plan(resizable, WindowMode.FREEFORM, DeviceCaps(supportsFreeform = true))
        assertEquals(WindowMode.FREEFORM, plan.mode)
        assertNull(plan.downgradedFrom)
    }

    @Test
    fun `unsupported outcomes never claim success`() {
        val plan = LaunchPlanner.plan(resizable, WindowMode.FREEFORM, DeviceCaps(supportsFreeform = true))
        val outcome = LaunchPlanner.outcomeForPlan(plan)
        assertTrue(outcome is LaunchOutcome.Started)
    }
}
