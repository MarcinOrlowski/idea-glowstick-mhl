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

import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * By design each frame segment contains of two equal generators:
 * OUTER (drawn first, at the window edge) then INNER (fades to 0).
 */
object FrameOpacityModel {

    fun rows(
        outerWidth: Int, outerAlpha: Int, outerValueProvider: ValueProvider,
        innerWidth: Int, innerAlpha: Int, innerValueProvider: ValueProvider,
        innerEndAlpha: Int = 0,
        masterAlpha: Int = 100,
    ): IntArray {
        val ow = outerWidth.coerceAtLeast(0)
        val iw = innerWidth.coerceAtLeast(0)

        // Global alpha multiplier to scale the whole final curve.
        val master = masterAlpha.coerceIn(0, 100) / 100.0
        val out = IntArray(ow + iw)
        var idx = 0
        if (ow > 0) {
            // Same generic generator as the inner segment
            val start = outerAlpha.coerceIn(0, 100) / 100.0
            val end = if (iw > 0) innerAlpha.coerceIn(0, 100) / 100.0 else 0.0
            for (r in 0 until ow) {
                out[idx++] =
                    to255(value(start, end, ow, outerValueProvider, r) * master)
            }
        }
        if (iw > 0) {
            val start = innerAlpha.coerceIn(0, 100) / 100.0
            val end = innerEndAlpha.coerceIn(0, 100) / 100.0
            for (r in 0 until iw) {
                out[idx++] =
                    to255(value(start, end, iw, innerValueProvider, r) * master)
            }
        }
        return out
    }

    private fun value(
        start: Double,
        end: Double,
        w: Int,
        valueProvider: ValueProvider,
        r: Int,
    ): Double {
        if (w <= 1) return start

        val p = r.toDouble() / (w - 1)   // 0 (outer) .. 1 (inner)
        val progressed = when (valueProvider) {
            ValueProvider.LINEAR -> p
            ValueProvider.CURVED -> 1.0 - curvedFactor(p)
        }
        return start + (end - start) * progressed
    }

    /** Normalized ease-out remaining factor: 1.0 at p=0 -> 0.0 at p=1. */
    private fun curvedFactor(p: Double): Double {
        val k = 3.5
        return (exp(-k * p) - exp(-k)) / (1.0 - exp(-k))
    }

    private fun to255(frac: Double): Int =
        (frac.coerceIn(0.0, 1.0) * 255).roundToInt()
}
