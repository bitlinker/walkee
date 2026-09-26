package me.bitlinker.walkee.ui.screens.home

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
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
import java.text.NumberFormat
import java.util.Locale

/** Navigation entry: wires the view model to the stateless screen. */
@Composable
fun HomeRoute(viewModel: HomeViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    HomeScreen(state = state, dispatch = viewModel::dispatch)
}

/**
 * The HUD drawn over the map. Has no background of its own, so touches outside the controls
 * reach the MapView beneath.
 */
@Composable
fun HomeScreen(state: HomeState, dispatch: (HomeAction) -> Unit) {
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        dispatch(HomeAction.PermissionResult(granted = result.values.any { it }))
    }
    LaunchedEffect(state.permissionRequestPending) {
        if (state.permissionRequestPending) {
            dispatch(HomeAction.PermissionRequestLaunched)
            permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    Box(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        ProgressCard(state, modifier = Modifier.align(Alignment.TopStart))

        Column(modifier = Modifier.align(Alignment.BottomEnd), horizontalAlignment = Alignment.End) {
            SmallFloatingActionButton(onClick = { dispatch(HomeAction.SettingsClicked) }) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.home_settings))
            }
            Spacer(Modifier.height(12.dp))
            if (!state.followUser) {
                SmallFloatingActionButton(onClick = { dispatch(HomeAction.RecenterClicked) }) {
                    Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.home_recenter))
                }
                Spacer(Modifier.height(12.dp))
            }
            TrackingButton(state, onClick = { dispatch(HomeAction.TrackingToggled) })
        }
    }
}

@Composable
private fun ProgressCard(state: HomeState, modifier: Modifier = Modifier) {
    val numbers = NumberFormat.getIntegerInstance(Locale.getDefault())
    Card(
        modifier = modifier,
        // A translucent container matches no scheme colour, so the content colour must be explicit:
        // otherwise it falls back to black, unreadable on the dark surface.
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(stringResource(R.string.home_progress_title), style = MaterialTheme.typography.labelMedium)
            Text(
                text = stringResource(R.string.home_progress_area, String.format(Locale.getDefault(), "%.2f", state.areaSquareKilometres)),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.home_progress_cells, numbers.format(state.visitedCells)),
                style = MaterialTheme.typography.bodySmall,
            )
            if (!state.hasLocationPermission) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.home_permission_needed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(0.7f),
                )
            }
        }
    }
}

@Composable
private fun TrackingButton(state: HomeState, onClick: () -> Unit) {
    val label = when {
        !state.hasLocationPermission -> stringResource(R.string.home_permission_grant)
        state.isTracking -> stringResource(R.string.home_tracking_stop)
        else -> stringResource(R.string.home_tracking_start)
    }
    ExtendedFloatingActionButton(
        onClick = onClick,
        containerColor = if (state.isTracking) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(if (state.isTracking) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(label)
        }
    }
}
