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

import com.intellij.ide.ProjectWindowCustomizerService
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleListCellRenderer
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Settings page
 */
class ProjectColorConfigurable(private val project: Project) : Configurable {

    private var ui: Ui? = null
    private var previewColor: Color =
        JBColor(Color(0x35, 0x74, 0xF0), Color(0x35, 0x74, 0xF0))

    private val settings get() = ProjectColorSettings.getInstance(project)

    override fun getDisplayName(): String = "GlowStick MHL"

    override fun createComponent(): JComponent {
        val s = settings
        val minW = ProjectColorSettings.MIN_WIDTH
        val maxW = ProjectColorSettings.MAX_WIDTH

        previewColor = runCatching {
            ProjectWindowCustomizerService.getInstance()
                .getProjectColorToCustomize(project)
        }.getOrNull() ?: previewColor

        val u = Ui(
            enabled = JBCheckBox("", s.enabled),
            masterAlpha = SliderInput(0, 100, s.masterAlpha, unit = "%").ticks(
                25,
                5,
                true
            ),
            edgeTop = JBCheckBox("Top", s.edgeTop),
            edgeBottom = JBCheckBox("Bottom", s.edgeBottom),
            edgeLeft = JBCheckBox("Left", s.edgeLeft),
            edgeRight = JBCheckBox("Right", s.edgeRight),
            outerWidth = SliderInput(0, maxW, s.outerWidth, unit = "px").ticks(
                16,
                4,
                true
            ),
            outerAlpha = SliderInput(0, 100, s.outerAlpha, unit = "%").ticks(
                25,
                5,
                true
            ),

            outerValueProvider = labeledCombo(
                ValueProvider.entries.toTypedArray(),
                s.outerValueProvider
            ) { it.label },
            innerWidth = SliderInput(0, maxW, s.width, minW, "px").ticks(
                16,
                4,
                true
            ),
            innerAlpha = SliderInput(0, 100, s.alpha, unit = "%").ticks(
                25,
                5,
                true
            ),
            innerEnd = SliderInput(0, 100, s.endAlpha, unit = "%").ticks(
                25,
                5,
                true
            ),
            innerValueProvider = labeledCombo(
                ValueProvider.entries.toTypedArray(),
                s.valueProvider
            ) { it.label },
            preview = OpacityPreview(),
        )
        ui = u

        val refresh = { refreshPreview() }
        listOf(
            u.masterAlpha,
            u.outerWidth,
            u.outerAlpha,
            u.innerWidth,
            u.innerAlpha,
            u.innerEnd
        ).forEach { it.onChange = refresh }
        u.outerValueProvider.addActionListener { refresh() }
        u.innerValueProvider.addActionListener { refresh() }
        u.enabled.addActionListener { setControlsEnabled(u.enabled.isSelected) }

        // Dragging a preview handle writes back to the matching slider, which re-fires
        // refreshPreview - so the handle and the slider/spinner stay in sync both ways.
        u.preview.onOuterAlpha = { u.outerAlpha.value = it }
        u.preview.onInnerAlpha = { u.innerAlpha.value = it }
        u.preview.onEndAlpha = { u.innerEnd.value = it }
        u.preview.onOuterWidth = { u.outerWidth.value = it }

        refreshPreview()

        // hgap 0 so the first checkbox has no leading inset and lines up with the
        // "Enabled" checkbox above; spacing goes between the boxes instead.
        val edgesRow = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0)).apply {
            add(u.edgeTop)
            add(javax.swing.Box.createHorizontalStrut(12))
            add(u.edgeBottom)
            add(javax.swing.Box.createHorizontalStrut(12))
            add(u.edgeLeft)
            add(javax.swing.Box.createHorizontalStrut(12))
            add(u.edgeRight)
        }

        val form = FormBuilder.createFormBuilder()
            .addComponent(sectionLabel("General"))
            .addLabeledComponent("Enabled:", u.enabled)
            .addLabeledComponent("Edges:", edgesRow)
            .addLabeledComponent("Opacity:", u.masterAlpha.panel)
            .addComponent(sectionLabel("Outer segment"))
            .addLabeledComponent("Width:", u.outerWidth.panel)
            .addLabeledComponent("Opacity:", u.outerAlpha.panel)
            .addLabeledComponent("Interpolation:", u.outerValueProvider)
            .addComponent(sectionLabel("Inner segment"))
            .addLabeledComponent("Width:", u.innerWidth.panel)
            .addLabeledComponent("Opacity:", u.innerAlpha.panel)
            .addLabeledComponent("Interpolation:", u.innerValueProvider)
            .addComponent(sectionLabel("End segment"))
            .addLabeledComponent("Opacity:", u.innerEnd.panel)
            .addComponent(sectionLabel("Preview"))
            .addComponent(u.preview)
            .addComponentFillVertically(JPanel(), 0)
            .panel

        setControlsEnabled(u.enabled.isSelected)

        return JPanel(BorderLayout()).apply {
            add(SettingsBanner.build(), BorderLayout.NORTH)
            add(form, BorderLayout.CENTER)
        }
    }

    private fun setControlsEnabled(on: Boolean) {
        val u = ui ?: return
        u.masterAlpha.setEnabled(on)
        u.edgeTop.isEnabled = on
        u.edgeBottom.isEnabled = on
        u.edgeLeft.isEnabled = on
        u.edgeRight.isEnabled = on
        u.outerWidth.setEnabled(on)
        u.outerAlpha.setEnabled(on)
        u.outerValueProvider.isEnabled = on
        u.innerWidth.setEnabled(on)
        u.innerAlpha.setEnabled(on)
        u.innerEnd.setEnabled(on)
        u.innerValueProvider.isEnabled = on
    }

    private fun refreshPreview() {
        val u = ui ?: return
        val rows = FrameOpacityModel.rows(
            u.outerWidth.value,
            u.outerAlpha.value,
            u.outerValueProvider.selectedItem as ValueProvider,
            u.innerWidth.value,
            u.innerAlpha.value,
            u.innerValueProvider.selectedItem as ValueProvider,
            u.innerEnd.value,
            u.masterAlpha.value,
        )
        u.preview.update(
            rows,
            previewColor,
            outerWidth = u.outerWidth.value,
            outerAlpha = u.outerAlpha.value,
            innerWidth = u.innerWidth.value,
            innerAlpha = u.innerAlpha.value,
            endAlpha = u.innerEnd.value,
            maxWidth = ProjectColorSettings.MAX_WIDTH,
        )
    }

    override fun isModified(): Boolean {
        val u = ui ?: return false
        val s = settings
        return u.enabled.isSelected != s.enabled ||
                u.masterAlpha.value != s.masterAlpha ||
                u.edgeTop.isSelected != s.edgeTop ||
                u.edgeBottom.isSelected != s.edgeBottom ||
                u.edgeLeft.isSelected != s.edgeLeft ||
                u.edgeRight.isSelected != s.edgeRight ||
                u.outerWidth.value != s.outerWidth ||
                u.outerAlpha.value != s.outerAlpha ||
                u.outerValueProvider.selectedItem != s.outerValueProvider ||
                u.innerWidth.value != s.width ||
                u.innerAlpha.value != s.alpha ||
                u.innerEnd.value != s.endAlpha ||
                u.innerValueProvider.selectedItem != s.valueProvider
    }

    override fun apply() {
        val u = ui ?: return
        val s = settings
        s.enabled = u.enabled.isSelected
        s.masterAlpha = u.masterAlpha.value
        s.edgeTop = u.edgeTop.isSelected
        s.edgeBottom = u.edgeBottom.isSelected
        s.edgeLeft = u.edgeLeft.isSelected
        s.edgeRight = u.edgeRight.isSelected
        s.outerWidth = u.outerWidth.value
        s.outerAlpha = u.outerAlpha.value
        s.outerValueProvider = u.outerValueProvider.selectedItem as ValueProvider
        s.width = u.innerWidth.value
        s.alpha = u.innerAlpha.value
        s.endAlpha = u.innerEnd.value
        s.valueProvider = u.innerValueProvider.selectedItem as ValueProvider
        if (s.enabled) {
            ProjectColorApplier.apply(project)
        } else {
            ProjectColorApplier.clear(project)
        }
    }

    override fun reset() {
        val u = ui ?: return
        val s = settings
        u.enabled.isSelected = s.enabled
        u.masterAlpha.value = s.masterAlpha
        u.edgeTop.isSelected = s.edgeTop
        u.edgeBottom.isSelected = s.edgeBottom
        u.edgeLeft.isSelected = s.edgeLeft
        u.edgeRight.isSelected = s.edgeRight
        u.outerWidth.value = s.outerWidth
        u.outerAlpha.value = s.outerAlpha
        u.outerValueProvider.selectedItem = s.outerValueProvider
        u.innerWidth.value = s.width
        u.innerAlpha.value = s.alpha
        u.innerEnd.value = s.endAlpha
        u.innerValueProvider.selectedItem = s.valueProvider
        setControlsEnabled(s.enabled)
        refreshPreview()
    }

    override fun disposeUIResources() {
        ui = null
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private fun sectionLabel(text: String) = TitledSeparator(text)

    private fun <T> labeledCombo(
        items: Array<T>,
        selected: T,
        label: (T) -> String,
    ) =
        ComboBox(items).apply {
            renderer = SimpleListCellRenderer.create("") { label(it) }
            selectedItem = selected
        }

    // ── UI holder ─────────────────────────────────────────────────────────────────

    private class Ui(
        val enabled: JBCheckBox,
        val masterAlpha: SliderInput,
        val edgeTop: JBCheckBox,
        val edgeBottom: JBCheckBox,
        val edgeLeft: JBCheckBox,
        val edgeRight: JBCheckBox,
        val outerWidth: SliderInput,
        val outerAlpha: SliderInput,
        val outerValueProvider: ComboBox<ValueProvider>,
        val innerWidth: SliderInput,
        val innerAlpha: SliderInput,
        val innerEnd: SliderInput,
        val innerValueProvider: ComboBox<ValueProvider>,
        val preview: OpacityPreview,
    )
}
