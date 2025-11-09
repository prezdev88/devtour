package com.prezdev.devtour.state

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.ui.JBColor
import com.prezdev.devtour.ui.DevTourGutterRenderer
import com.prezdev.devtour.ui.DevTourNotifications
import java.nio.file.Path
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.exists

@Service(Service.Level.PROJECT)
class DevTourManager(private val project: Project) : Disposable {

    companion object {
        fun getInstance(project: Project): DevTourManager = project.service()
    }

    private val listeners = CopyOnWriteArrayList<DevTourListener>()
    private val writeLock = Any()
    private var currentData: DevTourFile = DevTourDataParser.load(devTourFilePath())
    private var sessionTourId: String? = null
    private var sessionIndex: Int = -1
    private var highlightedEditor: Editor? = null
    private var activeHighlighter: RangeHighlighter? = null

    init {
        listenToConfigChanges()
        listenToEditorLifecycle()
    }

    fun state(): DevTourFile = currentData

    fun getTours(): List<DevTourTour> = currentData.tours

    fun getActiveTour(): DevTourTour? = currentData.activeTour()

    fun addListener(listener: DevTourListener, parent: Disposable? = null) {
        listeners.add(listener)
        if (parent != null) {
            Disposer.register(parent) { listeners.remove(listener) }
        }
    }

    fun removeListener(listener: DevTourListener) {
        listeners.remove(listener)
    }

    fun refreshFromDisk() {
        reloadState()
        DevTourNotifications.info(project, "DevTour recargado desde disco.")
    }

    fun ensureConfigFile(): Path? {
        val path = devTourFilePath() ?: return null
        if (!path.exists()) {
            val defaultTour = DevTourTour(name = "Main Tour")
            val bootstrap = DevTourFile(
                tours = mutableListOf(defaultTour),
                activeTourId = defaultTour.id
            )
            saveData(bootstrap)
            currentData = bootstrap
            notifyListeners()
        }
        return path
    }

    fun openConfigFile() {
        val path = ensureConfigFile() ?: return warnNoProject()
        val virtual = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
        if (virtual != null) {
            FileEditorManager.getInstance(project).openFile(virtual, true)
        } else {
            DevTourNotifications.warn(project, "No pude abrir ${path.toAbsolutePath()}")
        }
    }

