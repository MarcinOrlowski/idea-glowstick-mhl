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
import org.junit.jupiter.api.Test

class ValueProviderTest {

    @Test
    fun `from parses each enum name`() {
        assertEquals(ValueProvider.LINEAR, ValueProvider.from("LINEAR"))
        assertEquals(ValueProvider.CURVED, ValueProvider.from("CURVED"))
    }

    @Test
    fun `from falls back to LINEAR for null or unknown`() {
        assertEquals(ValueProvider.LINEAR, ValueProvider.from(null))
        assertEquals(ValueProvider.LINEAR, ValueProvider.from("nonsense"))
        assertEquals(ValueProvider.LINEAR, ValueProvider.from(""))
        // A stored "FLAT" from an older version no longer matches -> LINEAR.
        assertEquals(ValueProvider.LINEAR, ValueProvider.from("FLAT"))
    }

    @Test
    fun `from is case-sensitive`() {
        // it.name == name is an exact match; wrong-case input never matches and falls
        // through to the LINEAR default. "curved" proves it: a case-insensitive parse
        // would return CURVED, but exact matching yields the LINEAR fallback.
        assertEquals(ValueProvider.CURVED, ValueProvider.from("CURVED"))
        assertEquals(ValueProvider.LINEAR, ValueProvider.from("curved"))
        assertEquals(ValueProvider.LINEAR, ValueProvider.from("Curved"))
    }

    @Test
    fun `labels are the human-readable form`() {
        assertEquals("Linear", ValueProvider.LINEAR.label)
        assertEquals("Curved", ValueProvider.CURVED.label)
    }
}
