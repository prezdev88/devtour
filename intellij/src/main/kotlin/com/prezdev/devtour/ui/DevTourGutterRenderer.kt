package com.prezdev.devtour.ui

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.prezdev.devtour.state.DevTourManager
import com.prezdev.devtour.state.DevTourStep
import javax.swing.Icon

class DevTourGutterRenderer(
    private val step: DevTourStep
) : GutterIconRenderer() {

    override fun getIcon(): Icon = DevTourIcons.Gutter

    override fun getTooltipText(): String? = step.description ?: "DevTour step"

    override fun getClickAction(): AnAction = object : AnAction() {
        override fun actionPerformed(e: AnActionEvent) {
            val project = e.project ?: return
            DevTourManager.getInstance(project).openStep(step, true)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DevTourGutterRenderer) return false
        return step.id == other.step.id
    }

    override fun hashCode(): Int = step.id.hashCode()
}
