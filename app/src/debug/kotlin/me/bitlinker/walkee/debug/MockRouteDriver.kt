package me.bitlinker.walkee.debug

import android.content.Context
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.bitlinker.walkee.data.location.GeoDistance
import me.bitlinker.walkee.di.ApplicationScope
import me.bitlinker.walkee.fog.geo.GeoPoint
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.random.Random

data class MockRoute(
    val waypoints: List<GeoPoint>,
    val stepMetres: Double,
    val delayMillis: Long,
    val accuracyMetres: Float,
    val jitterMetres: Double,
    val interpolate: Boolean,
    val loops: Int,
)

/**
 * Feeds a [MockRoute] into the platform GPS test provider, so both the Fused Location Provider
 * (our [me.bitlinker.walkee.data.location.LocationRepository]) and MapKit's user marker see it.
 *
 * Routes run one at a time: starting a new one first cancels and *awaits* the previous, because
 * the test provider is a single shared resource. Nothing here may throw into the app scope.
 */
@Singleton
class MockRouteDriver @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val locationManager = context.getSystemService(LocationManager::class.java)
    private val mutex = Mutex()
    private var job: Job? = null

    fun start(route: MockRoute) {
        scope.launch {
            mutex.withLock {
                job?.cancelAndJoin()
                job = scope.launch { run(route) }
            }
        }
    }

    fun stop() {
        scope.launch { mutex.withLock { job?.cancelAndJoin(); job = null } }
    }

    private suspend fun run(route: MockRoute) {
        try {
            installProvider()
            var emitted = 0
            repeat(max(1, route.loops)) {
                for (point in points(route)) {
                    publish(point, route)
                    emitted++
                    delay(route.delayMillis)
                }
            }
            Log.i(TAG, "Route finished: $emitted fixes")
        } catch (e: SecurityException) {
            Log.e(TAG, "Not the mock location app: adb shell appops set ${context.packageName} android:mock_location allow", e)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Mock provider rejected", e)
        } finally {
            removeProvider()
        }
    }

    private fun points(route: MockRoute): Sequence<GeoPoint> = sequence {
        if (!route.interpolate || route.waypoints.size == 1) {
            yieldAll(route.waypoints)
            return@sequence
        }
        yield(route.waypoints.first())
        for ((from, to) in route.waypoints.zipWithNext()) {
            val length = GeoDistance.metres(from, to)
            val steps = max(1, ceil(length / route.stepMetres).toInt())
            for (i in 1..steps) {
                val t = i.toDouble() / steps
                yield(GeoPoint(from.latitude + (to.latitude - from.latitude) * t, from.longitude + (to.longitude - from.longitude) * t))
            }
        }
    }

    private fun publish(point: GeoPoint, route: MockRoute) {
        val jittered = if (route.jitterMetres > 0) jitter(point, route.jitterMetres) else point
        val location = Location(PROVIDER).apply {
            latitude = jittered.latitude
            longitude = jittered.longitude
            accuracy = route.accuracyMetres
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock = true
        }
        locationManager.setTestProviderLocation(PROVIDER, location)
        Log.d(TAG, "fix ${"%.6f".format(location.latitude)}, ${"%.6f".format(location.longitude)} ±${route.accuracyMetres}")
    }

    private fun jitter(point: GeoPoint, metres: Double): GeoPoint {
        val dNorth = (Random.nextDouble() * 2 - 1) * metres
        val dEast = (Random.nextDouble() * 2 - 1) * metres
        val lat = point.latitude + dNorth / METRES_PER_DEGREE_LAT
        val lon = point.longitude + dEast / (METRES_PER_DEGREE_LAT * cos(point.latitude * PI / 180))
        return GeoPoint(lat, lon)
    }

    private fun installProvider() {
        removeProvider()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val properties = ProviderProperties.Builder()
                .setHasSatelliteRequirement(true)
                .setHasSpeedSupport(true)
                .setHasBearingSupport(true)
                .setHasAltitudeSupport(true)
                .setPowerUsage(ProviderProperties.POWER_USAGE_HIGH)
                .setAccuracy(ProviderProperties.ACCURACY_FINE)
                .build()
            locationManager.addTestProvider(PROVIDER, properties)
        } else {
            @Suppress("DEPRECATION")
            locationManager.addTestProvider(
                PROVIDER, false, true, false, false, true, true, true,
                android.location.Criteria.POWER_HIGH, android.location.Criteria.ACCURACY_FINE,
            )
        }
        locationManager.setTestProviderEnabled(PROVIDER, true)
        Log.i(TAG, "Mock GPS provider installed")
    }

    private fun removeProvider() {
        try {
            locationManager.removeTestProvider(PROVIDER)
        } catch (_: IllegalArgumentException) {
            // Not installed.
        } catch (_: SecurityException) {
            // Not the mock location app; nothing to remove.
        }
    }

    private companion object {
        const val TAG = "MockRoute"
        const val PROVIDER = LocationManager.GPS_PROVIDER
        const val METRES_PER_DEGREE_LAT = 111_320.0
    }
}
