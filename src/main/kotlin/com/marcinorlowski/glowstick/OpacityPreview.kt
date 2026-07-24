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

import com.intellij.ui.JBColor
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Cursor
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Frame drawing preview
 */
internal class OpacityPreview : JComponent() {

    private var rows: IntArray = IntArray(0)
    private var color: Color = JBColor(
        Color(0x35, 0x74, 0xF0),
        Color(0x35, 0x74, 0xF0)
    )

    private var outerWidth = 0
    private var outerAlpha = 75
    private var innerWidth = 1
    private var innerAlpha = 75
    private var endAlpha = 0
    private var maxWidth = 32

    var onOuterAlpha: ((Int) -> Unit)? = null
    var onInnerAlpha: ((Int) -> Unit)? = null
    var onEndAlpha: ((Int) -> Unit)? = null
    var onOuterWidth: ((Int) -> Unit)? = null

    private enum class Handle { OUTER, JOIN, END }

    private var dragging: Handle? = null

    init {
        preferredSize = Dimension(260, 135)
        minimumSize = preferredSize
        isOpaque = true

        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                dragging = handleAt(e.x, e.y)
                dragging?.let { drag(it, e.x, e.y) }
            }

            override fun mouseDragged(e: MouseEvent) {
                dragging?.let { drag(it, e.x, e.y) }
            }

            override fun mouseReleased(e: MouseEvent) {
                dragging = null
            }

            override fun mouseMoved(e: MouseEvent) {
                cursor = if (handleAt(e.x, e.y) != null)
                    Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                else
                    Cursor.getDefaultCursor()
            }
        }
        addMouseListener(mouse)
        addMouseMotionListener(mouse)
    }

    fun update(
        rows: IntArray,
        color: Color,
        outerWidth: Int,
        outerAlpha: Int,
        innerWidth: Int,
        innerAlpha: Int,
        endAlpha: Int,
        maxWidth: Int,
    ) {
        this.rows = rows
        this.color = color
        this.outerWidth = outerWidth
        this.outerAlpha = outerAlpha
        this.innerWidth = innerWidth
        this.innerAlpha = innerAlpha
        this.endAlpha = endAlpha
        this.maxWidth = maxWidth
        repaint()
    }

    // ── geometry ──────────────────────────────────────────────────────────────

    private val radius = 6
    private val grab get() = radius + 5

    /** Opacity % (0..100) → y, padded so a handle at 0 or 100 stays fully visible. */
    private fun valueToY(v: Int): Int {
        val span = (height - 2 * radius).coerceAtLeast(1)
        return radius + ((100 - v.coerceIn(0, 100)) / 100.0 * span).roundToInt()
    }

    /** Inverse of [valueToY], clamped to 0..100. */
    private fun yToValue(y: Int): Int {
        val span = (height - 2 * radius).coerceAtLeast(1)
        return (100 - (y - radius).toDouble() / span * 100).roundToInt()
            .coerceIn(0, 100)
    }

    private fun outerX() = radius
    private fun endX() = width - radius

    /* X of the outer -> inner join = outerWidth fraction of the total;
       left edge when off. */
    private fun joinX(): Int {
        val total = outerWidth + innerWidth
        if (outerWidth <= 0 || total <= 0) return radius
        val x = (outerWidth.toDouble() / total * width).roundToInt()
        return x.coerceIn(radius, width - radius)
    }

    /* Drag-X of the join → outerWidth: invert the join fraction with
       innerWidth fixed. */
    private fun xToOuterWidth(x: Int): Int {
        val f =
            (x.toDouble() / width).coerceIn(0.0, 0.98)   // cap so 1-f never hits 0
        val iw = innerWidth.coerceAtLeast(1)
        return (f * iw / (1 - f)).roundToInt().coerceIn(0, maxWidth)
    }

    private fun handleAt(x: Int, y: Int): Handle? {
        data class C(val h: Handle, val cx: Int, val cy: Int)

        val candidates = buildList {
            if (outerWidth > 0) {
                add(C(Handle.OUTER, outerX(), valueToY(outerAlpha)))
            }
            add(C(Handle.JOIN, joinX(), valueToY(innerAlpha)))
            add(C(Handle.END, endX(), valueToY(endAlpha)))
        }
        return candidates
            .map { it to hypot((x - it.cx).toDouble(), (y - it.cy).toDouble()) }
            .filter { it.second <= grab }
            .minByOrNull { it.second }
            ?.first?.h
    }

    private fun drag(handle: Handle, x: Int, y: Int) {
        when (handle) {
            Handle.OUTER -> onOuterAlpha?.invoke(yToValue(y))
            Handle.END -> onEndAlpha?.invoke(yToValue(y))
            Handle.JOIN -> {
                onInnerAlpha?.invoke(yToValue(y))
                onOuterWidth?.invoke(xToOuterWidth(x))
            }
        }
    }

    // ── painting ──────────────────────────────────────────────────────────────

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        g2.setRenderingHint(
            RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON
        )
        val w = width
        val h = height

        // Checkerboard background so the segments' opacity is visible.
        val cell = 7
        val c1 = JBColor(Color(0xFF, 0xFF, 0xFF), Color(0x55, 0x58, 0x5A))
        val c2 = JBColor(Color(0xC9, 0xC9, 0xC9), Color(0x3C, 0x3F, 0x41))
        var cy = 0
        while (cy < h) {
            var cx = 0
            while (cx < w) {
                g2.color = if (((cx / cell) + (cy / cell)) % 2 == 0) c1 else c2
                g2.fillRect(cx, cy, minOf(cell, w - cx), minOf(cell, h - cy))
                cx += cell
            }
            cy += cell
        }

        val t = rows.size
        if (t > 0) {
            for (j in 0 until t) {
                val x0 = j * w / t
                val x1 = (j + 1) * w / t
                val a = rows[j].coerceIn(0, 255)
                g2.color = Color(color.red, color.green, color.blue, a)
                g2.fillRect(x0, 0, x1 - x0, h)
                g2.color = JBColor.foreground()
                val y = (h - 1 - a / 255.0 * (h - 1)).toInt()
                g2.fillRect(x0, y, (x1 - x0).coerceAtLeast(1), 2)
            }
            // faint separators
            g2.color = JBColor(Color(0, 0, 0, 40), Color(255, 255, 255, 40))
            for (j in 1 until t) {
                val x = j * w / t; g2.drawLine(x, 0, x, h)
            }
        }

        // Draggable handles on top of the profile.
        if (outerWidth > 0) {
            drawHandle(g2, outerX(), valueToY(outerAlpha))
        }
        drawHandle(g2, joinX(), valueToY(innerAlpha))
        drawHandle(g2, endX(), valueToY(endAlpha))

        g2.dispose()
    }

    private fun drawHandle(g2: Graphics2D, cx: Int, cy: Int) {
        val d = radius * 2
        g2.color = JBColor(Color(0xFF, 0xFF, 0xFF), Color(0x2B, 0x2D, 0x30))
        g2.fillOval(cx - radius, cy - radius, d, d)
        g2.color = JBColor.foreground()
        g2.stroke = BasicStroke(1.5f)
        g2.drawOval(cx - radius, cy - radius, d, d)
    }
}
