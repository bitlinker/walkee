package me.bitlinker.walkee.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import me.bitlinker.walkee.fog.geo.FogGrid
import javax.inject.Inject
import javax.inject.Singleton

/**
 * How the fog layer is drawn. `displayZoom` is the zoom of one visible "pixel" (ADR 0001).
 * Hidden cells get the fog colour at [opacity]; revealed cells get a light tint of the yellow
 * brand accent at [revealedOpacity] (ADR 0003).
 */
data class FogStyle(
    val opacity: Float = DEFAULT_OPACITY,
    val displayZoom: Int = DEFAULT_DISPLAY_ZOOM,
    val colorRgb: Int = DEFAULT_COLOR_RGB,
    val revealedOpacity: Float = DEFAULT_REVEALED_OPACITY,
    val revealedColorRgb: Int = DEFAULT_REVEALED_COLOR_RGB,
) {
    companion object {
        const val DEFAULT_OPACITY = 0.75f
        const val DEFAULT_DISPLAY_ZOOM = 18
        const val DEFAULT_COLOR_RGB = 0x1B1B1F
        const val DEFAULT_REVEALED_OPACITY = 0.1f
        /** The theme's accent yellow (`Yellow80` in `ui/theme`). */
        const val DEFAULT_REVEALED_COLOR_RGB = 0xFFD600
        val DISPLAY_ZOOM_RANGE = 16..FogGrid.STORAGE_ZOOM
        val OPACITY_RANGE = 0.3f..1f
    }
}

data class AppSettings(
    val fogStyle: FogStyle = FogStyle(),
    val trackingEnabled: Boolean = false,
)

/** Key-value settings in DataStore (ADR 0004). */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = dataStore.data
        .map { preferences ->
            AppSettings(
                fogStyle = FogStyle(
                    opacity = (preferences[FOG_OPACITY] ?: FogStyle.DEFAULT_OPACITY).coerceIn(FogStyle.OPACITY_RANGE),
                    displayZoom = (preferences[DISPLAY_ZOOM] ?: FogStyle.DEFAULT_DISPLAY_ZOOM).coerceIn(FogStyle.DISPLAY_ZOOM_RANGE),
                ),
                trackingEnabled = preferences[TRACKING_ENABLED] ?: false,
            )
        }
        .distinctUntilChanged()

    val fogStyle: Flow<FogStyle> = settings.map { it.fogStyle }.distinctUntilChanged()

    suspend fun setFogOpacity(opacity: Float) {
        dataStore.edit { it[FOG_OPACITY] = opacity.coerceIn(FogStyle.OPACITY_RANGE) }
    }

    suspend fun setDisplayZoom(zoom: Int) {
        dataStore.edit { it[DISPLAY_ZOOM] = zoom.coerceIn(FogStyle.DISPLAY_ZOOM_RANGE) }
    }

    suspend fun setTrackingEnabled(enabled: Boolean) {
        dataStore.edit { it[TRACKING_ENABLED] = enabled }
    }

    private companion object {
        val FOG_OPACITY = floatPreferencesKey("fog_opacity")
        val DISPLAY_ZOOM = intPreferencesKey("fog_display_zoom")
        val TRACKING_ENABLED = booleanPreferencesKey("tracking_enabled")
    }
}
