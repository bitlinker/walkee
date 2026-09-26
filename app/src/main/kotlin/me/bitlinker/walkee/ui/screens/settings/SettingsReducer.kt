package me.bitlinker.walkee.ui.screens.settings

import me.bitlinker.walkee.data.settings.FogStyle
import me.bitlinker.walkee.fog.geo.Epsg3395
import kotlin.math.roundToInt

fun reduceSettings(state: SettingsState, action: SettingsAction): SettingsState = when (action) {
    is SettingsAction.FogOpacityChanged -> state.copy(fogOpacity = action.opacity.coerceIn(FogStyle.OPACITY_RANGE))

    is SettingsAction.DisplayZoomChanged -> {
        val zoom = action.zoom.coerceIn(FogStyle.DISPLAY_ZOOM_RANGE)
        state.copy(displayZoom = zoom, displayCellMetres = displayCellMetres(zoom))
    }

    is SettingsAction.StyleLoaded -> state.copy(
        fogOpacity = action.style.opacity,
        displayZoom = action.style.displayZoom,
        displayCellMetres = displayCellMetres(action.style.displayZoom),
        isLoaded = true,
    )

    SettingsAction.BackClicked -> state
}

/** Moscow latitude as the reference for the human-readable cell size. */
private const val REFERENCE_LATITUDE = 55.75

private fun displayCellMetres(zoom: Int): Int = Epsg3395.tileSizeMetres(REFERENCE_LATITUDE, zoom).roundToInt()
