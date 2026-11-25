package com.sabil.qnow.ui.customer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.sabil.qnow.domain.model.Business
import com.sabil.qnow.domain.model.BusinessCategory
import com.sabil.qnow.domain.model.GeoLocation
import com.sabil.qnow.domain.model.MyTokenRef
import com.sabil.qnow.domain.model.Promo
import com.sabil.qnow.domain.model.QueueToken
import com.sabil.qnow.domain.model.Review
import com.sabil.qnow.domain.model.Service
import com.sabil.qnow.domain.queue.LiveBusiness
import com.sabil.qnow.domain.queue.ObserveLiveBusiness
import com.sabil.qnow.domain.queue.WaitEstimator
import com.sabil.qnow.domain.queue.clock
import com.sabil.qnow.domain.repository.AlreadyInQueueException
import com.sabil.qnow.domain.repository.BusinessNotOpenException
import com.sabil.qnow.domain.repository.BusinessRepository
import com.sabil.qnow.domain.repository.FavouritesRepository
import com.sabil.qnow.domain.repository.LocationRepository
import com.sabil.qnow.domain.repository.PromoRepository
import com.sabil.qnow.domain.repository.QueueRepository
import com.sabil.qnow.domain.repository.ReviewRepository
import com.sabil.qnow.domain.repository.UserRepository
import com.sabil.qnow.data.messaging.TokenTracker
import com.sabil.qnow.domain.model.TokenStatus
import com.sabil.qnow.ui.navigation.BusinessRoute
import com.sabil.qnow.ui.navigation.ReviewRoute
import com.sabil.qnow.ui.navigation.TokenRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Straight-line distance between two points, in km. */
internal fun distanceKm(a: GeoLocation, b: GeoLocation): Double {
    val r = 6371.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLng = Math.toRadians(b.lng - a.lng)
    val h = Math.sin(dLat / 2).let { it * it } +
        Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.sin(dLng / 2).let { it * it }
    return 2 * r * Math.asin(Math.sqrt(h))
}

/** Short, non-personal error code for support, e.g. "PERMISSION_DENIED". */
internal fun errorCode(e: Throwable): String {
    val root = generateSequence(e) { it.cause }.firstOrNull { it is com.google.firebase.firestore.FirebaseFirestoreException } ?: e
    return (root as? com.google.firebase.firestore.FirebaseFirestoreException)?.code?.name ?: root.javaClass.simpleName
}

private fun <T> Flow<T>.state(vm: ViewModel, initial: T): StateFlow<T> =
    stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

/** Combines any number of flows; an empty list emits an empty list (unlike [combine]). */
private inline fun <reified T> combineAll(flows: List<Flow<T>>): Flow<List<T>> =
    if (flows.isEmpty()) flowOf(emptyList()) else combine(flows) { it.toList() }

// ---------------------------------------------------------------- Home

data class HomeUiState(
    val loading: Boolean = true,
    val promos: List<Promo> = emptyList(),
    val favourites: List<LiveBusiness> = emptyList(),
    val activeToken: ActiveToken? = null,
    /** Shops near the customer, closest first (favourites are listed separately above). */
    val nearby: List<NearbyShop> = emptyList(),
    /** False until location permission is granted, so the UI can ask for it. */
    val hasLocation: Boolean = true,
)

data class NearbyShop(val live: LiveBusiness, val distanceKm: Double, val sponsored: Boolean = false)

data class ActiveToken(val ref: MyTokenRef, val token: QueueToken) {
    /** Still waiting/called, but from an earlier day: the shop closed without serving it. */
    val expired: Boolean get() = token.status.isActive && token.dayKey < todayKey()
    /** Truly live right now (today, not finished). */
    val live: Boolean get() = token.status.isActive && !expired
}

