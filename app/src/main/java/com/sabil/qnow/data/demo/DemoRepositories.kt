package com.sabil.qnow.data.demo

import android.app.Activity
import com.sabil.qnow.domain.model.AppUser
import com.sabil.qnow.domain.model.Business
import com.sabil.qnow.domain.model.BusinessPrivate
import com.sabil.qnow.domain.model.BusinessStatus
import com.sabil.qnow.domain.model.GeoLocation
import com.sabil.qnow.domain.model.MyTokenRef
import com.sabil.qnow.domain.model.Photo
import com.sabil.qnow.domain.model.Promo
import com.sabil.qnow.domain.model.QueueToken
import com.sabil.qnow.domain.model.Review
import com.sabil.qnow.domain.model.Service
import com.sabil.qnow.domain.model.TokenHolder
import com.sabil.qnow.domain.model.TokenSource
import com.sabil.qnow.domain.model.TokenStatus
import com.sabil.qnow.domain.model.UserRole
import com.sabil.qnow.domain.repository.AccountInfo
import com.sabil.qnow.domain.repository.AlreadyInQueueException
import com.sabil.qnow.domain.repository.AuthRepository
import com.sabil.qnow.domain.repository.BusinessDraft
import com.sabil.qnow.domain.repository.BusinessNotOpenException
import com.sabil.qnow.domain.repository.BusinessRepository
import com.sabil.qnow.domain.repository.FavouritesRepository
import com.sabil.qnow.domain.repository.PhoneAuthStep
import com.sabil.qnow.domain.repository.PhotoDraft
import com.sabil.qnow.domain.repository.PromoRepository
import com.sabil.qnow.domain.repository.QueueRepository
import com.sabil.qnow.domain.repository.ReviewRepository
import com.sabil.qnow.domain.repository.UserRepository
import com.sabil.qnow.util.ImageCompressor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onStart
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DemoAuthRepository @Inject constructor(
    private val world: DemoWorld,
    private val mode: DemoMode,
) : AuthRepository {
    override val currentUserId: String = world.userId
    override val authState: Flow<String?> = flowOf(world.userId)
    override fun sendPhoneCode(activity: Activity, phoneNumber: String): Flow<PhoneAuthStep> = emptyFlow()
    override suspend fun verifyPhoneCode(verificationId: String, code: String) = Unit
    override suspend fun signInWithGoogle(activity: Activity) = Unit
    /** Signing out of the demo leaves demo mode and restarts into the real app. */
    override suspend fun signOut() = mode.exit()
    override fun account() = AccountInfo(world.userName, null, null, null, "Demo mode")
}

@Singleton
class DemoUserRepository @Inject constructor(private val world: DemoWorld) : UserRepository {
    override fun observeUser(uid: String): Flow<AppUser?> = flowOf(AppUser(uid, world.userName, world.role))
    override suspend fun chooseRole(role: UserRole, displayName: String?) = Unit
    override suspend fun updateDisplayName(name: String) { world.userName = name.trim().take(40) }
    private val profile = kotlinx.coroutines.flow.MutableStateFlow(com.sabil.qnow.domain.model.CustomerProfile(world.userName, null, emptyList()))
    override fun observeProfile() = profile
    override suspend fun saveProfile(gender: com.sabil.qnow.domain.model.Gender?, family: List<com.sabil.qnow.domain.model.FamilyMember>) { profile.value = profile.value.copy(gender = gender, family = family) }
    override suspend fun deleteCustomerAccount() = Unit
}

@Singleton
class DemoBusinessRepository @Inject constructor(
    private val world: DemoWorld,
    private val compressor: ImageCompressor,
) : BusinessRepository {
    private val photoStore = HashMap<String, ByteArray>()

    override fun observeBusiness(id: String): Flow<Business?> = world.watch { business(id) }
    override fun observeMyBusiness(): Flow<Business?> = world.watch { myBusinessId?.let { business(it) } }
    override suspend fun myPrivateDetails() = BusinessPrivate(world.userName, "+91 90000 00000", false)

    override fun observeNearby(center: GeoLocation, radiusKm: Double): Flow<List<Business>> =
        world.watch { allBusinesses().filter { it.location != null } }.onStart { world.ensureShopsNear(center) }

    override suspend fun photos(business: Business): List<Photo> =
        business.photoIds.mapNotNull { id -> photoStore[id]?.let { Photo(id, it) } }

    override suspend fun saveBusiness(draft: BusinessDraft) {
        val photos = draft.photos.take(6).map { p ->
            when (p) {
                is PhotoDraft.Saved -> p.photo
                is PhotoDraft.New -> Photo(UUID.randomUUID().toString(), compressor.compress(p.uri)).also { photoStore[it.id] = it.bytes }
            }
        }
        val thumb = photos.firstOrNull()?.bytes?.let { compressor.compress(it, ImageCompressor.THUMB_SIDE) }
        world.edit {
            val id = myBusinessId ?: return@edit
            val b = business(id) ?: return@edit
            putBusiness(b.copy(
                name = draft.name.trim(), category = draft.category, address = draft.address.trim(),
                location = draft.location, services = draft.services, hours = draft.hours,
                chairs = draft.chairs, activeChairs = b.activeChairs.coerceIn(1, draft.chairs),
                graceMinutes = draft.graceMinutes, currencyCode = draft.currencyCode, thumbnail = thumb, photoIds = photos.map { it.id },
                publicPhone = if (draft.showPhonePublicly) draft.phone else null,
            ))
        }
    }

    override suspend fun setStatus(status: BusinessStatus) = world.edit {
        myBusinessId?.let { id -> business(id)?.let { putBusiness(it.copy(status = status)) } }
        Unit
    }

    override suspend fun setActiveChairs(count: Int) = world.edit {
        myBusinessId?.let { id -> business(id)?.let { putBusiness(it.copy(activeChairs = count.coerceIn(1, it.chairs))) } }
        Unit
    }
}

