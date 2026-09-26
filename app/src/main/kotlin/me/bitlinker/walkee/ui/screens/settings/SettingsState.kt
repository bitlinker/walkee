package me.bitlinker.walkee.ui.screens.settings

import me.bitlinker.walkee.data.settings.FogCellShape
import me.bitlinker.walkee.data.settings.FogStyle

data class SettingsState(
    val fogOpacity: Float = FogStyle.DEFAULT_OPACITY,
    val displayZoom: Int = FogStyle.DEFAULT_DISPLAY_ZOOM,
    val cellShape: FogCellShape = FogCellShape.SQUARES,
    /** Approximate side of one displayed pixel at the reference latitude, metres. */
    val displayCellMetres: Int = 0,
    val isLoaded: Boolean = false,
    val isClearConfirmationShown: Boolean = false,
    val isClearing: Boolean = false,
    val clearFailed: Boolean = false,
)
