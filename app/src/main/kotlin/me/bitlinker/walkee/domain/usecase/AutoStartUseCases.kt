package me.bitlinker.walkee.domain.usecase

import android.content.Intent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.bitlinker.walkee.data.location.LocationRepository
import me.bitlinker.walkee.data.settings.SettingsRepository
import me.bitlinker.walkee.domain.TrackingSession
import javax.inject.Inject

// Auto-start: tracking starts by itself when the user sets off on foot (ADR 0006).

class ObserveAutoStartUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(): Flow<Boolean> = settingsRepository.settings.map { it.autoStartEnabled }.distinctUntilChanged()
}

/** Remembers the choice and subscribes to, or drops, activity recognition accordingly. */
class SetAutoStartEnabledUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val syncAutoStart: SyncAutoStartUseCase,
) {
    suspend operator fun invoke(enabled: Boolean) {
        settingsRepository.setAutoStartEnabled(enabled)
        syncAutoStart()
    }
}

/**
 * Keeps the activity-recognition subscription in line with the setting and the permissions.
 * Idempotent; the subscription is lost on reboot, app update and force stop, so this runs whenever
 * the app is opened and after those events.
 */
class SyncAutoStartUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val locationRepository: LocationRepository,
) {
    suspend operator fun invoke() {
        val permissions = locationRepository.permissions.value
        val armed = settingsRepository.settings.first().autoStartEnabled &&
            permissions.backgroundLocation &&
            permissions.activityRecognition
        locationRepository.setWalkingStartUpdates(armed)
    }
}

/**
 * Handles an activity-recognition broadcast: starts tracking if the user has just set off on foot,
 * auto-start is on, and location may be used in the background — a service started from the
 * background gets no location otherwise. Returns whether tracking was started.
 */
class StartTrackingOnWalkUseCase @Inject constructor(
    private val locationRepository: LocationRepository,
    private val settingsRepository: SettingsRepository,
    private val trackingSession: TrackingSession,
) {
    suspend operator fun invoke(intent: Intent): Boolean {
        if (!locationRepository.isWalkingStart(intent) || trackingSession.isTracking.value) return false
        if (!settingsRepository.settings.first().autoStartEnabled) return false
        locationRepository.refreshPermissions()
        if (!locationRepository.permissions.value.backgroundLocation) return false
        if (!trackingSession.start()) return false
        // As if the user had pressed start: pausing and resuming on the next launch work the same way.
        settingsRepository.setTrackingEnabled(true)
        return true
    }
}
