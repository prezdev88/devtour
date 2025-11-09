package com.prezdev.devtour.state

fun interface DevTourListener {
    fun onStateChanged(data: DevTourFile)
}
