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

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.WindowManager
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * Draws the frame when a project opens.
 */
class ProjectColorStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        // Frame access + listener attach must be on the EDT (execute()
        // runs on a bg coroutine).
        if (ProjectColorSettings.getInstance(project).enabled) {
            SwingUtilities.invokeLater { waitForFrame(project) }
        }
    }

    private fun waitForFrame(project: Project, attempt: Int = 0) {
        if (project.isDisposed) return
        val frame = WindowManager.getInstance().getFrame(project)
        if (frame != null && frame.isShowing) {
            attachFocusRefresh(project, frame)
            ProjectColorApplier.apply(project)
        } else if (attempt < 40) {
            SwingUtilities.invokeLater { waitForFrame(project, attempt + 1) }
        }
    }

    /** Re-apply when the frame regains focus */
    private fun attachFocusRefresh(project: Project, frame: JFrame) {
        if (frame.windowFocusListeners.any { it.javaClass.name.contains("ProjectColor") }) return
        frame.addWindowFocusListener(object : WindowAdapter() {
            override fun windowGainedFocus(e: WindowEvent?) {
                ProjectColorApplier.apply(project)
            }
        })
    }
}
