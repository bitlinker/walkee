package me.bitlinker.walkee.ui.screens.settings

import me.bitlinker.walkee.data.settings.FogStyle
import me.bitlinker.walkee.fog.geo.Epsg3395
import kotlin.math.roundToInt

fun reduceSettings(state: SettingsState, action: SettingsAction): SettingsState = when (action) {
    is SettingsAction.FogOpacityChanged -> state.copy(fogOpacity = action.opacity.coerceIn(FogStyle.OPACITY_RANGE))

    is SettingsAction.DisplayZoomChanged -> {
        val zoom = action.zoom.coerceIn(state.cellShape.displayZoomRange)
        state.copy(displayZoom = zoom, displayCellMetres = displayCellMetres(zoom))
    }

    is SettingsAction.CellShapeChanged -> {
        val zoom = state.displayZoom.coerceIn(action.shape.displayZoomRange)
        state.copy(cellShape = action.shape, displayZoom = zoom, displayCellMetres = displayCellMetres(zoom))
    }

    is SettingsAction.FogEdgesChanged -> state.copy(fogEdges = action.edges)

    is SettingsAction.StyleLoaded -> state.copy(
        fogOpacity = action.style.opacity,
        displayZoom = action.style.displayZoom,
        displayCellMetres = displayCellMetres(action.style.displayZoom),
        cellShape = action.style.cellShape,
        fogEdges = action.style.edges,
        isLoaded = true,
    )

    SettingsAction.ClearExploredClicked -> state.copy(isClearConfirmationShown = true, clearFailed = false)

    SettingsAction.ClearExploredDismissed -> state.copy(isClearConfirmationShown = false)

    SettingsAction.ClearExploredConfirmed -> state.copy(isClearConfirmationShown = false, isClearing = true)

    is SettingsAction.ClearExploredFinished -> state.copy(isClearing = false, clearFailed = !action.success)

    SettingsAction.BackClicked -> state
}

/** Moscow latitude as the reference for the human-readable cell size (a hexagon is as wide as a square). */
private const val REFERENCE_LATITUDE = 55.75

private fun displayCellMetres(zoom: Int): Int = Epsg3395.tileSizeMetres(REFERENCE_LATITUDE, zoom).roundToInt()
