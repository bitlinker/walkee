package me.bitlinker.walkee.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.bitlinker.walkee.di.ApplicationScope
import me.bitlinker.walkee.domain.usecase.SyncAutoStartUseCase
import javax.inject.Inject

/**
 * Renews the activity-recognition subscription after a reboot or an app update, which drop it
 * (ADR 0006). Android 15+ also sends BOOT_COMPLETED on the first launch after a force stop.
 */
@AndroidEntryPoint
class AutoStartRestoreReceiver : BroadcastReceiver() {

    @Inject lateinit var syncAutoStart: SyncAutoStartUseCase

    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        scope.launch {
            try {
                syncAutoStart()
            } finally {
                pending.finish()
            }
        }
    }
}
