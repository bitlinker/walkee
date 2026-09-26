package me.bitlinker.walkee.ui.screens.settings

import me.bitlinker.walkee.data.settings.FogCellShape
import me.bitlinker.walkee.data.settings.FogStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsReducerTest {

    private val loaded = reduceSettings(SettingsState(), SettingsAction.StyleLoaded(FogStyle(displayZoom = 20)))

    @Test
    fun `switching to hexagons pulls the display zoom into their range`() {
        val hexagons = reduceSettings(loaded, SettingsAction.CellShapeChanged(FogCellShape.HEXAGONS))
        assertEquals(FogCellShape.HEXAGONS, hexagons.cellShape)
        assertEquals(19, hexagons.displayZoom)

        val squares = reduceSettings(hexagons, SettingsAction.CellShapeChanged(FogCellShape.SQUARES))
        assertEquals(19, squares.displayZoom)
    }

    @Test
    fun `display zoom is limited by the current shape`() {
        val hexagons = reduceSettings(loaded, SettingsAction.CellShapeChanged(FogCellShape.HEXAGONS))
        assertEquals(19, reduceSettings(hexagons, SettingsAction.DisplayZoomChanged(20)).displayZoom)
        assertEquals(20, reduceSettings(loaded, SettingsAction.DisplayZoomChanged(20)).displayZoom)
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
    fun `loaded style carries the shape`() {
        val state = reduceSettings(SettingsState(), SettingsAction.StyleLoaded(FogStyle(displayZoom = 17, cellShape = FogCellShape.HEXAGONS)))
        assertEquals(FogCellShape.HEXAGONS, state.cellShape)
        assertEquals(17, state.displayZoom)
        assertTrue(state.isLoaded)
    }
}
