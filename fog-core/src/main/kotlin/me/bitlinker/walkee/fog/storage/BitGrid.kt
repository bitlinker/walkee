package me.bitlinker.walkee.fog.storage

/**
 * Immutable square bitmap with side `2^sideShift`, stored row-major, 64 bits per word.
 *
 * Rows of grids narrower than 64 cells share words; because every block queried by
 * [countInBlock] is a power of two in size and aligned to its size, an aligned run of `size`
 * bits within one row never straddles a 64-bit word boundary, which keeps counting branch-free.
 */
class BitGrid private constructor(
    /** `log2` of the side length. */
    val sideShift: Int,
    private val words: LongArray,
    /** Number of set bits. */
    val cardinality: Int,
) {
    val side: Int get() = 1 shl sideShift

    val cellCount: Int get() = 1 shl (2 * sideShift)

    val isEmpty: Boolean get() = cardinality == 0

    val isFull: Boolean get() = cardinality == cellCount

    fun index(x: Int, y: Int): Int = (y shl sideShift) or x

    operator fun get(x: Int, y: Int): Boolean = get(index(x, y))

    fun get(index: Int): Boolean = (words[index ushr 6] ushr (index and 63)) and 1L != 0L

    /**
     * Number of set bits in the aligned square block with north-west corner `(x, y)` and side
     * [size] — a power of two not larger than the grid side; `x` and `y` must be multiples of it.
     */
    fun countInBlock(x: Int, y: Int, size: Int): Int {
        require(size in 1..side && size and (size - 1) == 0) { "Block size must be a power of two in 1..$side, got $size" }
        require(x % size == 0 && y % size == 0) { "Block ($x, $y) is not aligned to $size" }
        require(x + size <= side && y + size <= side) { "Block ($x, $y, $size) exceeds the grid" }

        var count = 0
        if (size >= 64) {
            val first = index(x, y) ushr 6
            val wordsPerRow = side ushr 6
            val blockWords = size ushr 6
            for (row in 0 until size) {
                val base = first + row * wordsPerRow
                for (w in base until base + blockWords) count += java.lang.Long.bitCount(words[w])
            }
        } else {
            // Rows of narrow grids share words, so the run's bit offset depends on the row too.
            val runMask = (1L shl size) - 1
            for (row in y until y + size) {
                val index = index(x, row)
                count += java.lang.Long.bitCount((words[index ushr 6] ushr (index and 63)) and runMask)
            }
        }
        return count
    }

    /** This grid with the given cell indices additionally set; `this` if nothing changed. */
    fun plus(indices: IntArray): BitGrid {
        var copy: LongArray? = null
        var added = 0
        for (index in indices) {
            val wordIndex = index ushr 6
            val bit = 1L shl (index and 63)
            val current = (copy ?: words)[wordIndex]
            if (current and bit == 0L) {
                val target = copy ?: words.copyOf().also { copy = it }
                target[wordIndex] = current or bit
                added++
            }
        }
        val changed = copy ?: return this
        return BitGrid(sideShift, changed, cardinality + added)
    }

    /**
     * Coarser grid with side `side >> blockShift` whose bit is set when the corresponding aligned
     * `2^blockShift`-sized block of this grid has at least one bit set.
     */
    fun occupancy(blockShift: Int): BitGrid {
        require(blockShift in 0..sideShift) { "Block shift must be in 0..$sideShift, got $blockShift" }
        if (blockShift == 0) return this
        val coarseShift = sideShift - blockShift
        val coarse = LongArray(wordCount(coarseShift))
        val blockSize = 1 shl blockShift
        var count = 0
        if (isEmpty) return BitGrid(coarseShift, coarse, 0)
        for (by in 0 until (1 shl coarseShift)) {
            for (bx in 0 until (1 shl coarseShift)) {
                if (countInBlock(bx shl blockShift, by shl blockShift, blockSize) > 0) {
                    val index = (by shl coarseShift) or bx
                    coarse[index ushr 6] = coarse[index ushr 6] or (1L shl (index and 63))
                    count++
                }
            }
        }
        return BitGrid(coarseShift, coarse, count)
    }

    /** Defensive copy of the raw words. */
    fun copyWords(): LongArray = words.copyOf()

    companion object {
        fun wordCount(sideShift: Int): Int = maxOf(1, (1 shl (2 * sideShift)) ushr 6)

        fun empty(sideShift: Int): BitGrid = BitGrid(sideShift, LongArray(wordCount(sideShift)), 0)

        fun full(sideShift: Int): BitGrid {
            val cells = 1 shl (2 * sideShift)
            val words = LongArray(wordCount(sideShift)) { -1L }
            if (cells < 64) words[0] = (1L shl cells) - 1
            return BitGrid(sideShift, words, cells)
        }

        /** Wraps [words] (taken over, not copied) and counts their bits. */
        fun fromWords(sideShift: Int, words: LongArray): BitGrid {
            require(words.size == wordCount(sideShift)) { "Grid with side shift $sideShift needs ${wordCount(sideShift)} words, got ${words.size}" }
            var count = 0
            for (word in words) count += java.lang.Long.bitCount(word)
            return BitGrid(sideShift, words, count)
        }
    }
}
