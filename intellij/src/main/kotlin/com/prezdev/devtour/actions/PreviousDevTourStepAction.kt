package com.prezdev.devtour.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent

class PreviousDevTourStepAction : DevTourAction(
    "DevTour: Previous Step",
    "Regresa al paso anterior de la DevTour",
    AllIcons.Actions.Back
) {
    override fun actionPerformed(e: AnActionEvent) {
        manager(e)?.previousStep()
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null
    }
}
