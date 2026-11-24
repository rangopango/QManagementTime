package com.sabil.qnow.ui.business

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sabil.qnow.domain.model.Business
import com.sabil.qnow.domain.model.BusinessCategory
import com.sabil.qnow.domain.model.BusinessStatus
import com.sabil.qnow.domain.model.DayHours
import com.sabil.qnow.domain.model.GeoLocation
import com.sabil.qnow.domain.model.QueueToken
import com.sabil.qnow.domain.model.Service
import com.sabil.qnow.domain.model.TokenHolder
import com.sabil.qnow.domain.model.TokenStatus
import com.sabil.qnow.domain.queue.DayKey
import com.sabil.qnow.domain.queue.LiveBusiness
import com.sabil.qnow.domain.queue.ObserveLiveBusiness
import com.sabil.qnow.domain.repository.BusinessDraft
import com.sabil.qnow.domain.repository.BusinessRepository
import com.sabil.qnow.domain.repository.PhotoDraft
import com.sabil.qnow.domain.repository.LocationRepository
import com.sabil.qnow.domain.repository.QueueRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** Each photo is a Firestore read for every customer who opens the shop, so keep it small. */
const val MAX_PHOTOS = 6

/** Whether the signed-in business account has registered its shop yet. */
sealed interface MyBusinessState {
    data object Loading : MyBusinessState
    data object NotRegistered : MyBusinessState
    data class Registered(val business: Business) : MyBusinessState
}

@HiltViewModel
class MyBusinessViewModel @Inject constructor(businesses: BusinessRepository) : ViewModel() {
    val state: StateFlow<MyBusinessState> = businesses.observeMyBusiness()
        .map { if (it == null) MyBusinessState.NotRegistered else MyBusinessState.Registered(it) }
        .catch { emit(MyBusinessState.NotRegistered) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MyBusinessState.Loading)
}

// ---------------------------------------------------------------- Registration / edit form

data class ServiceDraft(val id: String = UUID.randomUUID().toString(), val name: String = "", val price: String = "", val minutes: String = "20") {
    fun toService(): Service? {
        val p = price.toDoubleOrNull() ?: return null
        val m = minutes.toIntOrNull()?.takeIf { it in 1..600 } ?: return null
        if (name.isBlank()) return null
        return Service(id, name.trim().take(80), p, m)
    }
}

enum class FormStep(val title: String) {
    OWNER("About you"),
    SHOP("Your shop"),
    LOCATION("Location"),
    SERVICES("Services & rates"),
    HOURS("Opening hours"),
    CAPACITY("Chairs & photos"),
}

data class BusinessFormState(
    val loading: Boolean = true,
    val editing: Boolean = false,
    val step: FormStep = FormStep.OWNER,
    val ownerName: String = "",
    val phone: String = "",
    val showPhonePublicly: Boolean = false,
    val name: String = "",
    val category: BusinessCategory = BusinessCategory.BARBER,
    val address: String = "",
    val location: GeoLocation? = null,
    val services: List<ServiceDraft> = listOf(ServiceDraft(name = "Haircut", price = "", minutes = "20")),
    val hours: List<DayHours> = (1..7).map { DayHours(it, open = it != 7) },
    val chairs: Int = 1,
    val graceMinutes: Int = 3,
    /** Set from the country of the map pin; INR until a pin is dropped. */
    val currencyCode: String = "INR",
    val existingPhotos: List<com.sabil.qnow.domain.model.Photo> = emptyList(),
    val newPhotos: List<Uri> = emptyList(),
    val saving: Boolean = false,
    val error: String? = null,
) {
    val photoCount get() = existingPhotos.size + newPhotos.size

    fun stepError(s: FormStep = step): String? = when (s) {
        FormStep.OWNER -> when {
            ownerName.isBlank() -> "Enter your name"
            phone.count(Char::isDigit) < 6 -> "Enter a valid phone number"
            else -> null
        }
        FormStep.SHOP -> if (name.isBlank()) "Enter your shop's name" else null
        FormStep.LOCATION -> when {
            location == null -> "Tap the map to drop a pin on your shop"
            address.isBlank() -> "Enter your shop's address"
            else -> null
        }
        FormStep.SERVICES -> when {
            services.isEmpty() -> "Add at least one service"
            services.any { it.toService() == null } -> "Each service needs a name, a price and a time in minutes"
            else -> null
        }
        FormStep.HOURS -> when {
            hours.none { it.open } -> "Open on at least one day"
            hours.any { it.open && it.closesAt <= it.opensAt } -> "Closing time must be after opening time"
            else -> null
        }
        FormStep.CAPACITY -> null
    }
}

