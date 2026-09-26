package me.bitlinker.walkee.ui.map

import me.bitlinker.walkee.data.settings.FogStyle
import me.bitlinker.walkee.fog.geo.GeoPoint

sealed interface MapAction {
    // From the renderer (user gestures on the map)
    data object CameraMovedByUser : MapAction

    // From use cases
    data class LocationChanged(val point: GeoPoint) : MapAction
    data class FollowUserChanged(val followUser: Boolean) : MapAction
    data class FogStyleChanged(val style: FogStyle) : MapAction
    data class ZoomRequested(val zoom: Float) : MapAction
}
