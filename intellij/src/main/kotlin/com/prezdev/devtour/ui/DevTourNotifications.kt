package com.prezdev.devtour.ui

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

object DevTourNotifications {
    private const val GROUP_ID = "DevTour Notifications"

    fun info(project: Project?, message: String) {
        notify(project, message, NotificationType.INFORMATION)
    }

    fun warn(project: Project?, message: String) {
        notify(project, message, NotificationType.WARNING)
    }

    fun error(project: Project?, message: String) {
        notify(project, message, NotificationType.ERROR)
    }

    fun step(project: Project?, title: String, message: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(title, message, NotificationType.INFORMATION)
            .notify(project)
    }

    private fun notify(project: Project?, message: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(message, type)
            .notify(project)
    }
}
