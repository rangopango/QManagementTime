package com.sabil.qnow.domain.queue

import com.sabil.qnow.domain.model.QueueToken
import com.sabil.qnow.domain.model.TokenStatus
import kotlin.math.ceil
import kotlin.math.max

/**
 * Estimates waits by simulating the chairs.
 *
 * Every active chair has a "free at" time. Chairs serving someone are busy for the rest of
 * that service; idle chairs are free now. Each waiting token, in number order, takes the
 * chair that frees up first, and its wait is when that chair frees up. With one chair this is
 * the sum of the service times ahead; with more it spreads them across the chairs.
 */
object WaitEstimator {

    data class Estimate(
        val tokenId: String,
        val position: Int,       // 1 = next to be called
        val peopleAhead: Int,    // includes people in the chairs right now
        val waitMinutes: Int,
    )

    data class QueueSummary(
        val estimates: Map<String, Estimate>,
        /** Wait for someone who joins the back of the queue now. */
        val newcomerWaitMinutes: Int,
        val waitingCount: Int,
        val servingNumbers: List<Int>,
    )

    fun summarize(tokens: List<QueueToken>, activeChairs: Int, nowMillis: Long): QueueSummary {
        val inChair = tokens.filter { it.status == TokenStatus.SERVING || it.status == TokenStatus.GRACE }
        val waiting = tokens.filter { it.status == TokenStatus.WAITING }.sortedBy { it.number }

        val freeAt = DoubleArray(max(1, activeChairs))
        inChair.map { remainingMinutes(it, nowMillis) }
            .sortedDescending()
            .forEachIndexed { i, minutes -> freeAt[i % freeAt.size] += minutes }

        val estimates = LinkedHashMap<String, Estimate>()
        waiting.forEachIndexed { index, token ->
            val slot = freeAt.indices.minBy { freeAt[it] }
            estimates[token.id] = Estimate(
                tokenId = token.id,
                position = index + 1,
                peopleAhead = index + inChair.size,
                waitMinutes = ceil(freeAt[slot]).toInt(),
            )
            freeAt[slot] += token.durationMinutes
        }
        return QueueSummary(
            estimates = estimates,
            newcomerWaitMinutes = ceil(freeAt.min()).toInt(),
            waitingCount = waiting.size,
            servingNumbers = inChair.map { it.number }.sorted(),
        )
    }

    private fun remainingMinutes(token: QueueToken, nowMillis: Long): Double {
        val started = token.calledAtMillis ?: return token.durationMinutes.toDouble()
        val elapsed = (nowMillis - started) / 60_000.0
        // Someone running over still needs a little time; never assume a chair is free early.
        return (token.durationMinutes - elapsed).coerceIn(1.0, max(1.0, token.durationMinutes.toDouble()))
    }
}