/** Whole days since 1970 in this phone's time zone (same idea as the shop's day counter). */
fun todayKey(now: Long = System.currentTimeMillis()): Long =
    (now + java.util.TimeZone.getDefault().getOffset(now)) / 86_400_000L

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val promos: PromoRepository,
    users: com.sabil.qnow.domain.repository.UserRepository,
    favourites: FavouritesRepository,
    businesses: BusinessRepository,
    queue: QueueRepository,
    observeLive: ObserveLiveBusiness,
    private val tracker: TokenTracker,
    auth: com.sabil.qnow.domain.repository.AuthRepository,
    private val location: LocationRepository,
) : ViewModel() {

    private val here = MutableStateFlow<GeoLocation?>(null)
    private val located = MutableStateFlow(true)

    /** Called once location permission is known; loads shops around the customer. */
    fun onLocationPermission(granted: Boolean) {
        viewModelScope.launch {
            val loc = if (granted) location.lastKnownLocation() else null
            located.value = loc != null
            if (loc != null) here.value = loc
        }
    }

    val account = auth.account()
    val isDemo = account?.signedInWith == "Demo mode"

    private val favouriteBusinesses: Flow<List<LiveBusiness>> = favourites.observeFavouriteIds()
        .flatMapLatest { ids ->
            combineAll(ids.sorted().map { id -> observeLive(id).catch { emit(null) } })
        }
        .map { list -> list.filterNotNull().sortedBy { it.business.name.lowercase() } }

    private val activeToken: Flow<ActiveToken?> = queue.observeMyTokens()
        .flatMapLatest { refs ->
            // Only today's few most recent refs can still be live.
            val recent = refs.take(5)
            combineAll(recent.map { ref -> queue.observeToken(ref.businessId, ref.tokenId).map { t -> t?.let { ActiveToken(ref, it) } } })
        }
        .map { list -> list.firstOrNull { it != null && it.live } }
        .onEach { a -> a?.let { tracker.track(it.ref.businessId, it.ref.tokenId) } }
        .catch { emit(null) }

    /** The 10 closest shops, each with its live queue. */
    private val nearby: Flow<List<NearbyShop>> = here.filterNotNull()
        .flatMapLatest { center ->
            businesses.observeNearby(center, radiusKm = 10.0)
                .map { list ->
                    val sorted = list.mapNotNull { b -> b.location?.let { b to distanceKm(center, it) } }.sortedBy { it.second }
                    // The 10 closest, plus up to 3 sponsored shops a little further away.
                    sorted.take(10) + sorted.drop(10).filter { it.first.sponsorship != null }.take(3)
                }
                .distinctUntilChangedBy { pairs -> pairs.map { it.first.id } }
                .flatMapLatest { pairs ->
                    combineAll(pairs.map { (b, d) -> observeLive.live(b).map { live -> live?.let { NearbyShop(it, d) } }.catch { emit(null) } })
                }
                .map { it.filterNotNull().sortedBy { s -> s.distanceKm } }
        }
        .onStart { emit(emptyList()) }
        .catch { emit(emptyList()) }

    private val places = combine(nearby, located) { n, l -> n to l }

    /** The customer's own gender from their profile. Used only on this phone to pick ads. */
    private val gender = users.observeProfile().map { it.gender }.catch { emit(null) }.onStart { emit(null) }

    val state: StateFlow<HomeUiState> = combine(
        promos.observePromos(),
        favouriteBusinesses.catch { emit(emptyList()) },
        activeToken,
        places,
        gender,
    ) { p, f, a, (n, l), g ->
        val favIds = f.map { it.business.id }.toSet()
        val now = System.currentTimeMillis()
        HomeUiState(
            loading = false,
            promos = p.filter { it.showsTo(g, here.value, now) },
            favourites = f, activeToken = a,
            // Sponsored shops (for this customer) first, then by distance.
            nearby = n.filterNot { it.live.business.id in favIds }
                .map { it.copy(sponsored = it.live.business.sponsorship?.showsTo(g, now) == true) }
                .sortedWith(compareByDescending<NearbyShop> { it.sponsored }.thenBy { it.distanceKm }),
            hasLocation = l,
        )
    }
        .state(this, HomeUiState())

    private val seen = mutableSetOf<String>()

    /** Counts a view once per visit to the home screen (not on every auto-scroll). */
    fun promoSeen(p: Promo) {
        if (p.id.startsWith("builtin-") || !seen.add(p.id)) return
        viewModelScope.launch { runCatching { promos.recordView(p.id) } }
    }

    fun promoTapped(p: Promo) {
        if (p.id.startsWith("builtin-")) return
        viewModelScope.launch { runCatching { promos.recordTap(p.id) } }
    }
}

