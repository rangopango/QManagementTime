package com.sabil.qnow.domain.model

/** Which side of the app a signed-in person uses. Chosen once on first login. */
enum class UserRole { CUSTOMER, BUSINESS }

/** Kinds of queue-based business. Barbers, salons and parlours are live first; the rest are ready for later. */
enum class BusinessCategory(val label: String, val enabled: Boolean) {
    BARBER("Barber", true),
    SALON("Salon", true),
    PARLOUR("Beauty parlour", true),
    CAR_WASH("Car wash", false),
    CLINIC("Clinic", false),
    OTHER("Other", false),
}

/** Manual availability switch the business controls from its dashboard. */
enum class BusinessStatus { OPEN, ON_BREAK, CLOSED }

data class GeoLocation(val lat: Double, val lng: Double)

/** Something a business offers, with a price and how long it usually takes. */
data class Service(
    val id: String,
    val name: String,
    val price: Double,
    val durationMinutes: Int,
    /** Who the service is meant for, so customers see what fits the person they book for. */
    val audience: Audience = Audience.ALL,
)

enum class Audience(val label: String) { ALL("Everyone"), MEN("Men"), WOMEN("Women"), KIDS("Kids") }

enum class Gender(val label: String) { MALE("Male"), FEMALE("Female"), OTHER("Other") }

/** Someone a customer books for: themselves, a partner, a parent, or a child. */
data class FamilyMember(
    val id: String,
    val name: String,
    val gender: Gender,
    val isChild: Boolean,
    val age: Int? = null,
) {
    /** Services that suit this person. */
    fun fits(s: Service) = s.audience == Audience.ALL || when {
        isChild -> s.audience == Audience.KIDS
        gender == Gender.MALE -> s.audience == Audience.MEN
        gender == Gender.FEMALE -> s.audience == Audience.WOMEN
        else -> s.audience != Audience.KIDS
    }
}

/** The customer's own details plus the people they book for. Private to the customer. */
data class CustomerProfile(
    val name: String?,
    val gender: Gender?,
    val family: List<FamilyMember>,
) {
    /** "Me" first, then everyone else. */
    fun bookable(): List<FamilyMember> =
        listOf(FamilyMember(SELF_ID, name?.substringBefore(' ')?.ifBlank { null } ?: "Me", gender ?: Gender.OTHER, false)) + family

    companion object { const val SELF_ID = "self" }
}

/** Opening hours for one day, in minutes after midnight (local time of the business). */
data class DayHours(
    val day: Int, // 1 = Monday … 7 = Sunday (java.time.DayOfWeek values)
    val open: Boolean,
    val opensAt: Int = 9 * 60,
    val closesAt: Int = 21 * 60,
)

data class QueueCounter(
    val dayKey: Long = 0,
    val lastNumber: Int = 0,
)

/**
 * Everything a customer is allowed to see about a business. Lives in the public
 * `businesses` collection. Private owner details live in [BusinessPrivate].
 */
data class Business(
    val id: String,
    val ownerId: String,
    val name: String,
    val category: BusinessCategory,
    val address: String,
    val location: GeoLocation?,
    val geohash: String,
    /** Small JPEG (about 240 px) of the first photo, kept in the business doc for lists and pins. */
    val thumbnail: ByteArray?,
    /** Ids of full-size photos in businesses/{id}/photos, in display order. */
    val photoIds: List<String>,
    val services: List<Service>,
    val hours: List<DayHours>,
    val chairs: Int,
    val activeChairs: Int,
    val status: BusinessStatus,
    val publicPhone: String?,
    val ratingSum: Long,
    val ratingCount: Long,
    val utcOffsetMinutes: Int,
    val graceMinutes: Int,
    val queue: QueueCounter,
    /** ISO 4217 code of the shop's country (from its map pin), e.g. INR. Prices are shown in it. */
    val currencyCode: String = "INR",
    /** Set by QNow when the shop pays to be listed first. */
    val sponsorship: Sponsorship? = null,
) {
    val rating: Double get() = if (ratingCount == 0L) 0.0 else ratingSum.toDouble() / ratingCount
    val averageServiceMinutes: Int
        get() = services.map { it.durationMinutes }.average().takeIf { !it.isNaN() }?.toInt() ?: 20
}

/** Owner-only details. Never readable by customers (see firestore.rules). */
data class BusinessPrivate(
    val ownerName: String,
    val phone: String,
    val showPhonePublicly: Boolean,
)

enum class TokenStatus {
    WAITING,
    SERVING,
    /** Called but not at the counter yet; skipped as a no-show when the grace period ends. */
    GRACE,
    DONE,
    NO_SHOW,
    CANCELLED;

