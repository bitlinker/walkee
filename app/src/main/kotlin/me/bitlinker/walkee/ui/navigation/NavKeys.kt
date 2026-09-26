package me.bitlinker.walkee.ui.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The map HUD; always the root of the stack. */
@Serializable
data object HomeKey : NavKey

@Serializable
data object SettingsKey : NavKey
