package me.bitlinker.walkee.ui.map

import android.graphics.Bitmap
import android.util.Log
import com.yandex.mapkit.RawTile
import com.yandex.mapkit.TileId
import com.yandex.mapkit.Version
import com.yandex.mapkit.ZoomRange
import com.yandex.mapkit.geometry.geo.Projections
import com.yandex.mapkit.layers.Layer
import com.yandex.mapkit.layers.LayerOptions
import com.yandex.mapkit.layers.OverzoomMode
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
import me.bitlinker.walkee.data.settings.FogStyle
import me.bitlinker.walkee.domain.usecase.GetFogTileCoverageUseCase
import me.bitlinker.walkee.domain.usecase.ObserveFogInvalidationsUseCase
import me.bitlinker.walkee.domain.usecase.ObserveFogStyleUseCase
import me.bitlinker.walkee.fog.geo.TileKey
import java.io.ByteArrayOutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * The fog-of-war layer: a MapKit raster tile layer whose tiles are rendered on demand from fog
 * storage (ADR 0003). Owns the [TileProvider] strongly — MapKit keeps only a weak reference.
 */
class MapFogLayerRenderer @Inject constructor(
    private val getFogTileCoverage: GetFogTileCoverageUseCase,
    private val observeFogInvalidations: ObserveFogInvalidationsUseCase,
    private val observeFogStyle: ObserveFogStyleUseCase,
) {
    private val style = AtomicReference(FogStyle())
    private val solidTileCache = AtomicReference<Pair<FogStyle, ByteArray>?>(null)
    private var layer: Layer? = null

    private val tileProvider = TileProvider { tileId, version, features, etag ->
        loadTile(tileId, version, features, etag)
    }

    fun attach(map: Map, scope: CoroutineScope) {
        check(layer == null) { "Fog layer is already attached" }
        layer = map.addTileLayer(LAYER_ID, layerOptions()) { builder ->
            builder.setTileFormat(TileFormat.PNG)
            builder.setProjection(Projections.getWgs84Mercator())
            builder.setZoomRanges(listOf(ZoomRange(0, MAX_ZOOM_EXCLUSIVE)))
            builder.setTileProvider(WeakReference(tileProvider))
        }
        scope.launch { observeInvalidations() }
    }

    fun detach() {
        layer?.let { if (it.isValid) it.remove() }
        layer = null
    }

    @OptIn(FlowPreview::class)
    private suspend fun observeInvalidations() {
        val styleChanges = observeFogStyle().map { newStyle -> style.set(newStyle) }
        val dataChanges = observeFogInvalidations().map { }
        merge(styleChanges, dataChanges)
            .debounce(REFRESH_DEBOUNCE)
            .collectLatest { refresh() }
    }

    /** Drops cached tiles and re-requests the visible ones. Must run on the main thread. */
    private fun refresh() {
        val current = layer ?: return
        if (!current.isValid) return
        current.dataSourceLayer().clear()
    }

    private fun loadTile(tileId: TileId, version: Version, features: kotlin.collections.Map<String, String>, etag: String): RawTile {
        val currentStyle = style.get()
        return try {
            val tile = TileKey.of(tileId.z, tileId.x, tileId.y)
            val coverage = getFogTileCoverage(tile, currentStyle.displayZoom)
            RawTile(version, features, etag, RawTile.UseCache.NO, RawTile.State.OK, encode(coverage, currentStyle))
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Tile ${tileId.z}/${tileId.x}/${tileId.y} is outside the pyramid", e)
            RawTile(version, features, etag, RawTile.UseCache.NO, RawTile.State.ERROR, ByteArray(0))
        }
    }

    private fun encode(coverage: FogCoverage, style: FogStyle): ByteArray {
        if (coverage.isAllOpen) return TRANSPARENT_TILE
        if (coverage.isAllClosed) return solidTile(style)
        return toPng(FogTilePainter.paint(coverage, style))
    }

    private fun solidTile(style: FogStyle): ByteArray {
        solidTileCache.get()?.let { (cachedStyle, bytes) -> if (cachedStyle == style) return bytes }
        val bytes = toPng(FogTilePainter.solid(style))
        solidTileCache.set(style to bytes)
        return bytes
    }

    private fun layerOptions() = LayerOptions(
        /* active = */ true,
        /* nightModeAvailable = */ false,
        /* cacheable = */ false,
        /* animateOnActivation = */ false,
        /* tileAppearingAnimationDuration = */ 0L,
        /* overzoomMode = */ OverzoomMode.ENABLED,
        /* transparent = */ true,
        /* versionSupport = */ false,
    )

    private companion object {
        const val TAG = "MapFogLayerRenderer"
        const val LAYER_ID = "walkee_fog"
        const val MAX_ZOOM_EXCLUSIVE = 24
        val REFRESH_DEBOUNCE = 700.milliseconds

        val TRANSPARENT_TILE: ByteArray = toPng(IntArray(FogTilePainter.TILE_SIZE * FogTilePainter.TILE_SIZE))

        fun toPng(pixels: IntArray): ByteArray {
            val bitmap = Bitmap.createBitmap(pixels, FogTilePainter.TILE_SIZE, FogTilePainter.TILE_SIZE, Bitmap.Config.ARGB_8888)
            val out = ByteArrayOutputStream(4096)
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            bitmap.recycle()
            return out.toByteArray()
        }
    }
}
