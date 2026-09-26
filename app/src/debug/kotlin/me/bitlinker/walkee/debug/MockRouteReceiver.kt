package me.bitlinker.walkee.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import me.bitlinker.walkee.domain.usecase.RequestCameraZoomUseCase
import me.bitlinker.walkee.fog.geo.GeoPoint
import javax.inject.Inject

/**
 * Debug-only: replays a fake GPS route so painting can be exercised without leaving the desk.
 *
 * The app must be the device's mock location app:
 * `adb shell appops set me.bitlinker.walkee android:mock_location allow`
 *
 * Start:
 * ```
 * adb shell am broadcast -a me.bitlinker.walkee.debug.MOCK_ROUTE_START -n me.bitlinker.walkee/.debug.MockRouteReceiver \
 *   --es path "44.8200,20.4600;44.8230,20.4650" --ef step 5 --el delay 300 --ef accuracy 8 --ef jitter 2
 * ```
 * - `path`     waypoints `lat,lon;lat,lon;...`
 * - `step`     metres between emitted fixes (default 5); ignored when `interpolate` is false
 * - `delay`    real milliseconds between fixes (default 500)
 * - `accuracy` reported accuracy in metres (default 8)
 * - `jitter`   random offset added to every fix, metres (default 0)
 * - `interpolate` `false` to jump straight between waypoints (teleport test), default `true`
 * - `loops`    how many times to replay the path (default 1)
 *
 * Stop: `adb shell am broadcast -a me.bitlinker.walkee.debug.MOCK_ROUTE_STOP -n me.bitlinker.walkee/.debug.MockRouteReceiver`
 */
@AndroidEntryPoint
class MockRouteReceiver : BroadcastReceiver() {

    @Inject
    lateinit var driver: MockRouteDriver

    @Inject
    lateinit var requestCameraZoom: RequestCameraZoomUseCase

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_ZOOM -> requestCameraZoom(intent.getFloatExtra("zoom", 16f))
            ACTION_START -> {
                val route = parse(intent) ?: run {
                    Log.e(TAG, "MOCK_ROUTE_START needs --es path \"lat,lon;lat,lon;...\"")
                    return
                }
                driver.start(route)
            }
            ACTION_STOP -> driver.stop()
        }
    }

    private fun parse(intent: Intent): MockRoute? {
        val waypoints = intent.getStringExtra("path")
            ?.split(';')
            ?.map { pair ->
                val (lat, lon) = pair.split(',').map { it.trim().toDouble() }
                GeoPoint(lat, lon)
            }
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        return MockRoute(
            waypoints = waypoints,
            stepMetres = intent.getFloatExtra("step", 5f).toDouble(),
            delayMillis = intent.getLongExtra("delay", 500L),
            accuracyMetres = intent.getFloatExtra("accuracy", 8f),
            jitterMetres = intent.getFloatExtra("jitter", 0f).toDouble(),
            interpolate = intent.getBooleanExtra("interpolate", true),
            loops = intent.getIntExtra("loops", 1),
        )
    }

    private companion object {
        const val TAG = "MockRoute"
        const val ACTION_START = "me.bitlinker.walkee.debug.MOCK_ROUTE_START"
        const val ACTION_STOP = "me.bitlinker.walkee.debug.MOCK_ROUTE_STOP"
        /** `--ef zoom 12` — sets the camera zoom (adb cannot pinch). */
        const val ACTION_ZOOM = "me.bitlinker.walkee.debug.CAMERA_ZOOM"
    }
}
