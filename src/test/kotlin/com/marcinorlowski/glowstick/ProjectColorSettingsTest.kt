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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Color

/**
 * Unit tests for [ProjectColorSettings] value handling - defaults, clamping, and
 * state round-trip. The class is instantiated directly
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
        assertTrue(s.edgeTop)
        assertTrue(s.edgeBottom)
        assertTrue(s.edgeLeft)
        assertTrue(s.edgeRight)
    }

    @Test
    fun `color is auto by default and falls back to the name palette`() {
        val s = ProjectColorSettings()
        assertNull(s.customColor)
        assertTrue(s.isAutoColor)
        assertEquals(ProjectColorPalette.forName("demo"), s.effectiveColor("demo"))
    }

    @Test
    fun `an explicit color wins over the palette and round-trips as hex`() {
        val s = ProjectColorSettings()
        s.customColor = Color(0x12, 0x34, 0x56)
        assertFalse(s.isAutoColor)
        assertEquals("#123456", s.state.color)
        assertEquals(Color(0x12, 0x34, 0x56), s.effectiveColor("demo"))

        // Clearing it goes back to the name-derived color.
        s.customColor = null
        assertTrue(s.isAutoColor)
        assertEquals(ProjectColorPalette.forName("demo"), s.effectiveColor("demo"))
    }

    @Test
    fun `malformed stored colors read as auto`() {
        val bad = listOf("", "   ", "#12345", "#1234567", "nothex", "#GGGGGG", "123456789")
        for (raw in bad) {
            assertNull(ProjectColorSettings.parseHex(raw), "'$raw' should not parse")
        }
        assertNull(ProjectColorSettings.parseHex(null))
        assertEquals(Color(0xAB, 0xCD, 0xEF), ProjectColorSettings.parseHex("#ABCDEF"))
        assertEquals(Color(0xAB, 0xCD, 0xEF), ProjectColorSettings.parseHex("abcdef"))
        assertEquals(Color(0xAB, 0xCD, 0xEF), ProjectColorSettings.parseHex("  #abcdef "))
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
            customColor = Color(0xFF, 0x00, 0x80)
            masterAlpha = 42
            width = 12
            alpha = 33
            endAlpha = 7
            valueProvider = ValueProvider.LINEAR
            edgeTop = false
            edgeBottom = false
        }
        val b = ProjectColorSettings()
        b.loadState(a.state)

        assertFalse(b.enabled)
        assertEquals(Color(0xFF, 0x00, 0x80), b.customColor)
        assertEquals(42, b.masterAlpha)
        assertEquals(12, b.width)
        assertEquals(33, b.alpha)
        assertEquals(7, b.endAlpha)
        assertEquals(ValueProvider.LINEAR, b.valueProvider)
        assertFalse(b.edgeTop)
        assertFalse(b.edgeBottom)
    }
}
