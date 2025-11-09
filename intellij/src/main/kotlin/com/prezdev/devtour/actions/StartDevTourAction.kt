package com.prezdev.devtour.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent

class StartDevTourAction : DevTourAction(
    "DevTour: Start Tour",
    "Inicia la DevTour activa",
    AllIcons.Actions.Execute
) {
    override fun actionPerformed(e: AnActionEvent) {
        manager(e)?.startTour()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
