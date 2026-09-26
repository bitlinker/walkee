package me.bitlinker.walkee.fog.geo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.tan

class Epsg3395Test {

    @Test
    fun `equator projects to zero northing and the world centre`() {
        assertEquals(0.0, Epsg3395.projectY(0.0), 1e-9)
        val world = Epsg3395.toWorld(GeoPoint(0.0, 0.0))
        assertEquals(0.5, world.x, 1e-12)
        assertEquals(0.5, world.y, 1e-12)
    }

    @Test
    fun `easting is linear in longitude`() {
        assertEquals(PI * Epsg3395.EQUATORIAL_RADIUS / 2, Epsg3395.projectX(90.0), 1e-6)
        assertEquals(-Epsg3395.HALF_WORLD, Epsg3395.projectX(-180.0), 1e-6)
    }

    @Test
    fun `matches PROJ reference for merc on the WGS84 ellipsoid`() {
        // `echo 2 1 | proj +proj=merc +ellps=GRS80` → 222638.981586547 110579.965218249;
        // GRS80 and WGS84 eccentricities differ in the 11th digit, well below the tolerance.
        assertEquals(222638.981586547, Epsg3395.projectX(2.0), 1e-3)
        assertEquals(110579.965218249, Epsg3395.projectY(1.0), 1e-3)
    }

    @Test
    fun `is ellipsoidal, not spherical`() {
        val latitude = 55.75
        val spherical = Epsg3395.EQUATORIAL_RADIUS * ln(tan(PI / 4 + Math.toRadians(latitude) / 2))
        val difference = spherical - Epsg3395.projectY(latitude)
        // Web Mercator overshoots true Mercator by ~35 km at Moscow's latitude.
        assertEquals(35_347.0, difference, 50.0)
    }

    @Test
    fun `northing is antisymmetric in latitude`() {
        for (latitude in listOf(1.0, 23.5, 55.75, 84.0)) {
            assertEquals(-Epsg3395.projectY(latitude), Epsg3395.projectY(-latitude), 1e-6)
        }
    }

    @ParameterizedTest(name = "round trip lat={0} lon={1}")
    @CsvSource(
        "0.0, 0.0",
        "55.7558, 37.6173",
        "59.9386, 30.3141",
        "-22.9068, -43.1729",
        "1.3521, 103.8198",
        "84.9, -179.99",
        "-84.9, 179.99",
    )
    fun `world coordinates round trip`(latitude: Double, longitude: Double) {
        val geo = GeoPoint(latitude, longitude)
        val back = Epsg3395.toGeo(Epsg3395.toWorld(geo))
        assertEquals(latitude, back.latitude, 1e-9)
        assertEquals(longitude, back.longitude, 1e-9)
    }

    @Test
    fun `inverse northing converges to machine precision`() {
        for (latitude in listOf(-85.0, -60.0, -0.001, 0.0, 30.0, 55.7558, 80.0, 85.08)) {
            val back = Epsg3395.unprojectY(Epsg3395.projectY(latitude))
            assertEquals(latitude, back, 1e-11)
        }
    }

    @Test
    fun `max latitude is the top edge and beyond it is clamped`() {
        assertEquals(0.0, Epsg3395.toWorld(GeoPoint(Epsg3395.MAX_LATITUDE, 0.0)).y, 1e-12)
        assertEquals(1.0, Epsg3395.toWorld(GeoPoint(-Epsg3395.MAX_LATITUDE, 0.0)).y, 1e-12)
        assertEquals(0.0, Epsg3395.toWorld(GeoPoint(89.0, 0.0)).y, 1e-12)
        assertEquals(1.0, Epsg3395.toWorld(GeoPoint(-90.0, 0.0)).y, 1e-12)
    }

    @Test
    fun `longitude wraps around the antimeridian`() {
        assertEquals(Epsg3395.toWorld(GeoPoint(0.0, -170.0)).x, Epsg3395.toWorld(GeoPoint(0.0, 190.0)).x, 1e-12)
        assertEquals(0.0, Epsg3395.toWorld(GeoPoint(0.0, -180.0)).x, 1e-12)
        assertEquals(0.0, Epsg3395.toWorld(GeoPoint(0.0, 180.0)).x, 1e-12)
    }

    @Test
    fun `Moscow centre projects to known metres and world position`() {
        val moscow = GeoPoint(55.7558, 37.6173)
        assertEquals(4_187_538.681, Epsg3395.projectX(moscow.longitude), 1e-3)
        assertEquals(7_474_605.282, Epsg3395.projectY(moscow.latitude), 1e-3)
        val world = Epsg3395.toWorld(moscow)
        assertEquals(0.6044925, world.x, 1e-9)
        assertEquals(0.313484662, world.y, 1e-9)
    }

    @Test
    fun `tile sizes on the ground`() {
        assertEquals(2 * Epsg3395.HALF_WORLD, Epsg3395.tileSizeMetres(0.0, 0), 1e-6)
        assertEquals(86.22, Epsg3395.tileSizeMetres(55.7558, 18), 0.01)
        assertEquals(21.56, Epsg3395.tileSizeMetres(55.7558, 20), 0.01)
        assertTrue(abs(Epsg3395.tileSizeMetres(60.0, 18) - Epsg3395.tileSizeMetres(-60.0, 18)) < 1e-9)
    }
}
