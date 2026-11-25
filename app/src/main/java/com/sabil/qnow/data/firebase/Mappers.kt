package com.sabil.qnow.data.firebase

import com.google.firebase.firestore.DocumentSnapshot
import com.sabil.qnow.domain.model.Business
import com.sabil.qnow.domain.model.BusinessCategory
import com.sabil.qnow.domain.model.BusinessPrivate
import com.sabil.qnow.domain.model.BusinessStatus
import com.sabil.qnow.domain.model.DayHours
import com.sabil.qnow.domain.model.GeoLocation
import com.sabil.qnow.domain.model.MyTokenRef
import com.sabil.qnow.domain.model.Promo
import com.sabil.qnow.domain.model.QueueCounter
import com.sabil.qnow.domain.model.QueueToken
import com.sabil.qnow.domain.model.Review
import com.sabil.qnow.domain.model.Service
import com.sabil.qnow.domain.model.TokenHolder
import com.sabil.qnow.domain.model.TokenSource
import com.sabil.qnow.domain.model.TokenStatus

private inline fun <reified E : Enum<E>> enumOr(value: String?, default: E): E =
    value?.let { v -> enumValues<E>().firstOrNull { it.name == v } } ?: default

fun DocumentSnapshot.toBusiness(): Business? {
    if (!exists()) return null
    val lat = getDouble("lat")
    val lng = getDouble("lng")
    @Suppress("UNCHECKED_CAST")
    val services = (get("services") as? List<Map<String, Any?>>).orEmpty().mapNotNull { m ->
        Service(
            id = m.str("id") ?: return@mapNotNull null,
            name = m.str("name").orEmpty(),
            price = m.num("price")?.toDouble() ?: 0.0,
            durationMinutes = m.num("durationMinutes")?.toInt() ?: 20,
            audience = enumOr(m.str("for"), com.sabil.qnow.domain.model.Audience.ALL),
        )
    }
    @Suppress("UNCHECKED_CAST")
    val hours = (get("hours") as? List<Map<String, Any?>>).orEmpty().map { m ->
        DayHours(
            day = m.num("day")?.toInt() ?: 1,
            open = m["open"] as? Boolean ?: false,
            opensAt = m.num("opensAt")?.toInt() ?: 540,
            closesAt = m.num("closesAt")?.toInt() ?: 1260,
        )
    }
    val queue = get("queue") as? Map<*, *>
    return Business(
        id = id,
        ownerId = str("ownerId").orEmpty(),
        name = str("name").orEmpty(),
        category = enumOr(str("category"), BusinessCategory.OTHER),
        address = str("address").orEmpty(),
        location = if (lat != null && lng != null) GeoLocation(lat, lng) else null,
        geohash = str("geohash").orEmpty(),
        thumbnail = getBlob("thumbnail")?.toBytes(),
        photoIds = (get("photoIds") as? List<*>).orEmpty().filterIsInstance<String>(),
        services = services,
        hours = hours,
        chairs = int("chairs", 1),
        activeChairs = int("activeChairs", int("chairs", 1)),
        status = enumOr(str("status"), BusinessStatus.CLOSED),
        publicPhone = str("publicPhone"),
        ratingSum = long("ratingSum"),
        ratingCount = long("ratingCount"),
        utcOffsetMinutes = int("utcOffsetMinutes"),
        graceMinutes = int("graceMinutes", 3),
        currencyCode = str("currency")?.takeIf { it.length == 3 } ?: "INR",
        sponsorship = (get("sponsor") as? Map<*, *>)?.let { m ->
            val until = (m["until"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: return@let null
            com.sabil.qnow.domain.model.Sponsorship(enumOr(m["audience"] as? String, com.sabil.qnow.domain.model.AdAudience.ALL), until, m["sponsor"] as? String)
        },
        queue = QueueCounter(
            dayKey = queue?.num("dayKey")?.toLong() ?: 0,
            lastNumber = queue?.num("lastNumber")?.toInt() ?: 0,
        ),
    )
}

fun Service.toMap() = mapOf(
    "id" to id, "name" to name, "price" to price, "durationMinutes" to durationMinutes, "for" to audience.name,
)

fun DayHours.toMap() = mapOf(
    "day" to day, "open" to open, "opensAt" to opensAt, "closesAt" to closesAt,
)

fun DocumentSnapshot.toBusinessPrivate(): BusinessPrivate? {
    if (!exists()) return null
    return BusinessPrivate(
        ownerName = str("ownerName").orEmpty(),
        phone = str("phone").orEmpty(),
        showPhonePublicly = getBoolean("showPhonePublicly") ?: false,
    )
}

fun DocumentSnapshot.toToken(businessId: String): QueueToken? {
    if (!exists()) return null
    return QueueToken(
        id = id,
        businessId = businessId,
        dayKey = long("dayKey"),
        number = int("number"),
        status = enumOr(str("status"), TokenStatus.WAITING),
        serviceId = str("serviceId"),
        serviceName = str("serviceName"),
        durationMinutes = int("durationMinutes", 20),
        source = enumOr(str("source"), TokenSource.APP),
        createdAtMillis = millis("createdAt") ?: System.currentTimeMillis(),
        calledAtMillis = millis("calledAt"),
        graceEndsAtMillis = millis("graceEndsAt"),
        completedAtMillis = millis("completedAt"),
    )
}

fun DocumentSnapshot.toMyTokenRef(): MyTokenRef? {
    if (!exists()) return null
    return MyTokenRef(
        businessId = str("businessId") ?: return null,
        tokenId = str("tokenId") ?: return null,
        number = int("number"),
        businessName = str("businessName").orEmpty(),
        createdAtMillis = millis("createdAt") ?: 0,
        reviewed = getBoolean("reviewed") ?: false,
    )
}

fun DocumentSnapshot.toHolder(): TokenHolder = TokenHolder(
    tokenId = id,
    customerId = str("customerId"),
    label = str("label").orEmpty(),
)

fun DocumentSnapshot.toReview(): Review = Review(
    id = id,
    rating = int("rating"),
    comment = str("comment").orEmpty(),
    createdAtMillis = millis("createdAt") ?: 0,
)

fun DocumentSnapshot.toPromo(): Promo = Promo(
    id = id,
    title = str("title").orEmpty(),
    subtitle = str("subtitle").orEmpty(),
    ctaLabel = str("ctaLabel") ?: "View",
    imageUrl = str("imageUrl"),
    targetBusinessId = str("targetBusinessId"),
    priority = int("priority"),
    active = getBoolean("active") ?: false,
    audience = enumOr(str("audience"), com.sabil.qnow.domain.model.AdAudience.ALL),
    areaCenter = (get("area") as? Map<*, *>)?.let { m ->
        val lat = (m["lat"] as? Number)?.toDouble(); val lng = (m["lng"] as? Number)?.toDouble()
        if (lat != null && lng != null) GeoLocation(lat, lng) else null
    },
    radiusKm = ((get("area") as? Map<*, *>)?.get("radiusKm") as? Number)?.toDouble() ?: 0.0,
    startsAtMillis = millis("startsAt"),
    endsAtMillis = millis("endsAt"),
    sponsor = str("sponsor"),
    action = enumOr(str("action"), if (str("targetBusinessId") != null) com.sabil.qnow.domain.model.PromoAction.SHOP else com.sabil.qnow.domain.model.PromoAction.NONE),
    linkUrl = str("linkUrl"),
    views = long("views"),
    taps = long("taps"),
)
