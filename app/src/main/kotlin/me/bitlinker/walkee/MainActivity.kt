package me.bitlinker.walkee

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.mapview.MapView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import me.bitlinker.walkee.domain.usecase.FlushFogUseCase
import me.bitlinker.walkee.domain.usecase.ObserveLocationPermissionUseCase
import me.bitlinker.walkee.domain.usecase.RefreshPermissionsUseCase
import me.bitlinker.walkee.domain.usecase.ResumeTrackingUseCase
import me.bitlinker.walkee.domain.usecase.SyncAutoStartUseCase
import me.bitlinker.walkee.ui.map.MapFogLayerRenderer
import me.bitlinker.walkee.ui.map.MapRenderer
import me.bitlinker.walkee.ui.map.MapViewModel
import me.bitlinker.walkee.ui.navigation.MainNavDisplay
import me.bitlinker.walkee.ui.navigation.Router
import me.bitlinker.walkee.ui.theme.WalkeeTheme
import javax.inject.Inject

/**
 * The single activity (ADR 0004): a MapKit [MapView] at the bottom, Compose screens on top.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var router: Router
    @Inject lateinit var fogLayerRenderer: MapFogLayerRenderer
    @Inject lateinit var observeLocationPermission: ObserveLocationPermissionUseCase
    @Inject lateinit var flushFog: FlushFogUseCase
    @Inject lateinit var refreshPermissions: RefreshPermissionsUseCase
    @Inject lateinit var resumeTracking: ResumeTrackingUseCase
    @Inject lateinit var syncAutoStart: SyncAutoStartUseCase

    private val mapViewModel: MapViewModel by viewModels()

    private lateinit var mapView: MapView
    private lateinit var mapRenderer: MapRenderer

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapKitFactory.initialize(this)
        enableEdgeToEdge()

        mapView = MapView(this)
        mapRenderer = MapRenderer(mapView, mapViewModel.state, mapViewModel::dispatch, fogLayerRenderer)

        setContent {
            WalkeeTheme {
                Box(modifier = Modifier.fillMaxSize()) {
                    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
                    MainNavDisplay(router = router, modifier = Modifier.fillMaxSize())
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mapRenderer.start(this)
                launch {
                    observeLocationPermission().collect { granted -> mapRenderer.showUserLocation(granted) }
                }
                try {
                    awaitCancellation()
                } finally {
                    mapRenderer.stop()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        mapView.onStart()
        // Permissions may have changed in system settings meanwhile. Tracking left on comes back
        // now that the app is in the foreground, where its service may start (ADR 0006).
        refreshPermissions()
        lifecycleScope.launch {
            resumeTracking()
            syncAutoStart()
        }
    }

    override fun onStop() {
        mapView.onStop()
        MapKitFactory.getInstance().onStop()
        // Persist revealed cells before the process may be killed in the background.
        lifecycleScope.launch { runCatching { flushFog() } }
        super.onStop()
    }
}
