package me.bitlinker.walkee.ui.screens.home

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import me.bitlinker.walkee.domain.usecase.ObserveExplorationProgressUseCase
import me.bitlinker.walkee.domain.usecase.ObserveFollowUserUseCase
import me.bitlinker.walkee.domain.usecase.ObserveLocationPermissionUseCase
import me.bitlinker.walkee.domain.usecase.ObserveTrackingUseCase
import me.bitlinker.walkee.domain.usecase.RefreshLocationPermissionUseCase
import me.bitlinker.walkee.domain.usecase.SetFollowUserUseCase
import me.bitlinker.walkee.domain.usecase.SetTrackingEnabledUseCase
import me.bitlinker.walkee.ui.navigation.Router
import me.bitlinker.walkee.ui.navigation.SettingsKey
import me.bitlinker.walkee.ui.redux.ReduxViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeLocationPermission: ObserveLocationPermissionUseCase,
    observeTracking: ObserveTrackingUseCase,
    observeFollowUser: ObserveFollowUserUseCase,
    observeExplorationProgress: ObserveExplorationProgressUseCase,
    private val refreshLocationPermission: RefreshLocationPermissionUseCase,
    private val setTrackingEnabled: SetTrackingEnabledUseCase,
    private val setFollowUser: SetFollowUserUseCase,
    private val router: Router,
) : ReduxViewModel<HomeState, HomeAction>(HomeState(), ::reduceHome) {

    init {
        viewModelScope.launch {
            observeLocationPermission().collect { dispatch(HomeAction.PermissionChanged(it)) }
        }
        viewModelScope.launch {
            observeTracking().collect { dispatch(HomeAction.TrackingChanged(it)) }
        }
        viewModelScope.launch {
            observeFollowUser().collect { dispatch(HomeAction.FollowUserChanged(it)) }
        }
        viewModelScope.launch {
            observeExplorationProgress().collect {
                dispatch(HomeAction.ProgressChanged(it.visitedCells, it.areaSquareKilometres))
            }
        }
    }

    override fun onAction(action: HomeAction, state: HomeState) {
        when (action) {
            HomeAction.TrackingToggled -> if (!state.permissionRequestPending) {
                viewModelScope.launch { setTrackingEnabled(!state.isTracking) }
            }

            is HomeAction.PermissionResult -> {
                refreshLocationPermission()
                if (action.granted) viewModelScope.launch { setTrackingEnabled(true) }
            }

            HomeAction.RecenterClicked -> setFollowUser(true)

            HomeAction.SettingsClicked -> router.push(SettingsKey)

            else -> Unit
        }
    }
}
