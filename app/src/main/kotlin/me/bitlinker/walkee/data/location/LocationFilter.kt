package me.bitlinker.walkee.data.location

/**
 * Rejects fixes that cannot be trusted to reveal the map: poor accuracy, stale or out-of-order
 * samples, and jumps implying impossible speed (multipath in the city centre, tunnel exits).
 * Pure and stateless, so it is unit-tested directly.
 */
class LocationFilter(
    private val maxAccuracyMetres: Float = DEFAULT_MAX_ACCURACY_METRES,
    private val maxSpeedMetresPerSecond: Double = DEFAULT_MAX_SPEED_MPS,
) {
    fun accept(previous: LocationFix?, next: LocationFix): Boolean {
        if (next.accuracyMetres <= 0f || next.accuracyMetres > maxAccuracyMetres) return false
        if (previous == null) return true
        val elapsedSeconds = (next.timeMillis - previous.timeMillis) / 1000.0
        if (elapsedSeconds <= 0.0) return false
        val distance = GeoDistance.metres(previous.point, next.point)
        // Allow for both fixes' uncertainty before judging the implied speed.
        val slack = previous.accuracyMetres + next.accuracyMetres
        return (distance - slack) / elapsedSeconds <= maxSpeedMetresPerSecond
    }

    companion object {
        const val DEFAULT_MAX_ACCURACY_METRES = 60f
        /** ~200 km/h: still lets trains through but drops GPS teleports. */
        const val DEFAULT_MAX_SPEED_MPS = 55.0
    }
}
