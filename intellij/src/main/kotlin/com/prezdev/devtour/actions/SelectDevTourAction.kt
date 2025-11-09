package com.prezdev.devtour.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.prezdev.devtour.ui.DevTourDialogs

class SelectDevTourAction : DevTourAction(
    "DevTour: Select Tour",
    "Selecciona cuál DevTour estará activa",
    AllIcons.Actions.GroupBy
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val manager = manager(e) ?: return
        DevTourDialogs.chooseTour(project, manager, "Seleccionar DevTour", allowCreate = true)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
