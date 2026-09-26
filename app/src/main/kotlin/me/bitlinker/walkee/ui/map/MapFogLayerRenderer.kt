package me.bitlinker.walkee.ui.map

import android.util.Log
import com.yandex.mapkit.RawTile
import com.yandex.mapkit.TileId
import com.yandex.mapkit.Version
import com.yandex.mapkit.ZoomRange
import com.yandex.mapkit.geometry.geo.Projections
import com.yandex.mapkit.layers.BaseDataSource
import com.yandex.mapkit.layers.DataSourceListener
import com.yandex.mapkit.layers.Layer
import com.yandex.mapkit.layers.LayerOptions
import com.yandex.mapkit.layers.OverzoomMode
import com.yandex.mapkit.layers.TileDataSource
import com.yandex.mapkit.layers.TileFormat
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.tiles.TileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import me.bitlinker.walkee.data.map.FogCoverage
import me.bitlinker.walkee.data.map.HexCoverage
import me.bitlinker.walkee.data.map.TileCoverage
import me.bitlinker.walkee.data.settings.FogCellShape
import me.bitlinker.walkee.data.settings.FogStyle
import me.bitlinker.walkee.domain.usecase.GetFogTileCoverageUseCase
import me.bitlinker.walkee.domain.usecase.GetFogTileHexCoverageUseCase
import me.bitlinker.walkee.domain.usecase.ObserveFogInvalidationsUseCase
import me.bitlinker.walkee.domain.usecase.ObserveFogStyleUseCase
import me.bitlinker.walkee.fog.geo.TileKey
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * The fog-of-war layer: a MapKit raster tile layer whose tiles are rendered on demand from fog
 * storage (ADR 0003). Owns the [TileProvider] strongly — MapKit keeps only a weak reference.
 *
 * Refresh without flicker: instead of `DataSourceLayer.clear()` (drops every tile at once, so the
 * light base map flashes through) the layer is versioned and refreshed with
 * [TileDataSource.invalidate], which re-requests tiles while the old ones stay on screen. Each
 * tile carries an etag derived from its coverage, so re-requests of unchanged tiles are answered
 * with `NOT_MODIFIED` and cost only the coverage computation.
 */
