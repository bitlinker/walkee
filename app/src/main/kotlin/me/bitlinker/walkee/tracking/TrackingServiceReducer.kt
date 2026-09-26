package me.bitlinker.walkee.tracking

/** Builds the next state field by field; each field has its own reducer (ADR 0005). */
fun reduceTrackingService(state: TrackingServiceState, action: TrackingServiceAction): TrackingServiceState = state.copy(
    areaSquareKilometres = reduceAreaSquareKilometres(state.areaSquareKilometres, action),
    isFinished = reduceIsFinished(state.isFinished, action),
)

private fun reduceAreaSquareKilometres(area: Double, action: TrackingServiceAction): Double = when (action) {
    is TrackingServiceAction.ProgressChanged -> action.areaSquareKilometres
    else -> area
}

/**
 * Follows tracking only: a pause or a refused start end the service through the tracking stop
 * they cause, once it has been saved.
 */
private fun reduceIsFinished(finished: Boolean, action: TrackingServiceAction): Boolean = when (action) {
    is TrackingServiceAction.TrackingChanged -> !action.isTracking
    else -> finished
}
