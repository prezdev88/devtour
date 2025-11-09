package com.prezdev.devtour.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.prezdev.devtour.toolwindow.DevTourDataKeys
import com.prezdev.devtour.ui.DevTourDialogs
import com.prezdev.devtour.ui.DevTourNotifications

class DeleteDevTourStepAction : DevTourAction(
    "DevTour: Delete Step",
    "Eliminar el paso seleccionado",
    AllIcons.General.Remove
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val manager = manager(e) ?: return
        val step = e.getData(DevTourDataKeys.STEP) ?: return
        val tourId = step.tourId ?: manager.getActiveTour()?.id
        if (tourId == null) {
            DevTourNotifications.warn(project, "No pude determinar a qué DevTour pertenece este paso.")
            return
        }
        val label = buildString {
            step.file?.let { append(it) }
            step.line?.let {
                if (isNotEmpty()) append(':')
                append(it)
            }
            if (isEmpty()) append(step.id)
        }
        if (!DevTourDialogs.confirmDeletion(project, label)) return
        manager.deleteStep(step.id, tourId)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.getData(DevTourDataKeys.STEP) != null && e.project != null
    }
}
