package com.sabil.qnow.domain.repository

import android.app.Activity
import android.net.Uri
import com.sabil.qnow.domain.model.AppUser
import com.sabil.qnow.domain.model.Business
import com.sabil.qnow.domain.model.BusinessPrivate
import com.sabil.qnow.domain.model.BusinessStatus
import com.sabil.qnow.domain.model.GeoLocation
import com.sabil.qnow.domain.model.MyTokenRef
import com.sabil.qnow.domain.model.Promo
import com.sabil.qnow.domain.model.QueueToken
import com.sabil.qnow.domain.model.Review
import com.sabil.qnow.domain.model.Service
import com.sabil.qnow.domain.model.TokenHolder
import com.sabil.qnow.domain.model.UserRole
import kotlinx.coroutines.flow.Flow

sealed interface PhoneAuthStep {
    data class CodeSent(val verificationId: String) : PhoneAuthStep
    /** Android verified the number by itself (instant verification or SMS auto-read). */
    data object AutoVerified : PhoneAuthStep
}

interface AuthRepository {
    val currentUserId: String?
    val authState: Flow<String?>
    fun sendPhoneCode(activity: Activity, phoneNumber: String): Flow<PhoneAuthStep>
    suspend fun verifyPhoneCode(verificationId: String, code: String)
    suspend fun signInWithGoogle(activity: Activity)
    suspend fun signOut()
}

interface UserRepository {
    fun observeUser(uid: String): Flow<AppUser?>
    suspend fun chooseRole(role: UserRole, displayName: String?)
}

data class BusinessDraft(
    val ownerName: String,
    val phone: String,
    val showPhonePublicly: Boolean,
    val name: String,
    val category: com.sabil.qnow.domain.model.BusinessCategory,
    val address: String,
    val location: GeoLocation,
    /** Photos to keep, in order: already-saved ones (by id) and newly picked ones. */
    val photos: List<PhotoDraft>,
    val services: List<Service>,
    val hours: List<com.sabil.qnow.domain.model.DayHours>,
    val chairs: Int,
    val graceMinutes: Int,
)

sealed interface PhotoDraft {
    data class Saved(val photo: com.sabil.qnow.domain.model.Photo) : PhotoDraft
    data class New(val uri: Uri) : PhotoDraft
}

interface BusinessRepository {
    fun observeBusiness(id: String): Flow<Business?>
    fun observeMyBusiness(): Flow<Business?>
    suspend fun myPrivateDetails(): BusinessPrivate?
    fun observeNearby(center: GeoLocation, radiusKm: Double): Flow<List<Business>>
    suspend fun photos(business: Business): List<com.sabil.qnow.domain.model.Photo>
    suspend fun saveBusiness(draft: BusinessDraft)
    suspend fun setStatus(status: BusinessStatus)
    suspend fun setActiveChairs(count: Int)
}

class AlreadyInQueueException : IllegalStateException("You already have a token here")
class BusinessNotOpenException : IllegalStateException("This business isn't taking tokens right now")

interface QueueRepository {
    /** Today's active tokens (waiting, in a chair, or in their grace period) for a business. */
    fun observeLiveQueue(businessId: String, dayKey: Long): Flow<List<QueueToken>>
    fun observeToken(businessId: String, tokenId: String): Flow<QueueToken?>
    fun observeMyTokens(): Flow<List<MyTokenRef>>

    /** Issues the next number in a transaction, so two people can never get the same token. */
    suspend fun bookToken(business: Business, service: Service?): MyTokenRef
    suspend fun cancelToken(businessId: String, tokenId: String)

    // Business side
    fun observeHolders(businessId: String, dayKey: Long): Flow<Map<String, TokenHolder>>
    suspend fun addWalkIn(business: Business, service: Service?, label: String): Int
    /** Marks a token done and, in the same write, moves [next] (if any) into the free chair. */
    suspend fun complete(businessId: String, tokenId: String, next: QueueToken?)
    /** Fills free chairs with the lowest waiting numbers. */
    suspend fun callNext(businessId: String, tokens: List<QueueToken>)
    suspend fun startGrace(business: Business, tokenId: String)
    suspend fun markArrived(businessId: String, tokenId: String)
    suspend fun markNoShow(businessId: String, tokenId: String)
}

interface FavouritesRepository {
    fun observeFavouriteIds(): Flow<Set<String>>
    suspend fun setFavourite(businessId: String, favourite: Boolean)
}

interface ReviewRepository {
    fun observeReviews(businessId: String): Flow<List<Review>>
    suspend fun submitReview(businessId: String, tokenId: String, rating: Int, comment: String)
}

interface PromoRepository {
    fun observePromos(): Flow<List<Promo>>
}

interface LocationRepository {
    suspend fun lastKnownLocation(): GeoLocation?
    suspend fun geocode(query: String): GeoLocation?
    suspend fun reverseGeocode(location: GeoLocation): String?
}
