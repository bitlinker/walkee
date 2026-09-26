package me.bitlinker.walkee.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.bitlinker.walkee.R
import me.bitlinker.walkee.data.settings.FogStyle
import kotlin.math.roundToInt

@Composable
fun SettingsRoute(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(state = state, dispatch = viewModel::dispatch)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: SettingsState, dispatch: (SettingsAction) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { dispatch(SettingsAction.BackClicked) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(
                text = stringResource(R.string.settings_fog_opacity) + " · ${(state.fogOpacity * 100).roundToInt()} %",
                style = MaterialTheme.typography.titleMedium,
            )
            Slider(
                value = state.fogOpacity,
                onValueChange = { dispatch(SettingsAction.FogOpacityChanged(it)) },
                valueRange = FogStyle.OPACITY_RANGE,
                enabled = state.isLoaded,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(24.dp))

            val zoomRange = FogStyle.DISPLAY_ZOOM_RANGE
            Text(
                text = stringResource(R.string.settings_display_zoom) + " · " +
                    stringResource(R.string.settings_display_zoom_value, state.displayZoom, state.displayCellMetres),
                style = MaterialTheme.typography.titleMedium,
            )
            Slider(
                value = state.displayZoom.toFloat(),
                onValueChange = { dispatch(SettingsAction.DisplayZoomChanged(it.roundToInt())) },
                valueRange = zoomRange.first.toFloat()..zoomRange.last.toFloat(),
                steps = zoomRange.last - zoomRange.first - 1,
                enabled = state.isLoaded,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
