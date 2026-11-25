package com.sabil.qnow.ui.customer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.sabil.qnow.ui.i18n.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.sabil.qnow.domain.model.Promo
import com.sabil.qnow.domain.queue.LiveBusiness
import com.sabil.qnow.ui.components.EmptyState
import com.sabil.qnow.ui.components.LiveDot
import com.sabil.qnow.ui.components.Pill
import com.sabil.qnow.ui.components.QCard
import com.sabil.qnow.ui.components.SectionHeader
import com.sabil.qnow.ui.components.Shimmer
import com.sabil.qnow.ui.components.ShimmerList
import com.sabil.qnow.ui.components.StatusPill
import com.sabil.qnow.ui.components.TokenNumber
import com.sabil.qnow.ui.components.formatMinutes
import com.sabil.qnow.ui.theme.Brand
import com.sabil.qnow.ui.theme.QNowTheme
import com.sabil.qnow.ui.theme.Space
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    onFind: () -> Unit,
    onBusiness: (String) -> Unit,
    onToken: (String, String) -> Unit,
    onTryOn: () -> Unit,
    onProfile: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { result -> vm.onLocationPermission(result.values.any { it }) }
    LaunchedEffect(Unit) {
        permission.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = Space.xxl),
        verticalArrangement = Arrangement.spacedBy(Space.lg),
    ) {
        item { HomeTopBar(vm.account, onProfile) }
        if (vm.isDemo) item { DemoBanner(onProfile) }

        item {
            AnimatedVisibility(state.activeToken != null, enter = expandVertically() + fadeIn()) {
                state.activeToken?.let { active ->
                    ActiveTokenCard(active, onClick = { onToken(active.ref.businessId, active.ref.tokenId) })
                }
            }
        }

        item {
            PromoCarousel(
                state.promos.ifEmpty { placeholderPromos }, loading = state.loading,
                onShown = vm::promoSeen,
                onPromo = { p ->
                    vm.promoTapped(p)
                    when (p.action) {
                        com.sabil.qnow.domain.model.PromoAction.SHOP -> p.targetBusinessId?.let(onBusiness)
                        com.sabil.qnow.domain.model.PromoAction.LOOKS -> onTryOn()
                        com.sabil.qnow.domain.model.PromoAction.MAP -> onFind()
                        com.sabil.qnow.domain.model.PromoAction.LINK -> p.linkUrl?.takeIf { it.startsWith("https://") }?.let { url ->
                            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                        }
                        com.sabil.qnow.domain.model.PromoAction.NONE -> Unit
                    }
                },
            )
        }

        item { FindCta(onFind) }

        item { TryOnTeaser(onTryOn) }

        // Favourites first (only when there are some), then every shop close by.
        if (state.favourites.isNotEmpty()) {
            item { SectionHeader("Your favourites", modifier = Modifier.padding(horizontal = Space.screen)) }
            items(state.favourites, key = { "fav-" + it.business.id }) { live ->
                FavouriteRow(live, Modifier.padding(horizontal = Space.screen).animateItem()) { onBusiness(live.business.id) }
            }
        }

        item {
            SectionHeader(
                "Barbers & salons near you",
                modifier = Modifier.padding(horizontal = Space.screen),
                action = "See map",
                onAction = onFind,
            )
        }
        when {
            !state.hasLocation -> item {
                EmptyState(
                    icon = Icons.Rounded.LocationOn,
                    title = "Turn on location",
                    message = "Allow location so QNow can list the barbers, salons and parlours closest to you.",
                    actionLabel = "Allow location",
                    onAction = { permission.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION)) },
                )
            }
            state.loading -> item { ShimmerList(3, modifier = Modifier.padding(horizontal = Space.screen)) }
            state.nearby.isEmpty() -> item {
                EmptyState(
                    icon = Icons.Rounded.Storefront,
                    title = "No QNow shops nearby yet",
                    message = "Shops within 10 km will appear here as they join. Try the map to look further away.",
                    actionLabel = "Open map",
                    onAction = onFind,
                )
            }
            else -> items(state.nearby, key = { "near-" + it.live.business.id }) { shop ->
                FavouriteRow(shop.live, Modifier.padding(horizontal = Space.screen).animateItem(), distanceKm = shop.distanceKm, sponsored = shop.sponsored) {
                    onBusiness(shop.live.business.id)
                }
            }
        }
    }
}

