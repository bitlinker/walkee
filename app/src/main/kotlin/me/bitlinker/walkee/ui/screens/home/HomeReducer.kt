package me.bitlinker.walkee.ui.screens.home

/** Builds the next state field by field; each field has its own reducer (ADR 0005). */
fun reduceHome(state: HomeState, action: HomeAction): HomeState = state.copy(
    hasLocationPermission = reduceHasLocationPermission(state.hasLocationPermission, action),
    isTracking = reduceIsTracking(state.isTracking, action),
    followUser = reduceFollowUser(state.followUser, action),
    visitedCells = reduceVisitedCells(state.visitedCells, action),
    areaSquareKilometres = reduceAreaSquareKilometres(state.areaSquareKilometres, action),
    permissionRequestPending = reducePermissionRequestPending(state.permissionRequestPending, action, isTracking = state.isTracking),
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

/**
 * Every start goes through the permission request (location, and notifications for the tracking
 * service); the system asks only for what is missing and answers at once when nothing is. Tracking
 * starts from the result; handling it clears the request.
 */
private fun reducePermissionRequestPending(pending: Boolean, action: HomeAction, isTracking: Boolean): Boolean = when (action) {
    HomeAction.TrackingToggled -> if (!isTracking) true else pending
    HomeAction.PermissionRequestLaunched, is HomeAction.PermissionResult -> false
    else -> pending
}
