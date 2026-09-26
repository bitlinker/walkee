package me.bitlinker.walkee.ui.redux

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Screen store (ADR 0004): state lives in a [StateFlow] and changes only through the pure
 * [reducer]; the UI sees just [state] and [dispatch].
 *
 * Side effects (use cases, navigation) run in [onAction] *after* the reduction and report back
 * by dispatching further actions, so every state transition stays a reducer step.
 */
abstract class ReduxViewModel<S : Any, A : Any>(
    initialState: S,
    private val reducer: (S, A) -> S,
) : ViewModel() {

    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<S> = _state.asStateFlow()

    fun dispatch(action: A) {
        _state.update { reducer(it, action) }
        onAction(action, _state.value)
    }

    /** Effects for [action], given the state right after it was reduced. */
    protected open fun onAction(action: A, state: S) {}
}
