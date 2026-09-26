package me.bitlinker.walkee.tracking

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import me.bitlinker.walkee.MainActivity
import me.bitlinker.walkee.R
import java.util.Locale

/**
 * The tracking service's "screen": renders [TrackingServiceState] as the ongoing notification.
 * Stateless; its buttons send intents that the service turns into actions.
 */
object TrackingNotification {
    const val ID = 1
    private const val CHANNEL_ID = "tracking"

    fun createChannel(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(context.getString(R.string.tracking_channel_name))
                .setShowBadge(false)
                .build(),
        )
    }

    /** The only part that changes while tracking; the service skips updates that keep it. */
    fun text(context: Context, state: TrackingServiceState): String = context.getString(
        R.string.tracking_notification_text,
        String.format(Locale.getDefault(), "%.2f", state.areaSquareKilometres),
    )

    fun build(context: Context, state: TrackingServiceState): Notification =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.tracking_notification_title))
            .setContentText(text(context, state))
            .setContentIntent(openAppIntent(context))
            .addAction(0, context.getString(R.string.tracking_notification_pause), TrackingService.pauseIntent(context))
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

    // Without POST_NOTIFICATIONS the update is just not shown; the service runs regardless.
    @SuppressLint("MissingPermission")
    fun show(context: Context, state: TrackingServiceState) {
        NotificationManagerCompat.from(context).notify(ID, build(context, state))
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
