package me.bitlinker.walkee.ui.screens.home

/** Builds the next state field by field; each field has its own reducer (ADR 0005). */
fun reduceHome(state: HomeState, action: HomeAction): HomeState = state.copy(
    hasLocationPermission = reduceHasLocationPermission(state.hasLocationPermission, action),
    isTracking = reduceIsTracking(state.isTracking, action),
    followUser = reduceFollowUser(state.followUser, action),
    visitedCells = reduceVisitedCells(state.visitedCells, action),
    areaSquareKilometres = reduceAreaSquareKilometres(state.areaSquareKilometres, action),
    permissionRequestPending = reducePermissionRequestPending(
        state.permissionRequestPending,
        action,
        isTracking = state.isTracking,
        hasLocationPermission = state.hasLocationPermission,
    ),
)

private fun reduceHasLocationPermission(granted: Boolean, action: HomeAction): Boolean = when (action) {
    is HomeAction.PermissionResult -> action.granted
    is HomeAction.PermissionChanged -> action.granted
    else -> granted
}

private fun reduceIsTracking(isTracking: Boolean, action: HomeAction): Boolean = when (action) {
    is HomeAction.TrackingChanged -> action.isTracking
    else -> isTracking
}

private fun reduceFollowUser(followUser: Boolean, action: HomeAction): Boolean = when (action) {
    is HomeAction.FollowUserChanged -> action.followUser
    else -> followUser
}

private fun reduceVisitedCells(visitedCells: Long, action: HomeAction): Long = when (action) {
    is HomeAction.ProgressChanged -> action.visitedCells
    else -> visitedCells
}

private fun reduceAreaSquareKilometres(area: Double, action: HomeAction): Double = when (action) {
    is HomeAction.ProgressChanged -> action.areaSquareKilometres
    else -> area
}

/** Starting without permission asks for it instead of toggling; handling the dialog clears the request. */
private fun reducePermissionRequestPending(
    pending: Boolean,
    action: HomeAction,
    isTracking: Boolean,
    hasLocationPermission: Boolean,
): Boolean = when (action) {
    HomeAction.TrackingToggled -> if (!isTracking && !hasLocationPermission) true else pending
    HomeAction.PermissionRequestLaunched, is HomeAction.PermissionResult -> false
    else -> pending
}
