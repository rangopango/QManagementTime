package com.sabil.queuetimemanagement

import org.junit.Assert.*
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class SalonViewModelTest {

    private class TestClock(var now: LocalDateTime) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = now.toInstant(ZoneOffset.UTC)
    }

    private val clock = TestClock(LocalDateTime.of(2026, 11, 22, 9, 0))
    private val vm = SalonViewModel(clock)
    private val tomorrow get() = vm.today.plusDays(1)

    private fun book(salon: Salon = salons[0]) {
        vm.openSalon(salon)
        vm.openBooking()
        vm.confirmBooking()
    }

    @Test
    fun queueNumbersComeFromTheBookedBarbersLine() {
        book()
        assertEquals(12, vm.queue.booking?.token)
        assertEquals(3, vm.queue.waitingAheadOfBooking())
        assertEquals(30 + 15 + 45 + 30, vm.queue.bookingWait())

        vm.completeCurrent()
        assertEquals(2, vm.queue.waitingAheadOfBooking())
        assertEquals(15 + 45 + 30, vm.queue.bookingWait())
    }

    @Test
    fun otherSalonsHaveTheirOwnTokensAndWaits() {
        book(salons[2])
        assertEquals(9, vm.queue.booking?.token)
        assertEquals(45, vm.queue.bookingWait())
    }

    @Test
    fun completeFinishesTheCurrentCustomerAndCallsTheNext() {
        vm.completeCurrent()
        val line = vm.queue.queueFor(demoBarber)
        assertEquals(listOf(9, 10, 11), line.map { it.token })
        assertEquals(EntryStatus.IN_SERVICE, line[0].status)
        assertEquals(BarberStats(9, 1240 + 120), vm.queue.statsFor(demoBarber))
    }

    @Test
    fun completingDuringABreakDoesNotCallTheNextCustomer() {
        vm.toggleBreak()
        vm.completeCurrent()
        vm.startNext()
        assertTrue(vm.queue.queueFor(demoBarber).none { it.status == EntryStatus.IN_SERVICE })

        vm.toggleBreak()
        vm.startNext()
        assertEquals(EntryStatus.IN_SERVICE, vm.queue.queueFor(demoBarber).first().status)
    }

    @Test
    fun emptyQueueControlsDoNothing() {
        repeat(10) { vm.completeCurrent() }
        val before = vm.queue
        vm.completeCurrent(); vm.startNext(); vm.removeLastWaiting()
        assertEquals(before, vm.queue)
        assertEquals(12, before.statsFor(demoBarber).completed)
    }

    @Test
    fun bookingEndsWhenBarberCompletesOrRemovesIt() {
        book()
        repeat(5) { vm.completeCurrent() }
        assertNull(vm.queue.booking)

        book()
        assertNotNull(vm.queue.booking)
        vm.removeLastWaiting()
        assertNull(vm.queue.booking)
    }

    @Test
    fun sameTokenNumberAtAnotherSalonDoesNotEndTheBooking() {
        book(salons[2]) // token 9 here; Arjun's line at salon 0 also has a 9
        repeat(2) { vm.completeCurrent() }
        assertEquals(9, vm.queue.booking?.token)
    }

    @Test
    fun replacingABookingLeavesNoOldEntryBehind() {
        book()
        book(salons[1])
        assertEquals(salons[1], vm.queue.booking?.salon)
        assertEquals(listOf(8, 9, 10, 11), vm.queue.queueFor(demoBarber).map { it.token })
    }

    @Test
    fun cancellingLeavesTheQueue() {
        book()
        vm.cancelBooking()
        assertEquals(demoQueueState().entries, vm.queue.entries)
        assertNull(vm.queue.booking)
        assertEquals(AppPage.CUSTOMER_HOME, vm.page)
    }

    @Test
    fun appointmentsDoNotJoinTodaysQueue() {
        vm.openSalon(salons[0])
        vm.openBooking()
        vm.selectDate(tomorrow)
        assertEquals(LocalTime.of(11, 30), vm.selectedTime)
        vm.confirmBooking()
        assertEquals(tomorrow, vm.queue.booking?.date)
        assertNull(vm.queue.booking?.token)
        assertTrue(vm.queue.bookingQueue().isEmpty())
        assertEquals(demoQueueState().entries, vm.queue.entries)
    }

    @Test
    fun pastTimesAreNotOffered() {
        fun slotsAt(hour: Int, minute: Int) = slotsFor(vm.today, vm.today.atTime(hour, minute))
        assertEquals(listOf(null, LocalTime.of(11, 30), LocalTime.of(16, 0)), slotsAt(9, 0))
        assertEquals(listOf(null, LocalTime.of(16, 0)), slotsAt(11, 30))
        assertEquals(listOf<LocalTime?>(null), slotsAt(20, 0))
    }

    @Test
    fun slotThatPassesWhileTheScreenIsOpenIsRejected() {
        vm.openSalon(salons[0])
        vm.openBooking()
        vm.selectTime(LocalTime.of(11, 30))
        clock.now = clock.now.withHour(12)
        vm.confirmBooking()
        assertNull(vm.queue.booking)
        assertNotNull(vm.bookingError)
        assertNull(vm.selectedTime)

        vm.confirmBooking()
        assertNotNull(vm.queue.booking)
    }

    @Test
    fun dateThatPassesWhileTheScreenIsOpenIsRejected() {
        vm.openSalon(salons[0])
        vm.openBooking()
        clock.now = clock.now.plusDays(1)
        vm.confirmBooking()
        assertNull(vm.queue.booking)
        assertEquals(vm.today, vm.selectedDate)
    }

    @Test
    fun selectionFollowsTheSalon() {
        vm.openSalon(salons[0])
        vm.selectBarber("Imran Hussain")
        vm.openSalon(salons[1])
        assertEquals("Bikash Saikia", vm.selectedBarber)
        assertTrue(vm.selectedService in salons[1].services)
    }

    @Test
    fun barberOnBreakCannotBeSelected() {
        vm.openSalon(salons[0])
        vm.selectBarber("Rahul Bora")
        assertEquals("Arjun Das", vm.selectedBarber)

        vm.toggleBreak()
        vm.openSalon(salons[0])
        assertEquals("Imran Hussain", vm.selectedBarber)
    }

    @Test
    fun backReturnsToWhereTheSalonWasOpened() {
        vm.openSalon(salons[0])
        vm.back()
        assertEquals(AppPage.CUSTOMER_HOME, vm.page)

        vm.openFromHome(AppPage.SALONS)
        vm.openSalon(salons[0])
        vm.openBooking()
        vm.back()
        assertEquals(AppPage.BARBER, vm.page)
        vm.back()
        assertEquals(AppPage.SALONS, vm.page)
        vm.back()
        assertEquals(AppPage.CUSTOMER_HOME, vm.page)
        assertFalse(vm.canGoBack)
    }
}
