package me.bitlinker.walkee.ui.screens.settings

import me.bitlinker.walkee.data.location.LocationPermissions
import me.bitlinker.walkee.data.settings.FogCellShape
import me.bitlinker.walkee.data.settings.FogEdges
import me.bitlinker.walkee.data.settings.FogStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsReducerTest {

    private val loaded = reduceSettings(SettingsState(), SettingsAction.StyleLoaded(FogStyle(displayZoom = 21)))

    @Test
    fun `switching to hexagons pulls the display zoom into their range`() {
        val hexagons = reduceSettings(loaded, SettingsAction.CellShapeChanged(FogCellShape.HEXAGONS))
        assertEquals(FogCellShape.HEXAGONS, hexagons.cellShape)
        assertEquals(20, hexagons.displayZoom)

        val squares = reduceSettings(hexagons, SettingsAction.CellShapeChanged(FogCellShape.SQUARES))
        assertEquals(20, squares.displayZoom)
    }

    @Test
    fun `display zoom is limited by the current shape`() {
        val hexagons = reduceSettings(loaded, SettingsAction.CellShapeChanged(FogCellShape.HEXAGONS))
        assertEquals(20, reduceSettings(hexagons, SettingsAction.DisplayZoomChanged(21)).displayZoom)
        assertEquals(21, reduceSettings(loaded, SettingsAction.DisplayZoomChanged(21)).displayZoom)
        assertEquals(16, reduceSettings(loaded, SettingsAction.DisplayZoomChanged(3)).displayZoom)
    }

    @Test
    fun `clearing asks for confirmation first`() {
        val asked = reduceSettings(loaded, SettingsAction.ClearExploredClicked)
        assertTrue(asked.isClearConfirmationShown)
        assertFalse(asked.isClearing)

        val dismissed = reduceSettings(asked, SettingsAction.ClearExploredDismissed)
        assertFalse(dismissed.isClearConfirmationShown)
        assertFalse(dismissed.isClearing)

        val clearing = reduceSettings(asked, SettingsAction.ClearExploredConfirmed)
        assertFalse(clearing.isClearConfirmationShown)
        assertTrue(clearing.isClearing)
        assertFalse(reduceSettings(clearing, SettingsAction.ClearExploredFinished(success = true)).isClearing)
    }

    @Test
    fun `failed clearing is reported until the next attempt`() {
        val clearing = reduceSettings(loaded, SettingsAction.ClearExploredConfirmed)
        val failed = reduceSettings(clearing, SettingsAction.ClearExploredFinished(success = false))
        assertTrue(failed.clearFailed)
        assertFalse(failed.isClearing)
        assertFalse(reduceSettings(failed, SettingsAction.ClearExploredClicked).clearFailed)
    }

    @Test
    fun `edges follow the loaded style and the user's choice`() {
        val clouds = reduceSettings(SettingsState(), SettingsAction.StyleLoaded(FogStyle(edges = FogEdges.CLOUDS)))
        assertEquals(FogEdges.CLOUDS, clouds.fogEdges)
        assertEquals(FogEdges.HARD, reduceSettings(clouds, SettingsAction.FogEdgesChanged(FogEdges.HARD)).fogEdges)
    }

    @Test
    fun `cell size in metres follows the display zoom`() {
        assertEquals(11, loaded.displayCellMetres)
        assertEquals(86, reduceSettings(loaded, SettingsAction.DisplayZoomChanged(18)).displayCellMetres)
    }

    @Test
    fun `loaded style carries the shape`() {
        val state = reduceSettings(SettingsState(), SettingsAction.StyleLoaded(FogStyle(displayZoom = 17, cellShape = FogCellShape.HEXAGONS)))
        assertEquals(FogCellShape.HEXAGONS, state.cellShape)
        assertEquals(17, state.displayZoom)
        assertTrue(state.isLoaded)
    }

    private val allPermissions = LocationPermissions(location = true, backgroundLocation = true, activityRecognition = true)

    private fun withPermissions(permissions: LocationPermissions) =
        reduceSettings(loaded, SettingsAction.PermissionsChanged(permissions))

    @Test
    fun `auto-start turns on and off with every permission in place`() {
        val on = reduceSettings(withPermissions(allPermissions), SettingsAction.AutoStartToggled(true))
        assertTrue(on.autoStartEnabled)
        assertTrue(on.isAutoStartOn)
        assertFalse(on.activityRequestPending)

        val off = reduceSettings(on, SettingsAction.AutoStartToggled(false))
        assertFalse(off.autoStartEnabled)
        assertFalse(off.isAutoStartOn)
    }

    @Test
    fun `auto-start asks for the activity permission first`() {
        val noActivity = withPermissions(allPermissions.copy(activityRecognition = false))
        val asked = reduceSettings(noActivity, SettingsAction.AutoStartToggled(true))
        assertFalse(asked.autoStartEnabled)
        assertTrue(asked.activityRequestPending)

        val launched = reduceSettings(asked, SettingsAction.ActivityRequestLaunched)
        assertFalse(launched.activityRequestPending)

        val granted = reduceSettings(launched, SettingsAction.ActivityPermissionResult(granted = true))
        assertTrue(granted.autoStartEnabled)
        assertFalse(granted.activityPermissionDenied)
    }

    @Test
    fun `a refused activity permission keeps auto-start off and is explained until granted`() {
        val noActivity = withPermissions(allPermissions.copy(activityRecognition = false))
        val asked = reduceSettings(noActivity, SettingsAction.AutoStartToggled(true))
        val denied = reduceSettings(asked, SettingsAction.ActivityPermissionResult(granted = false))
        assertFalse(denied.autoStartEnabled)
        assertTrue(denied.activityPermissionDenied)

        assertFalse(reduceSettings(denied, SettingsAction.PermissionsChanged(allPermissions)).activityPermissionDenied)
    }

    @Test
    fun `auto-start needs location all the time`() {
        val whileInUse = withPermissions(allPermissions.copy(backgroundLocation = false))
        assertFalse(whileInUse.isAutoStartAvailable)

        val toggled = reduceSettings(whileInUse, SettingsAction.AutoStartToggled(true))
        assertFalse(toggled.autoStartEnabled)
        assertFalse(toggled.activityRequestPending)

        // A stored choice stays, but shows as off until the permission is back.
        val stored = reduceSettings(whileInUse, SettingsAction.AutoStartLoaded(true))
        assertTrue(stored.autoStartEnabled)
        assertFalse(stored.isAutoStartOn)
        assertTrue(reduceSettings(stored, SettingsAction.PermissionsChanged(allPermissions)).isAutoStartOn)
    }

    @Test
    fun `the background location button requests once`() {
        val clicked = reduceSettings(loaded, SettingsAction.BackgroundLocationClicked)
        assertTrue(clicked.locationRequestPending)
        assertFalse(reduceSettings(clicked, SettingsAction.LocationRequestLaunched).locationRequestPending)
    }
}