    val isActive: Boolean get() = this == WAITING || this == SERVING || this == GRACE
}

enum class TokenSource { APP, WALK_IN }

/**
 * A place in a business's queue as everyone can see it: only the number, never who holds it.
 * Who holds it is in a separate owner-only document.
 */
data class QueueToken(
    val id: String,
    val businessId: String,
    val dayKey: Long,
    val number: Int,
    val status: TokenStatus,
    val serviceId: String?,
    val serviceName: String?,
    val durationMinutes: Int,
    val source: TokenSource,
    val createdAtMillis: Long,
    val calledAtMillis: Long?,
    val graceEndsAtMillis: Long?,
    val completedAtMillis: Long?,
)

/** A customer's pointer to a token they hold. Only that customer can read it. */
data class MyTokenRef(
    val businessId: String,
    val tokenId: String,
    val number: Int,
    val businessName: String,
    val createdAtMillis: Long,
    val reviewed: Boolean,
)

/** What the business owner sees about who holds a token. Customers never read this. */
data class TokenHolder(
    val tokenId: String,
    val customerId: String?,
    val label: String,
)

data class Review(
    val id: String,
    val rating: Int,
    val comment: String,
    val createdAtMillis: Long,
)

/** Who an ad or a sponsorship is shown to. Children never get targeted ads (Google Play rules). */
enum class AdAudience(val label: String) { ALL("Everyone"), MEN("Men"), WOMEN("Women") }

/** What tapping an ad does. */
enum class PromoAction(val label: String) { NONE("Nothing"), SHOP("A shop"), LOOKS("Look Studio"), MAP("Map"), LINK("Web link") }

/**
 * A banner on the customer home screen: our own promotions and paid ads from sponsors.
 * Targeting (who, where, when) is checked ON THE PHONE, so nothing about the customer is sent
 * anywhere to pick their ads. Sponsors only ever see the [views] and [taps] totals.
 */
data class Promo(
    val id: String,
    val title: String,
    val subtitle: String,
    val ctaLabel: String,
    val imageUrl: String?,
    val targetBusinessId: String?,
    val priority: Int,
    val active: Boolean = true,
    val audience: AdAudience = AdAudience.ALL,
    /** Only shown within [radiusKm] of this point; null = everywhere. */
    val areaCenter: GeoLocation? = null,
    val radiusKm: Double = 0.0,
    val startsAtMillis: Long? = null,
    val endsAtMillis: Long? = null,
    /** Name of the paying sponsor; null for QNow's own promotions. Shown as "Sponsored". */
    val sponsor: String? = null,
    /** What a tap opens. Older ads without it open [targetBusinessId] when set. */
    val action: PromoAction = if (targetBusinessId != null) PromoAction.SHOP else PromoAction.NONE,
    /** https link for [PromoAction.LINK]. */
    val linkUrl: String? = null,
    val views: Long = 0,
    val taps: Long = 0,
) {
    /** Should this customer see the ad? Runs on the phone only. */
    fun showsTo(gender: Gender?, here: GeoLocation?, now: Long): Boolean {
        if (!active) return false
        if (startsAtMillis != null && now < startsAtMillis) return false
        if (endsAtMillis != null && now > endsAtMillis) return false
        when (audience) {
            AdAudience.ALL -> Unit
            AdAudience.MEN -> if (gender != Gender.MALE) return false
            AdAudience.WOMEN -> if (gender != Gender.FEMALE) return false
        }
        val c = areaCenter
        if (c != null && radiusKm > 0) {
            if (here == null || kmBetween(c, here) > radiusKm) return false
        }
        return true
    }
}

/** A shop paying to be listed first (with a "Sponsored" label) for an audience, until a date. */
data class Sponsorship(val audience: AdAudience, val untilMillis: Long, val sponsor: String? = null) {
    fun showsTo(gender: Gender?, now: Long): Boolean = now <= untilMillis && when (audience) {
        AdAudience.ALL -> true
        AdAudience.MEN -> gender == Gender.MALE
        AdAudience.WOMEN -> gender == Gender.FEMALE
    }
}

/** Straight-line distance in km (haversine). */
fun kmBetween(a: GeoLocation, b: GeoLocation): Double {
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val h = Math.sin(dLat / 2).let { it * it } +
        Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.sin(dLng / 2).let { it * it }
    return 2 * 6371.0 * Math.asin(Math.sqrt(h))
}

/** A shop photo stored as compressed JPEG bytes in Firestore (no paid file storage). */
class Photo(val id: String, val bytes: ByteArray)

data class AppUser(
    val uid: String,
    val displayName: String?,
    val role: UserRole?,
)
