package me.bitlinker.walkee.data.map

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-memory map session state shared between screens and the map: things that are neither
 * persisted settings nor fog data, e.g. whether the camera follows the user.
 */
@Singleton
class MapSessionRepository @Inject constructor() {

    private val _followUser = MutableStateFlow(true)
    val followUser: StateFlow<Boolean> = _followUser.asStateFlow()

    fun setFollowUser(follow: Boolean) {
        _followUser.value = follow
    }

    private val _zoomRequests = MutableSharedFlow<Float>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** One-off requests to set the camera zoom (city overview, debug tooling). */
    val zoomRequests: Flow<Float> = _zoomRequests.asSharedFlow()

    fun requestZoom(zoom: Float) {
        _zoomRequests.tryEmit(zoom)
    }
}
