package me.bitlinker.walkee.data.location

import me.bitlinker.walkee.fog.geo.GeoPoint
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocationFilterTest {

    private val filter = LocationFilter()
    private val start = fix(55.7558, 37.6173, accuracy = 10f, time = 0)

    @Test
    fun `first fix with sane accuracy is accepted`() {
        assertTrue(filter.accept(null, start))
        assertFalse(filter.accept(null, start.copy(accuracyMetres = 500f)))
        assertFalse(filter.accept(null, start.copy(accuracyMetres = 0f)))
    }

    @Test
    fun `walking pace is accepted`() {
        // ~14 m in 10 s.
        val next = fix(55.7558, 37.6175, accuracy = 8f, time = 10_000)
        assertTrue(filter.accept(start, next))
    }

    @Test
    fun `teleports are rejected`() {
        // ~1.2 km in 5 s with tight accuracy.
        val jump = fix(55.7668, 37.6173, accuracy = 5f, time = 5_000)
        assertFalse(filter.accept(start, jump))
    }

    @Test
    fun `accuracy slack forgives jitter`() {
        // 60 m apparent move in 1 s, but both fixes are ±40 m: plausible noise.
        val noisyStart = start.copy(accuracyMetres = 40f)
        val jitter = fix(55.7558, 37.6183, accuracy = 40f, time = 1_000)
        assertTrue(filter.accept(noisyStart, jitter))
    }

    @Test
    fun `stale or duplicate timestamps are rejected`() {
        assertFalse(filter.accept(start, start.copy(timeMillis = 0)))
        assertFalse(filter.accept(start.copy(timeMillis = 10_000), start.copy(timeMillis = 5_000)))
    }

    private fun fix(lat: Double, lon: Double, accuracy: Float, time: Long) =
        LocationFix(GeoPoint(lat, lon), accuracy, speedMetresPerSecond = null, timeMillis = time)
}
