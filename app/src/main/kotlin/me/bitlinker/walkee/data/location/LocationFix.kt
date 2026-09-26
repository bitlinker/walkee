package me.bitlinker.walkee.data.location

import me.bitlinker.walkee.fog.geo.GeoPoint

/** One accepted position sample. */
data class LocationFix(
    val point: GeoPoint,
    /** Horizontal accuracy radius, metres (68 % confidence). */
    val accuracyMetres: Float,
    /** Device-reported speed, m/s, when available. */
    val speedMetresPerSecond: Float?,
    /** Wall-clock time of the fix, epoch millis. */
    val timeMillis: Long,
)
