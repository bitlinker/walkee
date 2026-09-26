package me.bitlinker.walkee.fog.geo

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * WGS84 World Mercator (EPSG:3395) — the projection used by Yandex Maps and MapKit.
 *
 * Unlike the spherical Web Mercator (EPSG:3857) of Google/OSM this is Mercator on the WGS84
 * *ellipsoid*: `x` is identical, `y` differs by up to tens of kilometres, so tile grids of the two
 * projections are not interchangeable. See ADR 0001.
 */
object Epsg3395 {
    /** WGS84 semi-major axis, metres. */
    const val EQUATORIAL_RADIUS = 6378137.0

    /** WGS84 first eccentricity. */
    const val ECCENTRICITY = 0.0818191908426

    /** Projected `x` of longitude 180°; the world square spans `[-HALF_WORLD, HALF_WORLD]`. */
    const val HALF_WORLD = PI * EQUATORIAL_RADIUS

    /** Latitude where projected `|y|` reaches [HALF_WORLD]; the map is clipped there (85.084°). */
    const val MAX_LATITUDE = 85.08405905010976

    private const val ECCENTRICITY_SQUARED = ECCENTRICITY * ECCENTRICITY
    private const val INVERSE_TOLERANCE_RADIANS = 1e-13
    private const val INVERSE_MAX_ITERATIONS = 20

    /** Projected easting in metres. */
    fun projectX(longitude: Double): Double = EQUATORIAL_RADIUS * Math.toRadians(longitude)

    /** Projected northing in metres; latitude is clamped to `±MAX_LATITUDE`. */
    fun projectY(latitude: Double): Double {
        val phi = Math.toRadians(latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE))
        val eSinPhi = ECCENTRICITY * sin(phi)
        val conformal = tan(PI / 4 + phi / 2) * ((1 - eSinPhi) / (1 + eSinPhi)).pow(ECCENTRICITY / 2)
        return EQUATORIAL_RADIUS * ln(conformal)
    }

    fun unprojectX(x: Double): Double = Math.toDegrees(x / EQUATORIAL_RADIUS)

    /** Inverse of [projectY]; there is no closed form, so it iterates to machine precision. */
    fun unprojectY(y: Double): Double {
        val t = exp(-y / EQUATORIAL_RADIUS)
        var phi = PI / 2 - 2 * atan(t)
        repeat(INVERSE_MAX_ITERATIONS) {
            val eSinPhi = ECCENTRICITY * sin(phi)
            val next = PI / 2 - 2 * atan(t * ((1 - eSinPhi) / (1 + eSinPhi)).pow(ECCENTRICITY / 2))
            val converged = abs(next - phi) < INVERSE_TOLERANCE_RADIANS
            phi = next
            if (converged) return Math.toDegrees(phi)
        }
        return Math.toDegrees(phi)
    }

    /** Normalized world coordinates; longitude wraps, latitude is clamped. */
    fun toWorld(point: GeoPoint): WorldPoint {
        val rawX = point.longitude / 360.0 + 0.5
        val x = rawX - floor(rawX)
        val y = 0.5 - projectY(point.latitude) / (2 * HALF_WORLD)
        return WorldPoint(x, y.coerceIn(0.0, 1.0))
    }

    fun toGeo(point: WorldPoint): GeoPoint {
        val longitude = point.x * 360.0 - 180.0
        val latitude = unprojectY((0.5 - point.y) * 2 * HALF_WORLD)
        return GeoPoint(latitude, longitude)
    }

    /**
     * Ground length in metres of one tile side at [zoom] for a given latitude.
     *
     * Mercator scale on the ellipsoid is `√(1 − e²·sin²φ) / cos φ`, so ground length is the
     * projected length divided by it.
     */
    fun tileSizeMetres(latitude: Double, zoom: Int): Double {
        val phi = Math.toRadians(latitude)
        val sinPhi = sin(phi)
        val projected = 2 * HALF_WORLD / (1 shl zoom).toDouble()
        return projected * cos(phi) / sqrt(1 - ECCENTRICITY_SQUARED * sinPhi * sinPhi)
    }

    /** Metres of ground per one unit of normalized world coordinate at [latitude]. */
    fun metresPerWorldUnit(latitude: Double): Double = tileSizeMetres(latitude, 0)
}
