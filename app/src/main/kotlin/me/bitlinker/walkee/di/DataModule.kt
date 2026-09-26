package me.bitlinker.walkee.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import me.bitlinker.walkee.fog.storage.FileChunkStore
import me.bitlinker.walkee.fog.storage.MapStorage
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun mapStorage(@ApplicationContext context: Context): MapStorage =
        MapStorage(FileChunkStore(File(context.filesDir, FOG_DIRECTORY)))

    @Provides
    @Singleton
    fun preferencesDataStore(
        @ApplicationContext context: Context,
        @IoDispatcher ioDispatcher: CoroutineDispatcher,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = CoroutineScope(SupervisorJob() + ioDispatcher),
        produceFile = { context.preferencesDataStoreFile(SETTINGS_STORE) },
    )

    @Provides
    @Singleton
    fun fusedLocationProviderClient(@ApplicationContext context: Context): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @Provides
    @Singleton
    fun activityRecognitionClient(@ApplicationContext context: Context): ActivityRecognitionClient =
        ActivityRecognition.getClient(context)

    private const val FOG_DIRECTORY = "fog"
    private const val SETTINGS_STORE = "settings"
}
