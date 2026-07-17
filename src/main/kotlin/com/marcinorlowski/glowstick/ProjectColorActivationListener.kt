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

import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.wm.IdeFrame

/**
 * Listens for any IntelliJ window gaining focus.
 */
class ProjectColorActivationListener : ApplicationActivationListener {

    override fun applicationActivated(ideFrame: IdeFrame) {
        val project = ideFrame.project ?: return
        if (project.isDisposed) return

        val settings = ProjectColorSettings.getInstance(project)
        if (!settings.enabled) return

        ProjectColorApplier.apply(project)
    }
}
