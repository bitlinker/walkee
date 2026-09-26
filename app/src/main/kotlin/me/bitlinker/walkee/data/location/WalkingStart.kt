package me.bitlinker.walkee.data.location

enum class UserActivity { WALKING, RUNNING, OTHER }

/** One transition reported by activity recognition, without Play services types. */
data class UserActivityTransition(
    val activity: UserActivity,
    /** True when the activity began, false when it ended. */
    val isEnter: Boolean,
    /** When the transition happened, on the `SystemClock.elapsedRealtimeNanos()` clock. */
    val elapsedRealtimeNanos: Long,
)

/**
 * Decides whether activity-recognition transitions mean the user has just set off on foot (ADR 0006).
 * Pure, so it is unit-tested directly.
 *
 * The Transition API replays the last matching transition whenever the subscription is renewed,
 * and that one may be hours old; only fresh transitions count.
 */
object WalkingStart {
    /** Transitions arrive within seconds of being detected; anything older is a replay. */
    const val MAX_AGE_NANOS = 60_000_000_000L

    fun detected(transitions: List<UserActivityTransition>, nowElapsedRealtimeNanos: Long): Boolean =
        transitions.any { transition ->
            transition.isEnter &&
                transition.activity != UserActivity.OTHER &&
                nowElapsedRealtimeNanos - transition.elapsedRealtimeNanos <= MAX_AGE_NANOS
        }
}
