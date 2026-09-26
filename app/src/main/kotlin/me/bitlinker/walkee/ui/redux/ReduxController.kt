package me.bitlinker.walkee.ui.redux

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Store for hosts that are not screens, such as the tracking service (ADR 0005): the same contract
 * as [ReduxViewModel] — [state], [dispatch], a pure [reducer], effects in [onAction] — but the host
 * owns the lifetime and calls [clear] when it is destroyed.
 */
abstract class ReduxController<S : Any, A : Any>(
    initialState: S,
    private val reducer: (S, A) -> S,
) {
    /** Runs effects and use-case subscriptions until [clear]; like `viewModelScope`. */
    protected val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    fun dispatch(action: A) {
        _state.update { reducer(it, action) }
        onAction(action, _state.value)
    }

    /** Effects for [action], given the state right after it was reduced. */
    protected open fun onAction(action: A, state: S) {}

    /** Cancels everything started in [scope]. */
    fun clear() {
        scope.cancel()
    }
}
