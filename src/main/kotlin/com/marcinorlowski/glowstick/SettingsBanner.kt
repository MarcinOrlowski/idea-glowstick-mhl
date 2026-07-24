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

import com.intellij.icons.AllIcons
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.util.IconLoader
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.IconUtil
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * The project banner (shows some project details and action buttons)
 */
internal object SettingsBanner {

    private const val MARKETPLACE_URL = "https://plugins.jetbrains.com/"

    fun build(): JComponent {
        // Metadata is read from the bundled /glowstick-build.properties (written by the
        // generateBuildInfo Gradle task) rather than the internal PluginManager API.
        val meta = loadBuildInfo()
        val name = meta.getProperty("name")?.takeIf { it.isNotBlank() } ?: "GlowStick MHL"
        val vendor = meta.getProperty("vendor")?.takeIf { it.isNotBlank() }
        val version = meta.getProperty("version")?.takeIf { it.isNotBlank() }
        // Repo URL only - project links are derived from it.
        val url = meta.getProperty("url")?.takeIf { it.isNotBlank() }

        val raw = IconLoader.getIcon("/logo.png", javaClass)
        val logo = JBLabel(IconUtil.scale(raw, null, 56f / raw.iconWidth))

        // Name in the big bold font; the "vX.Y.Z" and author name in the normal
        // (smaller) label font. GridBagLayout with a BASELINE anchor drops them all
        // onto the title's text baseline instead of vertically centering them.
        val nameLabel = JBLabel(name).apply {
            font = font.deriveFont(Font.BOLD, (font.size2D + 3f) * 1.25f * 1.25f)
        }
        val titleRow = JPanel(java.awt.GridBagLayout()).apply {
            isOpaque = false
            val gbc = java.awt.GridBagConstraints().apply {
                gridy = 0
                anchor = java.awt.GridBagConstraints.BASELINE
            }
            add(nameLabel, gbc)
            version?.let { add(JBLabel(" v$it"), gbc) }
            vendor?.let {
                add(JBLabel(" · ").apply { foreground = JBColor.GRAY }, gbc)
                add(JBLabel(it), gbc)
            }
            add(javax.swing.Box.createHorizontalGlue(), gbc.apply { weightx = 1.0 })
        }

        val bi = meta.getProperty("build")?.takeIf { it.isNotBlank() }
            ?: version
            ?: "unknown"
        val buildLabel = JBLabel(bi)
        buildLabel.apply {
            foreground = JBColor.GRAY
            toolTipText = "Click to copy build info"
            cursor =
                java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR)
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent?) {
                    copyToClipboard(bi, buildLabel)
                }
            })
        }

        titleRow.alignmentX = java.awt.Component.LEFT_ALIGNMENT
        buildLabel.alignmentX = java.awt.Component.LEFT_ALIGNMENT
        val rowGap = javax.swing.Box.createVerticalStrut(6)
        val info = javax.swing.Box.createVerticalBox().apply {
            add(javax.swing.Box.createVerticalGlue())
            add(titleRow)
            add(rowGap)
            add(buildLabel)
            add(javax.swing.Box.createVerticalGlue())
        }

        val buttons = javax.swing.Box.createVerticalBox().apply {
            add(javax.swing.Box.createVerticalGlue())
//            add(javax.swing.Box.createHorizontalBox().apply {
            // GitHub/releases button hidden for now.
            // if (url != null) {
            //     add(iconButton(AllIcons.Vcs.Vendors.Github, "See project releases") {
            //         BrowserUtil.browse("$url/releases")
            //     })
            //     add(javax.swing.Box.createHorizontalStrut(6))
            // }
//                if (url != null) {
//                    add(iconButton(
//                        AllIcons.Toolwindows.ToolWindowDebugger,
//                        "Share ideas or report a bug",
//                    ) { BrowserUtil.browse("$url/issues") })
//                    add(javax.swing.Box.createHorizontalStrut(6))
//                }
//                // Constant link (not derived from the repo url) - always shown.
//                add(iconButton(AllIcons.Nodes.Plugin,
//                    "View on JetBrains Marketplace") {
//                    BrowserUtil.browse(MARKETPLACE_URL)
//                })
//                if (url != null) {
//                    add(javax.swing.Box.createHorizontalStrut(6))
//                    add(iconButton(AllIcons.General.Web,
//                        "Visit project page") {
//                        BrowserUtil.browse(url)
//                    })
//                }
//            })
//            add(javax.swing.Box.createVerticalGlue())
        }

        return JPanel(BorderLayout(12, 0)).apply {
            border = JBUI.Borders.emptyBottom(14)
            add(logo, BorderLayout.WEST)
            add(info, BorderLayout.CENTER)
            add(buttons, BorderLayout.EAST)
        }
    }

    /**
     * Square icon-only action buttons
     */
    private fun iconButton(
        icon: javax.swing.Icon,
        tooltip: String,
        onClick: (JComponent) -> Unit,
    ): JComponent {
        val scaled = IconUtil.scale(icon, null, 2f)
        val w = scaled.iconWidth.coerceAtLeast(1)
        val h = scaled.iconHeight.coerceAtLeast(1)
        val img = java.awt.image.BufferedImage(
            w, h, java.awt.image.BufferedImage.TYPE_INT_ARGB
        )
        val g = img.createGraphics()
        scaled.paintIcon(null, g, 0, 0)
        g.composite = java.awt.AlphaComposite.SrcAtop
        g.color = JBColor.foreground()
        g.fillRect(0, 0, w, h)
        g.dispose()
        val button = javax.swing.JButton(javax.swing.ImageIcon(img))
        return button.apply {
            toolTipText = tooltip
            val side = 56   // matches the scaled logo height
            preferredSize = Dimension(side, side)
            minimumSize = preferredSize
            maximumSize = preferredSize
            addActionListener { onClick(button) }
        }
    }

    /** Load the bundled build-info resource (name/vendor/url/version/build). */
    private fun loadBuildInfo(): java.util.Properties {
        val props = java.util.Properties()
        javaClass.getResourceAsStream("/glowstick-build.properties")
            ?.use { props.load(it) }
        return props
    }

    private fun copyToClipboard(text: String, anchor: JComponent) {
        com.intellij.openapi.ide.CopyPasteManager.getInstance()
            .setContents(java.awt.datatransfer.StringSelection(text))
        com.intellij.openapi.ui.popup.JBPopupFactory.getInstance()
            .createHtmlTextBalloonBuilder(
                "Build info copied to clipboard",
                com.intellij.openapi.ui.MessageType.INFO,
                null,
            )
            .setFadeoutTime(1500)
            .createBalloon()
            .show(
                com.intellij.ui.awt.RelativePoint.getSouthOf(anchor),
                com.intellij.openapi.ui.popup.Balloon.Position.below,
            )
    }
}