@HiltViewModel
class BusinessFormViewModel @Inject constructor(
    private val businesses: BusinessRepository,
    private val location: LocationRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(BusinessFormState())
    val state = _state.asStateFlow()
    private val _saved = Channel<Unit>(Channel.BUFFERED)
    val saved = _saved.receiveAsFlow()

    init {
        viewModelScope.launch {
            val existing = runCatching { businesses.observeMyBusiness().firstOrNull() }.getOrNull()
            val private = runCatching { businesses.myPrivateDetails() }.getOrNull()
            _state.update { s ->
                if (existing == null) s.copy(loading = false)
                else s.copy(
                    loading = false,
                    editing = true,
                    ownerName = private?.ownerName.orEmpty(),
                    phone = private?.phone.orEmpty(),
                    showPhonePublicly = private?.showPhonePublicly ?: false,
                    name = existing.name,
                    category = existing.category,
                    address = existing.address,
                    location = existing.location,
                    services = existing.services.map { ServiceDraft(it.id, it.name, it.price.toCleanString(), it.durationMinutes.toString()) },
                    hours = if (existing.hours.isEmpty()) s.hours else existing.hours.sortedBy { it.day },
                    chairs = existing.chairs,
                    graceMinutes = existing.graceMinutes,
                    currencyCode = existing.currencyCode,
                    existingPhotos = runCatching { businesses.photos(existing) }.getOrDefault(emptyList()),
                )
            }
            if (existing == null) {
                location.lastKnownLocation()?.let { here -> if (_state.value.location == null) setLocation(here) }
            }
        }
    }

    private fun Double.toCleanString() = if (this % 1.0 == 0.0) toLong().toString() else toString()

    fun edit(block: BusinessFormState.() -> BusinessFormState) = _state.update { it.block().copy(error = null) }

    fun setLocation(loc: GeoLocation) {
        _state.update { it.copy(location = loc, error = null) }
        viewModelScope.launch {
            val currency = com.sabil.qnow.ui.components.Money.currencyForCountry(location.countryCode(loc))
            _state.update { it.copy(currencyCode = currency) }
            val address = location.reverseGeocode(loc) ?: return@launch
            _state.update { if (it.address.isBlank()) it.copy(address = address) else it }
        }
    }

    fun useMyLocation() {
        viewModelScope.launch { location.lastKnownLocation()?.let(::setLocation) }
    }

    fun addService() = _state.update { it.copy(services = it.services + ServiceDraft()) }
    fun updateService(d: ServiceDraft) = _state.update { s -> s.copy(services = s.services.map { if (it.id == d.id) d else it }, error = null) }
    fun removeService(id: String) = _state.update { s -> s.copy(services = s.services.filterNot { it.id == id }) }
    fun updateDay(d: DayHours) = _state.update { s -> s.copy(hours = s.hours.map { if (it.day == d.day) d else it }, error = null) }
    fun addPhotos(uris: List<Uri>) = _state.update { s -> s.copy(newPhotos = (s.newPhotos + uris).distinct().take(MAX_PHOTOS - s.existingPhotos.size)) }
    fun removeNewPhoto(uri: Uri) = _state.update { s -> s.copy(newPhotos = s.newPhotos - uri) }
    fun removeExistingPhoto(id: String) = _state.update { s -> s.copy(existingPhotos = s.existingPhotos.filterNot { it.id == id }) }

    fun goTo(step: FormStep) = _state.update { it.copy(step = step, error = null) }

    fun back(): Boolean {
        val s = _state.value
        if (s.step.ordinal == 0) return false
        goTo(FormStep.entries[s.step.ordinal - 1])
        return true
    }

    fun next() {
        val s = _state.value
        s.stepError()?.let { e -> _state.update { it.copy(error = e) }; return }
        if (s.step.ordinal < FormStep.entries.lastIndex) goTo(FormStep.entries[s.step.ordinal + 1]) else save()
    }

    fun save() {
        val s = _state.value
        FormStep.entries.firstNotNullOfOrNull { step -> s.stepError(step)?.let { step to it } }?.let { (step, e) ->
            _state.update { it.copy(step = step, error = e) }
            return
        }
        _state.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                businesses.saveBusiness(
                    BusinessDraft(
                        ownerName = s.ownerName, phone = s.phone, showPhonePublicly = s.showPhonePublicly,
                        name = s.name, category = s.category, address = s.address, location = s.location!!,
                        photos = s.existingPhotos.map { PhotoDraft.Saved(it) } + s.newPhotos.map { PhotoDraft.New(it) },
                        services = s.services.mapNotNull { it.toService() }, hours = s.hours,
                        chairs = s.chairs, graceMinutes = s.graceMinutes, currencyCode = s.currencyCode,
                    ),
                )
            }.onSuccess {
                _state.update { it.copy(saving = false, newPhotos = emptyList()) }
                _saved.send(Unit)
            }.onFailure {
                _state.update { it.copy(saving = false, error = "Couldn't save. Check your connection and try again.") }
            }
        }
    }
}

