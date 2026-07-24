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
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.ProjectManager

/**
 * Draws the frame right after the plugin is installed into a RUNNING IDE
 * (dynamic load), for every already-open project as  the case
 * `postStartupActivity` may miss. Fires on the plugin's own `pluginLoaded`.
 */
class ProjectColorPluginListener : DynamicPluginListener {

    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        if (pluginDescriptor.pluginId != OUR_ID) return
        ApplicationManager.getApplication().invokeLater {
            for (p in ProjectManager.getInstance().openProjects) {
                if (p.isDisposed) continue
                if (ProjectColorSettings.getInstance(p).enabled) {
                    ProjectColorApplier.apply(p)
                }
            }
        }
    }

    companion object {
        // Must match <id> in META-INF/plugin.xml. Using a PluginId constant avoids the
        // internal PluginManager.getPluginByClass API just to learn our own id.
        private val OUR_ID = PluginId.getId("com.marcinorlowski.glowstick")
    }
}
