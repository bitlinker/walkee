package me.bitlinker.walkee.tracking

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Foreground service (type `location`) that keeps the process alive, and tracking going, while
 * tracking is on (ADR 0006). Built like the activity: a thin host that forwards system events to
 * [TrackingServiceController] as actions and renders its state as [TrackingNotification]. It stops
 * itself once the state says tracking is over.
 */
@AndroidEntryPoint
class TrackingService : Service() {

    @Inject lateinit var controller: TrackingServiceController

    private val renderScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Nothing is rendered before the first command: it must bring the service to the foreground first. */
    private var commandReceived = false
    private var inForeground = false
    private var shownText: String? = null

    override fun onCreate() {
        super.onCreate()
        TrackingNotification.createChannel(this)
        renderScope.launch { controller.state.collect(::render) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        commandReceived = true
        if (!inForeground) inForeground = enterForeground()
        when {
            !inForeground -> controller.dispatch(TrackingServiceAction.ForegroundRefused)
            intent?.action == ACTION_PAUSE -> controller.dispatch(TrackingServiceAction.PauseClicked)
        }
        render(controller.state.value)
        // Not restarted after the process dies: a restart from the background may not use location.
        // Tracking left on resumes when the app is opened, or by auto-start.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        renderScope.cancel()
        controller.clear()
        super.onDestroy()
    }

    private fun enterForeground(): Boolean {
        val state = controller.state.value
        return try {
            ServiceCompat.startForeground(
                this,
                TrackingNotification.ID,
                TrackingNotification.build(this, state),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
            )
            shownText = TrackingNotification.text(this, state)
            true
        } catch (e: Exception) {
            // SecurityException: no location access for a start from the background (Android 14+);
            // ForegroundServiceStartNotAllowedException: a background start without an exemption.
            Log.w(TAG, "Tracking service could not enter the foreground", e)
            false
        }
    }

    private fun render(state: TrackingServiceState) {
        if (!commandReceived) return
        if (state.isFinished) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        if (!inForeground) return
        val text = TrackingNotification.text(this, state)
        if (text != shownText) {
            TrackingNotification.show(this, state)
            shownText = text
        }
    }

    companion object {
        private const val TAG = "TrackingService"
        private const val ACTION_PAUSE = "me.bitlinker.walkee.tracking.PAUSE"

        fun startIntent(context: Context): Intent = Intent(context, TrackingService::class.java)

        fun pauseIntent(context: Context): PendingIntent = PendingIntent.getService(
            context,
            0,
            Intent(context, TrackingService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}
