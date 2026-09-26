package me.bitlinker.walkee.data.map

import me.bitlinker.walkee.fog.geo.Epsg3395
import me.bitlinker.walkee.fog.geo.FogGrid
import me.bitlinker.walkee.fog.geo.GeoPoint
import me.bitlinker.walkee.fog.geo.TileKey
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max

/**
 * Turns positions into storage cells to reveal. Works in "cell units" — projected world
 * coordinates scaled so that one storage cell is `1 × 1`; Mercator is conformal, so a ground
 * disk is a disk there (locally), and metres convert with the latitude-dependent scale.
 */
object FogBrush {

    /** Cells whose centres lie within [radiusMetres] of [center]; always includes the centre cell. */
    fun diskCells(center: GeoPoint, radiusMetres: Double): List<TileKey> {
        val out = LinkedHashSet<Long>()
        stampDisk(center, radiusMetres, out)
        return out.map(::TileKey)
    }

    /**
     * Cells covered by dragging a disk of [radiusMetres] from [from] to [to].
     *
     * The core of the stroke is the *supercover* of the segment — every cell the segment passes
     * through, visited one axis step at a time — so the result is always 4-connected: no two
     * cells (and hence no two coarser display cells) touch only at a corner. Disks stamped
     * every half cell along the segment add the brush width; each contains its own core cell,
     * so the union stays 4-connected.
     */
    fun strokeCells(from: GeoPoint, to: GeoPoint, radiusMetres: Double): List<TileKey> {
        val out = LinkedHashSet<Long>()
        val a = toCellUnits(from)
        val b = toCellUnits(to)
        addSupercover(a.x, a.y, b.x, b.y, out)
        val radiusCells = radiusMetres / Epsg3395.metresPerWorldUnit(from.latitude) * SCALE
        if (radiusCells > 0.5) {
            val length = hypot(b.x - a.x, b.y - a.y)
            val steps = max(1, ceil(length / STROKE_STEP_CELLS).toInt())
            for (i in 0..steps) {
                val t = i.toDouble() / steps
                stampDisk(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, radiusCells, out)
            }
        }
        return out.map(::TileKey)
    }

    /**
     * Grid traversal (Amanatides & Woo) from the cell containing `(x0, y0)` to the one containing
     * `(x1, y1)`. When the segment crosses a cell corner exactly, the X step is taken before the
     * Y step so the intermediate cell is included and consecutive cells always share an edge.
     */
    private fun addSupercover(x0: Double, y0: Double, x1: Double, y1: Double, out: MutableSet<Long>) {
        var cx = floor(x0).toInt()
        var cy = floor(y0).toInt()
        val endX = floor(x1).toInt()
        val endY = floor(y1).toInt()
        val dx = x1 - x0
        val dy = y1 - y0
        val stepX = if (dx > 0) 1 else if (dx < 0) -1 else 0
        val stepY = if (dy > 0) 1 else if (dy < 0) -1 else 0
        var tMaxX = if (stepX == 0) Double.POSITIVE_INFINITY else ((if (stepX > 0) cx + 1 else cx) - x0) / dx
        var tMaxY = if (stepY == 0) Double.POSITIVE_INFINITY else ((if (stepY > 0) cy + 1 else cy) - y0) / dy
        val tDeltaX = if (stepX == 0) Double.POSITIVE_INFINITY else 1.0 / abs(dx)
        val tDeltaY = if (stepY == 0) Double.POSITIVE_INFINITY else 1.0 / abs(dy)

        addCell(cx, cy, out)
        // Each loop iteration advances at least one axis, so this bounds the walk even if rounding
        // ever prevents an exact landing on the end cell.
        var remaining = abs(endX - cx) + abs(endY - cy)
        while ((cx != endX || cy != endY) && remaining > 0) {
            when {
                tMaxX < tMaxY -> { cx += stepX; tMaxX += tDeltaX; remaining-- }
                tMaxY < tMaxX -> { cy += stepY; tMaxY += tDeltaY; remaining-- }
                else -> {
                    cx += stepX; tMaxX += tDeltaX; remaining--
                    addCell(cx, cy, out)
                    if (remaining <= 0) break
                    cy += stepY; tMaxY += tDeltaY; remaining--
                }
            }
            addCell(cx, cy, out)
        }
    }

    private fun addCell(x: Int, y: Int, out: MutableSet<Long>) {
        out += TileKey.of(FogGrid.STORAGE_ZOOM, x.coerceIn(0, SCALE - 1), y.coerceIn(0, SCALE - 1)).packed
    }

    private fun stampDisk(center: GeoPoint, radiusMetres: Double, out: MutableSet<Long>) {
        val c = toCellUnits(center)
        val radiusCells = radiusMetres / Epsg3395.metresPerWorldUnit(center.latitude) * SCALE
        stampDisk(c.x, c.y, radiusCells, out)
    }

    private fun stampDisk(cx: Double, cy: Double, radius: Double, out: MutableSet<Long>) {
        val centreX = floor(cx).toInt().coerceIn(0, SCALE - 1)
        val centreY = floor(cy).toInt().coerceIn(0, SCALE - 1)
        out += TileKey.of(FogGrid.STORAGE_ZOOM, centreX, centreY).packed
        if (radius <= 0.0) return
        val minX = floor(cx - radius).toInt().coerceIn(0, SCALE - 1)
        val maxX = floor(cx + radius).toInt().coerceIn(0, SCALE - 1)
        val minY = floor(cy - radius).toInt().coerceIn(0, SCALE - 1)
        val maxY = floor(cy + radius).toInt().coerceIn(0, SCALE - 1)
        val r2 = radius * radius
        for (y in minY..maxY) {
            val dy = y + 0.5 - cy
            for (x in minX..maxX) {
                val dx = x + 0.5 - cx
                if (dx * dx + dy * dy <= r2) out += TileKey.of(FogGrid.STORAGE_ZOOM, x, y).packed
            }
        }
    }

    private data class CellUnits(val x: Double, val y: Double)

    private fun toCellUnits(point: GeoPoint): CellUnits {
        val world = Epsg3395.toWorld(point)
        return CellUnits(world.x * SCALE, world.y * SCALE)
    }

    private const val SCALE = 1 shl FogGrid.STORAGE_ZOOM
    private const val STROKE_STEP_CELLS = 0.5
}
