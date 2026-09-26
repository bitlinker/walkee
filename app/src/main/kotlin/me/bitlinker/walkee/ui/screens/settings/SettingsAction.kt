package me.bitlinker.walkee.ui.screens.settings

import me.bitlinker.walkee.data.location.LocationPermissions
import me.bitlinker.walkee.data.settings.FogCellShape
import me.bitlinker.walkee.data.settings.FogEdges
import me.bitlinker.walkee.data.settings.FogStyle

sealed interface SettingsAction {
    // From the UI
    data object BackClicked : SettingsAction
    data class FogOpacityChanged(val opacity: Float) : SettingsAction
    data class DisplayZoomChanged(val zoom: Int) : SettingsAction
    data class CellShapeChanged(val shape: FogCellShape) : SettingsAction
    data class FogEdgesChanged(val edges: FogEdges) : SettingsAction
    data object ClearExploredClicked : SettingsAction
    data object ClearExploredConfirmed : SettingsAction
    data object ClearExploredDismissed : SettingsAction
    data class AutoStartToggled(val enabled: Boolean) : SettingsAction
    data object BackgroundLocationClicked : SettingsAction
    data object LocationRequestLaunched : SettingsAction
    data object LocationPermissionResult : SettingsAction
    data object ActivityRequestLaunched : SettingsAction
    data class ActivityPermissionResult(val granted: Boolean) : SettingsAction

    // From use cases
    data class StyleLoaded(val style: FogStyle) : SettingsAction
    data class ClearExploredFinished(val success: Boolean) : SettingsAction
    data class AutoStartLoaded(val enabled: Boolean) : SettingsAction
    data class PermissionsChanged(val permissions: LocationPermissions) : SettingsAction
}
