package me.bitlinker.walkee

import android.app.Application
import android.util.Log
import com.yandex.mapkit.MapKitFactory
import dagger.hilt.android.HiltAndroidApp
import me.bitlinker.walkee.domain.AppInitializer
import javax.inject.Inject

@HiltAndroidApp
class WalkeeApp : Application() {

    @Inject
    lateinit var initializer: AppInitializer

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.MAPKIT_API_KEY.isBlank()) {
            Log.e(TAG, "MAPKIT_API_KEY is empty: add it to local.properties, the map will not load")
        }
        // Must precede MapKitFactory.initialize(), which MainActivity calls.
        MapKitFactory.setApiKey(BuildConfig.MAPKIT_API_KEY)
        initializer.start()
    }

    private companion object {
        const val TAG = "WalkeeApp"
    }
}
