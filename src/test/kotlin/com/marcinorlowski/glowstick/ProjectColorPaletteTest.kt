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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Unit tests for [ProjectColorPalette] - the name-derived default frame color.
 * Pure logic, no IDE runtime needed.
 */
class ProjectColorPaletteTest {

    @Test
    fun `same name always gives the same color`() {
        assertEquals(
            ProjectColorPalette.forName("idea-glowstick-mhl"),
            ProjectColorPalette.forName("idea-glowstick-mhl")
        )
    }

    @Test
    fun `index stays inside the palette for any name`() {
        val names = listOf("", " ", "a", "Zażółć gęślą jaźń", "x".repeat(4096), "😀")
        for (name in names) {
            val i = ProjectColorPalette.indexFor(name)
            assertTrue(
                i in ProjectColorPalette.COLORS.indices,
                "index $i out of bounds for name '$name'"
            )
        }
    }

    @Test
    fun `names differing in one character do not cluster`() {
        // A small stateless palette cannot promise that any given pair differs -
        // with 12 colors two projects sometimes clash, and the picker is the cure.
        // What it MUST NOT do is map near-identical names to a narrow band.
        val siblings = ('a'..'z').map { "app-$it" }
        val used = siblings.map { ProjectColorPalette.indexFor(it) }.toSet()
        assertEquals(
            ProjectColorPalette.COLORS.size, used.size,
            "sibling names cover only ${used.size} colors"
        )
    }

    @Test
    fun `a realistic set of names spreads over most of the palette`() {
        val names = (1..200).map { "project-$it" }
        val used = names.map { ProjectColorPalette.indexFor(it) }.toSet()
        assertTrue(
            used.size >= ProjectColorPalette.COLORS.size - 1,
            "only ${used.size} of ${ProjectColorPalette.COLORS.size} colors used"
        )
    }

    @Test
    fun `palette entries are distinct and opaque`() {
        val colors = ProjectColorPalette.COLORS
        assertEquals(colors.size, colors.toSet().size, "palette has duplicates")
        assertTrue(colors.isNotEmpty())
        colors.forEach { assertEquals(255, it.alpha) }
    }

    @Test
    fun `empty name is handled like any other`() {
        assertNotEquals(null, ProjectColorPalette.forName(""))
    }
}
