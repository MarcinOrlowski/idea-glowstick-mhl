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

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [FrameOpacityModel] - the single source of truth for per-row opacity.
 * Pure math, no IDE runtime; expected values are computed by hand from the easing spec.
 */
class FrameOpacityModelTest {

    private fun inner(
        width: Int,
        alpha: Int,
        vp: ValueProvider,
        endAlpha: Int = 0,
        masterAlpha: Int = 100,
    ) = FrameOpacityModel.rows(
        outerWidth = 0, outerAlpha = 75, outerValueProvider = ValueProvider.LINEAR,
        innerWidth = width, innerAlpha = alpha, innerValueProvider = vp,
        innerEndAlpha = endAlpha, masterAlpha = masterAlpha,
    )

    @Test
    fun `row count is outerWidth plus innerWidth`() {
        val rows = FrameOpacityModel.rows(
            outerWidth = 3,
            outerAlpha = 100,
            outerValueProvider = ValueProvider.LINEAR,
            innerWidth = 4,
            innerAlpha = 50,
            innerValueProvider = ValueProvider.LINEAR,
        )
        assertEquals(7, rows.size)
    }

    @Test
    fun `LINEAR with equal start and end is a constant (the old FLAT)`() {
        // FLAT was removed: a constant segment is LINEAR with start == end.
        assertArrayEquals(
            intArrayOf(204, 204, 204),
            inner(3, 80, ValueProvider.LINEAR, endAlpha = 80)
        )
    }

    @Test
    fun `LINEAR eases start down to end across the width`() {
        assertArrayEquals(
            intArrayOf(255, 170, 85, 0),
            inner(4, 100, ValueProvider.LINEAR)
        )
    }

    @Test
    fun `endAlpha is the inner segment's final value`() {
        // 100% -> 40% over two rows: 255 then round(0.40*255)=102.
        assertArrayEquals(
            intArrayOf(255, 102),
            inner(2, 100, ValueProvider.LINEAR, endAlpha = 40)
        )
    }

    @Test
    fun `masterAlpha scales every row`() {
        // Constant 80% (LINEAR start==end) halved -> round(0.40*255)=102.
        assertArrayEquals(
            intArrayOf(102, 102, 102),
            inner(3, 80, ValueProvider.LINEAR, endAlpha = 80, masterAlpha = 50)
        )
    }

    @Test
    fun `masterAlpha of zero blanks the whole pattern`() {
        assertArrayEquals(
            intArrayOf(0, 0, 0),
            inner(3, 100, ValueProvider.LINEAR, endAlpha = 100, masterAlpha = 0)
        )
    }

    @Test
    fun `width of one yields a single row at the start opacity`() {
        // round(0.70*255) = 179
        assertArrayEquals(intArrayOf(179), inner(1, 70, ValueProvider.LINEAR))
    }

    @Test
    fun `outerWidth zero skips the outer segment`() {
        assertEquals(2, inner(2, 50, ValueProvider.LINEAR).size)
    }

    @Test
    fun `chained segments join - outer end equals inner start`() {
        val rows = FrameOpacityModel.rows(
            outerWidth = 2,
            outerAlpha = 100,
            outerValueProvider = ValueProvider.LINEAR,
            innerWidth = 2,
            innerAlpha = 50,
            innerValueProvider = ValueProvider.LINEAR,
            innerEndAlpha = 0,
        )
        // outer 100->50 = [255,128]; inner 50->0 = [128,0]; they meet at 128.
        assertArrayEquals(intArrayOf(255, 128, 128, 0), rows)
    }

    @Test
    fun `negative widths are clamped to zero`() {
        assertTrue(
            FrameOpacityModel.rows(
                outerWidth = -5,
                outerAlpha = 75,
                outerValueProvider = ValueProvider.LINEAR,
                innerWidth = -3,
                innerAlpha = 50,
                innerValueProvider = ValueProvider.LINEAR,
            ).isEmpty()
        )
    }

    @Test
    fun `alpha above 100 is clamped`() {
        assertArrayEquals(intArrayOf(255), inner(1, 150, ValueProvider.LINEAR))
    }

    @Test
    fun `masterAlpha above 100 is clamped`() {
        assertArrayEquals(
            intArrayOf(255),
            inner(1, 100, ValueProvider.LINEAR, masterAlpha = 200)
        )
    }

    @Test
    fun `every row is a byte value between 0 and 255`() {
        val rows = FrameOpacityModel.rows(
            outerWidth = 8,
            outerAlpha = 90,
            outerValueProvider = ValueProvider.CURVED,
            innerWidth = 16,
            innerAlpha = 75,
            innerValueProvider = ValueProvider.CURVED,
            innerEndAlpha = 10,
            masterAlpha = 60,
        )
        assertTrue(rows.all { it in 0..255 })
    }
}