// ---------------------------------------------------------------- Map

data class MapUiState(
    val center: GeoLocation? = null,
    val hasLocation: Boolean = false,
    val businesses: List<Business> = emptyList(),
    val selected: LiveBusiness? = null,
    val query: String = "",
    val searching: Boolean = false,
    val message: String? = null,
    val category: BusinessCategory? = null,
    /** Bumped whenever the camera should move, even back to the same spot. */
    val cameraKey: Int = 0,
    /** Real shops from public map data that aren't on QNow yet (not bookable). */
    val outside: List<com.sabil.qnow.data.places.OutsideShop> = emptyList(),
    val selectedOutside: com.sabil.qnow.data.places.OutsideShop? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val location: LocationRepository,
    businesses: BusinessRepository,
    observeLive: ObserveLiveBusiness,
    places: com.sabil.qnow.data.places.OsmPlacesRepository,
) : ViewModel() {

    private val center = MutableStateFlow<GeoLocation?>(null)
    private val outsideId = MutableStateFlow<String?>(null)
    private val selectedId = MutableStateFlow<String?>(null)
    private val ui = MutableStateFlow(MapUiState())

    private val nearby = center.flatMapLatest { c ->
        if (c == null) flowOf(emptyList()) else businesses.observeNearby(c, radiusKm = 8.0).catch { emit(emptyList()) }
    }
    private val selected = selectedId.flatMapLatest { id -> if (id == null) flowOf(null) else observeLive(id).catch { emit(null) } }

    private val outside = center.filterNotNull().mapLatest { c -> places.nearby(c) }.onStart { emit(emptyList()) }

    private val outsidePicked = combine(outside, outsideId) { list, id -> list to list.firstOrNull { it.id == id } }

    val state: StateFlow<MapUiState> = combine(ui, nearby, selected, outsidePicked) { u, list, sel, (out, outSel) ->
        val onQNow = list.mapNotNull { it.location }
        u.copy(
            businesses = list.filter { u.category == null || it.category == u.category },
            selected = sel,
            // Hide public listings that sit right on top of a shop that already joined QNow.
            outside = out.filter { o -> onQNow.none { distanceKm(it, o.location) < 0.03 } }
                .filter { u.category == null || it.kind.startsWith(u.category.label.take(4), ignoreCase = true) },
            selectedOutside = outSel,
        )
    }.state(this, MapUiState())

    /** Default view if location isn't available: centre of India, zoomed out. */
    private val fallback = GeoLocation(20.5937, 78.9629)

    fun onLocationPermission(granted: Boolean) {
        viewModelScope.launch {
            val here = if (granted) location.lastKnownLocation() else null
            ui.update { it.copy(center = here ?: fallback, hasLocation = here != null, cameraKey = it.cameraKey + 1) }
            center.value = here ?: fallback
        }
    }

    fun onCameraSettled(target: GeoLocation) {
        val current = center.value
        // Re-query only after a meaningful move, to keep listeners stable while panning.
        if (current == null || distanceKm(current, target) > 2.0) center.value = target
    }

    fun onQuery(q: String) = ui.update { it.copy(query = q, message = null) }

    fun search() {
        val q = ui.value.query.trim()
        if (q.isEmpty()) return
        ui.update { it.copy(searching = true, message = null) }
        viewModelScope.launch {
            val found = location.geocode(q)
            if (found == null) {
                ui.update { it.copy(searching = false, message = "Couldn't find \"$q\". Try a nearby area or city.") }
            } else {
                ui.update { it.copy(searching = false, center = found, cameraKey = it.cameraKey + 1) }
                center.value = found
            }
        }
    }

    fun recenter() = onLocationPermission(true)
    fun select(id: String?) { selectedId.value = id; outsideId.value = null }
    fun selectOutside(id: String) { outsideId.value = id; selectedId.value = null }
    fun filter(category: BusinessCategory?) = ui.update { it.copy(category = category) }
    fun consumeMessage() = ui.update { it.copy(message = null) }

    private fun distanceKm(a: GeoLocation, b: GeoLocation): Double {
        val r = 6371.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLng = Math.toRadians(b.lng - a.lng)
        val h = Math.sin(dLat / 2).let { it * it } +
            Math.cos(Math.toRadians(a.lat)) * Math.cos(Math.toRadians(b.lat)) * Math.sin(dLng / 2).let { it * it }
        return 2 * r * Math.asin(Math.sqrt(h))
    }
}

