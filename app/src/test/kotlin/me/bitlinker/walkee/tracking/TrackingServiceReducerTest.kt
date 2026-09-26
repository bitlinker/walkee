package me.bitlinker.walkee.tracking

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrackingServiceReducerTest {

    @Test
    fun `the service is finished exactly while tracking is off`() {
        val tracking = reduceTrackingService(TrackingServiceState(), TrackingServiceAction.TrackingChanged(true))
        assertFalse(tracking.isFinished)

        val finished = reduceTrackingService(tracking, TrackingServiceAction.TrackingChanged(false))
        assertTrue(finished.isFinished)

        assertFalse(reduceTrackingService(finished, TrackingServiceAction.TrackingChanged(true)).isFinished)
    }

    @Test
    fun `pause and a refused start wait for tracking to stop`() {
        val state = TrackingServiceState(areaSquareKilometres = 1.5)
        assertEquals(state, reduceTrackingService(state, TrackingServiceAction.PauseClicked))
        assertEquals(state, reduceTrackingService(state, TrackingServiceAction.ForegroundRefused))
    }

    @Test
    fun `progress is copied verbatim`() {
        val state = reduceTrackingService(TrackingServiceState(), TrackingServiceAction.ProgressChanged(0.42))
        assertEquals(0.42, state.areaSquareKilometres)
        assertFalse(state.isFinished)
    }
}
