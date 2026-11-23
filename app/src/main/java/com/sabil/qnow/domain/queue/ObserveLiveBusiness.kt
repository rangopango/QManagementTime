package com.sabil.qnow.domain.queue

import com.sabil.qnow.domain.model.Business
import com.sabil.qnow.domain.model.QueueToken
import com.sabil.qnow.domain.repository.BusinessRepository
import com.sabil.qnow.domain.repository.QueueRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** A business together with its live queue and the current wait estimates. */
data class LiveBusiness(
    val business: Business,
    val tokens: List<QueueToken>,
    val summary: WaitEstimator.QueueSummary,
)

/** Emits the current time every [periodMillis], so estimates count down between queue changes. */
fun clock(periodMillis: Long = 30_000): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(periodMillis)
    }
}

class ObserveLiveBusiness @Inject constructor(
    private val businesses: BusinessRepository,
    private val queue: QueueRepository,
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    operator fun invoke(businessId: String): Flow<LiveBusiness?> =
        businesses.observeBusiness(businessId).flatMapLatest { biz -> live(biz) }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun live(biz: Business?): Flow<LiveBusiness?> {
        if (biz == null) return flowOf(null)
        val now = clock()
        // Follow the business's own day, so the queue rolls over at its midnight.
        return now.map { DayKey.of(it, biz.utcOffsetMinutes) }
            .distinctUntilChanged()
            .flatMapLatest { day -> queue.observeLiveQueue(biz.id, day) }
            .combine(now) { tokens, millis ->
                LiveBusiness(biz, tokens, WaitEstimator.summarize(tokens, biz.activeChairs, millis))
            }
    }
}
