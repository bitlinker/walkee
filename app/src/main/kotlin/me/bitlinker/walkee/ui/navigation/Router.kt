package me.bitlinker.walkee.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Owns the navigation back stack (ADR 0005). Screen view models call it in response to
 * navigation actions; the main composable renders [backStack] with Navigation 3.
 *
 * The root ([HomeKey]) can never be popped. The stack is process-scoped: after process death
 * the app restarts on the map, which is the right place for it anyway.
 */
@Singleton
class Router @Inject constructor() {

    private val _backStack = MutableStateFlow<List<NavKey>>(listOf(ROOT))
    val backStack: StateFlow<List<NavKey>> = _backStack.asStateFlow()

    val canPop: Boolean get() = _backStack.value.size > 1

    /** Pushes [key] unless it is already on top (guards against double taps). */
    fun push(key: NavKey) {
        _backStack.update { stack -> if (stack.last() == key) stack else stack + key }
    }

    /** Pops up to [count] entries, never removing the root. Returns whether anything was popped. */
    fun pop(count: Int = 1): Boolean {
        var popped = false
        _backStack.update { stack ->
            val n = min(count, stack.size - 1)
            popped = n > 0
            if (n > 0) stack.dropLast(n) else stack
        }
        return popped
    }

    fun replaceTop(key: NavKey) {
        _backStack.update { stack -> stack.dropLast(1) + key }
    }

    /** Pops everything above the root. */
    fun popToRoot() {
        _backStack.value = listOf(ROOT)
    }

    private companion object {
        val ROOT: NavKey = HomeKey
    }
}
