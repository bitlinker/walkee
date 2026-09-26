package me.bitlinker.walkee.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.tasks.await
import me.bitlinker.walkee.di.ApplicationScope
import me.bitlinker.walkee.fog.geo.GeoPoint
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything about the device position (ADR 0004): permission state, a shared stream of
 * plausible fixes from the Fused Location Provider, and the last known position.
 *
 * Platform callbacks are wrapped into flows at this boundary; nothing above sees a callback.
 */
@Singleton
class LocationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val client: FusedLocationProviderClient,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val filter = LocationFilter()

    private val _hasPermission = MutableStateFlow(checkPermission())

    /** Whether fine or coarse location is granted; refresh with [refreshPermission] after a request. */
    val hasPermission: StateFlow<Boolean> = _hasPermission.asStateFlow()

    /**
     * Plausible fixes while permission is granted. Shared: the tracker and the map both collect
     * it, but the device is asked for updates only once, and only while someone listens.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val fixes: SharedFlow<LocationFix> = _hasPermission
        .flatMapLatest { granted -> if (granted) rawUpdates() else emptyFlow() }
        .plausible()
        .shareIn(scope, SharingStarted.WhileSubscribed(stopTimeoutMillis = SHARE_TIMEOUT_MILLIS), replay = 1)

    fun refreshPermission() {
        _hasPermission.value = checkPermission()
    }

    /** Last position known to the system, if any and if permitted. */
    suspend fun lastKnownFix(): LocationFix? {
        if (!checkPermission()) return null
        return try {
            @SuppressLint("MissingPermission")
            val location = client.lastLocation.await()
            location?.toFix()
        } catch (e: SecurityException) {
            null
        }
    }

    private fun checkPermission(): Boolean =
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION) || isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // Guarded by hasPermission upstream; SecurityException handled below.
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

    private companion object {
        const val UPDATE_INTERVAL_MILLIS = 2_000L
        const val MIN_UPDATE_INTERVAL_MILLIS = 1_000L
        const val MIN_UPDATE_DISTANCE_METRES = 3f
        const val SHARE_TIMEOUT_MILLIS = 5_000L
    }
}
