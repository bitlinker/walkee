package me.bitlinker.walkee.ui.screens.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.bitlinker.walkee.R
import me.bitlinker.walkee.data.location.LocationPermissions
import me.bitlinker.walkee.data.settings.FogCellShape
import me.bitlinker.walkee.data.settings.FogEdges
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
    PermissionRequests(state, dispatch)

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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            AutoStartSection(state, dispatch)

            Spacer(Modifier.height(24.dp))

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

            val edgesSelectable = state.cellShape == FogCellShape.SQUARES
            Text(text = stringResource(R.string.settings_fog_edges), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                val edgeStyles = FogEdges.entries
                edgeStyles.forEachIndexed { index, edges ->
                    SegmentedButton(
                        selected = state.fogEdges == edges,
                        onClick = { dispatch(SettingsAction.FogEdgesChanged(edges)) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = edgeStyles.size),
                        enabled = state.isLoaded && edgesSelectable,
                    ) {
                        Text(stringResource(edges.label()))
                    }
                }
            }
            if (!edgesSelectable) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_fog_edges_hexagons_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

/** Launches the system permission dialogs that the state asks for. */
@Composable
private fun PermissionRequests(state: SettingsState, dispatch: (SettingsAction) -> Unit) {
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        dispatch(SettingsAction.LocationPermissionResult)
    }
    LaunchedEffect(state.locationRequestPending) {
        if (state.locationRequestPending) {
            dispatch(SettingsAction.LocationRequestLaunched)
            locationLauncher.launch(locationPermissionsToRequest(state.permissions))
        }
    }

    val activityLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        dispatch(SettingsAction.ActivityPermissionResult(granted))
    }
    LaunchedEffect(state.activityRequestPending) {
        if (state.activityRequestPending) {
            dispatch(SettingsAction.ActivityRequestLaunched)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            } else {
                dispatch(SettingsAction.ActivityPermissionResult(granted = true))
            }
        }
    }
}

/**
 * Location "all the time" can be asked for only once location while in use is granted; Android 11+
 * then opens the app's location settings. Before Android 10 it comes with location itself.
 */
private fun locationPermissionsToRequest(permissions: LocationPermissions): Array<String> =
    if (!permissions.location || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    } else {
        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

@Composable
private fun AutoStartSection(state: SettingsState, dispatch: (SettingsAction) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = stringResource(R.string.settings_auto_start), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.settings_auto_start_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        Switch(
            checked = state.isAutoStartOn,
            onCheckedChange = { dispatch(SettingsAction.AutoStartToggled(it)) },
            enabled = state.isLoaded && state.isAutoStartAvailable,
        )
    }
    if (!state.isAutoStartAvailable) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_auto_start_needs_background),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { dispatch(SettingsAction.BackgroundLocationClicked) }) {
            Text(stringResource(R.string.settings_auto_start_grant))
        }
    }
    if (state.activityPermissionDenied) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.settings_auto_start_activity_denied),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

private fun FogCellShape.label(): Int = when (this) {
    FogCellShape.SQUARES -> R.string.settings_cell_shape_squares
    FogCellShape.HEXAGONS -> R.string.settings_cell_shape_hexagons
}

private fun FogEdges.label(): Int = when (this) {
    FogEdges.HARD -> R.string.settings_fog_edges_hard
    FogEdges.SOFT -> R.string.settings_fog_edges_soft
    FogEdges.CLOUDS -> R.string.settings_fog_edges_clouds
}
