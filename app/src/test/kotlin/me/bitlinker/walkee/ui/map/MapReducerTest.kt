package me.bitlinker.walkee.ui.map

import me.bitlinker.walkee.data.settings.FogCellShape
import me.bitlinker.walkee.data.settings.FogStyle
import me.bitlinker.walkee.fog.geo.GeoPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MapReducerTest {

    @Test
    fun `switching into following requests a recenter, staying in it does not`() {
        val free = MapState(followUser = false)
        val following = reduceMap(free, MapAction.FollowUserChanged(true))
        assertEquals(true, following.followUser)
        assertEquals(1, following.recenterRequests)

        assertEquals(1, reduceMap(following, MapAction.FollowUserChanged(true)).recenterRequests)
        val stopped = reduceMap(following, MapAction.FollowUserChanged(false))
        assertEquals(false, stopped.followUser)
        assertEquals(1, stopped.recenterRequests)
    }

    @Test
    fun `repeated identical zoom requests stay distinguishable`() {
        val first = reduceMap(MapState(), MapAction.ZoomRequested(17f))
        val second = reduceMap(first, MapAction.ZoomRequested(17f))
        assertEquals(MapState.ZoomRequest(17f, 1), first.zoomRequest)
        assertEquals(MapState.ZoomRequest(17f, 2), second.zoomRequest)
    }

    @Test
    fun `location and style are copied, camera gestures leave the state alone`() {
        val point = GeoPoint(55.75, 37.62)
        val style = FogStyle(cellShape = FogCellShape.HEXAGONS)
        val state = reduceMap(reduceMap(MapState(), MapAction.LocationChanged(point)), MapAction.FogStyleChanged(style))
        assertEquals(point, state.userLocation)
        assertEquals(style, state.fogStyle)
        assertEquals(state, reduceMap(state, MapAction.CameraMovedByUser))
    }
}