// ---------------------------------------------------------------- Business detail

data class DetailUiState(
    val loading: Boolean = true,
    val live: LiveBusiness? = null,
    val favourite: Boolean = false,
    val reviews: List<Review> = emptyList(),
    /** Services ticked for this visit (one or more). */
    val selectedServices: List<Service> = emptyList(),
    val booking: Boolean = false,
    /** Id of this customer's live token here, if they already hold one. */
    val myToken: String? = null,
    val photos: List<com.sabil.qnow.domain.model.Photo> = emptyList(),
    /** Me + family; the token is booked for [person]. */
    val people: List<com.sabil.qnow.domain.model.FamilyMember> = emptyList(),
    val person: com.sabil.qnow.domain.model.FamilyMember? = null,
    /** Services that suit [person] (all of them if none are marked). */
    val services: List<Service> = emptyList(),
) {
    val totalMinutes: Int get() = selectedServices.sumOf { it.durationMinutes }
    val totalPrice: Double get() = selectedServices.sumOf { it.price }

    /** All ticked services as one booking: names joined, times and prices added up. */
    val bookedService: Service?
        get() = when (selectedServices.size) {
            0 -> null
            1 -> selectedServices.first()
            else -> Service(
                id = selectedServices.joinToString("+") { it.id }.let { if (it.length <= 64) it else selectedServices.first().id.take(64) },
                name = selectedServices.joinToString(" + ") { it.name }.take(80),
                price = totalPrice,
                durationMinutes = totalMinutes.coerceIn(1, 600),
            )
        }
}

