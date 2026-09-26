package me.bitlinker.walkee.ui.map

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import me.bitlinker.walkee.domain.usecase.GetLastKnownLocationUseCase
import me.bitlinker.walkee.domain.usecase.ObserveFogStyleUseCase
import me.bitlinker.walkee.domain.usecase.ObserveFollowUserUseCase
import me.bitlinker.walkee.domain.usecase.ObserveLocationUseCase
import me.bitlinker.walkee.domain.usecase.SetFollowUserUseCase
import me.bitlinker.walkee.ui.redux.ReduxViewModel
import javax.inject.Inject

fun reduceMap(state: MapState, action: MapAction): MapState = when (action) {
    is MapAction.LocationChanged -> state.copy(userLocation = action.point)
    is MapAction.FollowUserChanged -> state.copy(
        followUser = action.followUser,
        recenterRequests = if (action.followUser && !state.followUser) state.recenterRequests + 1 else state.recenterRequests,
    )
    is MapAction.FogStyleChanged -> state.copy(fogStyle = action.style)
    MapAction.CameraMovedByUser -> state
}

/** Activity-scoped state for the map beneath the screens (ADR 0005). */
@HiltViewModel
class MapViewModel @Inject constructor(
    observeLocation: ObserveLocationUseCase,
    observeFollowUser: ObserveFollowUserUseCase,
    observeFogStyle: ObserveFogStyleUseCase,
    getLastKnownLocation: GetLastKnownLocationUseCase,
    private val setFollowUser: SetFollowUserUseCase,
) : ReduxViewModel<MapState, MapAction>(MapState(), ::reduceMap) {

    init {
        viewModelScope.launch {
            getLastKnownLocation()?.let { dispatch(MapAction.LocationChanged(it.point)) }
        }
        viewModelScope.launch {
            observeLocation().collect { dispatch(MapAction.LocationChanged(it.point)) }
        }
        viewModelScope.launch {
            observeFollowUser().collect { dispatch(MapAction.FollowUserChanged(it)) }
        }
        viewModelScope.launch {
            observeFogStyle().collect { dispatch(MapAction.FogStyleChanged(it)) }
        }
    }

    override fun onAction(action: MapAction, state: MapState) {
        when (action) {
            MapAction.CameraMovedByUser -> if (state.followUser) setFollowUser(false)
            else -> Unit
        }
    }
}
