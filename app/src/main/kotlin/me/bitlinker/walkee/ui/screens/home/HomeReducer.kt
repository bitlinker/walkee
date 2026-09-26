package me.bitlinker.walkee.ui.screens.home

fun reduceHome(state: HomeState, action: HomeAction): HomeState = when (action) {
    HomeAction.TrackingToggled ->
        if (!state.isTracking && !state.hasLocationPermission) state.copy(permissionRequestPending = true) else state

    HomeAction.PermissionRequestLaunched -> state.copy(permissionRequestPending = false)

    is HomeAction.PermissionResult -> state.copy(hasLocationPermission = action.granted, permissionRequestPending = false)

    is HomeAction.PermissionChanged -> state.copy(hasLocationPermission = action.granted)

    is HomeAction.TrackingChanged -> state.copy(isTracking = action.isTracking)

    is HomeAction.FollowUserChanged -> state.copy(followUser = action.followUser)

    is HomeAction.ProgressChanged -> state.copy(
        visitedCells = action.visitedCells,
        areaSquareKilometres = action.areaSquareKilometres,
    )

    HomeAction.RecenterClicked,
    HomeAction.SettingsClicked,
    -> state
}
