package com.prezdev.devtour.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent

class NextDevTourStepAction : DevTourAction(
    "DevTour: Next Step",
    "Avanza al siguiente paso de la DevTour",
    AllIcons.Actions.Forward
) {
    override fun actionPerformed(e: AnActionEvent) {
        manager(e)?.nextStep()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
