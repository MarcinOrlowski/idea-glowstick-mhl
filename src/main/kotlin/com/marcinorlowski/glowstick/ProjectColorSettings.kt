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

import com.intellij.openapi.components.*
import com.intellij.openapi.project.Project
import java.awt.Color

/**
 * How border opacity is distributed across the frame thickness (outer → inner).
 */
enum class ValueProvider(val label: String) {
    LINEAR("Linear"),

    CURVED("Curved");

    companion object {
        fun from(name: String?): ValueProvider =
            entries.firstOrNull { it.name == name } ?: LINEAR
    }
}

/**
 * Per-project settings, stored in .idea/glowStickMhl.xml.
 */
@State(
    name = "ProjectColorSettings",
    storages = [Storage("glowStickMhl.xml")]
)
@Service(Service.Level.PROJECT)
class ProjectColorSettings : PersistentStateComponent<ProjectColorSettings.State> {

    data class State(
        var enabled: Boolean = true,

        // Frame color as #RRGGBB. Empty = derive it from the project name
        var color: String = "",

        // Global opacity multiplier % applied to the final per-row values, so
        // the whole pattern can be scaled down without editing the segments.
        var masterAlpha: Int = DEFAULT_MASTER_ALPHA,

        var width: Int = DEFAULT_WIDTH,  // px, clamped to [MIN_WIDTH, MAX_WIDTH]
        var alpha: Int = DEFAULT_ALPHA,  // opacity %, clamped to [0, 100]

        // x value interpolator
        var valueProvider: String = ValueProvider.CURVED.name,

        // Optional OUTER generator (drawn first, at the window edge).
        // Width 0 = disabled.
        // When enabled it eases from outerAlpha down to the inner generator's
        // alpha, so the two segments join; the inner generator then fades to 0.
        var outerWidth: Int = 0,              // default off
        var outerAlpha: Int = DEFAULT_ALPHA,
        var outerValueProvider: String = ValueProvider.LINEAR.name,

        // Inner generator end opacity % (what it fades to).
        // Default 0 = fully transparent.
        var endAlpha: Int = 0,

        // Which window edges the frame is drawn on.
        var edgeTop: Boolean = true,
        var edgeBottom: Boolean = true,
        var edgeLeft: Boolean = true,
        var edgeRight: Boolean = true,
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }

    var enabled: Boolean
        get() = myState.enabled
        set(value) {
            myState.enabled = value
        }

    /**
     * The color picked by the user, or `null` when it is derived from the
     * project name instead. Stored as `#RRGGBB`; malformed values read as `null`.
     */
    var customColor: Color?
        get() = parseHex(myState.color)
        set(value) {
            myState.color = value?.let(::toHex).orEmpty()
        }

    /** True while no explicit color is set, i.e. the palette default is used. */
    val isAutoColor: Boolean
        get() = customColor == null

    /** The color to paint for a project named [projectName]. */
    fun effectiveColor(projectName: String): Color =
        customColor ?: ProjectColorPalette.forName(projectName)

    /** Border thickness in px, always within [MIN_WIDTH, MAX_WIDTH]. */
    var width: Int
        get() = myState.width.coerceIn(MIN_WIDTH, MAX_WIDTH)
        set(value) {
            myState.width = value.coerceIn(MIN_WIDTH, MAX_WIDTH)
        }

    /** Border opacity as a percentage, always within [0, 100]. */
    var alpha: Int
        get() = myState.alpha.coerceIn(0, 100)
        set(value) {
            myState.alpha = value.coerceIn(0, 100)
        }

    /** How opacity is distributed across the frame thickness. */
    var valueProvider: ValueProvider
        get() = ValueProvider.from(myState.valueProvider)
        set(value) {
            myState.valueProvider = value.name
        }

    /** Outer generator width in px; 0 disables it. Clamped to [0, MAX_WIDTH]. */
    var outerWidth: Int
        get() = myState.outerWidth.coerceIn(0, MAX_WIDTH)
        set(value) {
            myState.outerWidth = value.coerceIn(0, MAX_WIDTH)
        }

    /** Outer generator start opacity %, [0, 100]. */
    var outerAlpha: Int
        get() = myState.outerAlpha.coerceIn(0, 100)
        set(value) {
            myState.outerAlpha = value.coerceIn(0, 100)
        }

    /** Outer generator valueProvider. */
    var outerValueProvider: ValueProvider
        get() = ValueProvider.from(myState.outerValueProvider)
        set(value) {
            myState.outerValueProvider = value.name
        }

    /** Inner generator end opacity % (what it fades to), [0, 100]. */
    var endAlpha: Int
        get() = myState.endAlpha.coerceIn(0, 100)
        set(value) {
            myState.endAlpha = value.coerceIn(0, 100)
        }

    /** Global opacity multiplier % applied to the final per-row values, [0, 100]. */
    var masterAlpha: Int
        get() = myState.masterAlpha.coerceIn(0, 100)
        set(value) {
            myState.masterAlpha = value.coerceIn(0, 100)
        }

    var edgeTop: Boolean
        get() = myState.edgeTop
        set(value) {
            myState.edgeTop = value
        }

    var edgeBottom: Boolean
        get() = myState.edgeBottom
        set(value) {
            myState.edgeBottom = value
        }

    var edgeLeft: Boolean
        get() = myState.edgeLeft
        set(value) {
            myState.edgeLeft = value
        }

    var edgeRight: Boolean
        get() = myState.edgeRight
        set(value) {
            myState.edgeRight = value
        }

    companion object {
        const val MIN_WIDTH = 1
        const val MAX_WIDTH = 32
        const val DEFAULT_WIDTH = 32
        const val DEFAULT_ALPHA = 75
        const val DEFAULT_MASTER_ALPHA = 100

        /** Parses `#RRGGBB` or `RRGGBB`; `null` for blank or malformed input. */
        fun parseHex(hex: String?): Color? {
            val digits = hex?.trim()?.removePrefix("#") ?: return null
            if (digits.length != 6) return null
            return digits.toIntOrNull(16)?.let { Color(it) }
        }

        /** Formats [color] as `#RRGGBB`. Alpha is dropped - the rows carry it. */
        fun toHex(color: Color): String =
            String.format("#%02X%02X%02X", color.red, color.green, color.blue)

        fun getInstance(project: Project): ProjectColorSettings =
            project.getService(ProjectColorSettings::class.java)
    }
}