class MapFogLayerRenderer @Inject constructor(
    private val getFogTileCoverage: GetFogTileCoverageUseCase,
    private val getFogTileHexCoverage: GetFogTileHexCoverageUseCase,
    private val observeFogInvalidations: ObserveFogInvalidationsUseCase,
    private val observeFogStyle: ObserveFogStyleUseCase,
) {
    private val style = AtomicReference(FogStyle())
    private val uniformTileCache = AtomicReference<UniformTiles?>(null)
    private val dataVersion = AtomicLong(1)
    private var layer: Layer? = null
    private var tileDataSource: TileDataSource? = null

    private val tileProvider = TileProvider { tileId, version, features, etag ->
        loadTile(tileId, version, features, etag)
    }

    // Kept as a field: MapKit holds listeners weakly. Delivers the data source behind the layer,
    // which is the only handle for versioned invalidation in MapKit 4.x.
    private val dataSourceListener = DataSourceListener { source: BaseDataSource ->
        tileDataSource = source as? TileDataSource
        Log.i(TAG, "Fog data source ${source.id} attached, versioned refresh ${if (tileDataSource != null) "on" else "unavailable"}")
    }

    fun attach(map: Map, scope: CoroutineScope) {
        check(layer == null) { "Fog layer is already attached" }
        val added = map.addTileLayer(LAYER_ID, layerOptions()) { builder ->
            builder.setTileFormat(TileFormat.PNG)
            builder.setProjection(Projections.getWgs84Mercator())
            builder.setZoomRanges(listOf(ZoomRange(0, MAX_ZOOM_EXCLUSIVE)))
            builder.setTileProvider(WeakReference(tileProvider))
        }
        added.dataSourceLayer().setDataSourceListener(WeakReference(dataSourceListener))
        layer = added
        scope.launch { observeInvalidations() }
    }

    fun detach() {
        layer?.let { if (it.isValid) it.remove() }
        layer = null
        tileDataSource = null
    }

    @OptIn(FlowPreview::class)
    private suspend fun observeInvalidations() {
        val styleChanges = observeFogStyle().map { newStyle -> style.set(newStyle) }
        val dataChanges = observeFogInvalidations().map { }
        merge(styleChanges, dataChanges)
            .debounce(REFRESH_DEBOUNCE)
            .collectLatest { refresh() }
    }

    /** Re-requests visible tiles. Must run on the main thread. */
    private fun refresh() {
        val current = layer ?: return
        if (!current.isValid) return
        val source = tileDataSource
        if (source != null && source.isValid) {
            source.invalidate(dataVersion.incrementAndGet().toString())
        } else {
            // Fallback: flickers, but keeps the fog correct if the data source never arrives.
            current.dataSourceLayer().clear()
        }
    }

    private fun loadTile(tileId: TileId, version: Version, features: kotlin.collections.Map<String, String>, etag: String): RawTile {
        val currentStyle = style.get()
        return try {
            val tile = TileKey.of(tileId.z, tileId.x, tileId.y)
            val coverage = coverageOf(tile, currentStyle)
            val tileEtag = etagOf(coverage, currentStyle)
            // UseCache.YES lets MapKit keep tiles in memory, so returning to a zoom level (and
            // overzoom placeholders while zooming out) does not start from an empty layer.
            // Freshness comes from versioned invalidation + etags, not from dropping the cache.
            if (tileEtag == etag) {
                RawTile(version, features, etag, RawTile.UseCache.YES, RawTile.State.NOT_MODIFIED, ByteArray(0))
            } else {
                RawTile(version, features, tileEtag, RawTile.UseCache.YES, RawTile.State.OK, encode(coverage, currentStyle))
            }
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Tile ${tileId.z}/${tileId.x}/${tileId.y} is outside the pyramid", e)
            RawTile(version, features, etag, RawTile.UseCache.NO, RawTile.State.ERROR, ByteArray(0))
        }
    }

    /** Hexagons while they are big enough to see (see [HexFogTilePainter.drawsHexagons]), squares otherwise. */
    private fun coverageOf(tile: TileKey, style: FogStyle): TileCoverage =
        if (style.cellShape == FogCellShape.HEXAGONS && HexFogTilePainter.drawsHexagons(tile.zoom, style.displayZoom)) {
            getFogTileHexCoverage(tile, style.displayZoom)
        } else {
            getFogTileCoverage(tile, style.displayZoom)
        }

    private fun etagOf(coverage: TileCoverage, style: FogStyle): String =
        "${style.hashCode().toUInt().toString(16)}-${coverage.fingerprint().toULong().toString(16)}"

    private fun encode(coverage: TileCoverage, style: FogStyle): ByteArray {
        if (coverage.isAllOpen) return uniformTiles(style).revealed
        if (coverage.isAllClosed) return uniformTiles(style).hidden
        val pixels = when (coverage) {
            is FogCoverage -> FogTilePainter.paint(coverage, style)
            is HexCoverage -> HexFogTilePainter.paint(coverage, style)
        }
        return toPng(pixels)
    }

    /** Most tiles are entirely hidden or entirely revealed; their PNGs are encoded once per style. */
    private fun uniformTiles(style: FogStyle): UniformTiles {
        uniformTileCache.get()?.let { if (it.style == style) return it }
        return UniformTiles(style, toPng(FogTilePainter.solid(style)), toPng(FogTilePainter.revealed(style)))
            .also { uniformTileCache.set(it) }
    }

    private class UniformTiles(val style: FogStyle, val hidden: ByteArray, val revealed: ByteArray)

    private fun layerOptions() = LayerOptions(
        /* active = */ true,
        /* nightModeAvailable = */ false,
        /* cacheable = */ false,
        /* animateOnActivation = */ false,
        /* tileAppearingAnimationDuration = */ 0L,
        // Shows cached tiles of adjacent zooms while a level loads. WITH_PREFETCH was tried and
        // made no visible difference (ADR 0003).
        /* overzoomMode = */ OverzoomMode.ENABLED,
        /* transparent = */ true,
        /* versionSupport = */ true,
    )

    private companion object {
        const val TAG = "MapFogLayerRenderer"
        const val LAYER_ID = "walkee_fog"
        const val MAX_ZOOM_EXCLUSIVE = 24
        val REFRESH_DEBOUNCE = 400.milliseconds

        fun toPng(pixels: IntArray): ByteArray =
            IndexedPngEncoder.encode(pixels, FogTilePainter.TILE_SIZE, FogTilePainter.TILE_SIZE)
    }
}
