package me.bitlinker.walkee.ui.screens.settings

import me.bitlinker.walkee.data.settings.FogCellShape
import me.bitlinker.walkee.data.settings.FogEdges
import me.bitlinker.walkee.data.settings.FogStyle
import me.bitlinker.walkee.fog.geo.Epsg3395
import kotlin.math.roundToInt

data class SettingsState(
    val fogOpacity: Float = FogStyle.DEFAULT_OPACITY,
    val displayZoom: Int = FogStyle.DEFAULT_DISPLAY_ZOOM,
    val cellShape: FogCellShape = FogCellShape.SQUARES,
    /** Applies to square cells; hexagons are always drawn with hard edges. */
    val fogEdges: FogEdges = FogStyle.DEFAULT_EDGES,
    val isLoaded: Boolean = false,
    val isClearConfirmationShown: Boolean = false,
    val isClearing: Boolean = false,
    val clearFailed: Boolean = false,
) {
    /** Approximate side of one displayed pixel at the reference latitude, metres; derived from [displayZoom]. */
    val displayCellMetres: Int get() = Epsg3395.tileSizeMetres(REFERENCE_LATITUDE, displayZoom).roundToInt()

    private companion object {
        /** Moscow latitude as the reference for the human-readable cell size (a hexagon is as wide as a square). */
        const val REFERENCE_LATITUDE = 55.75
    }
}
