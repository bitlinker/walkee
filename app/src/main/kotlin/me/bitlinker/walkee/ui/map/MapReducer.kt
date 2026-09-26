package me.bitlinker.walkee.ui.map

import me.bitlinker.walkee.data.settings.FogStyle
import me.bitlinker.walkee.fog.geo.GeoPoint

/** Builds the next view state field by field; each field has its own reducer (ADR 0005). */
fun reduceMap(state: MapState, action: MapAction): MapState = state.copy(
    userLocation = reduceUserLocation(state.userLocation, action),
    followUser = reduceFollowUser(state.followUser, action),
    fogStyle = reduceFogStyle(state.fogStyle, action),
    recenterRequests = reduceRecenterRequests(state.recenterRequests, action, followUser = state.followUser),
    zoomRequest = reduceZoomRequest(state.zoomRequest, action),
)

private fun reduceUserLocation(location: GeoPoint?, action: MapAction): GeoPoint? = when (action) {
    is MapAction.LocationChanged -> action.point
    else -> location
}

private fun reduceFollowUser(followUser: Boolean, action: MapAction): Boolean = when (action) {
    is MapAction.FollowUserChanged -> action.followUser
    else -> followUser
}

private fun reduceFogStyle(style: FogStyle, action: MapAction): FogStyle = when (action) {
    is MapAction.FogStyleChanged -> action.style
    else -> style
}

/** Counts switches into following: each one makes the camera jump to the user. */
private fun reduceRecenterRequests(requests: Int, action: MapAction, followUser: Boolean): Int = when (action) {
    is MapAction.FollowUserChanged -> if (action.followUser && !followUser) requests + 1 else requests
    else -> requests
}

private fun reduceZoomRequest(request: MapState.ZoomRequest?, action: MapAction): MapState.ZoomRequest? = when (action) {
    is MapAction.ZoomRequested -> MapState.ZoomRequest(action.zoom, (request?.sequence ?: 0) + 1)
    else -> request
}
