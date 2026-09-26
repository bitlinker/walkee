package me.bitlinker.walkee.ui.map

import com.yandex.mapkit.Animation
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraListener
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.CameraUpdateReason
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.mapview.MapView
import com.yandex.mapkit.user_location.UserLocationLayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.bitlinker.walkee.fog.geo.GeoPoint
import java.lang.ref.WeakReference

/**
 * Applies [MapState] to the MapView and reports gestures back as [MapAction]s (ADR 0005).
 * Everything that touches MapKit's map, camera and layers lives here or in the layer renderers.
 */
class MapRenderer(
    private val mapView: MapView,
    private val state: StateFlow<MapState>,
    private val dispatch: (MapAction) -> Unit,
    private val fogLayerRenderer: MapFogLayerRenderer,
) {
    private val map: Map get() = mapView.mapWindow.map
    private var userLocationLayer: UserLocationLayer? = null
    private var hasCenteredOnce = false

    // Kept as a field: MapKit holds listeners weakly.
    private val cameraListener = CameraListener { _, _, reason, finished ->
        if (reason == CameraUpdateReason.GESTURES && !finished) dispatch(MapAction.CameraMovedByUser)
    }

    fun start(scope: CoroutineScope) {
        map.addCameraListener(WeakReference(cameraListener))
        fogLayerRenderer.attach(map, scope)

        scope.launch {
            state.map { it.userLocation to it.followUser }.distinctUntilChanged().collect { (location, follow) ->
                if (location != null && follow) moveCamera(location, animated = hasCenteredOnce)
            }
        }
        scope.launch {
            state.map { it.recenterRequests }.distinctUntilChanged().collect { requests ->
                val location = state.value.userLocation
                if (requests > 0 && location != null) moveCamera(location, animated = true)
            }
        }
    }

    /** Call when location permission is available: shows MapKit's own user marker. */
    fun showUserLocation(visible: Boolean) {
        if (visible && userLocationLayer == null) {
            userLocationLayer = MapKitFactory.getInstance().createUserLocationLayer(mapView.mapWindow).also {
                it.isVisible = true
            }
        }
        userLocationLayer?.isVisible = visible
    }

    fun stop() {
        map.removeCameraListener(WeakReference(cameraListener))
        fogLayerRenderer.detach()
    }

    private fun moveCamera(location: GeoPoint, animated: Boolean) {
        val zoom = if (hasCenteredOnce) map.cameraPosition.zoom.coerceAtLeast(MIN_FOLLOW_ZOOM) else INITIAL_ZOOM
        val position = CameraPosition(Point(location.latitude, location.longitude), zoom, 0f, 0f)
        if (animated) {
            map.move(position, Animation(Animation.Type.SMOOTH, ANIMATION_SECONDS), null)
        } else {
            map.move(position)
        }
        hasCenteredOnce = true
    }

    private companion object {
        const val INITIAL_ZOOM = 16f
        const val MIN_FOLLOW_ZOOM = 13f
        const val ANIMATION_SECONDS = 0.6f
    }
}
