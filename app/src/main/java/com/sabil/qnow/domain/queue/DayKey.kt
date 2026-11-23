package com.sabil.qnow.domain.queue

/**
 * Tokens restart at 1 every day. A day is counted in the business's own time zone as
 * whole days since 1970-01-01. firestore.rules computes the same value from the server clock,
 * so nobody can restart the numbering by sending a different day.
 */
object DayKey {
    private const val DAY_MS = 86_400_000L

    fun of(nowMillis: Long, utcOffsetMinutes: Int): Long =
        Math.floorDiv(nowMillis + utcOffsetMinutes * 60_000L, DAY_MS)

    fun tokenId(dayKey: Long, number: Int): String = "$dayKey-$number"
}
