package me.bitlinker.walkee.domain

import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration

/**
 * Runs [block], calling [save] every [interval] while it runs and once more when it ends —
 * normally, with an exception or by cancellation. The final save is not cancellable: stopping may
 * be the last chance to persist before the process goes away.
 *
 * [save] should handle its own failures; one thrown from a periodic save cancels [block].
 */
internal suspend fun runSavingPeriodically(
    interval: Duration,
    save: suspend () -> Unit,
    block: suspend () -> Unit,
) {
    try {
        coroutineScope {
            val saver = launch {
                while (true) {
                    delay(interval)
                    save()
                }
            }
            block()
            saver.cancel()
        }
    } finally {
        withContext(NonCancellable) { save() }
    }
}
