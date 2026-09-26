package me.bitlinker.walkee.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import me.bitlinker.walkee.data.location.LocationFix
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

class ObserveLocationPermissionUseCase @Inject constructor(
    private val locationRepository: LocationRepository,
) {
    operator fun invoke(): StateFlow<Boolean> = locationRepository.hasPermission
}

/** Call after the system permission dialog closes. */
class RefreshLocationPermissionUseCase @Inject constructor(
    private val locationRepository: LocationRepository,
) {
    operator fun invoke() = locationRepository.refreshPermission()
}

class ObserveTrackingUseCase @Inject constructor(
    private val trackingSession: TrackingSession,
) {
    operator fun invoke(): StateFlow<Boolean> = trackingSession.isTracking
}

/** Starts or pauses revealing the map and remembers the choice for the next launch. */
class SetTrackingEnabledUseCase @Inject constructor(
    private val trackingSession: TrackingSession,
    private val settingsRepository: SettingsRepository,
    private val locationRepository: LocationRepository,
) {
    suspend operator fun invoke(enabled: Boolean) {
        settingsRepository.setTrackingEnabled(enabled)
        trackingSession.setEnabled(enabled && locationRepository.hasPermission.value)
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
