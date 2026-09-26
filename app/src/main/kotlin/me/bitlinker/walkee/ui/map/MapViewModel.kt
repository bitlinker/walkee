package me.bitlinker.walkee.ui.map

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import me.bitlinker.walkee.domain.usecase.GetLastKnownLocationUseCase
import me.bitlinker.walkee.domain.usecase.ObserveCameraZoomRequestsUseCase
import me.bitlinker.walkee.domain.usecase.ObserveFogStyleUseCase
import me.bitlinker.walkee.domain.usecase.ObserveFollowUserUseCase
import me.bitlinker.walkee.domain.usecase.ObserveLocationUseCase
import me.bitlinker.walkee.domain.usecase.SetFollowUserUseCase
import me.bitlinker.walkee.ui.redux.ReduxViewModel
import javax.inject.Inject

/** Activity-scoped state for the map beneath the screens (ADR 0005). */
@HiltViewModel
class MapViewModel @Inject constructor(
    observeLocation: ObserveLocationUseCase,
    observeFollowUser: ObserveFollowUserUseCase,
    observeFogStyle: ObserveFogStyleUseCase,
    observeCameraZoomRequests: ObserveCameraZoomRequestsUseCase,
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
        viewModelScope.launch {
            observeCameraZoomRequests().collect { dispatch(MapAction.ZoomRequested(it)) }
        }
    }

    override fun onAction(action: MapAction, state: MapState) {
        when (action) {
            MapAction.CameraMovedByUser -> if (state.followUser) setFollowUser(false)
            else -> Unit
        }
    }
}
