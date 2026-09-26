package me.bitlinker.walkee.fog.storage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import kotlin.random.Random

class BitGridTest {

    @ParameterizedTest(name = "side shift {0}")
    @ValueSource(ints = [0, 1, 2, 3, 4, 5, 6, 7, 8])
    fun `countInBlock matches brute force for every grid and block size`(sideShift: Int) {
        val random = Random(sideShift + 11)
        val grid = BitGrid.empty(sideShift).plus(IntArray(maxOf(1, (1 shl (2 * sideShift)) / 3)) { random.nextInt(1 shl (2 * sideShift)) })
        val side = grid.side
        for (blockShift in 0..sideShift) {
            val size = 1 shl blockShift
            for (y in 0 until side step size) {
                for (x in 0 until side step size) {
                    var expected = 0
                    for (dy in 0 until size) for (dx in 0 until size) if (grid[x + dx, y + dy]) expected++
                    assertEquals(expected, grid.countInBlock(x, y, size), "shift $sideShift block ($x,$y) size $size")
                }
            }
        }
        assertEquals(grid.cardinality, grid.countInBlock(0, 0, side))
    }

    @ParameterizedTest(name = "side shift {0}")
    @ValueSource(ints = [1, 2, 3, 6, 8])
    fun `occupancy marks exactly the non-empty blocks`(sideShift: Int) {
        val random = Random(sideShift)
        val grid = BitGrid.empty(sideShift).plus(IntArray(1 shl sideShift) { random.nextInt(1 shl (2 * sideShift)) })
        for (blockShift in 0..sideShift) {
            val coarse = grid.occupancy(blockShift)
            assertEquals(sideShift - blockShift, coarse.sideShift)
            var expectedCount = 0
            for (by in 0 until coarse.side) {
                for (bx in 0 until coarse.side) {
                    val expected = grid.countInBlock(bx shl blockShift, by shl blockShift, 1 shl blockShift) > 0
                    if (expected) expectedCount++
                    assertEquals(expected, coarse[bx, by], "block ($bx,$by) shift $blockShift")
                }
            }
            assertEquals(expectedCount, coarse.cardinality)
        }
        assertSame(grid, grid.occupancy(0))
        assertTrue(BitGrid.empty(sideShift).occupancy(1).isEmpty)
    }

    @Test
    fun `full and empty grids of every size`() {
        for (sideShift in 0..8) {
            val full = BitGrid.full(sideShift)
            assertTrue(full.isFull, "shift $sideShift")
            assertEquals(full.cellCount, full.cardinality)
            assertEquals(full.cellCount, full.countInBlock(0, 0, full.side))
            assertTrue(full.occupancy(sideShift).isFull)

            val empty = BitGrid.empty(sideShift)
            assertTrue(empty.isEmpty)
            assertFalse(empty[0, 0])
            assertSame(empty, empty.plus(IntArray(0)))
        }
        assertEquals(1, BitGrid.wordCount(0))
        assertEquals(1, BitGrid.wordCount(3))
        assertEquals(1024, BitGrid.wordCount(8))
    }

    @ParameterizedTest(name = "side shift {0}")
    @ValueSource(ints = [0, 2, 3, 5, 6, 8])
    fun `forEachSetBit visits exactly the set bits of a rectangle`(sideShift: Int) {
        val random = Random(sideShift + 5)
        val side = 1 shl sideShift
        val grid = BitGrid.empty(sideShift).plus(IntArray(maxOf(1, side * side / 4)) { random.nextInt(side * side) })
        repeat(50) {
            val fromX = random.nextInt(side + 1)
            val toX = random.nextInt(fromX, side + 1)
            val fromY = random.nextInt(side + 1)
            val toY = random.nextInt(fromY, side + 1)
            val expected = ArrayList<Pair<Int, Int>>()
            for (y in fromY until toY) for (x in fromX until toX) if (grid[x, y]) expected += x to y
            val visited = ArrayList<Pair<Int, Int>>()
            grid.forEachSetBit(fromX, fromY, toX, toY) { x, y -> visited += x to y }
            assertEquals(expected, visited, "rectangle [$fromX, $toX) × [$fromY, $toY)")
        }
        val all = ArrayList<Int>()
        grid.forEachSetBit(0, 0, side, side) { x, y -> all += grid.index(x, y) }
        assertEquals(grid.cardinality, all.size)
        assertThrows(IllegalArgumentException::class.java) { grid.forEachSetBit(0, 0, side + 1, side) { _, _ -> } }
    }

    @Test
    fun `plus is copy-on-write`() {
        val a = BitGrid.empty(3)
        val b = a.plus(intArrayOf(0, 63, 63))
        assertEquals(0, a.cardinality)
        assertEquals(2, b.cardinality)
        assertTrue(b[0, 0])
        assertTrue(b[7, 7])
        assertSame(b, b.plus(intArrayOf(63)))
    }

    @Test
    fun `validates arguments`() {
        val grid = BitGrid.empty(4)
        assertThrows(IllegalArgumentException::class.java) { grid.countInBlock(1, 0, 2) }
        assertThrows(IllegalArgumentException::class.java) { grid.countInBlock(0, 0, 3) }
        assertThrows(IllegalArgumentException::class.java) { grid.countInBlock(0, 0, 32) }
        assertThrows(IllegalArgumentException::class.java) { grid.occupancy(5) }
        assertThrows(IllegalArgumentException::class.java) { BitGrid.fromWords(4, LongArray(3)) }
    }
}
