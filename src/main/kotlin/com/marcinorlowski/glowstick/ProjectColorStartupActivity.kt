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

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.AnActionResult
import com.intellij.openapi.actionSystem.ex.AnActionListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.wm.WindowManager
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.JFrame
import javax.swing.SwingUtilities

/**
 * Draws the frame for a project and auto-refreshes on IDE color change.
 *
 * Auto-refresh: the color is changed by `ChangeProjectColorAction`. No usable
 * listener/topic seem to exists in current builds - `LafManagerListener` fires
 * mid-change (reads the OLD color); `RECENT_PROJECTS_CHANGE_TOPIC` /
 * `UISettingsListener` don't drive a redraw; `PROJECT_COLOR_CHANGE_TOPIC` exists
 * only in a newer platform. So we hook to `AnActionListener` and re-apply after
 * any action whose class mentions "ProjectColor".
 */
class ProjectColorStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        subscribeToColorChanges(project)   // message-bus connect is thread-safe
        // Frame access + listener attach must be on the EDT (execute()
        // runs on a bg coroutine).
        if (ProjectColorSettings.getInstance(project).enabled) {
            SwingUtilities.invokeLater { waitForFrame(project) }
        }
    }

    private fun subscribeToColorChanges(project: Project) {
        ApplicationManager.getApplication().messageBus.connect(project).subscribe(
            AnActionListener.TOPIC,
            object : AnActionListener {
                override fun afterActionPerformed(
                    action: AnAction,
                    event: AnActionEvent,
                    result: AnActionResult,
                ) {
                    if (action.javaClass.name.contains("ProjectColor")) {
                        ApplicationManager.getApplication()
                            .invokeLater { reapplyAll() }
                    }
                }
            }
        )
    }

    private fun reapplyAll() {
        for (p in ProjectManager.getInstance().openProjects) {
            if (p.isDisposed) continue
            if (ProjectColorSettings.getInstance(p).enabled) {
                ProjectColorApplier.apply(p)
            }
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
