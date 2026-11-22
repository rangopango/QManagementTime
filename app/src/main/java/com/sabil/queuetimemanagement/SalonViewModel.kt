package com.sabil.queuetimemanagement

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.time.Clock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

internal enum class AppPage {
    CUSTOMER_HOME, SALONS, BARBER, BOOKING, QUEUE, PROFILE,
    BARBER_DASHBOARD, OWNER_DASHBOARD
}

/** Holds app state outside the activity so it survives rotation. */
internal class SalonViewModel(
    private val clock: Clock = Clock.systemDefaultZone()
) : ViewModel() {

    private var backStack by mutableStateOf(listOf(AppPage.CUSTOMER_HOME))
    val page: AppPage get() = backStack.last()
    val canGoBack: Boolean get() = backStack.size > 1

    var queue by mutableStateOf(demoQueueState())
        private set

    var selectedSalon by mutableStateOf(salons.first())
        private set
    /** Null when nobody at the selected salon is available. */
    var selectedBarber by mutableStateOf<String?>(null)
        private set
    var selectedService by mutableStateOf(salons.first().services.first())
        private set
    var selectedDate by mutableStateOf(LocalDate.now(clock))
        private set
    var selectedTime by mutableStateOf<LocalTime?>(null)
        private set
    var bookingError by mutableStateOf<String?>(null)
        private set

    val today: LocalDate get() = LocalDate.now(clock)
    val slots: List<LocalTime?> get() = slotsFor(selectedDate, LocalDateTime.now(clock))

    // Navigation

    fun open(page: AppPage) {
        backStack = backStack + page
    }

    /** Opens a page directly above home, dropping any other history. */
    fun openFromHome(page: AppPage) {
        backStack = listOf(AppPage.CUSTOMER_HOME, page).distinct()
    }

    fun back() {
        if (canGoBack) backStack = backStack.dropLast(1)
    }

    // Booking

    fun openSalon(salon: Salon) {
        val keepBarber = salon == selectedSalon && selectedBarber?.let { isAvailable(salon, it) } == true
        if (!keepBarber) {
            selectedBarber = salon.barbers.map { it.name }.firstOrNull { isAvailable(salon, it) }
        }
        if (selectedService !in salon.services) selectedService = salon.services.first()
        selectedSalon = salon
        open(AppPage.BARBER)
    }

    fun selectBarber(name: String) {
        if (isAvailable(selectedSalon, name)) selectedBarber = name
    }

    fun openBooking() {
        if (selectedBarber == null) return
        bookingError = null
        selectDate(if (selectedDate.isBefore(today)) today else selectedDate)
        open(AppPage.BOOKING)
    }

    fun selectService(service: Service) {
        selectedService = service
    }

    fun selectDate(date: LocalDate) {
        selectedDate = date
        bookingError = null
        dropStaleTime()
    }

    fun selectTime(time: LocalTime?) {
        selectedTime = time
        bookingError = null
    }

    fun confirmBooking() {
        val barber = selectedBarber ?: return
        // The screen may have been open long enough for the chosen slot to pass.
        if (selectedTime !in slots) {
            if (selectedDate.isBefore(today)) selectedDate = today
            dropStaleTime()
            bookingError = "That time has passed. Check the new time and confirm again."
            return
        }
        queue = queue.book(selectedSalon, barber, selectedService, selectedDate, selectedTime, today)
        openFromHome(AppPage.QUEUE)
    }

    fun cancelBooking() {
        queue = queue.cancelBooking()
        openFromHome(AppPage.CUSTOMER_HOME)
    }

    // Barber dashboard

    fun startNext() { queue = queue.startNext(demoBarber) }
    fun completeCurrent() { queue = queue.completeCurrent(demoBarber) }
    fun addWalkIn() { queue = queue.addWalkIn(demoBarber, demoSalon.services.first()) }
    fun removeLastWaiting() { queue = queue.removeLastWaiting(demoBarber) }
    fun toggleBreak() { queue = queue.toggleBreak(demoBarber) }

    private fun isAvailable(salon: Salon, barber: String) =
        queue.isAvailable(BarberKey(salon.name, barber))

    private fun dropStaleTime() {
        if (selectedTime !in slots) selectedTime = slots.firstOrNull()
    }
}
