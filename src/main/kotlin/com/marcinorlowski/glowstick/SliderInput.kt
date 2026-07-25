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

import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JPanel
import javax.swing.JSlider
import javax.swing.JSpinner
import javax.swing.SpinnerNumberModel

/**
 * A slider paired with a numeric spinner. [onChange] fires on either edit;
 * values below [valueMin] are coerced up (the slider may allow a lower
 * minimum for its scale).
 */
internal class SliderInput(
    sliderMin: Int,
    max: Int,
    value: Int,
    private val valueMin: Int = sliderMin,
    unit: String = "",
) {
    private val slider = JSlider(sliderMin, max, value.coerceAtLeast(valueMin))
    private val spinner = JSpinner(
        SpinnerNumberModel(
            value.coerceAtLeast(valueMin),
            valueMin,
            max,
            1
        )
    ).apply {
        if (unit.isNotEmpty()) {
            editor = JSpinner.NumberEditor(this, "0' $unit'")
        }
        preferredSize = Dimension(128, preferredSize.height)
        minimumSize = preferredSize
    }
    val panel: JPanel = JPanel(BorderLayout(8, 0)).apply {
        add(slider, BorderLayout.CENTER)
        add(spinner, BorderLayout.EAST)
    }
    var onChange: (() -> Unit)? = null
    private var syncing = false

    init {
        slider.addChangeListener {
            if (!syncing) {
                syncing = true
                val v = slider.value.coerceAtLeast(valueMin)
                if (v != slider.value) slider.value = v
                spinner.value = v
                syncing = false
                onChange?.invoke()
            }
        }
        spinner.addChangeListener {
            if (!syncing) {
                syncing = true
                slider.value = spinner.value as Int
                syncing = false; onChange?.invoke()
            }
        }
    }

    fun ticks(major: Int, minor: Int, labels: Boolean): SliderInput {
        slider.majorTickSpacing = major
        slider.minorTickSpacing = minor
        slider.paintTicks = true
        slider.paintLabels = labels
        return this
    }

    fun setEnabled(b: Boolean) {
        slider.isEnabled = b
        spinner.isEnabled = b
    }

    var value: Int
        get() = spinner.value as Int
        set(v) {
            val cv = v.coerceAtLeast(valueMin)
            spinner.value = cv
            slider.value = cv
        }
}
