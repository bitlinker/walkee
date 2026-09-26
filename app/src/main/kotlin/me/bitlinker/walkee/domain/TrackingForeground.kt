package me.bitlinker.walkee.domain

/**
 * Brings up the foreground service that keeps the process alive while [TrackingSession] runs
 * (ADR 0006). Implemented by the service's package; the service stops itself once tracking stops.
 */
fun interface TrackingForeground {
    /** False when the system does not allow a foreground service right now (a background start). */
    fun start(): Boolean
}
