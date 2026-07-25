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
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.WindowManager
import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JRootPane
import javax.swing.SwingUtilities

object ProjectColorApplier {

    private const val COMPONENT_NAME_BORDER = "ProjectColorBorder"

    private val FALLBACK_COLOR = Color(0x35, 0x74, 0xF0)   // IDE-ish blue

    fun apply(project: Project) {
        if (!ProjectColorSettings.getInstance(project).enabled) return

        // Everything below touches the frame AND the color - and
        // `getProjectColorToCustomize` asserts EDT - so all of it must run on
        // the Event Dispatch Thread.
        fun tryPaint(attemptsLeft: Int) {
            SwingUtilities.invokeLater {
                if (project.isDisposed) return@invokeLater
                val frame = getFrame(project)
                if (frame == null) {
                    if (attemptsLeft > 0) tryPaint(attemptsLeft - 1)
                    return@invokeLater
                }
                val settings = ProjectColorSettings.getInstance(project)
                if (!settings.enabled) return@invokeLater
                val rowAlphas = FrameOpacityModel.rows(
                    settings.outerWidth,
                    settings.outerAlpha,
                    settings.outerValueProvider,
                    settings.width,
                    settings.alpha,
                    settings.valueProvider,
                    settings.endAlpha,
                    settings.masterAlpha,
                )
                val edges = Edges(
                    settings.edgeTop,
                    settings.edgeBottom,
                    settings.edgeLeft,
                    settings.edgeRight,
                )
                if (rowAlphas.isEmpty() || edges.none) {
                    clearFrame(frame)
                    return@invokeLater
                }
                paintFrame(frame, currentColor(project), rowAlphas, edges)
            }
        }
        tryPaint(attemptsLeft = 10)
    }

    fun clear(project: Project) {
        val frame = getFrame(project) ?: return
        SwingUtilities.invokeLater { clearFrame(frame) }
    }

    // ── internal ──────────────────────────────────────────────────────────────

    private fun getFrame(project: Project): JFrame? =
        WindowManager.getInstance().getFrame(project)

    private fun currentColor(project: Project): Color =
        ProjectWindowCustomizerService.getInstance()
            .getProjectColorToCustomize(project) ?: FALLBACK_COLOR

    private fun paintFrame(
        frame: JFrame,
        baseColor: Color,
        rowAlphas: IntArray,
        edges: Edges,
    ) {
        val rootPane = frame.rootPane
        val glassPane = rootPane.glassPane as? JComponent ?: return

        removeOldBorder(rootPane)

        // 2. Create the single frame border spanning the whole window
        val border = BorderComponent(baseColor, rowAlphas, edges)
        border.name = COMPONENT_NAME_BORDER

        glassPane.isVisible = true
        glassPane.layout = null
        glassPane.add(border)

        positionBorder(border, frame)

        rootPane.revalidate()
        rootPane.repaint()

        // Reposition border on window resize
        frame.componentListeners
            .filter { it.javaClass.name.contains("ProjectColorApplier") }
            .forEach { frame.removeComponentListener(it) }
        frame.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
                positionBorder(border, frame)
            }
        })
    }

    private fun clearFrame(frame: JFrame) {
        val rootPane = frame.rootPane
        removeOldBorder(rootPane)
        (rootPane.glassPane as? JComponent)?.let { glass ->
            if (glass.componentCount == 0) {
                glass.isVisible = false
            }
        }
        rootPane.revalidate()
        rootPane.repaint()
    }

    private fun removeOldBorder(rootPane: JRootPane) {
        val glass = rootPane.glassPane as? JComponent ?: return
        glass.components
            .filter { it.name == COMPONENT_NAME_BORDER }
            .forEach { glass.remove(it) }
        glass.revalidate()
    }

    private fun positionBorder(border: BorderComponent, frame: JFrame) {
        val glass = frame.rootPane.glassPane as? JComponent ?: return
        border.setBounds(0, 0, glass.width, glass.height)
        glass.revalidate()
        glass.repaint()
    }

    // ── border component ────────────────────────────────────────────────────────

    /** Which window edges the frame is drawn on. */
    data class Edges(
        val top: Boolean,
        val bottom: Boolean,
        val left: Boolean,
        val right: Boolean,
    ) {
        val none get() = !top && !bottom && !left && !right
    }

    private class BorderComponent(
        private val baseColor: Color,     // opaque RGB; per-row alpha applied below
        private val alphaAt: IntArray,    // per-row alpha 0..255, outer (0) -> inner (size-1)
        private val edges: Edges,
    ) : JComponent() {

        init {
            isOpaque = false
        }

        // Never claim mouse events, so the full-window glass-pane child does
        // not block IDE interaction (dragging split dividers, etc.).
        override fun contains(x: Int, y: Int): Boolean = false

        override fun paintComponent(g: Graphics) {
            val g2 = g.create() as Graphics2D
            g2.setRenderingHint(
                RenderingHints.KEY_RENDERING,
                RenderingHints.VALUE_RENDER_QUALITY
            )

            val t = alphaAt.size
            if (t <= 0) {
                g2.dispose()
                return
            }

            fun col(d: Int) {
                g2.color = Color(
                    baseColor.red,
                    baseColor.green,
                    baseColor.blue,
                    alphaAt[d]
                )
            }

            // Solid frame
            for (d in 0 until t) {
                if (width - 2 * d <= 0 || height - 2 * d <= 0) break
                col(d)
                val x0 = if (edges.left) d else 0
                val x1 = if (edges.right) width - 1 - d else width - 1
                val y0 = if (edges.top) d else 0
                val y1 = if (edges.bottom) height - 1 - d else height - 1

                // A disabled edge contributes no rows, so the strips of the
                // adjacent edges run to the very window border on that side
                // (no corner inset).
                if (edges.top) g2.fillRect(x0, d, x1 - x0 + 1, 1)
                if (edges.bottom) g2.fillRect(x0, height - 1 - d, x1 - x0 + 1, 1)
                if (edges.left) g2.fillRect(d, y0, 1, y1 - y0 + 1)
                if (edges.right) g2.fillRect(width - 1 - d, y0, 1, y1 - y0 + 1)
            }
            g2.dispose()
        }
    }
}
