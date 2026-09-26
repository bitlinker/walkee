package me.bitlinker.walkee.ui.screens.settings

import me.bitlinker.walkee.data.settings.FogStyle

data class SettingsState(
    val fogOpacity: Float = FogStyle.DEFAULT_OPACITY,
    val displayZoom: Int = FogStyle.DEFAULT_DISPLAY_ZOOM,
    /** Approximate side of one displayed pixel at the reference latitude, metres. */
    val displayCellMetres: Int = 0,
    val isLoaded: Boolean = false,
)
