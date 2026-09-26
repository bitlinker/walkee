package me.bitlinker.walkee.di

import android.app.PendingIntent
import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import me.bitlinker.walkee.domain.TrackingForeground
import me.bitlinker.walkee.tracking.ActivityTransitionReceiver
import me.bitlinker.walkee.tracking.TrackingServiceStarter
import javax.inject.Qualifier
import javax.inject.Singleton

/** The broadcast that activity recognition delivers transitions to (ADR 0006). */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class ActivityTransitionsIntent

@Module
@InstallIn(SingletonComponent::class)
abstract class TrackingModule {

    @Binds
    abstract fun trackingForeground(starter: TrackingServiceStarter): TrackingForeground

    companion object {
        @Provides
        @Singleton
        @ActivityTransitionsIntent
        fun activityTransitionsIntent(@ApplicationContext context: Context): PendingIntent =
            ActivityTransitionReceiver.pendingIntent(context)
    }
}
