package me.bitlinker.walkee.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionEvent
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.tasks.await
import me.bitlinker.walkee.di.ActivityTransitionsIntent
import me.bitlinker.walkee.di.ApplicationScope
import me.bitlinker.walkee.fog.geo.GeoPoint
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything about the device position and the user's movement (ADR 0004): permission state, a
 * shared stream of plausible fixes from the Fused Location Provider, the last known position, and
 * the activity-recognition subscription that detects the start of a walk (ADR 0006).
 *
 * Platform callbacks are wrapped into flows at this boundary; nothing above sees a callback.
 */
@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: FusedLocationProviderClient,
    private val activityClient: ActivityRecognitionClient,
    @ActivityTransitionsIntent private val transitionsIntent: PendingIntent,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val filter = LocationFilter()

    private val _permissions = MutableStateFlow(checkPermissions())

    /** What is granted; refresh with [refreshPermissions] after a request or on return to the app. */
    val permissions: StateFlow<LocationPermissions> = _permissions.asStateFlow()

    /**
     * Plausible fixes while permission is granted. Shared: the tracker and the map both collect
     * it, but the device is asked for updates only once, and only while someone listens.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val fixes: SharedFlow<LocationFix> = _permissions
        .map { it.location }
        .distinctUntilChanged()
        .flatMapLatest { granted -> if (granted) rawUpdates() else emptyFlow() }
        .plausible()
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = SHARE_TIMEOUT_MILLIS), replay = 1)

    fun refreshPermissions() {
        _permissions.value = checkPermissions()
    }

    /** Last position known to the system, if any and if permitted. */
    suspend fun lastKnownFix(): LocationFix? {
        if (!_permissions.value.location) return null
        return try {
            @SuppressLint("MissingPermission")
            val location = client.lastLocation.await()
            location?.toFix()
        } catch (e: SecurityException) {
            null
        }
    }

    /**
     * Subscribes to the starts of walking and running (Activity Recognition Transition API), or
     * cancels the subscription. Idempotent. The subscription is lost on reboot, app update and force
     * stop, so callers renew it (ADR 0006).
     */
    @SuppressLint("MissingPermission") // Subscribing is guarded by activityRecognition upstream.
    suspend fun setWalkingStartUpdates(enabled: Boolean) {
        try {
            if (enabled) {
                activityClient.requestActivityTransitionUpdates(WALKING_START_REQUEST, transitionsIntent).await()
            } else {
                activityClient.removeActivityTransitionUpdates(transitionsIntent).await()
            }
            Log.d(TAG, "Walking-start updates ${if (enabled) "on" else "off"}")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not turn walking-start updates ${if (enabled) "on" else "off"}", e)
        }
    }

    /** Whether an activity-transition broadcast says the user has just started walking or running. */
    fun isWalkingStart(intent: Intent): Boolean {
        val result = ActivityTransitionResult.extractResult(intent) ?: return false
        return WalkingStart.detected(result.transitionEvents.map { it.toTransition() }, SystemClock.elapsedRealtimeNanos())
    }

    private fun checkPermissions(): LocationPermissions {
        val location = isGranted(Manifest.permission.ACCESS_FINE_LOCATION) || isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)
        val tenOrNewer = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        return LocationPermissions(
            location = location,
            backgroundLocation = if (tenOrNewer) location && isGranted(Manifest.permission.ACCESS_BACKGROUND_LOCATION) else location,
            activityRecognition = if (tenOrNewer) isGranted(Manifest.permission.ACTIVITY_RECOGNITION) else true,
        )
    }

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // Guarded by permissions upstream; SecurityException handled below.
    private fun rawUpdates(): Flow<LocationFix> = callbackFlow {
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                for (location in result.locations) trySend(location.toFix())
            }
        }
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MILLIS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MILLIS)
            .setMinUpdateDistanceMeters(MIN_UPDATE_DISTANCE_METRES)
            .setWaitForAccurateLocation(false)
            .build()
        try {
            client.requestLocationUpdates(request, callback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            close(e)
        }
        awaitClose { client.removeLocationUpdates(callback) }
    }

    private fun Flow<LocationFix>.plausible(): Flow<LocationFix> = flow {
        var previous: LocationFix? = null
        collect { fix ->
            if (filter.accept(previous, fix)) {
                previous = fix
                emit(fix)
            }
        }
    }

    private fun Location.toFix(): LocationFix = LocationFix(
        point = GeoPoint(latitude, longitude),
        accuracyMetres = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
        speedMetresPerSecond = if (hasSpeed()) speed else null,
        timeMillis = time,
    )

    private fun ActivityTransitionEvent.toTransition(): UserActivityTransition = UserActivityTransition(
        activity = when (activityType) {
            DetectedActivity.WALKING -> UserActivity.WALKING
            DetectedActivity.RUNNING -> UserActivity.RUNNING
            else -> UserActivity.OTHER
        },
        isEnter = transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER,
        elapsedRealtimeNanos = elapsedRealTimeNanos,
    )

    private companion object {
        const val TAG = "LocationRepository"
        const val UPDATE_INTERVAL_MILLIS = 2_000L
        const val MIN_UPDATE_INTERVAL_MILLIS = 1_000L
        const val MIN_UPDATE_DISTANCE_METRES = 3f
        const val SHARE_TIMEOUT_MILLIS = 5_000L

        /** Only setting off on foot starts tracking; vehicles and bicycles do not (ADR 0006). */
        val WALKING_START_REQUEST = ActivityTransitionRequest(
            listOf(DetectedActivity.WALKING, DetectedActivity.RUNNING).map { activity ->
                ActivityTransition.Builder()
                    .setActivityType(activity)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build()
            },
        )
    }
}
