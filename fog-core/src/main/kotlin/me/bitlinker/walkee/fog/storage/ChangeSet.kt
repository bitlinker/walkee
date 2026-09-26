package me.bitlinker.walkee.fog.storage

import me.bitlinker.walkee.fog.geo.TileKey

/** Result of a write to [MapStorage]: which chunks changed and how many cells became visited. */
data class ChangeSet(
    val chunks: Set<TileKey>,
    val addedCells: Int,
) {
    val isEmpty: Boolean get() = chunks.isEmpty()

    companion object {
        val EMPTY = ChangeSet(emptySet(), 0)
    }
}
