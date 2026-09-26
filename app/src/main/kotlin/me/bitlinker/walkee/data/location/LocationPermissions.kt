package me.bitlinker.walkee.data.location

/** What the user has allowed the app to know about their position and movement. */
data class LocationPermissions(
    /** Fine or coarse location, at least while the app is in use. */
    val location: Boolean = false,
    /** Location "all the time"; before Android 10 it comes with [location]. */
    val backgroundLocation: Boolean = false,
    /** Physical activity, needed for activity recognition; granted at install before Android 10. */
    val activityRecognition: Boolean = false,
)