sealed interface DetailEvent {
    data class Booked(val route: TokenRoute) : DetailEvent
    data class Error(val message: String) : DetailEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class BusinessDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    observeLive: ObserveLiveBusiness,
    private val favourites: FavouritesRepository,
    reviews: ReviewRepository,
    private val queue: QueueRepository,
    private val businesses: BusinessRepository,
    users: UserRepository,
) : ViewModel() {
    val businessId = savedState.toRoute<BusinessRoute>().businessId
    private val personId = MutableStateFlow(com.sabil.qnow.domain.model.CustomerProfile.SELF_ID)
    private val people = users.observeProfile().map { it.bookable() }.catch { emit(com.sabil.qnow.domain.model.CustomerProfile(null, null, emptyList()).bookable()) }

    /** Ticked service ids. Nothing is ticked until the customer chooses. */
    private val selected = MutableStateFlow<Set<String>>(emptySet())
    private val booking = MutableStateFlow(false)
    private val _events = Channel<DetailEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val myToken: Flow<String?> = queue.observeMyTokens()
        .flatMapLatest { refs ->
            val here = refs.filter { it.businessId == businessId }.take(3)
            combineAll(here.map { ref -> queue.observeToken(businessId, ref.tokenId) })
        }
        .map { tokens -> tokens.firstOrNull { it != null && it.status.isActive }?.id }
        .catch { emit(null) }

    private val live = observeLive(businessId).shareIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1)

    /** Full photos are loaded once per change of the photo list, not on every queue update. */
    private val photos = live.map { it?.business }.filterNotNull()
        .distinctUntilChangedBy { it.photoIds }
        .mapLatest { biz -> runCatching { businesses.photos(biz) }.getOrDefault(emptyList()) }
        .onStart { emit(emptyList()) }

    private val who = combine(people, personId) { list, id -> list to (list.firstOrNull { it.id == id } ?: list.first()) }
    private val extras = combine(selected, booking, myToken, photos, who) { s, b, m, p, w -> Extras(s, b, m, p, w.first, w.second) }
    private data class Extras(
        val selected: Set<String>, val booking: Boolean, val myToken: String?, val photos: List<com.sabil.qnow.domain.model.Photo>,
        val people: List<com.sabil.qnow.domain.model.FamilyMember>, val person: com.sabil.qnow.domain.model.FamilyMember,
    )

    val state: StateFlow<DetailUiState> = combine(
        live,
        favourites.observeFavouriteIds().map { businessId in it }.catch { emit(false) },
        reviews.observeReviews(businessId).catch { emit(emptyList()) },
        extras,
    ) { live, fav, revs, x ->
        val all = live?.business?.services.orEmpty()
        val fitting = all.filter { x.person.fits(it) }.ifEmpty { all }
        DetailUiState(
            people = x.people,
            person = x.person,
            services = fitting,
            myToken = x.myToken,
            photos = x.photos,
            loading = false,
            live = live,
            favourite = fav,
            reviews = revs,
            selectedServices = fitting.filter { it.id in x.selected },
            booking = x.booking,
        )
    }.state(this, DetailUiState())

    /** Tick or untick a service. */
    fun toggleService(s: Service) {
        selected.value = selected.value.let { if (s.id in it) it - s.id else it + s.id }
    }
    fun selectPerson(id: String) { personId.value = id }

    fun toggleFavourite() {
        val now = state.value.favourite
        viewModelScope.launch { runCatching { favourites.setFavourite(businessId, !now) } }
    }

    fun book() {
        val biz = state.value.live?.business ?: return
        booking.value = true
        viewModelScope.launch {
            val p = state.value.person
            val forName = p?.takeIf { it.id != com.sabil.qnow.domain.model.CustomerProfile.SELF_ID }?.let { if (it.isChild) "${it.name} (child)" else it.name }
            runCatching { queue.bookToken(biz, state.value.bookedService, forName) }
                .onSuccess { _events.send(DetailEvent.Booked(TokenRoute(it.businessId, it.tokenId))) }
                .onFailure { e ->
                    _events.send(DetailEvent.Error(when (e) {
                        is AlreadyInQueueException -> "You already have a live token here."
                        is BusinessNotOpenException -> "${biz.name} isn't taking tokens right now."
                        else -> "Couldn't book right now. Check your connection and try again. (Error: ${errorCode(e)})"
                    }))
                }
            booking.value = false
        }
    }
}

// ---------------------------------------------------------------- My token

