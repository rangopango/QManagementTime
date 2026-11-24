package com.sabil.qnow.data.user

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.UserProfileChangeRequest
import com.sabil.qnow.domain.repository.RecentLoginRequiredException
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
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

    override fun observeProfile(): Flow<com.sabil.qnow.domain.model.CustomerProfile> {
        val uid = auth.currentUser?.uid ?: return kotlinx.coroutines.flow.flowOf(com.sabil.qnow.domain.model.CustomerProfile(null, null, emptyList()))
        return db.collection(Paths.USERS).document(uid).observe().map { snap ->
            val gender = snap?.getString("gender")?.let { g -> com.sabil.qnow.domain.model.Gender.entries.firstOrNull { it.name == g } }
            val family = (snap?.get("family") as? List<*>).orEmpty().mapNotNull { m ->
                (m as? Map<*, *>)?.let {
                    com.sabil.qnow.domain.model.FamilyMember(
                        id = it["id"] as? String ?: return@mapNotNull null,
                        name = it["name"] as? String ?: "",
                        gender = com.sabil.qnow.domain.model.Gender.entries.firstOrNull { g -> g.name == it["gender"] } ?: com.sabil.qnow.domain.model.Gender.OTHER,
                        isChild = it["child"] as? Boolean ?: false,
                        age = (it["age"] as? Number)?.toInt(),
                    )
                }
            }
            com.sabil.qnow.domain.model.CustomerProfile(snap?.getString("displayName") ?: auth.currentUser?.displayName, gender, family)
        }
    }

    override suspend fun saveProfile(gender: com.sabil.qnow.domain.model.Gender?, family: List<com.sabil.qnow.domain.model.FamilyMember>) {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        db.collection(Paths.USERS).document(uid).update(mapOf(
            "gender" to gender?.name,
            "family" to family.take(10).map { m ->
                mapOf("id" to m.id, "name" to m.name.trim().take(30), "gender" to m.gender.name, "child" to m.isChild, "age" to m.age)
            },
        )).await()
    }

    override suspend fun updateDisplayName(name: String) {
        val user = auth.currentUser ?: error("Not signed in")
        val clean = name.trim().take(40)
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(clean).build()).await()
        db.collection(Paths.USERS).document(user.uid).update("displayName", clean).await()
    }

    override suspend fun deleteCustomerAccount() {
        val user = auth.currentUser ?: error("Not signed in")
        // Firebase only deletes a login that signed in within the last few minutes. Check first,
        // so data isn't removed while the login itself is left behind.
        val lastSignIn = user.metadata?.lastSignInTimestamp ?: 0L
        if (System.currentTimeMillis() - lastSignIn > 4 * 60_000L) throw RecentLoginRequiredException()

        val me = db.collection(Paths.USERS).document(user.uid)
        for (sub in listOf(Paths.FAVOURITES, Paths.MY_TOKENS, Paths.ACTIVE_TOKENS)) {
            me.collection(sub).get().await().documents.chunked(400).forEach { chunk ->
                db.runBatch { b -> chunk.forEach { b.delete(it.reference) } }.await()
            }
        }
        me.delete().await()
        secureStore.clear()
        try {
            user.delete().await()
        } catch (e: FirebaseAuthRecentLoginRequiredException) {
            auth.signOut()
            throw RecentLoginRequiredException()
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
class FirestoreSupportRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : com.sabil.qnow.domain.repository.SupportRepository {
    override suspend fun submit(category: String, message: String) {
        val uid = auth.currentUser?.uid ?: error("Not signed in")
        db.collection("supportTickets").add(mapOf(
            "uid" to uid,
            "category" to category.take(40),
            "message" to message.trim().take(2000),
            "status" to "OPEN",
            "appVersion" to com.sabil.qnow.BuildConfig.VERSION_NAME,
            "device" to "Android {android.os.Build.VERSION.RELEASE} · {android.os.Build.MANUFACTURER} {android.os.Build.MODEL}".take(80),
            "createdAt" to FieldValue.serverTimestamp(),
        )).await()
    }

    override fun observeMine(): Flow<List<com.sabil.qnow.domain.repository.SupportTicket>> {
        val uid = auth.currentUser?.uid ?: return kotlinx.coroutines.flow.flowOf(emptyList())
        return db.collection("supportTickets").whereEqualTo("uid", uid).limit(20).observe().map { snap ->
            snap.documents.map {
                com.sabil.qnow.domain.repository.SupportTicket(
                    it.id, it.getString("category").orEmpty(), it.getString("message").orEmpty(),
                    it.getString("status") ?: "OPEN", it.getTimestamp("createdAt")?.toDate()?.time ?: 0, it.getString("reply"),
                )
            }.sortedByDescending { it.createdAtMillis }
        }
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
