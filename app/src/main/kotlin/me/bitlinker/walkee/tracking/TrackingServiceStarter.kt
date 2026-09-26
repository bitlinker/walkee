package me.bitlinker.walkee.tracking

import android.content.Context
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import me.bitlinker.walkee.domain.TrackingForeground
import javax.inject.Inject

/** Starts [TrackingService] for the domain's [TrackingForeground] port. */
class TrackingServiceStarter @Inject constructor(
    @ApplicationContext private val context: Context,
) : TrackingForeground {

    override fun start(): Boolean = try {
        ContextCompat.startForegroundService(context, TrackingService.startIntent(context))
        true
    } catch (e: IllegalStateException) {
        // ForegroundServiceStartNotAllowedException (Android 12+): started from the background
        // without an exemption such as an activity-recognition broadcast.
        Log.w(TAG, "The system did not allow the tracking service to start", e)
        false
    }

    private companion object {
        const val TAG = "TrackingServiceStarter"
    }
}
