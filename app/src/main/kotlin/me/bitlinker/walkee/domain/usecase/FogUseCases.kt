package me.bitlinker.walkee.domain.usecase

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import me.bitlinker.walkee.data.location.LocationRepository
import me.bitlinker.walkee.data.map.FogCoverage
import me.bitlinker.walkee.data.map.FogInvalidation
import me.bitlinker.walkee.data.map.HexCoverage
import me.bitlinker.walkee.data.map.MapRepository
import me.bitlinker.walkee.data.settings.FogCellShape
import me.bitlinker.walkee.data.settings.FogStyle
import me.bitlinker.walkee.data.settings.SettingsRepository
import me.bitlinker.walkee.fog.geo.FogGrid
import me.bitlinker.walkee.fog.geo.TileKey
import javax.inject.Inject

/** Coverage grid for one map tile; synchronous because MapKit asks for tiles on its own threads. */
class GetFogTileCoverageUseCase @Inject constructor(
    private val mapRepository: MapRepository,
) {
    operator fun invoke(tile: TileKey, displayZoom: Int): FogCoverage = mapRepository.coverage(tile, displayZoom)
}

/** Hexagon coverage for one map tile; synchronous for the same reason as [GetFogTileCoverageUseCase]. */
class GetFogTileHexCoverageUseCase @Inject constructor(
    private val mapRepository: MapRepository,
) {
    operator fun invoke(tile: TileKey, displayZoom: Int): HexCoverage = mapRepository.hexCoverage(tile, displayZoom)
}

class ObserveFogInvalidationsUseCase @Inject constructor(
    private val mapRepository: MapRepository,
) {
    operator fun invoke(): Flow<FogInvalidation> = mapRepository.invalidations
}

class ObserveFogStyleUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    operator fun invoke(): Flow<FogStyle> = settingsRepository.fogStyle
}

class SetFogOpacityUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(opacity: Float) = settingsRepository.setFogOpacity(opacity)
}

class SetFogDisplayZoomUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(zoom: Int) = settingsRepository.setDisplayZoom(zoom)
}

class SetFogCellShapeUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(shape: FogCellShape) = settingsRepository.setCellShape(shape)
}

class FlushFogUseCase @Inject constructor(
    private val mapRepository: MapRepository,
) {
    suspend operator fun invoke() = mapRepository.flush()
}

data class ExplorationProgress(
    val visitedCells: Long,
    /** Approximate revealed area using the cell size at the player's latitude. */
    val areaSquareKilometres: Double,
)

class ObserveExplorationProgressUseCase @Inject constructor(
    private val mapRepository: MapRepository,
    private val locationRepository: LocationRepository,
) {
    operator fun invoke(): Flow<ExplorationProgress> {
        val latitude: Flow<Double> = locationRepository.fixes
            .map { it.point.latitude }
            .onStart { emit(DEFAULT_LATITUDE) }
            .distinctUntilChanged()
        return combine(mapRepository.progress, latitude) { progress, lat ->
            val cellSide = FogGrid.cellSizeMetres(lat)
            ExplorationProgress(
                visitedCells = progress.visitedCells,
                areaSquareKilometres = progress.visitedCells * cellSide * cellSide / 1_000_000.0,
            )
        }.distinctUntilChanged()
    }

    private companion object {
        /** Moscow, until the first fix arrives. */
        const val DEFAULT_LATITUDE = 55.75
    }
}