data class TokenUiState(
    val loading: Boolean = true,
    val token: QueueToken? = null,
    val live: LiveBusiness? = null,
    val estimate: WaitEstimator.Estimate? = null,
    val reviewed: Boolean = false,
    val cancelling: Boolean = false,
    val nowMillis: Long = System.currentTimeMillis(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TokenViewModel @Inject constructor(
    savedState: SavedStateHandle,
    observeLive: ObserveLiveBusiness,
    private val queue: QueueRepository,
    private val tracker: TokenTracker,
) : ViewModel() {
    val route = savedState.toRoute<TokenRoute>()
    private val cancelling = MutableStateFlow(false)

    private val reviewed = queue.observeMyTokens()
        .map { refs -> refs.firstOrNull { it.businessId == route.businessId && it.tokenId == route.tokenId }?.reviewed ?: false }
        .catch { emit(false) }

    val state: StateFlow<TokenUiState> = combine(
        queue.observeToken(route.businessId, route.tokenId),
        observeLive(route.businessId),
        reviewed,
        cancelling,
        clock(15_000),
    ) { token, live, rev, cancel, now ->
        TokenUiState(
            loading = false,
            token = token,
            live = live,
            estimate = token?.let { live?.summary?.estimates?.get(it.id) },
            reviewed = rev,
            cancelling = cancel,
            nowMillis = now,
        )
    }
        // Keep following the token in the background (alerts, live notification) while it's active.
        .onEach { s -> if (s.token?.status?.isActive == true) tracker.track(route.businessId, route.tokenId) }
        .state(this, TokenUiState())

    fun cancel() {
        cancelling.value = true
        viewModelScope.launch {
            runCatching { queue.cancelToken(route.businessId, route.tokenId) }
            cancelling.value = false
        }
    }

    fun reviewRoute(): ReviewRoute? {
        val s = state.value
        return ReviewRoute(route.businessId, route.tokenId, s.live?.business?.name ?: return null)
    }
}

// ---------------------------------------------------------------- My tokens list

/** A live token with where it stands in the queue right now. */
data class LiveTokenItem(val item: ActiveToken, val estimate: WaitEstimator.Estimate?, val nowServing: Int?)

data class MyTokensUiState(
    val loading: Boolean = true,
    val live: List<LiveTokenItem> = emptyList(),
    /** Finished, cancelled, skipped and expired tokens, newest first. */
    val past: List<ActiveToken> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyTokensViewModel @Inject constructor(queue: QueueRepository, observeLive: ObserveLiveBusiness) : ViewModel() {
    private val all: Flow<List<ActiveToken>> = queue.observeMyTokens()
        .flatMapLatest { refs ->
            combineAll(refs.take(40).map { ref ->
                queue.observeToken(ref.businessId, ref.tokenId).map { t -> t?.let { ActiveToken(ref, it) } }.catch { emit(null) }
            })
        }
        .map { list -> list.filterNotNull().sortedByDescending { it.ref.createdAtMillis } }

    val state: StateFlow<MyTokensUiState> = all
        .flatMapLatest { items ->
            val live = items.filter { it.live }
            // Only live tokens need the shop's live queue (position and wait).
            combineAll(live.map { a ->
                observeLive(a.ref.businessId).map { lb ->
                    LiveTokenItem(
                        a, lb?.summary?.estimates?.get(a.token.id),
                        lb?.summary?.servingNumbers?.minOrNull(),
                    )
                }.catch { emit(LiveTokenItem(a, null, null)) }
            }).map { liveItems -> MyTokensUiState(false, liveItems, items.filterNot { it.live }) }
        }
        .catch { emit(MyTokensUiState(false)) }
        .state(this, MyTokensUiState())
}

// ---------------------------------------------------------------- Review

data class ReviewUiState(val rating: Int = 0, val comment: String = "", val sending: Boolean = false, val error: String? = null, val done: Boolean = false)

@HiltViewModel
class ReviewViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val reviews: ReviewRepository,
) : ViewModel() {
    val route = savedState.toRoute<ReviewRoute>()
    private val _state = MutableStateFlow(ReviewUiState())
    val state = _state.asStateFlow()

    fun rate(stars: Int) = _state.update { it.copy(rating = stars, error = null) }
    fun comment(text: String) = _state.update { it.copy(comment = text.take(500)) }

    fun submit() {
        val s = _state.value
        if (s.rating == 0) return
        _state.update { it.copy(sending = true, error = null) }
        viewModelScope.launch {
            runCatching { reviews.submitReview(route.businessId, route.tokenId, s.rating, s.comment) }
                .onSuccess { _state.update { it.copy(sending = false, done = true) } }
                .onFailure {
                    _state.update {
                        it.copy(sending = false, error = "Couldn't send your review. You can only review a business once, after a completed visit.")
                    }
                }
        }
    }
}
