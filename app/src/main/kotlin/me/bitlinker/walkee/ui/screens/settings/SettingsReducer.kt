package me.bitlinker.walkee.ui.screens.settings

import me.bitlinker.walkee.data.location.LocationPermissions
import me.bitlinker.walkee.data.settings.FogCellShape
import me.bitlinker.walkee.data.settings.FogEdges
import me.bitlinker.walkee.data.settings.FogStyle

/** Builds the next state field by field; each field has its own reducer (ADR 0005). */
fun reduceSettings(state: SettingsState, action: SettingsAction): SettingsState = state.copy(
    fogOpacity = reduceFogOpacity(state.fogOpacity, action),
    displayZoom = reduceDisplayZoom(state.displayZoom, action, cellShape = state.cellShape),
    cellShape = reduceCellShape(state.cellShape, action),
    fogEdges = reduceFogEdges(state.fogEdges, action),
    isLoaded = reduceIsLoaded(state.isLoaded, action),
    isClearConfirmationShown = reduceIsClearConfirmationShown(state.isClearConfirmationShown, action),
    isClearing = reduceIsClearing(state.isClearing, action),
    clearFailed = reduceClearFailed(state.clearFailed, action),
    autoStartEnabled = reduceAutoStartEnabled(state.autoStartEnabled, action, permissions = state.permissions),
    permissions = reducePermissions(state.permissions, action),
    locationRequestPending = reduceLocationRequestPending(state.locationRequestPending, action),
    activityRequestPending = reduceActivityRequestPending(state.activityRequestPending, action, permissions = state.permissions),
    activityPermissionDenied = reduceActivityPermissionDenied(state.activityPermissionDenied, action),
)

private fun reduceFogOpacity(opacity: Float, action: SettingsAction): Float = when (action) {
    is SettingsAction.FogOpacityChanged -> action.opacity.coerceIn(FogStyle.OPACITY_RANGE)
    is SettingsAction.StyleLoaded -> action.style.opacity
    else -> opacity
}

/** Always within the range of the shape: the chosen zoom is limited by it, a new shape pulls the zoom in. */
private fun reduceDisplayZoom(zoom: Int, action: SettingsAction, cellShape: FogCellShape): Int = when (action) {
    is SettingsAction.DisplayZoomChanged -> action.zoom.coerceIn(cellShape.displayZoomRange)
    is SettingsAction.CellShapeChanged -> zoom.coerceIn(action.shape.displayZoomRange)
    is SettingsAction.StyleLoaded -> action.style.displayZoom
    else -> zoom
}

private fun reduceCellShape(shape: FogCellShape, action: SettingsAction): FogCellShape = when (action) {
    is SettingsAction.CellShapeChanged -> action.shape
    is SettingsAction.StyleLoaded -> action.style.cellShape
    else -> shape
}

private fun reduceFogEdges(edges: FogEdges, action: SettingsAction): FogEdges = when (action) {
    is SettingsAction.FogEdgesChanged -> action.edges
    is SettingsAction.StyleLoaded -> action.style.edges
    else -> edges
}

private fun reduceIsLoaded(isLoaded: Boolean, action: SettingsAction): Boolean = when (action) {
    is SettingsAction.StyleLoaded -> true
    else -> isLoaded
}

private fun reduceIsClearConfirmationShown(shown: Boolean, action: SettingsAction): Boolean = when (action) {
    SettingsAction.ClearExploredClicked -> true
    SettingsAction.ClearExploredDismissed, SettingsAction.ClearExploredConfirmed -> false
    else -> shown
}

private fun reduceIsClearing(isClearing: Boolean, action: SettingsAction): Boolean = when (action) {
    SettingsAction.ClearExploredConfirmed -> true
    is SettingsAction.ClearExploredFinished -> false
    else -> isClearing
}

/** A failure stays on screen until the next attempt. */
private fun reduceClearFailed(failed: Boolean, action: SettingsAction): Boolean = when (action) {
    SettingsAction.ClearExploredClicked -> false
    is SettingsAction.ClearExploredFinished -> !action.success
    else -> failed
}

/**
 * Turning on needs location "all the time" and the activity permission; without the latter the
 * switch asks for it first and turns on once it is granted.
 */
private fun reduceAutoStartEnabled(enabled: Boolean, action: SettingsAction, permissions: LocationPermissions): Boolean = when (action) {
    is SettingsAction.AutoStartToggled -> action.enabled && permissions.backgroundLocation && permissions.activityRecognition
    is SettingsAction.ActivityPermissionResult -> if (action.granted && permissions.backgroundLocation) true else enabled
    is SettingsAction.AutoStartLoaded -> action.enabled
    else -> enabled
}

private fun reducePermissions(permissions: LocationPermissions, action: SettingsAction): LocationPermissions = when (action) {
    is SettingsAction.PermissionsChanged -> action.permissions
    else -> permissions
}

private fun reduceLocationRequestPending(pending: Boolean, action: SettingsAction): Boolean = when (action) {
    SettingsAction.BackgroundLocationClicked -> true
    SettingsAction.LocationRequestLaunched, SettingsAction.LocationPermissionResult -> false
    else -> pending
}

private fun reduceActivityRequestPending(pending: Boolean, action: SettingsAction, permissions: LocationPermissions): Boolean = when (action) {
    is SettingsAction.AutoStartToggled -> action.enabled && permissions.backgroundLocation && !permissions.activityRecognition
    SettingsAction.ActivityRequestLaunched, is SettingsAction.ActivityPermissionResult -> false
    else -> pending
}

/** A refusal is explained until the permission shows up. */
private fun reduceActivityPermissionDenied(denied: Boolean, action: SettingsAction): Boolean = when (action) {
    is SettingsAction.ActivityPermissionResult -> !action.granted
    is SettingsAction.PermissionsChanged -> if (action.permissions.activityRecognition) false else denied
    else -> denied
}
