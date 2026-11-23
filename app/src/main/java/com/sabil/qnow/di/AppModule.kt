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
import javax.inject.Singleton

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
    @Binds abstract fun auth(impl: FirebaseAuthRepository): AuthRepository
    @Binds abstract fun users(impl: FirestoreUserRepository): UserRepository
    @Binds abstract fun businesses(impl: FirestoreBusinessRepository): BusinessRepository
    @Binds abstract fun queue(impl: FirestoreQueueRepository): QueueRepository
    @Binds abstract fun favourites(impl: FirestoreFavouritesRepository): FavouritesRepository
    @Binds abstract fun reviews(impl: FirestoreReviewRepository): ReviewRepository
    @Binds abstract fun promos(impl: FirestorePromoRepository): PromoRepository
    @Binds abstract fun location(impl: AndroidLocationRepository): LocationRepository
}
