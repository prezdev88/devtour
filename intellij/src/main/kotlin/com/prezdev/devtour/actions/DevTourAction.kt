package com.prezdev.devtour.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.prezdev.devtour.state.DevTourManager
import javax.swing.Icon

abstract class DevTourAction(
    text: String,
    description: String,
    icon: Icon? = null
) : AnAction(text, description, icon) {

    protected fun manager(e: AnActionEvent): DevTourManager? =
        e.project?.let { DevTourManager.getInstance(it) }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT
}
