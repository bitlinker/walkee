package me.bitlinker.walkee.ui.screens.settings

import me.bitlinker.walkee.data.settings.FogCellShape
import me.bitlinker.walkee.data.settings.FogStyle

sealed interface SettingsAction {
    // From the UI
    data object BackClicked : SettingsAction
    data class FogOpacityChanged(val opacity: Float) : SettingsAction
    data class DisplayZoomChanged(val zoom: Int) : SettingsAction
    data class CellShapeChanged(val shape: FogCellShape) : SettingsAction
    data object ClearExploredClicked : SettingsAction
    data object ClearExploredConfirmed : SettingsAction
    data object ClearExploredDismissed : SettingsAction

    // From use cases
    data class StyleLoaded(val style: FogStyle) : SettingsAction
    data class ClearExploredFinished(val success: Boolean) : SettingsAction
}
