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
)

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

/** A banner slot on the customer home screen. Managed from the Firebase console. */
data class Promo(
    val id: String,
    val title: String,
    val subtitle: String,
    val ctaLabel: String,
    val imageUrl: String?,
    val targetBusinessId: String?,
    val priority: Int,
)

/** A shop photo stored as compressed JPEG bytes in Firestore (no paid file storage). */
class Photo(val id: String, val bytes: ByteArray)

data class AppUser(
    val uid: String,
    val displayName: String?,
    val role: UserRole?,
)
