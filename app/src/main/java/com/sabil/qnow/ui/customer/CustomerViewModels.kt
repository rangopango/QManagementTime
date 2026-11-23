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
)

data class ActiveToken(val ref: MyTokenRef, val token: QueueToken)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    promos: PromoRepository,
    favourites: FavouritesRepository,
    businesses: BusinessRepository,
    queue: QueueRepository,
    observeLive: ObserveLiveBusiness,
    private val tracker: TokenTracker,
) : ViewModel() {

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
        .map { list -> list.firstOrNull { it != null && it.token.status.isActive } }
        .onEach { a -> a?.let { tracker.track(it.ref.businessId, it.ref.tokenId) } }
        .catch { emit(null) }

    val state: StateFlow<HomeUiState> = combine(
        promos.observePromos(),
        favouriteBusinesses.catch { emit(emptyList()) },
        activeToken,
    ) { p, f, a -> HomeUiState(loading = false, promos = p, favourites = f, activeToken = a) }
        .state(this, HomeUiState())
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
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MapViewModel @Inject constructor(
    private val location: LocationRepository,
    businesses: BusinessRepository,
    observeLive: ObserveLiveBusiness,
) : ViewModel() {

    private val center = MutableStateFlow<GeoLocation?>(null)
    private val selectedId = MutableStateFlow<String?>(null)
    private val ui = MutableStateFlow(MapUiState())

    private val nearby = center.flatMapLatest { c ->
        if (c == null) flowOf(emptyList()) else businesses.observeNearby(c, radiusKm = 8.0).catch { emit(emptyList()) }
    }
    private val selected = selectedId.flatMapLatest { id -> if (id == null) flowOf(null) else observeLive(id).catch { emit(null) } }

    val state: StateFlow<MapUiState> = combine(ui, nearby, selected) { u, list, sel ->
        u.copy(
            businesses = list.filter { u.category == null || it.category == u.category },
            selected = sel,
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
    fun select(id: String?) { selectedId.value = id }
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
    val selectedService: Service? = null,
    val booking: Boolean = false,
    /** Id of this customer's live token here, if they already hold one. */
    val myToken: String? = null,
    val photos: List<com.sabil.qnow.domain.model.Photo> = emptyList(),
)

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
) : ViewModel() {
    val businessId = savedState.toRoute<BusinessRoute>().businessId

    private val selected = MutableStateFlow<Service?>(null)
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

    private val extras = combine(selected, booking, myToken, photos) { s, b, m, p -> Extras(s, b, m, p) }
    private data class Extras(val selected: Service?, val booking: Boolean, val myToken: String?, val photos: List<com.sabil.qnow.domain.model.Photo>)

    val state: StateFlow<DetailUiState> = combine(
        live,
        favourites.observeFavouriteIds().map { businessId in it }.catch { emit(false) },
        reviews.observeReviews(businessId).catch { emit(emptyList()) },
        extras,
    ) { live, fav, revs, (sel, book, mine, pics) ->
        DetailUiState(
            myToken = mine,
            photos = pics,
            loading = false,
            live = live,
            favourite = fav,
            reviews = revs,
            selectedService = sel ?: live?.business?.services?.firstOrNull(),
            booking = book,
        )
    }.state(this, DetailUiState())

    fun selectService(s: Service) { selected.value = s }

    fun toggleFavourite() {
        val now = state.value.favourite
        viewModelScope.launch { runCatching { favourites.setFavourite(businessId, !now) } }
    }

    fun book() {
        val biz = state.value.live?.business ?: return
        booking.value = true
        viewModelScope.launch {
            runCatching { queue.bookToken(biz, state.value.selectedService) }
                .onSuccess { _events.send(DetailEvent.Booked(TokenRoute(it.businessId, it.tokenId))) }
                .onFailure { e ->
                    _events.send(DetailEvent.Error(when (e) {
                        is AlreadyInQueueException -> "You already have a live token here."
                        is BusinessNotOpenException -> "${biz.name} isn't taking tokens right now."
                        else -> "Couldn't book right now. Check your connection and try again."
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

data class MyTokensUiState(val loading: Boolean = true, val items: List<ActiveToken> = emptyList())

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MyTokensViewModel @Inject constructor(queue: QueueRepository) : ViewModel() {
    val state: StateFlow<MyTokensUiState> = queue.observeMyTokens()
        .flatMapLatest { refs ->
            combineAll(refs.map { ref ->
                queue.observeToken(ref.businessId, ref.tokenId).map { t -> t?.let { ActiveToken(ref, it) } }.catch { emit(null) }
            })
        }
        .map { MyTokensUiState(false, it.filterNotNull()) }
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
