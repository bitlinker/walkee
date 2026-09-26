package me.bitlinker.walkee.ui.screens.home

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HomeReducerTest {

    @Test
    fun `starting without permission requests it instead of toggling`() {
        val state = reduceHome(HomeState(hasLocationPermission = false), HomeAction.TrackingToggled)
        assertTrue(state.permissionRequestPending)
        assertFalse(state.isTracking)

        val launched = reduceHome(state, HomeAction.PermissionRequestLaunched)
        assertFalse(launched.permissionRequestPending)
    }

    @Test
    fun `starting with permission still goes through the request, for the notification permission`() {
        val state = HomeState(hasLocationPermission = true)
        assertTrue(reduceHome(state, HomeAction.TrackingToggled).permissionRequestPending)
        assertEquals(state.copy(isTracking = true), reduceHome(state, HomeAction.TrackingChanged(true)))
    }

    @Test
    fun `pausing leaves state to the use case result`() {
        val state = HomeState(hasLocationPermission = true, isTracking = true)
        assertEquals(state, reduceHome(state, HomeAction.TrackingToggled))
        assertEquals(state.copy(isTracking = false), reduceHome(state, HomeAction.TrackingChanged(false)))
    }

    @Test
    fun `permission result updates the flag and clears the request`() {
        val pending = HomeState(permissionRequestPending = true)
        val granted = reduceHome(pending, HomeAction.PermissionResult(granted = true))
        assertTrue(granted.hasLocationPermission)
        assertFalse(granted.permissionRequestPending)
        val denied = reduceHome(pending, HomeAction.PermissionResult(granted = false))
        assertFalse(denied.hasLocationPermission)
        assertFalse(denied.permissionRequestPending)
    }

    @Test
    fun `progress and follow updates are copied verbatim`() {
        val state = reduceHome(HomeState(), HomeAction.ProgressChanged(visitedCells = 42, areaSquareKilometres = 0.02))
        assertEquals(42L, state.visitedCells)
        assertEquals(0.02, state.areaSquareKilometres)
        assertFalse(reduceHome(state, HomeAction.FollowUserChanged(false)).followUser)
    }

    @Test
    fun `navigation-only actions do not change state`() {
        val state = HomeState(visitedCells = 7)
        assertEquals(state, reduceHome(state, HomeAction.SettingsClicked))
        assertEquals(state, reduceHome(state, HomeAction.RecenterClicked))
    }
}
