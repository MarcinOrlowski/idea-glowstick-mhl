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

import java.awt.Color

/**
 * Frame color for a project the user has not picked one for yet.
 */
object ProjectColorPalette {

    /** Distinct hues, mid saturation - readable over both light and dark UI. */
    val COLORS: List<Color> = listOf(
        Color(0x35, 0x74, 0xF0),   // blue
        Color(0x00, 0x91, 0xD5),   // azure
        Color(0x00, 0xA5, 0xB5),   // cyan
        Color(0x11, 0xA4, 0x7C),   // teal
        Color(0x5A, 0xAF, 0x3C),   // green
        Color(0x8F, 0xAF, 0x20),   // lime
        Color(0xD8, 0xA4, 0x00),   // amber
        Color(0xE0, 0x7B, 0x1A),   // orange
        Color(0xE0, 0x4F, 0x3D),   // red
        Color(0xE0, 0x47, 0x8C),   // pink
        Color(0xB0, 0x5B, 0xD8),   // purple
        Color(0x7A, 0x5A, 0xF0),   // violet
    )

    /** The default color for a project called [name]. */
    fun forName(name: String): Color = COLORS[indexFor(name)]

    /** Palette index for [name], always within [COLORS] bounds. */
    fun indexFor(name: String): Int = hash(name).mod(COLORS.size)

    /**
     * FNV-1a. `String.hashCode` maps names that differ in one trailing character
     * onto a consecutive run of buckets, so a group of sibling projects
     * ("app-api" / "app-web" / ...) would walk the palette in order instead of
     * scattering over it. FNV-1a has no such pattern.
     */
    private fun hash(name: String): Int {
        var h = HASH_BASIS
        for (ch in name) {
            h = h xor ch.code
            h *= HASH_PRIME      // Int overflow wraps, as the algorithm requires
        }
        return h
    }

    private const val HASH_BASIS = -2128831035   // FNV-1a 32-bit offset basis
    private const val HASH_PRIME = 16777619      // FNV-1a 32-bit prime
}
