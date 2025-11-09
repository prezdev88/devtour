package com.prezdev.devtour.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.prezdev.devtour.state.DevTourManager
import com.prezdev.devtour.state.DevTourTour
import java.util.LinkedHashMap

object DevTourDialogs {

    private const val CREATE_OPTION = "Crear nueva DevTour…"

    fun askStepDescription(project: Project): String? {
        return Messages.showInputDialog(
            project,
            "Ingresa una descripción para este paso (opcional).",
            "Descripción del paso",
            null
        )?.trim()
    }

    fun chooseTour(
        project: Project,
        manager: DevTourManager,
        title: String,
        allowCreate: Boolean = true
    ): DevTourTour? {
        while (true) {
            val tours = manager.getTours()
            if (tours.isEmpty()) {
                if (!allowCreate) {
                    Messages.showWarningDialog(
                        project,
                        "No existen DevTours. Crea una antes de continuar.",
                        title
                    )
                    return null
                }
                val name = promptTourName(project, "Tour 1") ?: return null
                val created = manager.createTour(name) ?: return null
                return created
            }

            val options = LinkedHashMap<String, DevTourTour?>()
            val activeTourId = manager.getActiveTour()?.id
            tours.forEach { tour ->
                val label = buildString {
                    append(tour.name)
                    append(" — ${tour.steps.size} paso${if (tour.steps.size == 1) "" else "s"}")
                    if (tour.id == activeTourId) {
                        append(" (activa)")
                    }
                }
                options[label] = tour
            }
            if (allowCreate) {
                options[CREATE_OPTION] = null
            }

            val labels = options.keys.toList()
            val defaultIndex = labels.indexOfFirst { label ->
                val tour = options[label]
                tour != null && tour.id == activeTourId
            }.takeIf { it >= 0 } ?: 0

            val choiceIndex = Messages.showDialog(
                project,
                "Selecciona una DevTour",
                title,
                labels.toTypedArray(),
                defaultIndex,
                null
            )

            if (choiceIndex < 0 || choiceIndex >= labels.size) {
                return null
            }

            val choice = labels[choiceIndex]

            if (choice == CREATE_OPTION) {
                val name = promptTourName(
                    project,
                    "Tour ${tours.size + 1}"
                ) ?: continue
                val created = manager.createTour(name) ?: continue
                return created
            }

            val selected = options[choice] ?: continue
            if (activeTourId != selected.id) {
                manager.setActiveTour(selected.id)
            }
            return selected
        }
    }

    fun promptTourName(project: Project, defaultValue: String = ""): String? {
        val name = Messages.showInputDialog(
            project,
            "Nombre de la DevTour",
            "Crear DevTour",
            null,
            defaultValue,
            null
        )?.trim()
        return name?.takeIf { it.isNotEmpty() }
    }

    fun confirmDeletion(project: Project, stepLabel: String): Boolean {
        val answer = Messages.showYesNoDialog(
            project,
            "¿Seguro que deseas eliminar el paso \"$stepLabel\"?",
            "Eliminar paso",
            Messages.getQuestionIcon()
        )
        return answer == Messages.YES
    }
}
