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

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction

/**
 * Tools-menu action to turns the frame on/off easily
 */
class ToggleColorFrameAction : ToggleAction() {

    override fun isSelected(ev: AnActionEvent): Boolean {
        val project = ev.project ?: return false

        return ProjectColorSettings.getInstance(project).enabled
    }

    override fun setSelected(ev: AnActionEvent, state: Boolean) {
        val project = ev.project ?: return

        ProjectColorSettings.getInstance(project).enabled = state
        if (state) {
            ProjectColorApplier.apply(project)
        } else {
            ProjectColorApplier.clear(project)
        }
    }

    override fun update(ev: AnActionEvent) {
        super.update(ev)
        ev.presentation.isEnabled = ev.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread =
        ActionUpdateThread.BGT
}
