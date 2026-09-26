package me.bitlinker.walkee.tracking

data class TrackingServiceState(
    val areaSquareKilometres: Double = 0.0,
    /** Set once tracking is off: the service then leaves the foreground and stops itself. */
    val isFinished: Boolean = false,
)
