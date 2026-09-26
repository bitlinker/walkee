package me.bitlinker.walkee.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PeriodicSaveTest {

    @Test
    fun `saves every interval while running and once more when the block returns`() = runTest {
        val saves = mutableListOf<Long>()
        runSavingPeriodically(5.minutes, save = { saves += currentTime }) {
            delay(12.minutes)
        }
        assertEquals(listOf(5.minutes, 10.minutes, 12.minutes).map { it.inWholeMilliseconds }, saves)
    }

    @Test
    fun `saves once more when cancelled, and that save is not cancelled`() = runTest {
        val saves = mutableListOf<Long>()
        val job = launch {
            runSavingPeriodically(5.minutes, save = { delay(1.seconds); saves += currentTime }) {
                awaitCancellation()
            }
        }
        delay(1.minutes)
        job.cancelAndJoin()
        assertEquals(listOf((1.minutes + 1.seconds).inWholeMilliseconds), saves)
    }

    @Test
    fun `saves once more when the block fails`() = runTest {
        val saves = mutableListOf<Long>()
        val error = runCatching {
            runSavingPeriodically(5.minutes, save = { saves += currentTime }) {
                delay(7.minutes)
                error("Location updates failed")
            }
        }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertEquals(listOf(5.minutes, 7.minutes).map { it.inWholeMilliseconds }, saves)
    }
}
