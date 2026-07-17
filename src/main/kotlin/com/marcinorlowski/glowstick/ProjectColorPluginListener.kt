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

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.ProjectManager

/**
 * Draws the frame right after the plugin is installed into a RUNNING IDE
 * (dynamic load), for every already-open project as  the case
 * `postStartupActivity` may miss. Fires on the plugin's own `pluginLoaded`.
 */
class ProjectColorPluginListener : DynamicPluginListener {

    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        val ours = PluginManager.getPluginByClass(javaClass)?.pluginId
        if (pluginDescriptor.pluginId != ours) return
        ApplicationManager.getApplication().invokeLater {
            for (p in ProjectManager.getInstance().openProjects) {
                if (p.isDisposed) continue
                if (ProjectColorSettings.getInstance(p).enabled) {
                    ProjectColorApplier.apply(p)
                }
            }
        }
    }
}
