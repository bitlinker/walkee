package me.bitlinker.walkee.ui.map

import me.bitlinker.walkee.data.settings.FogStyle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class FogPaletteTest {

    private val style = FogStyle(opacity = 1f, colorRgb = 0x112233, revealedOpacity = 0.2f, revealedColorRgb = 0xFFD600)
    private val fog = 0xFF112233.toInt()
    private val tint = (51 shl 24) or 0xFFD600 // 0.2 * 255 = 51

    @Test
    fun `ends of the palette are pure fog and pure tint`() {
        val palette = FogPalette(style)
        assertEquals(fog, palette.hiddenColor)
        assertEquals(fog, palette.color(1f))
        assertEquals(tint, palette.revealedColor)
        assertEquals(tint, palette.color(0f))
        assertEquals(0x80, FogPalette(style.copy(opacity = 0.5f)).hiddenColor ushr 24)
        assertEquals(0, FogPalette(style.copy(revealedOpacity = 0f)).revealedColor)
    }

    @Test
    fun `shares outside 0 to 1 are clamped`() {
        val palette = FogPalette(style)
        assertEquals(fog, palette.color(1.3f))
        assertEquals(tint, palette.color(-0.2f))
    }

    @Test
    fun `partial share mixes fog and tint by area`() {
        val color = FogPalette(style.copy(opacity = 0.8f)).color(0.25f)
        // A quarter is fog at 0.8, three quarters tint at 0.2: alpha 0.2 + 0.15 = 0.35 → 89,
        // colour (fog · 0.2 + tint · 0.15) / 0.35 per channel.
        assertClose(89, color ushr 24)
        assertClose(119, (color ushr 16) and 0xFF)
        assertClose(111, (color ushr 8) and 0xFF)
        assertClose(29, color and 0xFF)
    }

    @Test
    fun `without tint a partial share only scales fog alpha`() {
        val color = FogPalette(style.copy(opacity = 0.8f, revealedOpacity = 0f)).color(0.25f)
        // 0.8 * 255 * 0.25 = 51
        assertClose(51, color ushr 24)
        assertEquals(0x112233, color and 0xFFFFFF)
    }

    @Test
    fun `any share maps to one of 256 colours`() {
        val palette = FogPalette(style)
        val colours = (0..10_000).map { palette.color(it / 10_000f) }.toSet()
        assertTrue(colours.size <= 256)
    }

    private fun assertClose(expected: Int, actual: Int) =
        assertTrue(abs(expected - actual) <= 1, "expected $expected ± 1, got $actual")
}
