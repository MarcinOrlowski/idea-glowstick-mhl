package com.marcinorlowski.glowstick

/** *********************************************************************
 *
 * GlowStick MHL for IntelliJ IDEA
 *
 * @author    Marcin Orlowski <mail (#) marcinOrlowski (.) com>
 * @copyright ©2026 Marcin Orlowski
 * @license   https://opensource.org/license/mit MIT
 * @link      https://github.com/MarcinOrlowski/idea-glowstick-mhl
 *
 ******************************************************************** **/

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ProjectColorSettings] value handling - defaults, clamping, and
 * state round-trip. The class is instantiated directly (no project/service lookup),
 * so these exercise only the pure getter/setter/normalization logic.
 */
class ProjectColorSettingsTest {

    @Test
    fun `defaults match the documented contract`() {
        val s = ProjectColorSettings()
        assertTrue(s.enabled)
        assertEquals(100, s.masterAlpha)
        assertEquals(ProjectColorSettings.DEFAULT_WIDTH, s.width)
        assertEquals(ProjectColorSettings.DEFAULT_ALPHA, s.alpha)
        assertEquals(ValueProvider.CURVED, s.valueProvider)
        assertEquals(0, s.endAlpha)
        assertEquals(0, s.outerWidth)
        // Top off by default; the other three edges on.
        assertFalse(s.edgeTop)
        assertTrue(s.edgeBottom)
        assertTrue(s.edgeLeft)
        assertTrue(s.edgeRight)
    }

    @Test
    fun `width is clamped to its min and max`() {
        val s = ProjectColorSettings()
        s.width = 999
        assertEquals(ProjectColorSettings.MAX_WIDTH, s.width)
        s.width = 0
        assertEquals(ProjectColorSettings.MIN_WIDTH, s.width)
    }

    @Test
    fun `alpha and masterAlpha are clamped to 0 to 100`() {
        val s = ProjectColorSettings()
        s.alpha = 150
        assertEquals(100, s.alpha)
        s.alpha = -10
        assertEquals(0, s.alpha)
        s.masterAlpha = 200
        assertEquals(100, s.masterAlpha)
        s.masterAlpha = -1
        assertEquals(0, s.masterAlpha)
    }

    @Test
    fun `state round-trips through getState and loadState`() {
        val a = ProjectColorSettings().apply {
            enabled = false
            masterAlpha = 42
            width = 12
            alpha = 33
            endAlpha = 7
            valueProvider = ValueProvider.LINEAR
            edgeTop = true
            edgeBottom = false
        }
        val b = ProjectColorSettings()
        b.loadState(a.state)

        assertFalse(b.enabled)
        assertEquals(42, b.masterAlpha)
        assertEquals(12, b.width)
        assertEquals(33, b.alpha)
        assertEquals(7, b.endAlpha)
        assertEquals(ValueProvider.LINEAR, b.valueProvider)
        assertTrue(b.edgeTop)
        assertFalse(b.edgeBottom)
    }
}
