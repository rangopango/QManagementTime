package com.sabil.qnow.domain.queue

import com.sabil.qnow.domain.model.QueueToken
import com.sabil.qnow.domain.model.TokenSource
import com.sabil.qnow.domain.model.TokenStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class WaitEstimatorTest {

    private val now = 1_000_000_000L

    private fun token(number: Int, status: TokenStatus = TokenStatus.WAITING, minutes: Int = 20, calledMinutesAgo: Int? = null) =
        QueueToken(
            id = "t$number", businessId = "b", dayKey = 1, number = number, status = status,
            serviceId = null, serviceName = null, durationMinutes = minutes, source = TokenSource.APP,
            createdAtMillis = now, calledAtMillis = calledMinutesAgo?.let { now - it * 60_000L },
            graceEndsAtMillis = null, completedAtMillis = null,
        )

    @Test
    fun `one chair adds up the services ahead`() {
        val tokens = listOf(
            token(1, TokenStatus.SERVING, 20, calledMinutesAgo = 5), // 15 left
            token(2, minutes = 10),
            token(3, minutes = 30),
            token(4, minutes = 20),
        )
        val s = WaitEstimator.summarize(tokens, activeChairs = 1, nowMillis = now)
        assertEquals(15, s.estimates.getValue("t2").waitMinutes)
        assertEquals(25, s.estimates.getValue("t3").waitMinutes)
        assertEquals(55, s.estimates.getValue("t4").waitMinutes)
        assertEquals(75, s.newcomerWaitMinutes)
        assertEquals(3, s.estimates.getValue("t4").peopleAhead)
        assertEquals(3, s.estimates.getValue("t4").position)
    }

    @Test
    fun `two chairs share the queue`() {
        val tokens = listOf(
            token(1, TokenStatus.SERVING, 20, calledMinutesAgo = 10), // chair A free in 10
            token(2, minutes = 20), // chair B is idle: starts now
            token(3, minutes = 20), // chair A at 10
            token(4, minutes = 20), // chair B at 20
        )
        val s = WaitEstimator.summarize(tokens, activeChairs = 2, nowMillis = now)
        assertEquals(0, s.estimates.getValue("t2").waitMinutes)
        assertEquals(10, s.estimates.getValue("t3").waitMinutes)
        assertEquals(20, s.estimates.getValue("t4").waitMinutes)
        assertEquals(30, s.newcomerWaitMinutes)
    }

    @Test
    fun `someone running over still holds the chair for a minute`() {
        val tokens = listOf(token(1, TokenStatus.SERVING, 20, calledMinutesAgo = 45), token(2))
        val s = WaitEstimator.summarize(tokens, activeChairs = 1, nowMillis = now)
        assertEquals(1, s.estimates.getValue("t2").waitMinutes)
    }

    @Test
    fun `empty queue means no wait`() {
        val s = WaitEstimator.summarize(emptyList(), activeChairs = 3, nowMillis = now)
        assertEquals(0, s.newcomerWaitMinutes)
        assertEquals(0, s.waitingCount)
    }

    @Test
    fun `day key follows the business time zone`() {
        // 2026-11-23 20:00 UTC is already the 24th in India (UTC+5:30).
        val millis = java.time.Instant.parse("2026-11-23T20:00:00Z").toEpochMilli()
        val utc = DayKey.of(millis, 0)
        val ist = DayKey.of(millis, 330)
        assertEquals(utc + 1, ist)
        assertEquals("${ist}-7", DayKey.tokenId(ist, 7))
    }
}
