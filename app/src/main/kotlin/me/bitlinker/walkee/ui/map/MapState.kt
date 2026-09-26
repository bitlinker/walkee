package me.bitlinker.walkee.ui.map

import me.bitlinker.walkee.data.settings.FogStyle
import me.bitlinker.walkee.fog.geo.GeoPoint

/** Immutable view state the [MapRenderer] applies to the MapView. */
data class MapState(
    val userLocation: GeoPoint? = null,
    val followUser: Boolean = true,
    val fogStyle: FogStyle = FogStyle(),
    /** Incremented whenever the camera should jump to the user even if it already follows. */
    val recenterRequests: Int = 0,
    /** Latest explicit zoom request; `sequence` makes repeated identical zooms distinguishable. */
    val zoomRequest: ZoomRequest? = null,
) {
    data class ZoomRequest(val zoom: Float, val sequence: Int)
}
