package com.sabil.qnow.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Collection layout. Public and private data never share a document:
 *
 * users/{uid}                        private profile: role, display name (owner only)
 * users/{uid}/favourites/{bizId}     starred businesses (owner only)
 * users/{uid}/tokens/{bizId_tokenId} tokens this customer holds (owner only)
 * users/{uid}/activeTokens/{bizId}   lock: at most one live token per business (owner only)
 * businesses/{bizId}                 public profile + queue counter (anyone signed in)
 * businessPrivate/{bizId}            owner name, phone (owner only)
 * businesses/{bizId}/tokens/{id}     token numbers and status only, no customer info (anyone signed in)
 * businesses/{bizId}/holders/{id}    who holds each token (business owner + that customer)
 * businesses/{bizId}/reviews/{uid}   anonymous rating + comment (anyone signed in)
 * businesses/{bizId}/photos/{id}     compressed shop photos as bytes (anyone signed in; owner writes)
 * promos/{id}                        home banners, written from the console only
 */
object Paths {
    const val USERS = "users"
    const val FAVOURITES = "favourites"
    const val MY_TOKENS = "tokens"
    const val ACTIVE_TOKENS = "activeTokens"
    const val BUSINESSES = "businesses"
    const val BUSINESS_PRIVATE = "businessPrivate"
    const val TOKENS = "tokens"
    const val HOLDERS = "holders"
    const val REVIEWS = "reviews"
    const val PHOTOS = "photos"
    const val PROMOS = "promos"
}

fun DocumentReference.observe(): Flow<DocumentSnapshot?> = callbackFlow {
    val registration = addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
        if (error != null) close(error) else trySend(snapshot)
    }
    awaitClose { registration.remove() }
}

fun Query.observe(): Flow<QuerySnapshot> = callbackFlow {
    val registration = addSnapshotListener(MetadataChanges.EXCLUDE) { snapshot, error ->
        when {
            error != null -> close(error)
            snapshot != null -> trySend(snapshot)
        }
    }
    awaitClose { registration.remove() }
}

fun FirebaseFirestore.business(id: String) = collection(Paths.BUSINESSES).document(id)

internal fun DocumentSnapshot.str(key: String): String? = getString(key)
internal fun DocumentSnapshot.int(key: String, default: Int = 0): Int = getLong(key)?.toInt() ?: default
internal fun DocumentSnapshot.long(key: String, default: Long = 0): Long = getLong(key) ?: default
internal fun DocumentSnapshot.millis(key: String): Long? = getTimestamp(key)?.toDate()?.time

internal fun Map<*, *>.str(key: String): String? = this[key] as? String
internal fun Map<*, *>.num(key: String): Number? = this[key] as? Number

internal fun timestampOf(millis: Long) = Timestamp(java.util.Date(millis))
