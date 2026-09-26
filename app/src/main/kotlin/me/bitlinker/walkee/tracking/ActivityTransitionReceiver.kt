package me.bitlinker.walkee.tracking

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.bitlinker.walkee.di.ApplicationScope
import me.bitlinker.walkee.domain.usecase.StartTrackingOnWalkUseCase
import javax.inject.Inject

/**
 * Receives activity-recognition transitions (ADR 0006) and starts tracking when the user sets off on
 * foot. Wakes the process if it is not running; receiving the broadcast is what allows the
 * foreground service to start from the background, so it starts right here.
 */
@AndroidEntryPoint
class ActivityTransitionReceiver : BroadcastReceiver() {

    @Inject lateinit var startTrackingOnWalk: StartTrackingOnWalkUseCase

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        scope.launch {
            try {
                if (startTrackingOnWalk(intent)) Log.i(TAG, "Walking detected: tracking started")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to handle an activity transition", e)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "ActivityTransitions"

        /** Mutable: Play services writes the transitions into it (required on Android 12+). */
        fun pendingIntent(context: Context): PendingIntent {
            val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            return PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, ActivityTransitionReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or mutable,
            )
        }
    }
}
