package me.bitlinker.walkee.data.location

import me.bitlinker.walkee.fog.geo.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Great-circle distance on a sphere; accurate to ~0.3 % which is plenty for movement checks. */
object GeoDistance {
    private const val EARTH_RADIUS_METRES = 6_371_008.8

    fun metres(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * EARTH_RADIUS_METRES * atan2(sqrt(h), sqrt(1 - h))
    }
}
