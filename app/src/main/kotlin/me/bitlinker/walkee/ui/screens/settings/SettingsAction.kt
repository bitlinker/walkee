package me.bitlinker.walkee.ui.screens.settings

import me.bitlinker.walkee.data.settings.FogCellShape
import me.bitlinker.walkee.data.settings.FogStyle

sealed interface SettingsAction {
    // From the UI
    data object BackClicked : SettingsAction
    data class FogOpacityChanged(val opacity: Float) : SettingsAction
    data class DisplayZoomChanged(val zoom: Int) : SettingsAction
    data class CellShapeChanged(val shape: FogCellShape) : SettingsAction

    // From use cases
    data class StyleLoaded(val style: FogStyle) : SettingsAction
}
