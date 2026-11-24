package com.sabil.qnow.data.queue

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.Transaction
import com.sabil.qnow.data.firebase.Paths
import com.sabil.qnow.data.firebase.business
import com.sabil.qnow.data.firebase.observe
import com.sabil.qnow.data.firebase.timestampOf
import com.sabil.qnow.data.firebase.toBusiness
import com.sabil.qnow.data.firebase.toHolder
import com.sabil.qnow.data.firebase.toMyTokenRef
import com.sabil.qnow.data.firebase.toToken
import com.sabil.qnow.domain.model.Business
import com.sabil.qnow.domain.model.BusinessStatus
import com.sabil.qnow.domain.model.MyTokenRef
import com.sabil.qnow.domain.model.QueueToken
import com.sabil.qnow.domain.model.Service
import com.sabil.qnow.domain.model.TokenHolder
import com.sabil.qnow.domain.model.TokenSource
import com.sabil.qnow.domain.model.TokenStatus
import com.sabil.qnow.domain.queue.DayKey
import com.sabil.qnow.domain.repository.AlreadyInQueueException
import com.sabil.qnow.domain.repository.BusinessNotOpenException
import com.sabil.qnow.domain.repository.QueueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreQueueRepository @Inject constructor(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
) : QueueRepository {

    private fun uid() = auth.currentUser?.uid ?: error("Not signed in")
    private fun tokens(businessId: String) = db.business(businessId).collection(Paths.TOKENS)
    private fun holders(businessId: String) = db.business(businessId).collection(Paths.HOLDERS)
    private fun me() = db.collection(Paths.USERS).document(uid())

    private val activeStatuses = listOf(TokenStatus.WAITING.name, TokenStatus.SERVING.name, TokenStatus.GRACE.name)

    override fun observeLiveQueue(businessId: String, dayKey: Long): Flow<List<QueueToken>> =
        tokens(businessId)
            .whereEqualTo("dayKey", dayKey)
            .whereIn("status", activeStatuses)
            .orderBy("number")
            .observe()
            .map { snap -> snap.documents.mapNotNull { it.toToken(businessId) } }

    override fun observeToken(businessId: String, tokenId: String): Flow<QueueToken?> =
        tokens(businessId).document(tokenId).observe().map { it?.toToken(businessId) }

    override fun observeMyTokens(): Flow<List<MyTokenRef>> =
        me().collection(Paths.MY_TOKENS)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(20)
            .observe()
            .map { snap -> snap.documents.mapNotNull { it.toMyTokenRef() } }

    /**
     * Reads the business's counter and writes the next number in one transaction. Firestore
     * retries the whole thing if someone else booked in between, and the token's document id
     * is "{day}-{number}", so a duplicate number can't be written even by a modified client
     * (firestore.rules checks the counter and the id together).
     */
    override suspend fun bookToken(business: Business, service: Service?, forName: String?): MyTokenRef {
        val uid = uid()
        val label = (forName?.trim()?.takeIf { it.isNotBlank() }
            ?: auth.currentUser?.displayName?.substringBefore(' ')?.takeIf { it.isNotBlank() }
            ?: "App customer").take(40)
        val bizRef = db.business(business.id)
        val lockRef = me().collection(Paths.ACTIVE_TOKENS).document(business.id)

        val ref = db.runTransaction { tx ->
            val biz = tx.get(bizRef).toBusiness() ?: throw BusinessNotOpenException()
            if (biz.status != BusinessStatus.OPEN) throw BusinessNotOpenException()
            val lock = tx.get(lockRef)
            lock.getString("tokenId")?.let { heldId ->
                val held = tx.get(tokens(business.id).document(heldId)).toToken(business.id)
                if (held != null && held.status.isActive) throw AlreadyInQueueException()
            }
            val created = issue(tx, biz, service, TokenSource.APP)
            tx.set(holders(business.id).document(created.id), mapOf(
                "customerId" to uid,
                "label" to label,
                "dayKey" to created.dayKey,
            ))
            tx.set(lockRef, mapOf("tokenId" to created.id))
            tx.set(me().collection(Paths.MY_TOKENS).document("${business.id}_${created.id}"), mapOf(
                "businessId" to business.id,
                "tokenId" to created.id,
                "number" to created.number,
                "businessName" to biz.name,
                "createdAt" to FieldValue.serverTimestamp(),
                "reviewed" to false,
            ))
            MyTokenRef(business.id, created.id, created.number, biz.name, System.currentTimeMillis(), false)
        }.await()
        return ref
    }

    private data class Issued(val id: String, val number: Int, val dayKey: Long)

    private fun issue(tx: Transaction, biz: Business, service: Service?, source: TokenSource): Issued {
        val dayKey = DayKey.of(System.currentTimeMillis(), biz.utcOffsetMinutes)
        val number = if (biz.queue.dayKey == dayKey) biz.queue.lastNumber + 1 else 1
        val id = DayKey.tokenId(dayKey, number)
        tx.update(db.business(biz.id), "queue", mapOf("dayKey" to dayKey, "lastNumber" to number))
        tx.set(tokens(biz.id).document(id), mapOf(
            "dayKey" to dayKey,
            "number" to number,
            "status" to TokenStatus.WAITING.name,
            "serviceId" to service?.id,
            "serviceName" to service?.name,
            "durationMinutes" to (service?.durationMinutes ?: biz.averageServiceMinutes),
            "source" to source.name,
            "createdAt" to FieldValue.serverTimestamp(),
        ))
        return Issued(id, number, dayKey)
    }

    override suspend fun cancelToken(businessId: String, tokenId: String) {
        tokens(businessId).document(tokenId).update(mapOf(
            "status" to TokenStatus.CANCELLED.name,
            "completedAt" to FieldValue.serverTimestamp(),
        )).await()
    }

    override fun observeHolders(businessId: String, dayKey: Long): Flow<Map<String, TokenHolder>> =
        holders(businessId).whereEqualTo("dayKey", dayKey).observe()
            .map { snap -> snap.documents.associate { it.id to it.toHolder() } }

    override suspend fun addWalkIn(business: Business, service: Service?, label: String): Int =
        db.runTransaction { tx ->
            val biz = tx.get(db.business(business.id)).toBusiness() ?: error("Business not found")
            val issued = issue(tx, biz, service, TokenSource.WALK_IN)
            tx.set(holders(business.id).document(issued.id), mapOf(
                "customerId" to null,
                "label" to label.trim().ifBlank { "Walk-in" }.take(40),
                "dayKey" to issued.dayKey,
            ))
            issued.number
        }.await()

    override suspend fun complete(businessId: String, tokenId: String, next: QueueToken?) {
        db.runBatch { b ->
            b.update(tokens(businessId).document(tokenId), mapOf(
                "status" to TokenStatus.DONE.name,
                "completedAt" to FieldValue.serverTimestamp(),
            ))
            if (next != null) b.update(tokens(businessId).document(next.id), serving())
        }.await()
    }

    override suspend fun callNext(businessId: String, tokens: List<QueueToken>) {
        if (tokens.isEmpty()) return
        val collection = tokens(businessId)
        db.runBatch { b -> tokens.forEach { b.update(collection.document(it.id), serving()) } }.await()
    }

    private fun serving() = mapOf(
        "status" to TokenStatus.SERVING.name,
        "calledAt" to FieldValue.serverTimestamp(),
        "graceEndsAt" to null,
    )

    override suspend fun startGrace(business: Business, tokenId: String) {
        val ends = System.currentTimeMillis() + business.graceMinutes * 60_000L
        tokens(business.id).document(tokenId).update(mapOf(
            "status" to TokenStatus.GRACE.name,
            "graceEndsAt" to timestampOf(ends),
        )).await()
    }

    override suspend fun markArrived(businessId: String, tokenId: String) {
        tokens(businessId).document(tokenId).update(serving()).await()
    }

    override suspend fun markNoShow(businessId: String, tokenId: String) {
        tokens(businessId).document(tokenId).update(mapOf(
            "status" to TokenStatus.NO_SHOW.name,
            "completedAt" to FieldValue.serverTimestamp(),
        )).await()
    }
}
