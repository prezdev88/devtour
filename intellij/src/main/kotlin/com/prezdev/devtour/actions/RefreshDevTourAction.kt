package com.prezdev.devtour.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent

class RefreshDevTourAction : DevTourAction(
    "DevTour: Refresh",
    "Recarga la configuración de DevTour",
    AllIcons.Actions.Refresh
) {
    override fun actionPerformed(e: AnActionEvent) {
        manager(e)?.refreshFromDisk()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
