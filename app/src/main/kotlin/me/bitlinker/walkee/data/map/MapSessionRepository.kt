package me.bitlinker.walkee.data.map

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
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
}
