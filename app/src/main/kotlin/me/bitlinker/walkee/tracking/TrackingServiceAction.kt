package me.bitlinker.walkee.tracking

sealed interface TrackingServiceAction {
    // From the notification and the system
    data object PauseClicked : TrackingServiceAction

    /** The system did not let the service into the foreground, so tracking cannot go on. */
    data object ForegroundRefused : TrackingServiceAction

    // From use cases
    data class TrackingChanged(val isTracking: Boolean) : TrackingServiceAction
    data class ProgressChanged(val areaSquareKilometres: Double) : TrackingServiceAction
}