@Singleton
class DemoQueueRepository @Inject constructor(private val world: DemoWorld) : QueueRepository {

    override fun observeLiveQueue(businessId: String, dayKey: Long): Flow<List<QueueToken>> =
        world.watch { liveTokens(businessId) }

    override fun observeToken(businessId: String, tokenId: String): Flow<QueueToken?> =
        world.watch { token(businessId, tokenId) }

    override fun observeMyTokens(): Flow<List<MyTokenRef>> = world.watch { myTokenRefs() }

    override suspend fun bookToken(business: Business, service: Service?, forName: String?): MyTokenRef = world.edit {
        val biz = business(business.id) ?: throw BusinessNotOpenException()
        if (biz.status != BusinessStatus.OPEN) throw BusinessNotOpenException()
        val holding = myTokenRefs().filter { it.businessId == biz.id }.any { token(it.businessId, it.tokenId)?.status?.isActive == true }
        if (holding) throw AlreadyInQueueException()
        val t = issue(biz.id, service, TokenSource.APP, TokenHolder("", userId, forName ?: userName.substringBefore(' ')))
        MyTokenRef(biz.id, t.id, t.number, biz.name, System.currentTimeMillis(), false).also { addMyToken(it) }
    }

    override suspend fun cancelToken(businessId: String, tokenId: String) = world.edit {
        setToken(businessId, tokenId) { it.copy(status = TokenStatus.CANCELLED, completedAtMillis = System.currentTimeMillis()) }
    }

    override fun observeHolders(businessId: String, dayKey: Long): Flow<Map<String, TokenHolder>> =
        world.watch { holdersOf(businessId) }

    override suspend fun addWalkIn(business: Business, service: Service?, label: String): Int = world.edit {
        issue(business.id, service, TokenSource.WALK_IN, TokenHolder("", null, label.trim().ifBlank { "Walk-in" })).number
    }

    override suspend fun complete(businessId: String, tokenId: String, next: QueueToken?) = world.edit {
        val now = System.currentTimeMillis()
        setToken(businessId, tokenId) { it.copy(status = TokenStatus.DONE, completedAtMillis = now) }
        if (next != null) setToken(businessId, next.id) { it.copy(status = TokenStatus.SERVING, calledAtMillis = now, graceEndsAtMillis = null) }
    }

    override suspend fun callNext(businessId: String, tokens: List<QueueToken>) = world.edit {
        val now = System.currentTimeMillis()
        tokens.forEach { t -> setToken(businessId, t.id) { it.copy(status = TokenStatus.SERVING, calledAtMillis = now, graceEndsAtMillis = null) } }
    }

    override suspend fun startGrace(business: Business, tokenId: String) = world.edit {
        setToken(business.id, tokenId) { it.copy(status = TokenStatus.GRACE, graceEndsAtMillis = System.currentTimeMillis() + business.graceMinutes * 60_000L) }
    }

    override suspend fun markArrived(businessId: String, tokenId: String) = world.edit {
        setToken(businessId, tokenId) { it.copy(status = TokenStatus.SERVING, calledAtMillis = System.currentTimeMillis(), graceEndsAtMillis = null) }
    }

    override suspend fun markNoShow(businessId: String, tokenId: String) = world.edit {
        setToken(businessId, tokenId) { it.copy(status = TokenStatus.NO_SHOW, completedAtMillis = System.currentTimeMillis()) }
    }
}

@Singleton
class DemoFavouritesRepository @Inject constructor(private val world: DemoWorld) : FavouritesRepository {
    override fun observeFavouriteIds(): Flow<Set<String>> = world.watch { favouriteIds() }
    override suspend fun setFavourite(businessId: String, favourite: Boolean) = world.edit { setFavourite(businessId, favourite) }
}

@Singleton
class DemoReviewRepository @Inject constructor(private val world: DemoWorld) : ReviewRepository {
    override fun observeReviews(businessId: String): Flow<List<Review>> = world.watch { reviewsOf(businessId) }
    override suspend fun submitReview(businessId: String, tokenId: String, rating: Int, comment: String) = world.edit {
        addReview(businessId, Review(UUID.randomUUID().toString(), rating.coerceIn(1, 5), comment.trim().take(500), System.currentTimeMillis()))
        markReviewed(businessId, tokenId)
    }
}

@Singleton
class DemoSupportRepository @Inject constructor() : com.sabil.qnow.domain.repository.SupportRepository {
    private val tickets = kotlinx.coroutines.flow.MutableStateFlow(emptyList<com.sabil.qnow.domain.repository.SupportTicket>())
    override suspend fun submit(category: String, message: String) {
        tickets.value = listOf(com.sabil.qnow.domain.repository.SupportTicket(UUID.randomUUID().toString(), category, message, "OPEN", System.currentTimeMillis(), null)) + tickets.value
    }
    override fun observeMine() = tickets
}

@Singleton
class DemoPromoRepository @Inject constructor() : PromoRepository {
    /** Empty, so the home screen shows its built-in banners. */
    override fun observePromos(): Flow<List<Promo>> = flowOf(emptyList())
}