@Composable
private fun HomeTopBar(account: com.sabil.qnow.domain.repository.AccountInfo?, onProfile: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = Space.screen, end = Space.screen, top = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(com.sabil.qnow.ui.i18n.tr(greeting()) + (account?.name?.substringBefore(' ')?.let { ", $it" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Skip the wait.", style = MaterialTheme.typography.headlineMedium)
        }
        // Profile button, top right (like big shopping apps): photo or initial on the brand gradient.
        Surface(onClick = onProfile, shape = CircleShape, color = Color.Transparent) {
            Box(Modifier.clip(CircleShape).background(QNowTheme.extended.heroGradient)) {
                Avatar(account, 46)
            }
        }
    }
}

@Composable
private fun DemoBanner(onProfile: () -> Unit) {
    Surface(
        onClick = onProfile,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.padding(horizontal = Space.screen).fillMaxWidth(),
    ) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            Pill("DEMO", MaterialTheme.colorScheme.secondary)
            Spacer(Modifier.width(Space.sm))
            Text(
                "Bot shops and bot customers. Exit from your profile.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private fun greeting(): String {
    val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    return when (h) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        else -> "Good evening"
    }
}

@Composable
private fun ActiveTokenCard(active: ActiveToken, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.padding(horizontal = Space.screen).fillMaxWidth(),
        color = Color.Transparent,
    ) {
        Row(
            Modifier.background(QNowTheme.extended.heroGradient).padding(Space.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LiveDot(Brand.Coral)
                    Spacer(Modifier.width(6.dp))
                    Text("YOUR LIVE TOKEN", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                }
                Spacer(Modifier.height(Space.xs))
                Text(active.ref.businessName, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Tap to see your place in line", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f))
            }
            TokenNumber(active.token.number, style = MaterialTheme.typography.displaySmall)
        }
    }
}

/** Banners shown until real promos are added to the `promos` collection. */
private val placeholderPromos = listOf(
    Promo("builtin-1", "Fresh cut, zero wait", "Book a token and arrive just in time.", "Explore", null, null, 0),
    Promo("builtin-2", "Your shop on QNow", "Business owners: list your queue for free.", "Learn more", null, null, 1),
    Promo("builtin-3", "Never miss your turn", "We'll ping you 15 and 5 minutes before.", "Got it", null, null, 2),
)

private val promoGradients = listOf(
    Brush.linearGradient(listOf(Color(0xFF7C63FF), Color(0xFF3A22C4))),
    Brush.linearGradient(listOf(Color(0xFFFF8A65), Color(0xFFD9502E))),
    Brush.linearGradient(listOf(Color(0xFF1FD1A0), Color(0xFF0B7A5C))),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PromoCarousel(promos: List<Promo>, loading: Boolean, onShown: (Promo) -> Unit = {}, onPromo: (Promo) -> Unit) {
    if (loading) {
        Shimmer(Modifier.padding(horizontal = Space.screen).fillMaxWidth().height(160.dp), MaterialTheme.shapes.large)
        return
    }
    val pager = rememberPagerState { promos.size }
    LaunchedEffect(pager, promos.size) {
        while (promos.size > 1) {
            delay(5_000)
            pager.animateScrollToPage((pager.currentPage + 1) % promos.size)
        }
    }
    LaunchedEffect(pager.currentPage, promos) { promos.getOrNull(pager.currentPage)?.let(onShown) }
    Column {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = Space.screen),
            pageSpacing = Space.md,
        ) { page ->
            val promo = promos[page]
            Surface(
                onClick = { onPromo(promo) },
                shape = MaterialTheme.shapes.large,
                color = Color.Transparent,
                modifier = Modifier.fillMaxWidth().height(160.dp),
            ) {
                Box(Modifier.fillMaxSize().background(promoGradients[page % promoGradients.size])) {
                    if (promo.imageUrl != null) {
                        AsyncImage(promo.imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent))))
                    } else {
                        // Decorative rings so placeholder banners don't look flat.
                        Box(Modifier.size(180.dp).offset(x = 220.dp, y = (-40).dp).clip(CircleShape).background(Color.White.copy(alpha = 0.10f)))
                        Box(Modifier.size(110.dp).offset(x = 260.dp, y = 80.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)))
                    }
                    if (promo.sponsor != null) {
                        Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.35f), modifier = Modifier.align(Alignment.TopEnd).padding(Space.md)) {
                            Text("Sponsored · ${promo.sponsor}", style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                        }
                    }
                    Column(Modifier.padding(Space.lg).align(Alignment.CenterStart).fillMaxWidth(0.72f)) {
                        Text(promo.title, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(Space.xs))
                        Text(promo.subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f), maxLines = 2)
                        Spacer(Modifier.height(Space.md))
                        Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.2f)) {
                            Text(promo.ctaLabel, style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            repeat(promos.size) { i ->
                val selected = pager.currentPage == i
                val width by animateDpAsState(if (selected) 20.dp else 6.dp, label = "dot")
                Box(
                    Modifier.padding(horizontal = 3.dp).height(6.dp).width(width).clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                )
            }
        }
    }
}

