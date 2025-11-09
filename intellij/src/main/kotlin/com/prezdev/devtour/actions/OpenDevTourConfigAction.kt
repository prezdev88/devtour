package com.prezdev.devtour.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent

class OpenDevTourConfigAction : DevTourAction(
    "DevTour: Open Config",
    "Abre el archivo .devtour/devtour.json",
    AllIcons.General.Settings
) {
    override fun actionPerformed(e: AnActionEvent) {
        manager(e)?.openConfigFile()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
