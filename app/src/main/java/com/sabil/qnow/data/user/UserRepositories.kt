package com.sabil.qnow.data.user

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.sabil.qnow.data.firebase.Paths
import com.sabil.qnow.data.firebase.business
import com.sabil.qnow.data.firebase.observe
import com.sabil.qnow.data.firebase.toPromo
import com.sabil.qnow.data.firebase.toReview
import com.sabil.qnow.data.local.SecureStore
import com.sabil.qnow.domain.model.AppUser
import com.sabil.qnow.domain.model.Promo
import com.sabil.qnow.domain.model.Review
import com.sabil.qnow.domain.model.UserRole
import com.sabil.qnow.domain.repository.FavouritesRepository
import com.sabil.qnow.domain.repository.PromoRepository
import com.sabil.qnow.domain.repository.ReviewRepository
import com.sabil.qnow.domain.repository.UserRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreUserRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val secureStore: SecureStore,
) : UserRepository {

    override fun observeUser(uid: String): Flow<AppUser?> =
        db.collection(Paths.USERS).document(uid).observe()
            .map { snap ->
                val role = snap?.getString("role")?.let { r -> UserRole.entries.firstOrNull { it.name == r } }
                AppUser(uid, snap?.getString("displayName"), role)
            }
            // Keep a local copy (encrypted) so the right home shows instantly next launch.
            .onEach { user -> user?.role?.let { secureStore.putRole(uid, it) } }

    /** The role is written once; firestore.rules refuses any later change to it. */
    override suspend fun chooseRole(role: UserRole, displayName: String?) {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        db.collection(Paths.USERS).document(uid).set(
            mapOf(
                "role" to role.name,
                "displayName" to displayName?.trim()?.take(40),
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
        secureStore.putRole(uid, role)
    }

    override suspend fun registerPushToken(token: String) {
        val uid = auth.currentUser?.uid ?: return
        val doc = db.collection(Paths.USERS).document(uid)
        // Only update an existing profile; the role screen creates it.
        if (doc.get().await().exists()) {
            doc.set(mapOf("fcmTokens" to FieldValue.arrayUnion(token)), SetOptions.merge()).await()
        }
    }
}

@Singleton
class FirestoreFavouritesRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : FavouritesRepository {
    private fun col() = db.collection(Paths.USERS).document(auth.currentUser?.uid ?: error("Not signed in"))
        .collection(Paths.FAVOURITES)

    override fun observeFavouriteIds(): Flow<Set<String>> =
        col().observe().map { snap -> snap.documents.map { it.id }.toSet() }

    override suspend fun setFavourite(businessId: String, favourite: Boolean) {
        val doc = col().document(businessId)
        if (favourite) doc.set(mapOf("addedAt" to FieldValue.serverTimestamp())).await() else doc.delete().await()
    }
}

@Singleton
class FirestoreReviewRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : ReviewRepository {

    override fun observeReviews(businessId: String): Flow<List<Review>> =
        db.business(businessId).collection(Paths.REVIEWS)
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(30)
            .observe().map { snap -> snap.documents.map { it.toReview() } }

    /**
     * Reviews are anonymous: no name is stored, and the document id (the reviewer's uid) is only
     * there to allow one review per customer. firestore.rules checks the reviewer held a token
     * that was completed, and that the rating totals move by exactly this review.
     */
    override suspend fun submitReview(businessId: String, tokenId: String, rating: Int, comment: String) {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        val stars = rating.coerceIn(1, 5)
        db.runBatch { b ->
            b.set(db.business(businessId).collection(Paths.REVIEWS).document(uid), mapOf(
                "rating" to stars,
                "comment" to comment.trim().take(500),
                "tokenId" to tokenId,
                "createdAt" to FieldValue.serverTimestamp(),
            ))
            b.update(db.business(businessId), mapOf(
                "ratingSum" to FieldValue.increment(stars.toLong()),
                "ratingCount" to FieldValue.increment(1),
            ))
            b.update(
                db.collection(Paths.USERS).document(uid).collection(Paths.MY_TOKENS).document("${businessId}_$tokenId"),
                "reviewed", true,
            )
        }.await()
    }
}

@Singleton
class FirestorePromoRepository @Inject constructor(
    private val db: FirebaseFirestore,
) : PromoRepository {
    override fun observePromos(): Flow<List<Promo>> =
        db.collection(Paths.PROMOS).whereEqualTo("active", true).limit(10)
            .observe()
            .map { snap -> snap.documents.map { it.toPromo() }.sortedBy { it.priority } }
            .catch { emit(emptyList()) }
}
