package com.prezdev.devtour.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.PopupHandler
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.tree.TreeUtil
import com.prezdev.devtour.state.DevTourFile
import com.prezdev.devtour.state.DevTourListener
import com.prezdev.devtour.state.DevTourManager
import com.prezdev.devtour.state.DevTourStep
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeSelectionModel

class DevTourToolWindowFactory : ToolWindowFactory {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = DevTourToolWindowPanel(project)
        val content = toolWindow.contentManager.factory.createContent(panel, null, false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }
}

class DevTourToolWindowPanel(
    private val project: Project
) : SimpleToolWindowPanel(true, true), Disposable, DataProvider {

    private val manager = DevTourManager.getInstance(project)
    private val rootNode = DefaultMutableTreeNode()
    private val treeModel = DefaultTreeModel(rootNode)
    private val tree = Tree(treeModel)

    init {
        tree.isRootVisible = false
        tree.selectionModel.selectionMode = TreeSelectionModel.SINGLE_TREE_SELECTION
        tree.cellRenderer = DevTourTreeCellRenderer()
        tree.emptyText.text = "No hay DevTours todavía."
        TreeSpeedSearch(tree) { path ->
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return@TreeSpeedSearch ""
            when (val payload = node.userObject) {
                is DevTourNode.TourNode -> payload.tour.name
                is DevTourNode.StepNode -> payload.step.file ?: payload.step.description ?: ""
                else -> ""
            }
        }
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val node = tree.getNodeForLocation(e.x, e.y) ?: return
                    when (val payload = node.userObject) {
                        is DevTourNode.StepNode -> manager.openStep(payload.step, true)
                        is DevTourNode.TourNode -> manager.setActiveTour(payload.tour.id)
                    }
                }
            }
        })
        PopupHandler.installPopupHandler(tree, "DevTour.Tree.Popup", ActionPlaces.POPUP)

        val scrollPane = JBScrollPane(tree)
        setContent(scrollPane)
        toolbar = createToolbar()

        manager.addListener(DevTourListener { data ->
            rebuildTree(data)
        }, this)
        rebuildTree(manager.state())
    }

    override fun dispose() = Unit

    override fun getData(dataId: String): Any? {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return null
        val payload = node.userObject
        return when {
            DevTourDataKeys.TOUR.`is`(dataId) && payload is DevTourNode.TourNode -> payload.tour
            DevTourDataKeys.STEP.`is`(dataId) && payload is DevTourNode.StepNode -> payload.step
            else -> null
        }
    }

    private fun createToolbar(): JComponent {
        val actionManager = ActionManager.getInstance()
        val actionIds = listOf(
            "DevTour.Start",
            "DevTour.Stop",
            null,
            "DevTour.Previous",
            "DevTour.Next",
            null,
            "DevTour.CreateTour",
            "DevTour.SelectTour",
            null,
            "DevTour.AddStep",
            "DevTour.DeleteStep",
            null,
            "DevTour.Refresh",
            "DevTour.OpenConfig"
        )
        val group = DefaultActionGroup()
        actionIds.forEach { id ->
            if (id == null) {
                group.addSeparator()
            } else {
                actionManager.getAction(id)?.let { group.add(it) }
            }
        }
        return actionManager.createActionToolbar("DevTourToolWindowToolbar", group, true).apply {
            targetComponent = tree
        }.component
    }

    private fun rebuildTree(data: DevTourFile) {
        ApplicationManager.getApplication().invokeLater {
            val newRoot = DefaultMutableTreeNode()
            data.tours.forEach { tour ->
                val tourNode = DefaultMutableTreeNode(
                    DevTourNode.TourNode(tour, data.activeTourId == tour.id)
                )
                tour.steps
                    .sortedWith(
                        compareBy<com.prezdev.devtour.state.DevTourStep> { it.order ?: Int.MAX_VALUE }
                            .thenBy { it.file ?: "" }
                            .thenBy { it.line ?: Int.MAX_VALUE }
                    )
                    .forEach { step ->
                        tourNode.add(
                            DefaultMutableTreeNode(
                                DevTourNode.StepNode(step, tour.id, data.activeTourId == tour.id)
                            )
                        )
                    }
                newRoot.add(tourNode)
            }
            treeModel.setRoot(newRoot)
            treeModel.reload()
            TreeUtil.expandAll(tree)
        }
    }

    private fun Tree.getNodeForLocation(x: Int, y: Int): DefaultMutableTreeNode? {
        val row = getRowForLocation(x, y)
        if (row == -1) return null
        val path = getPathForRow(row) ?: return null
        return path.lastPathComponent as? DefaultMutableTreeNode
    }
}
