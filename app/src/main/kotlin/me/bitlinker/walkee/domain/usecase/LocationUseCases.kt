package me.bitlinker.walkee.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import me.bitlinker.walkee.data.location.LocationFix
import me.bitlinker.walkee.data.location.LocationPermissions
import me.bitlinker.walkee.data.location.LocationRepository
import me.bitlinker.walkee.data.map.MapSessionRepository
import me.bitlinker.walkee.data.settings.SettingsRepository
import me.bitlinker.walkee.domain.TrackingSession
import javax.inject.Inject

class ObserveLocationUseCase @Inject constructor(
    private val locationRepository: LocationRepository,
) {
    operator fun invoke(): Flow<LocationFix> = locationRepository.fixes
}

class GetLastKnownLocationUseCase @Inject constructor(
    private val locationRepository: LocationRepository,
) {
    suspend operator fun invoke(): LocationFix? = locationRepository.lastKnownFix()
}

/** Whether location may be used at all (at least while the app is in use). */
class ObserveLocationPermissionUseCase @Inject constructor(
    private val locationRepository: LocationRepository,
) {
    operator fun invoke(): Flow<Boolean> = locationRepository.permissions.map { it.location }.distinctUntilChanged()
}

class ObservePermissionsUseCase @Inject constructor(
    private val locationRepository: LocationRepository,
) {
    operator fun invoke(): StateFlow<LocationPermissions> = locationRepository.permissions
}

/** Call after a system permission dialog closes and whenever the app comes back to the screen. */
class RefreshPermissionsUseCase @Inject constructor(
    private val locationRepository: LocationRepository,
) {
    operator fun invoke() = locationRepository.refreshPermissions()
}

class ObserveTrackingUseCase @Inject constructor(
    private val trackingSession: TrackingSession,
) {
    operator fun invoke(): StateFlow<Boolean> = trackingSession.isTracking
}

/**
 * Starts or pauses revealing the map and remembers the choice for the next launch. Starting brings
 * up the foreground service, so it must come from the app in the foreground (ADR 0006).
 */
class SetTrackingEnabledUseCase @Inject constructor(
    private val trackingSession: TrackingSession,
    private val settingsRepository: SettingsRepository,
    private val locationRepository: LocationRepository,
) {
    suspend operator fun invoke(enabled: Boolean) {
        settingsRepository.setTrackingEnabled(enabled)
        if (enabled && locationRepository.permissions.value.location) trackingSession.start() else trackingSession.stop()
    }
}

/**
 * Brings tracking back when the app is opened, if it was left on: the process may have died since,
 * e.g. across a reboot. Must run with the app in the foreground, like [SetTrackingEnabledUseCase].
 */
class ResumeTrackingUseCase @Inject constructor(
    private val trackingSession: TrackingSession,
    private val settingsRepository: SettingsRepository,
    private val locationRepository: LocationRepository,
) {
    suspend operator fun invoke() {
        if (trackingSession.isTracking.value || !locationRepository.permissions.value.location) return
        if (settingsRepository.settings.first().trackingEnabled) trackingSession.start()
    }
}

class ObserveFollowUserUseCase @Inject constructor(
    private val mapSessionRepository: MapSessionRepository,
) {
    operator fun invoke(): StateFlow<Boolean> = mapSessionRepository.followUser
}

class SetFollowUserUseCase @Inject constructor(
    private val mapSessionRepository: MapSessionRepository,
) {
    operator fun invoke(follow: Boolean) = mapSessionRepository.setFollowUser(follow)
}

class ObserveCameraZoomRequestsUseCase @Inject constructor(
    private val mapSessionRepository: MapSessionRepository,
) {
    operator fun invoke(): Flow<Float> = mapSessionRepository.zoomRequests
}

class RequestCameraZoomUseCase @Inject constructor(
    private val mapSessionRepository: MapSessionRepository,
) {
    operator fun invoke(zoom: Float) = mapSessionRepository.requestZoom(zoom)
}
