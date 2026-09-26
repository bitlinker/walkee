package me.bitlinker.walkee.ui.navigation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RouterTest {

    @Test
    fun `starts on home and never pops the root`() {
        val router = Router()
        assertEquals(listOf(HomeKey), router.backStack.value)
        assertFalse(router.canPop)
        assertFalse(router.pop())
        assertEquals(listOf(HomeKey), router.backStack.value)
    }

    @Test
    fun `push, pop and replace`() {
        val router = Router()
        router.push(SettingsKey)
        assertEquals(listOf(HomeKey, SettingsKey), router.backStack.value)
        assertTrue(router.canPop)

        router.push(SettingsKey) // ignored: already on top
        assertEquals(2, router.backStack.value.size)

        router.replaceTop(HomeKey)
        assertEquals(listOf(HomeKey, HomeKey), router.backStack.value)

        assertTrue(router.pop())
        assertEquals(listOf(HomeKey), router.backStack.value)
    }

    @Test
    fun `pop count is clamped and popToRoot resets`() {
        val router = Router()
        router.push(SettingsKey)
        router.push(HomeKey)
        router.push(SettingsKey)
        assertTrue(router.pop(10))
        assertEquals(listOf(HomeKey), router.backStack.value)

        router.push(SettingsKey)
        router.popToRoot()
        assertEquals(listOf(HomeKey), router.backStack.value)
    }
}
