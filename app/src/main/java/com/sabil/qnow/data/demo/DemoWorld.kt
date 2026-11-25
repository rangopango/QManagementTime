package com.sabil.qnow.data.demo

import com.sabil.qnow.domain.model.Business
import com.sabil.qnow.domain.model.BusinessCategory
import com.sabil.qnow.domain.model.BusinessStatus
import com.sabil.qnow.domain.model.DayHours
import com.sabil.qnow.domain.model.GeoLocation
import com.sabil.qnow.domain.model.MyTokenRef
import com.sabil.qnow.domain.model.QueueCounter
import com.sabil.qnow.domain.model.QueueToken
import com.sabil.qnow.domain.model.Review
import com.sabil.qnow.domain.model.Service
import com.sabil.qnow.domain.model.TokenHolder
import com.sabil.qnow.domain.model.TokenSource
import com.sabil.qnow.domain.model.TokenStatus
import com.sabil.qnow.domain.model.UserRole
import com.sabil.qnow.domain.queue.DayKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.cos
import kotlin.random.Random

/**
 * The prototype's whole world, in memory: bot shops around the user, bot customers who join
 * queues, and chairs that finish customers and call the next. Everything resets when the app
 * restarts. Nothing here touches Firebase.
 */
@Singleton
class DemoWorld @Inject constructor(mode: DemoMode) {

    val role: UserRole = mode.role
    val userId = "demo-you"
    var userName: String = if (role == UserRole.BUSINESS) "Demo owner" else "Demo customer"

    private val lock = Any()
    private val version = MutableStateFlow(0L)
    private val rnd = Random(System.currentTimeMillis())
    private val offset = TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000

    private val businesses = LinkedHashMap<String, Business>()
    private val tokens = HashMap<String, MutableMap<String, QueueToken>>()   // businessId -> tokenId -> token
    private val holders = HashMap<String, MutableMap<String, TokenHolder>>()
    private val reviews = HashMap<String, MutableList<Review>>()
    private val favourites = LinkedHashSet<String>()
    private val myTokens = ArrayList<MyTokenRef>()
    private var seededAround: GeoLocation? = null
    var myBusinessId: String? = null
        private set