    fun createTour(name: String): DevTourTour? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            DevTourNotifications.warn(project, "El nombre de la DevTour no puede estar vacío.")
            return null
        }
        var created: DevTourTour? = null
        mutateData {
            val tour = DevTourTour(name = trimmed)
            it.tours.add(tour)
            it.activeTourId = tour.id
            created = tour
        }
        created?.let {
            DevTourNotifications.info(project, "DevTour \"${it.name}\" creada.")
        }
        return created
    }

    fun setActiveTour(tourId: String) {
        if (currentData.activeTourId == tourId) return
        mutateData {
            if (it.tours.any { tour -> tour.id == tourId }) {
                it.activeTourId = tourId
            }
        }
        DevTourNotifications.info(project, "DevTour activa cambiada.")
    }

    fun addStep(relativeFilePath: String, line: Int, description: String?, tourId: String): DevTourStep? {
        var added: DevTourStep? = null
        mutateData { data ->
            val targetTour = data.tours.firstOrNull { it.id == tourId }
            if (targetTour == null) {
                DevTourNotifications.warn(project, "No se encontró la DevTour seleccionada.")
                return@mutateData
            }
            val order = (targetTour.steps.maxOfOrNull { it.order ?: 0 } ?: 0) + 1
            val step = DevTourStep(
                id = UUID.randomUUID().toString(),
                file = relativeFilePath,
                line = line,
                description = description?.takeIf { it.isNotBlank() },
                order = order,
                tourId = targetTour.id
            )
            targetTour.steps.add(step)
            data.activeTourId = targetTour.id
            added = step
        }
        added?.let {
            DevTourNotifications.info(
                project,
                "Paso agregado a la DevTour en la línea ${it.line ?: "?"}."
            )
        }
        return added
    }

    fun deleteStep(stepId: String, tourId: String) {
        mutateData { data ->
            val tour = data.tours.firstOrNull { it.id == tourId } ?: return@mutateData
            val originalIndex = tour.steps.indexOfFirst { it.id == stepId }
            val removed = tour.steps.removeIf { it.id == stepId }
            if (removed) {
                tour.steps.sortBy { it.order ?: Int.MAX_VALUE }
                tour.steps.forEachIndexed { idx, step ->
                    tour.steps[idx] = step.copy(order = idx + 1)
                }
                if (sessionTourId == tour.id && originalIndex >= 0) {
                    sessionIndex = sessionIndex.coerceAtMost(tour.steps.size - 1)
                }
            }
        }
    }

    fun selectTour(): DevTourTour? = getActiveTour()

    fun startTour(): DevTourStep? {
        val tour = getActiveTour()
        if (tour == null) {
            DevTourNotifications.warn(project, "No hay DevTour activa.")
            return null
        }
        if (tour.steps.isEmpty()) {
            DevTourNotifications.warn(project, "La DevTour no tiene pasos.")
            return null
        }
        sessionTourId = tour.id
        sessionIndex = 0
        val firstStep = tour.steps.first()
        DevTourNotifications.info(project, "DevTour iniciada (${tour.name}).")
        openStep(firstStep, true)
        showStepNotification(tour, firstStep, sessionIndex)
        return firstStep
    }

    fun nextStep(): DevTourStep? {
        val tour = ensureSessionTour() ?: return null
        if (sessionIndex + 1 >= tour.steps.size) {
            DevTourNotifications.info(project, "Ya estás en el último paso.")
            return null
        }
        sessionIndex++
        val step = tour.steps[sessionIndex]
        openStep(step, true)
        showStepNotification(tour, step, sessionIndex)
        return step
    }

    fun previousStep(): DevTourStep? {
        val tour = ensureSessionTour() ?: return null
        if (sessionIndex - 1 < 0) {
            DevTourNotifications.info(project, "Ya estás en el primer paso.")
            return null
        }
        sessionIndex--
        val step = tour.steps[sessionIndex]
        openStep(step, true)
        showStepNotification(tour, step, sessionIndex)
        return step
    }

    fun stopTour() {
        sessionTourId = null
        sessionIndex = -1
        clearHighlight()
        DevTourNotifications.info(project, "DevTour detenida.")
    }

    fun openStep(step: DevTourStep, requestFocus: Boolean) {
        val projectPath = project.basePath
        if (projectPath == null) {
            warnNoProject()
            return
        }
        val relative = step.file
        if (relative.isNullOrBlank()) {
            DevTourNotifications.warn(project, "El paso no tiene ruta asociada.")
            return
        }
        val nioPath = Paths.get(projectPath, relative).normalize()
        val virtual = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(nioPath)
            ?: run {
                DevTourNotifications.warn(project, "No pude abrir $relative")
                return
            }

        val targetLine = (step.line ?: 1) - 1
        val descriptor = OpenFileDescriptor(project, virtual, targetLine.coerceAtLeast(0), 0)
        val editor = FileEditorManager.getInstance(project).openTextEditor(descriptor, requestFocus)
        if (editor != null) {
            highlightStep(editor, step, targetLine)
        }
    }

    override fun dispose() {
        clearHighlight()
    }

    fun findTour(tourId: String?): DevTourTour? =
        currentData.tours.firstOrNull { it.id == tourId }

    private fun ensureSessionTour(): DevTourTour? {
        val tourId = sessionTourId
        if (tourId == null) {
            val started = startTour()
            return if (started != null) getActiveTour() else null
        }
        return currentData.tours.firstOrNull { it.id == tourId } ?: run {
            DevTourNotifications.warn(project, "La DevTour en ejecución ya no existe.")
            stopTour()
            null
        }
    }

    private fun devTourFilePath(): Path? {
        val base = project.basePath ?: return null
        return Paths.get(base, ".devtour", "devtour.json")
    }

    private fun saveData(data: DevTourFile) {
        val path = devTourFilePath() ?: run {
            warnNoProject()
            return
        }
        runWriteAction {
            DevTourDataParser.save(path, data)
        }
        LocalFileSystem.getInstance().refreshAndFindFileByNioFile(path)
    }

    private fun mutateData(block: (DevTourFile) -> Unit) {
        synchronized(writeLock) {
            block(currentData)
            saveData(currentData)
            reloadState(notify = true)
        }
    }

    private fun reloadState(notify: Boolean = true) {
        val latest = DevTourDataParser.load(devTourFilePath())
        currentData = latest
        if (notify) {
            notifyListeners()
        }
    }

    private fun notifyListeners() {
        listeners.forEach { it.onStateChanged(currentData) }
    }

    private fun highlightStep(editor: Editor, step: DevTourStep, targetLine: Int) {
        clearHighlight()
        val document = editor.document
        if (targetLine < 0 || targetLine >= document.lineCount) return
        val lineRange: TextRange = TextRange(
            document.getLineStartOffset(targetLine),
            document.getLineEndOffset(targetLine)
        )
        val attributes = TextAttributes().apply {
            effectType = com.intellij.openapi.editor.markup.EffectType.ROUNDED_BOX
            effectColor = JBColor(0xFFE6B0, 0x664400)
            backgroundColor = JBColor(0xFFF7D6, 0x4B3621)
        }
        val highlighter = editor.markupModel.addRangeHighlighter(
            lineRange.startOffset,
            lineRange.endOffset,
            HighlighterLayer.SELECTION - 1,
            attributes,
            HighlighterTargetArea.EXACT_RANGE
        )
        highlighter.gutterIconRenderer = DevTourGutterRenderer(step)
        highlightedEditor = editor
        activeHighlighter = highlighter
    }

    private fun clearHighlight() {
        val editor = highlightedEditor
        val highlighter = activeHighlighter
        if (editor != null && highlighter != null && !editor.isDisposed) {
            editor.markupModel.removeHighlighter(highlighter)
        }
        activeHighlighter = null
        highlightedEditor = null
    }

    private fun listenToConfigChanges() {
        val connection = project.messageBus.connect(this)
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                val path = devTourFilePath()?.let { FileUtil.toSystemIndependentName(it.toString()) } ?: return
                if (events.any { FileUtil.toSystemIndependentName(it.path) == path }) {
                    reloadState()
                }
            }
        })
    }

    private fun listenToEditorLifecycle() {
        EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
            override fun editorReleased(event: EditorFactoryEvent) {
                if (event.editor == highlightedEditor) {
                    clearHighlight()
                }
            }
        }, this)
    }

    private fun warnNoProject() {
        DevTourNotifications.warn(project, "No hay un proyecto abierto para resolver la ruta de .devtour.")
    }

    private fun showStepNotification(tour: DevTourTour, step: DevTourStep, index: Int) {
        val total = tour.steps.size
        val title = "${tour.name} — Paso ${index + 1} de $total"
        val details = buildString {
            val description = step.description?.takeIf { it.isNotBlank() }
            if (description != null) {
                append(description)
            } else if (!step.file.isNullOrBlank()) {
                append(step.file)
                step.line?.let { append(":$it") }
            } else {
                append("Sin descripción")
            }
        }
        DevTourNotifications.step(project, title, details)
    }
}
