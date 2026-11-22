package com.sabil.queuetimemanagement

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class Service(
    val name: String,
    val duration: Int,
    val price: Int
)

internal data class Barber(
    val name: String,
    val info: String
)

internal data class Salon(
    val name: String,
    val area: String,
    val rating: String,
    val tags: String,
    val services: List<Service>,
    val barbers: List<Barber>
) {
    val fromPrice: Int get() = services.minOf { it.price }
}

internal enum class EntryStatus { WAITING, IN_SERVICE }

/** One person in a barber's line today. Tokens are unique within a salon. */
internal data class QueueEntry(
    val token: Int,
    val salon: String,
    val barber: String,
    val service: Service,
    val status: EntryStatus = EntryStatus.WAITING
)

internal data class BarberKey(val salon: String, val barber: String)

internal data class BarberStats(val completed: Int = 0, val earned: Int = 0)

/**
 * The customer's booking. [time] is null for "Next available".
 * [token] is set only when the booking joined today's live queue; a booking
 * for a fixed time or another day is an appointment and has no token.
 */
internal data class Booking(
    val salon: Salon,
    val barber: String,
    val service: Service,
    val date: LocalDate,
    val time: LocalTime?,
    val token: Int?
)

/** Everything the customer, barber and owner screens share. */
internal data class QueueState(
    val entries: List<QueueEntry> = emptyList(),
    val onBreak: Set<BarberKey> = emptySet(),
    val stats: Map<BarberKey, BarberStats> = emptyMap(),
    val nextToken: Map<String, Int> = emptyMap(),
    val booking: Booking? = null
) {
    fun queueFor(key: BarberKey): List<QueueEntry> =
        entries.filter { it.salon == key.salon && it.barber == key.barber }

    fun isAvailable(key: BarberKey): Boolean = key !in onBreak

    fun statsFor(key: BarberKey): BarberStats = stats[key] ?: BarberStats()

    fun waitingCount(salon: String): Int =
        entries.count { it.salon == salon && it.status == EntryStatus.WAITING }

    /** Minutes until a new arrival would be seen by this barber. */
    fun barberWait(key: BarberKey): Int = queueFor(key).sumOf { it.service.duration }

    /** Shortest wait among barbers who are working, or null if none are. */
    fun salonWait(salon: Salon): Int? =
        salon.barbers.map { BarberKey(salon.name, it.name) }
            .filter(::isAvailable)
            .minOfOrNull(::barberWait)

    /** The line the customer is in, or empty when the booking has no token. */
    fun bookingQueue(): List<QueueEntry> {
        val b = booking ?: return emptyList()
        if (b.token == null) return emptyList()
        return queueFor(BarberKey(b.salon.name, b.barber))
    }

    private fun entriesAheadOfBooking() = bookingQueue().takeWhile { it.token != booking?.token }

    fun bookingWait(): Int = entriesAheadOfBooking().sumOf { it.service.duration }

    fun waitingAheadOfBooking(): Int =
        entriesAheadOfBooking().count { it.status == EntryStatus.WAITING }

    /** Makes a booking, replacing the current one if there is one. */
    fun book(
        salon: Salon,
        barber: String,
        service: Service,
        date: LocalDate,
        time: LocalTime?,
        today: LocalDate
    ): QueueState {
        val base = cancelBooking()
        val joinsQueue = date == today && time == null
        if (!joinsQueue) {
            return base.copy(booking = Booking(salon, barber, service, date, time, token = null))
        }
        val (queued, token) = base.enqueue(BarberKey(salon.name, barber), service)
        return queued.copy(booking = Booking(salon, barber, service, date, time, token))
    }

    fun cancelBooking(): QueueState {
        val b = booking ?: return this
        return copy(
            entries = entries.filterNot { b.holds(it) },
            booking = null
        )
    }

    fun addWalkIn(key: BarberKey, service: Service): QueueState = enqueue(key, service).first

    /** Starts the first waiting customer if the barber is working and free. */
    fun startNext(key: BarberKey): QueueState {
        val line = queueFor(key)
        if (!isAvailable(key) || line.any { it.status == EntryStatus.IN_SERVICE }) return this
        val next = line.firstOrNull() ?: return this
        return copy(entries = entries.map {
            if (it == next) it.copy(status = EntryStatus.IN_SERVICE) else it
        })
    }

    /** Finishes the customer in the chair, records the sale and calls the next one. */
    fun completeCurrent(key: BarberKey): QueueState {
        val done = queueFor(key).firstOrNull { it.status == EntryStatus.IN_SERVICE } ?: return this
        val old = statsFor(key)
        return removeEntry(done)
            .copy(stats = stats + (key to BarberStats(old.completed + 1, old.earned + done.service.price)))
            .startNext(key)
    }

    fun removeLastWaiting(key: BarberKey): QueueState {
        val last = queueFor(key).lastOrNull { it.status == EntryStatus.WAITING } ?: return this
        return removeEntry(last)
    }

    fun toggleBreak(key: BarberKey): QueueState =
        copy(onBreak = if (key in onBreak) onBreak - key else onBreak + key)

    private fun enqueue(key: BarberKey, service: Service): Pair<QueueState, Int> {
        val token = nextToken[key.salon] ?: 1
        return copy(
            entries = entries + QueueEntry(token, key.salon, key.barber, service),
            nextToken = nextToken + (key.salon to token + 1)
        ) to token
    }

    /** Also ends the customer's booking if the removed entry was theirs. */
    private fun removeEntry(entry: QueueEntry): QueueState = copy(
        entries = entries - entry,
        booking = booking?.takeUnless { it.holds(entry) }
    )

    private fun Booking.holds(entry: QueueEntry) =
        token == entry.token && salon.name == entry.salon
}

private val appointmentTimes = listOf(LocalTime.of(11, 30), LocalTime.of(16, 0))

/** Bookable times for [date]; null means "Next available", which only exists today. */
internal fun slotsFor(date: LocalDate, now: LocalDateTime): List<LocalTime?> {
    val today = now.toLocalDate()
    return when {
        date.isBefore(today) -> emptyList()
        date == today -> listOf<LocalTime?>(null) + appointmentTimes.filter { it.isAfter(now.toLocalTime()) }
        else -> appointmentTimes
    }
}

private val timeFormat = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
private val dateFormat = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

internal fun timeLabel(time: LocalTime?): String = time?.format(timeFormat) ?: "Next available"

internal fun dateLabel(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> date.format(dateFormat)
}

internal fun waitLabel(minutes: Int?): String = when (minutes) {
    null -> "No barber available"
    0 -> "No wait"
    else -> "~$minutes min wait"
}

internal fun tokenLabel(token: Int): String = "#%02d".format(Locale.ENGLISH, token)

internal fun rupees(amount: Int): String = "₹%,d".format(Locale.ENGLISH, amount)
