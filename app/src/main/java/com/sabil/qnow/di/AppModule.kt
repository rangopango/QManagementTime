package com.sabil.qnow.di

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.firestoreSettings
import com.sabil.qnow.data.auth.FirebaseAuthRepository
import com.sabil.qnow.data.business.FirestoreBusinessRepository
import com.sabil.qnow.data.location.AndroidLocationRepository
import com.sabil.qnow.data.queue.FirestoreQueueRepository
import com.sabil.qnow.data.user.FirestoreFavouritesRepository
import com.sabil.qnow.data.user.FirestorePromoRepository
import com.sabil.qnow.data.user.FirestoreReviewRepository
import com.sabil.qnow.data.user.FirestoreUserRepository
import com.sabil.qnow.domain.repository.AuthRepository
import com.sabil.qnow.domain.repository.BusinessRepository
import com.sabil.qnow.domain.repository.FavouritesRepository
import com.sabil.qnow.domain.repository.LocationRepository
import com.sabil.qnow.domain.repository.PromoRepository
import com.sabil.qnow.domain.repository.QueueRepository
import com.sabil.qnow.domain.repository.ReviewRepository
import com.sabil.qnow.domain.repository.UserRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Provider
import javax.inject.Singleton
import com.sabil.qnow.data.demo.*

@Module
@InstallIn(SingletonComponent::class)
object FirebaseModule {
    @Provides @Singleton
    fun auth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides @Singleton
    fun firestore(): FirebaseFirestore = FirebaseFirestore.getInstance().apply {
        firestoreSettings = firestoreSettings {
            setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
        }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun location(impl: AndroidLocationRepository): LocationRepository
}

/**
 * Picks real (Firebase) or prototype (bot world) data once, at start-up. Providers keep the
 * unused side from ever being created, so demo mode never touches Firebase.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataSourceModule {
    @Provides @Singleton
    fun auth(mode: DemoMode, live: Provider<FirebaseAuthRepository>, demo: Provider<DemoAuthRepository>): AuthRepository =
        if (mode.enabled) demo.get() else live.get()

    @Provides @Singleton
    fun users(mode: DemoMode, live: Provider<FirestoreUserRepository>, demo: Provider<DemoUserRepository>): UserRepository =
        if (mode.enabled) demo.get() else live.get()

    @Provides @Singleton
    fun businesses(mode: DemoMode, live: Provider<FirestoreBusinessRepository>, demo: Provider<DemoBusinessRepository>): BusinessRepository =
        if (mode.enabled) demo.get() else live.get()

    @Provides @Singleton
    fun queue(mode: DemoMode, live: Provider<FirestoreQueueRepository>, demo: Provider<DemoQueueRepository>): QueueRepository =
        if (mode.enabled) demo.get() else live.get()

    @Provides @Singleton
    fun favourites(mode: DemoMode, live: Provider<FirestoreFavouritesRepository>, demo: Provider<DemoFavouritesRepository>): FavouritesRepository =
        if (mode.enabled) demo.get() else live.get()

    @Provides @Singleton
    fun reviews(mode: DemoMode, live: Provider<FirestoreReviewRepository>, demo: Provider<DemoReviewRepository>): ReviewRepository =
        if (mode.enabled) demo.get() else live.get()

    @Provides @Singleton
    fun support(mode: DemoMode, live: Provider<com.sabil.qnow.data.user.FirestoreSupportRepository>, demo: Provider<DemoSupportRepository>): com.sabil.qnow.domain.repository.SupportRepository =
        if (mode.enabled) demo.get() else live.get()

    @Provides @Singleton
    fun promos(mode: DemoMode, live: Provider<FirestorePromoRepository>, demo: Provider<DemoPromoRepository>): PromoRepository =
        if (mode.enabled) demo.get() else live.get()
}