// ---------------------------------------------------------------- Live dashboard

data class DashboardState(
    val loading: Boolean = true,
    val live: LiveBusiness? = null,
    val holders: Map<String, TokenHolder> = emptyMap(),
    val busy: Set<String> = emptySet(),
    val message: String? = null,
) {
    val inChair: List<QueueToken> get() = live?.tokens.orEmpty().filter { it.status == TokenStatus.SERVING || it.status == TokenStatus.GRACE }
    val waiting: List<QueueToken> get() = live?.tokens.orEmpty().filter { it.status == TokenStatus.WAITING }
    val freeChairs: Int get() = ((live?.business?.activeChairs ?: 1) - inChair.size).coerceAtLeast(0)
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val businesses: BusinessRepository,
    private val queue: QueueRepository,
    observeLive: ObserveLiveBusiness,
) : ViewModel() {

    private val busy = MutableStateFlow<Set<String>>(emptySet())
    private val message = MutableStateFlow<String?>(null)

    private val live = businesses.observeMyBusiness().flatMapLatest { observeLive.live(it) }

    private val holders = businesses.observeMyBusiness()
        .map { biz -> biz?.let { it.id to DayKey.of(System.currentTimeMillis(), it.utcOffsetMinutes) } }
        .distinctUntilChanged()
        .flatMapLatest { key -> if (key == null) flowOf(emptyMap()) else queue.observeHolders(key.first, key.second) }
        .catch { emit(emptyMap()) }

    val state: StateFlow<DashboardState> = combine(live, holders, busy, message) { l, h, b, m ->
        DashboardState(loading = false, live = l, holders = h, busy = b, message = m)
    }
        .onEach(::skipExpiredGrace)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DashboardState())

    /** A called customer who doesn't show up within the grace period is skipped automatically. */
    private fun skipExpiredGrace(s: DashboardState) {
        val now = System.currentTimeMillis()
        s.inChair.filter { it.status == TokenStatus.GRACE && (it.graceEndsAtMillis ?: Long.MAX_VALUE) < now && it.id !in s.busy }
            .forEach { expired -> noShowNow(expired) }
    }

    private fun run(tokenId: String, action: suspend () -> Unit) {
        if (tokenId in busy.value) return
        busy.update { it + tokenId }
        viewModelScope.launch {
            runCatching { action() }.onFailure { message.value = "That didn't go through. Check your connection." }
            busy.update { it - tokenId }
        }
    }

    private fun biz() = state.value.live?.business

    /** Done with this customer, and the next one in line takes the chair. */
    fun doneAndNext(token: QueueToken) = run(token.id) {
        val b = biz() ?: return@run
        val next = if (b.status == BusinessStatus.OPEN || b.status == BusinessStatus.ON_BREAK) state.value.waiting.firstOrNull() else null
        queue.complete(b.id, token.id, next)
    }

    fun callNext() = run("call-next") {
        val b = biz() ?: return@run
        queue.callNext(b.id, state.value.waiting.take(state.value.freeChairs))
    }

    fun noShow(token: QueueToken) = run(token.id) { biz()?.let { queue.startGrace(it, token.id) } }

    fun arrived(token: QueueToken) = run(token.id) { biz()?.let { queue.markArrived(it.id, token.id) } }

    fun noShowNow(token: QueueToken) = run(token.id) {
        val b = biz() ?: return@run
        queue.markNoShow(b.id, token.id)
        state.value.waiting.firstOrNull()?.let { queue.callNext(b.id, listOf(it)) }
    }

    fun removeWaiting(token: QueueToken) = run(token.id) { biz()?.let { queue.markNoShow(it.id, token.id) } }

    fun addWalkIn(service: Service?, label: String) = run("walk-in") {
        val b = biz() ?: return@run
        val number = queue.addWalkIn(b, service, label)
        message.value = "Walk-in token #$number added"
    }

    fun setStatus(status: BusinessStatus) = run("status") { businesses.setStatus(status) }

    fun setActiveChairs(count: Int) = run("chairs") {
        val b = biz() ?: return@run
        businesses.setActiveChairs(count.coerceIn(1, b.chairs))
    }

    fun consumeMessage() { message.value = null }
}
