package me.bitlinker.walkee.ui.screens.home

sealed interface HomeAction {
    // From the UI
    data object TrackingToggled : HomeAction
    data object RecenterClicked : HomeAction
    data object SettingsClicked : HomeAction
    data object PermissionRequestLaunched : HomeAction
    data class PermissionResult(val granted: Boolean) : HomeAction

    // From use cases
    data class PermissionChanged(val granted: Boolean) : HomeAction
    data class TrackingChanged(val isTracking: Boolean) : HomeAction
    data class FollowUserChanged(val followUser: Boolean) : HomeAction
    data class ProgressChanged(val visitedCells: Long, val areaSquareKilometres: Double) : HomeAction
}
