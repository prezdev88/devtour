package com.prezdev.devtour.toolwindow

import com.prezdev.devtour.state.DevTourStep
import com.prezdev.devtour.state.DevTourTour

sealed interface DevTourNode {
    data class TourNode(val tour: DevTourTour, val isActive: Boolean) : DevTourNode
    data class StepNode(val step: DevTourStep, val tourId: String, val tourIsActive: Boolean) : DevTourNode
}
