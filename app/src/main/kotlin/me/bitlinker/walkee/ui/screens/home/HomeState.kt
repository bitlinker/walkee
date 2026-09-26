package me.bitlinker.walkee.ui.screens.home

data class HomeState(
    val hasLocationPermission: Boolean = false,
    val isTracking: Boolean = false,
    val followUser: Boolean = true,
    val visitedCells: Long = 0,
    val areaSquareKilometres: Double = 0.0,
    /** Set when the user asked to start without permission; cleared once the dialog is handled. */
    val permissionRequestPending: Boolean = false,
)
