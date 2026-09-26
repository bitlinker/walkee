package me.bitlinker.walkee.tracking

import kotlinx.coroutines.launch
import me.bitlinker.walkee.domain.usecase.ObserveExplorationProgressUseCase
import me.bitlinker.walkee.domain.usecase.ObserveTrackingUseCase
import me.bitlinker.walkee.domain.usecase.SetTrackingEnabledUseCase
import me.bitlinker.walkee.ui.redux.ReduxController
import javax.inject.Inject

/**
 * The tracking service's store, its counterpart of a screen's view model (ADR 0005): follows
 * tracking and progress through use cases and turns notification actions into use-case calls.
 * The service sees only [state] and [dispatch]; one controller per service instance.
 */
class TrackingServiceController @Inject constructor(
    observeTracking: ObserveTrackingUseCase,
    observeExplorationProgress: ObserveExplorationProgressUseCase,
    private val setTrackingEnabled: SetTrackingEnabledUseCase,
) : ReduxController<TrackingServiceState, TrackingServiceAction>(TrackingServiceState(), ::reduceTrackingService) {

    init {
        scope.launch {
            observeTracking().collect { dispatch(TrackingServiceAction.TrackingChanged(it)) }
        }
        scope.launch {
            observeExplorationProgress().collect { dispatch(TrackingServiceAction.ProgressChanged(it.areaSquareKilometres)) }
        }
    }

    override fun onAction(action: TrackingServiceAction, state: TrackingServiceState) {
        when (action) {
            // A pause from the notification is the same choice as the button on the map. A refused
            // start is saved as a pause too, so the app does not keep trying on every launch.
            TrackingServiceAction.PauseClicked,
            TrackingServiceAction.ForegroundRefused,
            -> scope.launch { setTrackingEnabled(false) }

            is TrackingServiceAction.TrackingChanged,
            is TrackingServiceAction.ProgressChanged,
            -> Unit
        }
    }
}
