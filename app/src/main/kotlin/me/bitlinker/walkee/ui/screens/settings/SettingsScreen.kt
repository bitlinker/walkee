package me.bitlinker.walkee.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.bitlinker.walkee.R
import me.bitlinker.walkee.data.settings.FogCellShape
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

            Text(text = stringResource(R.string.settings_cell_shape), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val shapes = FogCellShape.entries
                shapes.forEachIndexed { index, shape ->
                    SegmentedButton(
                        selected = state.cellShape == shape,
                        onClick = { dispatch(SettingsAction.CellShapeChanged(shape)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = shapes.size),
                        enabled = state.isLoaded,
                    ) {
                        Text(stringResource(shape.label()))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            val zoomRange = state.cellShape.displayZoomRange
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

            Spacer(Modifier.height(32.dp))

            OutlinedButton(
                onClick = { dispatch(SettingsAction.ClearExploredClicked) },
                enabled = !state.isClearing,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_clear_explored))
            }
            if (state.clearFailed) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_clear_explored_failed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }

    if (state.isClearConfirmationShown) {
        AlertDialog(
            onDismissRequest = { dispatch(SettingsAction.ClearExploredDismissed) },
            title = { Text(stringResource(R.string.settings_clear_explored_title)) },
            text = { Text(stringResource(R.string.settings_clear_explored_message)) },
            confirmButton = {
                TextButton(
                    onClick = { dispatch(SettingsAction.ClearExploredConfirmed) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.settings_clear_explored_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { dispatch(SettingsAction.ClearExploredDismissed) }) {
                    Text(stringResource(R.string.settings_cancel))
                }
            },
        )
    }
}

private fun FogCellShape.label(): Int = when (this) {
    FogCellShape.SQUARES -> R.string.settings_cell_shape_squares
    FogCellShape.HEXAGONS -> R.string.settings_cell_shape_hexagons
}
