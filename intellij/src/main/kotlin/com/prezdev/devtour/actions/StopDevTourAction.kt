package com.prezdev.devtour.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent

class StopDevTourAction : DevTourAction(
    "DevTour: Stop",
    "Detiene la DevTour en ejecución",
    AllIcons.Actions.Suspend
) {
    override fun actionPerformed(e: AnActionEvent) {
        manager(e)?.stopTour()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
