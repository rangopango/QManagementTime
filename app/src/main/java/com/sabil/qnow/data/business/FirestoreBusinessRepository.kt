package com.sabil.qnow.data.business

import com.firebase.geofire.GeoFireUtils
import com.firebase.geofire.GeoLocation as FireGeo
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.Blob
import com.sabil.qnow.domain.model.Photo
import com.sabil.qnow.domain.repository.PhotoDraft
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import com.sabil.qnow.data.firebase.Paths
import com.sabil.qnow.data.firebase.business
import com.sabil.qnow.data.firebase.observe
import com.sabil.qnow.data.firebase.toBusiness
import com.sabil.qnow.data.firebase.toBusinessPrivate
import com.sabil.qnow.data.firebase.toMap
import com.sabil.qnow.domain.model.Business
import com.sabil.qnow.domain.model.BusinessPrivate
import com.sabil.qnow.domain.model.BusinessStatus
import com.sabil.qnow.domain.model.GeoLocation
import com.sabil.qnow.domain.repository.BusinessDraft
import com.sabil.qnow.domain.repository.BusinessRepository
import com.sabil.qnow.util.ImageCompressor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Firestore reads are the free tier's main limit, so shops show at most this many photos. */
private const val MAX_PHOTOS = 6

@Singleton
class FirestoreBusinessRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val compressor: ImageCompressor,
) : BusinessRepository {

    private fun uid() = auth.currentUser?.uid ?: error("Not signed in")

    override fun observeBusiness(id: String): Flow<Business?> =
        db.business(id).observe().map { it?.toBusiness() }

    /** A business account owns exactly one business, stored under the owner's uid. */
    override fun observeMyBusiness(): Flow<Business?> = observeBusiness(uid())

    override suspend fun myPrivateDetails(): BusinessPrivate? =
        db.collection(Paths.BUSINESS_PRIVATE).document(uid()).get().await().toBusinessPrivate()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeNearby(center: GeoLocation, radiusKm: Double): Flow<List<Business>> {
        val radiusM = radiusKm * 1000
        val bounds = GeoFireUtils.getGeoHashQueryBounds(FireGeo(center.lat, center.lng), radiusM)
        if (bounds.isEmpty()) return flowOf(emptyList())
        val flows = bounds.map { b ->
            db.collection(Paths.BUSINESSES).orderBy("geohash").startAt(b.startHash).endAt(b.endHash)
                .limit(60).observe()
                .map { snap -> snap.documents.mapNotNull { it.toBusiness() } }
        }
        return combine(flows) { parts ->
            parts.toList().flatten()
                .distinctBy { it.id }
                .filter { biz ->
                    val loc = biz.location ?: return@filter false
                    GeoFireUtils.getDistanceBetween(FireGeo(loc.lat, loc.lng), FireGeo(center.lat, center.lng)) <= radiusM
                }
        }
    }

    override suspend fun saveBusiness(draft: BusinessDraft) {
        val ownerId = uid()
        val ref = db.business(ownerId)
        // Photos are compressed JPEG bytes kept in Firestore, so no paid file storage is needed.
        val photos = draft.photos.take(MAX_PHOTOS).map { p ->
            when (p) {
                is PhotoDraft.Saved -> Photo(p.photo.id, p.photo.bytes) to false
                is PhotoDraft.New -> Photo(UUID.randomUUID().toString(), compressor.compress(p.uri)) to true
            }
        }
        val thumbnail = photos.firstOrNull()?.first?.bytes?.let { compressor.compress(it, ImageCompressor.THUMB_SIDE) }
        val existing = ref.get().await()
        val removedIds = (existing.get("photoIds") as? List<*>).orEmpty().filterIsInstance<String>() - photos.map { it.first.id }.toSet()
        val geohash = GeoFireUtils.getGeoHashForLocation(FireGeo(draft.location.lat, draft.location.lng))
        val offset = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000
        val chairs = draft.chairs.coerceIn(1, 50)

        val public = mutableMapOf<String, Any?>(
            "ownerId" to ownerId,
            "name" to draft.name.trim(),
            "category" to draft.category.name,
            "address" to draft.address.trim(),
            "lat" to draft.location.lat,
            "lng" to draft.location.lng,
            "geohash" to geohash,
            "photoIds" to photos.map { it.first.id },
            "thumbnail" to thumbnail?.let { Blob.fromBytes(it) },
            "services" to draft.services.map { it.toMap() },
            "hours" to draft.hours.map { it.toMap() },
            "chairs" to chairs,
            "graceMinutes" to draft.graceMinutes.coerceIn(1, 15),
            "currency" to draft.currencyCode,
            "publicPhone" to if (draft.showPhonePublicly) draft.phone.trim() else null,
            "utcOffsetMinutes" to offset,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (!existing.exists()) {
            public += mapOf(
                "status" to BusinessStatus.CLOSED.name,
                "activeChairs" to chairs,
                "ratingSum" to 0,
                "ratingCount" to 0,
                "queue" to mapOf("dayKey" to 0, "lastNumber" to 0),
                "createdAt" to FieldValue.serverTimestamp(),
            )
        } else {
            val active = existing.getLong("activeChairs")?.toInt() ?: chairs
            public["activeChairs"] = active.coerceIn(1, chairs)
        }
        val private = mapOf(
            "ownerId" to ownerId,
            "ownerName" to draft.ownerName.trim(),
            "phone" to draft.phone.trim(),
            "showPhonePublicly" to draft.showPhonePublicly,
        )
        db.runBatch { batch ->
            batch.set(ref, public, SetOptions.merge())
            batch.set(db.collection(Paths.BUSINESS_PRIVATE).document(ownerId), private)
        }.await()
        // One write per photo keeps each request small.
        photos.filter { it.second }.forEach { (photo, _) ->
            ref.collection(Paths.PHOTOS).document(photo.id).set(mapOf(
                "data" to Blob.fromBytes(photo.bytes),
                "createdAt" to FieldValue.serverTimestamp(),
            )).await()
        }
        removedIds.forEach { ref.collection(Paths.PHOTOS).document(it).delete().await() }
    }

    override suspend fun photos(business: Business): List<Photo> = coroutineScope {
        business.photoIds.map { id ->
            async {
                runCatching { db.business(business.id).collection(Paths.PHOTOS).document(id).get().await() }
                    .getOrNull()?.getBlob("data")?.toBytes()?.let { Photo(id, it) }
            }
        }.awaitAll().filterNotNull()
    }

    override suspend fun setStatus(status: BusinessStatus) {
        db.business(uid()).update("status", status.name).await()
    }

    override suspend fun setActiveChairs(count: Int) {
        db.business(uid()).update("activeChairs", count).await()
    }
}
