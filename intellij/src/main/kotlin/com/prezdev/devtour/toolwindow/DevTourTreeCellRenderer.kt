package com.prezdev.devtour.toolwindow

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode

class DevTourTreeCellRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ) {
        val node = (value as? DefaultMutableTreeNode)?.userObject as? DevTourNode ?: return
        clear()
        when (node) {
            is DevTourNode.TourNode -> {
                icon = if (node.isActive) AllIcons.Nodes.Plugin else AllIcons.Nodes.Folder
                val attributes = if (node.isActive) {
                    SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
                } else {
                    SimpleTextAttributes.REGULAR_ATTRIBUTES
                }
                append(node.tour.name, attributes)
                if (node.isActive) {
                    append("  (activa)", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
            }
            is DevTourNode.StepNode -> {
                icon = if (node.tourIsActive) AllIcons.Actions.Forward else AllIcons.Actions.Lightning
                val label = node.step.file?.substringAfterLast('/') ?: "Paso"
                val lineText = node.step.line?.let { ":$it" } ?: ""
                append("$label$lineText", SimpleTextAttributes.REGULAR_ATTRIBUTES)
                node.step.description?.takeIf { it.isNotBlank() }?.let {
                    append("  — $it", SimpleTextAttributes.GRAYED_ATTRIBUTES)
                }
            }
        }
    }
}