@Composable
private fun FindCta(onFind: () -> Unit) {
    QCard(Modifier.padding(horizontal = Space.screen).fillMaxWidth(), onClick = onFind) {
        Row(Modifier.padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(MaterialTheme.shapes.medium).background(QNowTheme.extended.accentGradient),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.ContentCut, null, tint = Color.White) }
            Spacer(Modifier.width(Space.lg))
            Column(Modifier.weight(1f)) {
                Text("Find your barber / stylist", style = MaterialTheme.typography.titleMedium)
                Text("See live queues on the map near you", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onPrimary) }
        }
    }
}

@Composable
private fun TryOnTeaser(onClick: () -> Unit) {
    QCard(
        Modifier.padding(horizontal = Space.screen).fillMaxWidth(),
        onClick = onClick,
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Row(Modifier.padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text("Look Studio", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text("Try colours, cuts and beards on your photo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f))
            }
            Pill("Try it", MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun FavouriteRow(live: LiveBusiness, modifier: Modifier = Modifier, distanceKm: Double? = null, sponsored: Boolean = false, onClick: () -> Unit) {
    val biz = live.business
    QCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
            BusinessThumb(biz.thumbnail, biz.name, Modifier.size(64.dp))
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(biz.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val where = listOfNotNull(distanceKm?.let(::formatDistance), biz.address.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (where.isNotEmpty()) {
                    Text(where, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (sponsored) { Pill("Sponsored", QNowTheme.extended.star); Spacer(Modifier.width(Space.sm)) }
                    StatusPill(biz.status)
                    if (biz.ratingCount > 0) {
                        Spacer(Modifier.width(Space.sm))
                        Icon(Icons.Rounded.Star, null, tint = QNowTheme.extended.star, modifier = Modifier.size(14.dp))
                        Text(" %.1f".format(biz.rating), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.People, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(4.dp))
                    Text("${live.summary.waitingCount}", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(3.dp))
                    Text(formatMinutes(live.summary.newcomerWaitMinutes), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(Space.xs))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatDistance(km: Double): String = if (km < 1) "${(km * 1000).toInt() / 10 * 10} m" else "%.1f km".format(km)

/** First letter of a name, skipping tags like "[Bot] ". */
fun initialOf(name: String): String = (name.removePrefix("[Bot]").firstOrNull { it.isLetterOrDigit() } ?: 'Q').uppercase()

/** Shop thumbnail, or a gradient monogram when there isn't one. */
@Composable
fun BusinessThumb(thumbnail: ByteArray?, name: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(MaterialTheme.shapes.medium).background(QNowTheme.extended.heroGradient), contentAlignment = Alignment.Center) {
        if (thumbnail != null) {
            AsyncImage(thumbnail, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().aspectRatio(1f))
        } else {
            Text(initialOf(name), style = MaterialTheme.typography.headlineSmall, color = Color.White)
        }
    }
}
