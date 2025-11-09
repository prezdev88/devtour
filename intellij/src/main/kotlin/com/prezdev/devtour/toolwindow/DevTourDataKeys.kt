package com.prezdev.devtour.toolwindow

import com.intellij.openapi.actionSystem.DataKey
import com.prezdev.devtour.state.DevTourStep
import com.prezdev.devtour.state.DevTourTour

object DevTourDataKeys {
    val TOUR: DataKey<DevTourTour> = DataKey.create("DEV_TOUR_TOUR")
    val STEP: DataKey<DevTourStep> = DataKey.create("DEV_TOUR_STEP")
}