    init {
        seed(DEFAULT_CENTER)
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            while (true) {
                delay(TICK_MS)
                tick()
            }
        }
    }

    /** Re-reads [read] whenever anything in the world changes. */
    fun <T> watch(read: DemoWorld.() -> T): Flow<T> = version.map { synchronized(lock) { read() } }.distinctUntilChanged()

    fun <T> edit(block: DemoWorld.() -> T): T {
        val result = synchronized(lock) { block() }
        version.value++
        return result
    }

    fun today() = DayKey.of(System.currentTimeMillis(), offset)

    // ------------------------------------------------------------ reads (call inside watch/edit)

    fun business(id: String) = businesses[id]
    fun allBusinesses(): List<Business> = businesses.values.toList()
    fun liveTokens(businessId: String) = tokens[businessId].orEmpty().values.filter { it.status.isActive }.sortedBy { it.number }
    fun token(businessId: String, tokenId: String) = tokens[businessId]?.get(tokenId)
    fun holdersOf(businessId: String): Map<String, TokenHolder> = holders[businessId].orEmpty().toMap()
    fun reviewsOf(businessId: String): List<Review> = reviews[businessId].orEmpty().sortedByDescending { it.createdAtMillis }
    fun favouriteIds(): Set<String> = favourites.toSet()
    fun myTokenRefs(): List<MyTokenRef> = myTokens.sortedByDescending { it.createdAtMillis }

    // ------------------------------------------------------------ writes (call inside edit)

    fun issue(businessId: String, service: Service?, source: TokenSource, holder: TokenHolder?): QueueToken {
        val biz = businesses.getValue(businessId)
        val day = today()
        val number = if (biz.queue.dayKey == day) biz.queue.lastNumber + 1 else 1
        businesses[businessId] = biz.copy(queue = QueueCounter(day, number))
        val id = DayKey.tokenId(day, number)
        val token = QueueToken(
            id = id, businessId = businessId, dayKey = day, number = number, status = TokenStatus.WAITING,
            serviceId = service?.id, serviceName = service?.name,
            durationMinutes = service?.durationMinutes ?: biz.averageServiceMinutes,
            source = source, createdAtMillis = System.currentTimeMillis(),
            calledAtMillis = null, graceEndsAtMillis = null, completedAtMillis = null,
        )
        tokens.getOrPut(businessId) { mutableMapOf() }[id] = token
        holders.getOrPut(businessId) { mutableMapOf() }[id] = (holder ?: botHolder()).copy(tokenId = id)
        return token
    }

    fun setToken(businessId: String, tokenId: String, change: (QueueToken) -> QueueToken) {
        val map = tokens[businessId] ?: return
        map[tokenId]?.let { map[tokenId] = change(it) }
    }

    fun putBusiness(b: Business) { businesses[b.id] = b }
    fun setFavourite(id: String, on: Boolean) { if (on) favourites += id else favourites -= id }
    fun addMyToken(ref: MyTokenRef) { myTokens += ref }
    fun markReviewed(businessId: String, tokenId: String) {
        val i = myTokens.indexOfFirst { it.businessId == businessId && it.tokenId == tokenId }
        if (i >= 0) myTokens[i] = myTokens[i].copy(reviewed = true)
    }
    fun addReview(businessId: String, r: Review) {
        reviews.getOrPut(businessId) { mutableListOf() } += r
        businesses[businessId]?.let { businesses[businessId] = it.copy(ratingSum = it.ratingSum + r.rating, ratingCount = it.ratingCount + 1) }
    }

    /** Moves the bot shops near wherever the user is looking, so the map is never empty. */
    fun ensureShopsNear(center: GeoLocation) {
        val seeded = seededAround
        if (seeded != null && distanceKm(seeded, center) < 15) return
        edit { seed(center) }
    }

    // ------------------------------------------------------------ simulation

    private fun tick() = edit {
        val now = System.currentTimeMillis()
        for (biz in businesses.values.toList()) {
            val live = liveTokens(biz.id)
            val mine = biz.id == myBusinessId
            // Bot customers join open shops now and then (the owner's own shop gets them too).
            if (biz.status == BusinessStatus.OPEN && live.count { it.status == TokenStatus.WAITING } < 8 && rnd.nextInt(100) < if (mine) 30 else 18) {
                issue(biz.id, biz.services.randomOrNull(rnd), if (rnd.nextInt(4) == 0) TokenSource.WALK_IN else TokenSource.APP, null)
            }
            if (mine) continue // the owner runs their own chairs from the dashboard
            for (t in live) {
                when {
                    // Bot shops finish customers a bit faster than estimated, so the demo moves.
                    t.status == TokenStatus.SERVING && t.calledAtMillis != null && !isMine(biz.id, t.id) &&
                        now - t.calledAtMillis > SERVE_MS + rnd.nextLong(SERVE_MS) ->
                        setToken(biz.id, t.id) { it.copy(status = TokenStatus.DONE, completedAtMillis = now) }
                    // The customer's own turn wraps up after a short while so they can rate the visit.
                    t.status == TokenStatus.SERVING && isMine(biz.id, t.id) && now - (t.calledAtMillis ?: now) > YOUR_SERVICE_MS ->
                        setToken(biz.id, t.id) { it.copy(status = TokenStatus.DONE, completedAtMillis = now) }
                    // Bots occasionally cancel.
                    t.status == TokenStatus.WAITING && !isMine(biz.id, t.id) && rnd.nextInt(1000) < 6 ->
                        setToken(biz.id, t.id) { it.copy(status = TokenStatus.CANCELLED, completedAtMillis = now) }
                }
            }
            if (biz.status != BusinessStatus.CLOSED) {
                val after = liveTokens(biz.id)
                val free = biz.activeChairs - after.count { it.status == TokenStatus.SERVING || it.status == TokenStatus.GRACE }
                after.filter { it.status == TokenStatus.WAITING }.take(free.coerceAtLeast(0)).forEach { next ->
                    setToken(biz.id, next.id) { it.copy(status = TokenStatus.SERVING, calledAtMillis = now) }
                }
            }
        }
    }

    private fun isMine(businessId: String, tokenId: String) = holders[businessId]?.get(tokenId)?.customerId == userId

    // ------------------------------------------------------------ seeding

    private fun seed(center: GeoLocation) {
        val keep = myBusinessId?.let { businesses[it] }
        businesses.clear(); tokens.clear(); holders.clear(); reviews.clear()
        seededAround = center
        SHOPS.forEachIndexed { i, s ->
            val id = "demo-shop-$i"
            val angle = i * (2 * Math.PI / SHOPS.size) + rnd.nextDouble(0.3)
            val km = 0.4 + rnd.nextDouble(2.6)
            val loc = GeoLocation(center.lat + km / 111.0 * kotlin.math.sin(angle), center.lng + km / (111.0 * cos(Math.toRadians(center.lat))) * cos(angle))
            val count = rnd.nextLong(8, 140)
            businesses[id] = Business(
                id = id, ownerId = "bot-owner-$i", name = s.name, category = s.category,
                address = "${s.street}, near you", location = loc, geohash = "",
                thumbnail = null, photoIds = emptyList(),
                services = s.services.mapIndexed { j, (n, p, m) -> Service("$id-s$j", n, p, m) },
                hours = (1..7).map { DayHours(it, open = it != 7 || i % 2 == 0, opensAt = 9 * 60, closesAt = 21 * 60) },
                chairs = s.chairs, activeChairs = s.chairs, status = s.status,
                publicPhone = null, ratingSum = (count * (3.8 + rnd.nextDouble(1.1))).toLong(), ratingCount = count,
                utcOffsetMinutes = offset, graceMinutes = 3, queue = QueueCounter(today(), 0),
            )
            reviews[id] = REVIEWS.shuffled(rnd).take(4).mapIndexed { j, (stars, text) ->
                Review("r$i-$j", stars, text, System.currentTimeMillis() - (j + 1) * 86_400_000L * (1 + rnd.nextInt(5)))
            }.toMutableList()
            // A queue already in progress: some customers done today, some in chairs, some waiting.
            if (s.status != BusinessStatus.CLOSED) {
                val past = rnd.nextInt(4, 15)
                repeat(past) { issue(id, null, TokenSource.APP, null).let { t -> setToken(id, t.id) { it.copy(status = TokenStatus.DONE) } } }
                repeat(s.chairs) {
                    val t = issue(id, businesses.getValue(id).services.random(rnd), TokenSource.APP, null)
                    setToken(id, t.id) { it.copy(status = TokenStatus.SERVING, calledAtMillis = System.currentTimeMillis() - rnd.nextLong(SERVE_MS)) }
                }
                repeat(rnd.nextInt(1, 6)) { issue(id, businesses.getValue(id).services.random(rnd), TokenSource.APP, null) }
            }
        }
        if (favourites.isEmpty()) favourites += listOf("demo-shop-0", "demo-shop-2")
        if (role == UserRole.BUSINESS) {
            val mine = keep?.copy(location = keep.location ?: center) ?: myShop(center)
            myBusinessId = mine.id
            businesses[mine.id] = mine
            if (tokens[mine.id].isNullOrEmpty()) {
                repeat(3) { issue(mine.id, mine.services.random(rnd), TokenSource.APP, null) }
            }
        }
    }

    private fun myShop(center: GeoLocation) = Business(
        id = "demo-my-shop", ownerId = userId, name = "Your Demo Salon", category = BusinessCategory.SALON,
        address = "Main Road (demo)", location = GeoLocation(center.lat + 0.002, center.lng + 0.002), geohash = "",
        thumbnail = null, photoIds = emptyList(),
        services = listOf(Service("m1", "Haircut", 150.0, 20), Service("m2", "Beard trim", 80.0, 10), Service("m3", "Hair colour", 600.0, 45)),
        hours = (1..7).map { DayHours(it, open = it != 7) }, chairs = 2, activeChairs = 2, status = BusinessStatus.OPEN,
        publicPhone = null, ratingSum = 0, ratingCount = 0, utcOffsetMinutes = offset, graceMinutes = 3,
        queue = QueueCounter(today(), 0),
    )

    private fun botHolder() = TokenHolder("", "bot", BOT_NAMES.random(rnd))

    private fun distanceKm(a: GeoLocation, b: GeoLocation): Double {
        val dLat = (a.lat - b.lat) * 111.0
        val dLng = (a.lng - b.lng) * 111.0 * cos(Math.toRadians(a.lat))
        return kotlin.math.sqrt(dLat * dLat + dLng * dLng)
    }

    private data class Shop(
        val name: String, val category: BusinessCategory, val street: String, val chairs: Int,
        val status: BusinessStatus, val services: List<Triple<String, Double, Int>>,
    )

    private companion object {
        val DEFAULT_CENTER = GeoLocation(24.8333, 92.7789)
        const val TICK_MS = 4_000L
        const val SERVE_MS = 40_000L
        const val YOUR_SERVICE_MS = 45_000L

        val BARBER = listOf(Triple("Haircut", 120.0, 20), Triple("Beard trim", 60.0, 10), Triple("Clean shave", 50.0, 10), Triple("Head massage", 100.0, 15))
        val SALON = listOf(Triple("Haircut & style", 300.0, 30), Triple("Hair spa", 700.0, 45), Triple("Hair colour", 900.0, 60), Triple("Blow-dry", 250.0, 20))
        val PARLOUR = listOf(Triple("Facial", 500.0, 40), Triple("Threading", 50.0, 10), Triple("Waxing", 300.0, 30), Triple("Manicure", 350.0, 30))

        val SHOPS = listOf(
            Shop("Fade Factory", BusinessCategory.BARBER, "College Road", 2, BusinessStatus.OPEN, BARBER),
            Shop("Urban Mane Salon", BusinessCategory.SALON, "Park Road", 3, BusinessStatus.OPEN, SALON),
            Shop("Glow Beauty Parlour", BusinessCategory.PARLOUR, "Club Road", 2, BusinessStatus.OPEN, PARLOUR),
            Shop("Kings Cut Barbershop", BusinessCategory.BARBER, "Station Road", 1, BusinessStatus.OPEN, BARBER),
            Shop("Silk Touch Salon", BusinessCategory.SALON, "Hospital Road", 2, BusinessStatus.ON_BREAK, SALON),
            Shop("The Gentlemen's Chair", BusinessCategory.BARBER, "Link Road", 3, BusinessStatus.OPEN, BARBER),
            Shop("Bella Beauty Studio", BusinessCategory.PARLOUR, "Central Road", 1, BusinessStatus.OPEN, PARLOUR),
            Shop("Sharp & Co. Barbers", BusinessCategory.BARBER, "Market Lane", 2, BusinessStatus.CLOSED, BARBER),
        )

        val BOT_NAMES = listOf("Aarav", "Riya", "Kabir", "Ananya", "Vihaan", "Meera", "Arjun", "Isha", "Rohan", "Sara", "Dev", "Nisha", "Walk-in")

        val REVIEWS = listOf(
            5 to "Super quick, I walked in right when my number came up.",
            5 to "Great fade and the live queue meant no waiting around.",
            4 to "Good service. Wait estimate was spot on.",
            4 to "Friendly staff, clean place.",
            3 to "Decent cut, a little busy on weekends.",
            5 to "Love not sitting in the shop for an hour anymore!",
            4 to "Booked from home and arrived just in time.",
            2 to "Took longer than estimated today.",
        )
    }
}
