package me.bitlinker.walkee.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import me.bitlinker.walkee.ui.screens.home.HomeRoute
import me.bitlinker.walkee.ui.screens.settings.SettingsRoute

/** Renders the [Router] stack with Navigation 3; screens draw over the MapView beneath. */
@Composable
fun MainNavDisplay(router: Router, modifier: Modifier = Modifier) {
    val backStack by router.backStack.collectAsStateWithLifecycle()
    NavDisplay(
        backStack = backStack,
        modifier = modifier,
        onBack = { router.pop() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
            // Scopes each screen's Hilt view model to its back stack entry.
            rememberViewModelStoreNavEntryDecorator<NavKey>(),
        ),
        entryProvider = entryProvider {
            entry<HomeKey> { HomeRoute() }
            entry<SettingsKey> { SettingsRoute() }
        },
    )
}
