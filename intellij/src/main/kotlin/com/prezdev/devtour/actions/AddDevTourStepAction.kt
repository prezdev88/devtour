package com.prezdev.devtour.actions

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.util.io.FileUtil
import com.prezdev.devtour.ui.DevTourDialogs
import com.prezdev.devtour.ui.DevTourNotifications

class AddDevTourStepAction : DevTourAction(
    "DevTour: Add Step",
    "Agrega la línea actual como un paso de DevTour",
    AllIcons.Actions.AddList
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val editor = e.getData(CommonDataKeys.EDITOR)
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val basePath = project.basePath
        val devTourManager = manager(e)

        if (editor == null || file == null || basePath == null || devTourManager == null) {
            DevTourNotifications.warn(project, "Necesitas un archivo abierto dentro del proyecto para crear un paso.")
            return
        }

        val relativePath = FileUtil.getRelativePath(basePath, file.path, '/')?.replace('\\', '/')
        if (relativePath.isNullOrEmpty()) {
            DevTourNotifications.warn(project, "El archivo debe estar dentro del proyecto para crear un paso.")
            return
        }

        val tour = DevTourDialogs.chooseTour(project, devTourManager, "Agregar paso") ?: return
        val description = DevTourDialogs.askStepDescription(project)
        val line = editor.caretModel.logicalPosition.line + 1
        devTourManager.addStep(relativePath, line, description, tour.id)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = e.project != null && e.getData(CommonDataKeys.EDITOR) != null
    }
}
