package com.prezdev.devtour.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.prezdev.devtour.ui.DevTourDialogs

class CreateDevTourAction : DevTourAction(
    "DevTour: Create Tour",
    "Crea una nueva DevTour",
    AllIcons.Actions.NewFolder
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val manager = manager(e) ?: return
        val defaultName = "Tour ${manager.getTours().size + 1}"
        val name = DevTourDialogs.promptTourName(project, defaultName) ?: return
        manager.createTour(name)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
