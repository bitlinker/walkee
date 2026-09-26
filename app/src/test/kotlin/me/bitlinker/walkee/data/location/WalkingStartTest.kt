package me.bitlinker.walkee.data.location

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WalkingStartTest {

    private val now = 10_000_000_000_000L
    private val second = 1_000_000_000L

    private fun transition(activity: UserActivity, isEnter: Boolean = true, ageNanos: Long = 2 * second) =
        UserActivityTransition(activity, isEnter, elapsedRealtimeNanos = now - ageNanos)

    @Test
    fun `fresh start of walking or running counts`() {
        assertTrue(WalkingStart.detected(listOf(transition(UserActivity.WALKING)), now))
        assertTrue(WalkingStart.detected(listOf(transition(UserActivity.RUNNING)), now))
    }

    @Test
    fun `ends and other activities do not count`() {
        assertFalse(WalkingStart.detected(listOf(transition(UserActivity.WALKING, isEnter = false)), now))
        assertFalse(WalkingStart.detected(listOf(transition(UserActivity.OTHER)), now))
        assertFalse(WalkingStart.detected(emptyList(), now))
    }

    @Test
    fun `replayed transitions older than the limit are ignored`() {
        assertTrue(WalkingStart.detected(listOf(transition(UserActivity.WALKING, ageNanos = WalkingStart.MAX_AGE_NANOS)), now))
        assertFalse(WalkingStart.detected(listOf(transition(UserActivity.WALKING, ageNanos = WalkingStart.MAX_AGE_NANOS + 1)), now))
        assertFalse(WalkingStart.detected(listOf(transition(UserActivity.RUNNING, ageNanos = 3_600 * second)), now))
    }

    @Test
    fun `one fresh start in a batch is enough`() {
        val batch = listOf(
            transition(UserActivity.WALKING, ageNanos = 3_600 * second),
            transition(UserActivity.OTHER),
            transition(UserActivity.RUNNING),
        )
        assertTrue(WalkingStart.detected(batch, now))
    }
}
