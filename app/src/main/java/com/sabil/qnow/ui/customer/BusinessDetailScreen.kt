package com.sabil.qnow.ui.customer

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.sabil.qnow.domain.model.Business
import com.sabil.qnow.domain.model.BusinessStatus
import com.sabil.qnow.domain.model.Review
import com.sabil.qnow.domain.model.Service
import com.sabil.qnow.domain.model.TokenStatus
import com.sabil.qnow.domain.queue.LiveBusiness
import com.sabil.qnow.ui.components.EmptyState
import com.sabil.qnow.ui.components.LiveDot
import com.sabil.qnow.ui.components.PrimaryButton
import com.sabil.qnow.ui.components.QCard
import com.sabil.qnow.ui.components.RatingRow
import com.sabil.qnow.ui.components.SectionHeader
import com.sabil.qnow.ui.components.Shimmer
import com.sabil.qnow.ui.components.StatusPill
import com.sabil.qnow.ui.components.TokenChip
import com.sabil.qnow.ui.components.formatMinutes
import com.sabil.qnow.ui.components.formatPrice
import com.sabil.qnow.ui.components.formatTime
import com.sabil.qnow.ui.navigation.TokenRoute
import com.sabil.qnow.ui.theme.QNowTheme
import com.sabil.qnow.ui.theme.Space
import java.time.LocalDate

@Composable
fun BusinessDetailScreen(
    onBack: () -> Unit,
    onBooked: (TokenRoute) -> Unit,
    onToken: (String, String) -> Unit,
    vm: BusinessDetailViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.book() }

    LaunchedEffect(Unit) {
        vm.events.collect { e ->
            when (e) {
                is DetailEvent.Booked -> onBooked(e.route)
                is DetailEvent.Error -> snackbar.showSnackbar(e.message)
            }
        }
    }

    val live = state.live
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            state.loading -> DetailSkeleton()
            live == null -> EmptyState(
                Icons.Rounded.Storefront, "Business not found", "It may have been removed.",
                Modifier.align(Alignment.Center), "Go back", onBack,
            )
            else -> DetailContent(live, state, vm)
        }

        // Floating header buttons over the photo.
        Row(Modifier.statusBarsPadding().padding(Space.sm).fillMaxWidth()) {
            RoundIcon(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Spacer(Modifier.weight(1f))
            if (live != null) {
                RoundIcon(vm::toggleFavourite) {
                    val tint by animateColorAsState(if (state.favourite) Color(0xFFE5484D) else MaterialTheme.colorScheme.onSurface, label = "fav")
                    Icon(
                        if (state.favourite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                        if (state.favourite) "Remove from favourites" else "Add to favourites",
                        tint = tint,
                    )
                }
            }
        }

        if (live != null) {
            BookBar(
                live = live,
                myToken = state.myToken,
                booking = state.booking,
                onBook = {
                    if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else vm.book()
                },
                onMyToken = { t -> onToken(live.business.id, t) },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 110.dp))
    }
}

@Composable
private fun RoundIcon(onClick: () -> Unit, content: @Composable () -> Unit) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.92f)),
    ) { content() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailContent(live: LiveBusiness, state: DetailUiState, vm: BusinessDetailViewModel) {
    val biz = live.business
    LazyColumn(contentPadding = PaddingValues(bottom = 140.dp)) {
        item { PhotoHeader(biz) }
        item {
            Column(Modifier.padding(horizontal = Space.screen, vertical = Space.lg)) {
                Text(biz.category.label.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(Space.xs))
                Text(biz.name, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(Space.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(biz.status)
                    Spacer(Modifier.width(Space.md))
                    RatingRow(biz.rating, biz.ratingCount)
                }
                Spacer(Modifier.height(Space.md))
                InfoLine(Icons.Rounded.LocationOn, biz.address)
                biz.publicPhone?.let { InfoLine(Icons.Rounded.Phone, it) }
            }
        }
        item { LiveQueueCard(live, Modifier.padding(horizontal = Space.screen)) }
        item {
            SectionHeader("Services & rates", Modifier.padding(start = Space.screen, end = Space.screen, top = Space.xl, bottom = Space.sm))
        }
        if (biz.services.isEmpty()) {
            item { Text("No services listed yet.", Modifier.padding(horizontal = Space.screen), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(biz.services, key = { it.id }) { s ->
            ServiceRow(s, selected = state.selectedService?.id == s.id, onClick = { vm.selectService(s) })
        }
        item { SectionHeader("Opening hours", Modifier.padding(start = Space.screen, end = Space.screen, top = Space.xl, bottom = Space.sm)) }
        item { HoursCard(biz, Modifier.padding(horizontal = Space.screen)) }
        item { SectionHeader("Reviews", Modifier.padding(start = Space.screen, end = Space.screen, top = Space.xl, bottom = Space.sm)) }
        if (state.reviews.isEmpty()) {
            item {
                Text(
                    "No reviews yet. Reviews come from verified customers after their visit.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Space.screen),
                )
            }
        }
        items(state.reviews, key = { it.id }) { ReviewItem(it) }
    }
}

@Composable
private fun PhotoHeader(biz: Business) {
    Box(Modifier.fillMaxWidth().height(280.dp).background(QNowTheme.extended.heroGradient)) {
        if (biz.photoUrls.isNotEmpty()) {
            val pager = rememberPagerState { biz.photoUrls.size }
            HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
                AsyncImage(biz.photoUrls[page], null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            if (biz.photoUrls.size > 1) {
                Row(Modifier.align(Alignment.BottomCenter).padding(bottom = Space.xl)) {
                    repeat(biz.photoUrls.size) { i ->
                        Box(
                            Modifier.padding(3.dp).size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape)
                                .background(Color.White.copy(alpha = if (i == pager.currentPage) 1f else 0.5f)),
                        )
                    }
                }
            }
        } else {
            Icon(Icons.Rounded.Storefront, null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.size(96.dp).align(Alignment.Center))
        }
        Box(
            Modifier.fillMaxWidth().height(96.dp).align(Alignment.BottomCenter)
                .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))),
        )
    }
}

@Composable
private fun InfoLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(Space.sm))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Live queue, numbers only. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveQueueCard(live: LiveBusiness, modifier: Modifier = Modifier, highlight: Int? = null) {
    QCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LiveDot(QNowTheme.extended.open, 8.dp)
                Spacer(Modifier.width(Space.sm))
                Text("Live queue", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text("${live.business.activeChairs} chair${if (live.business.activeChairs == 1) "" else "s"} active", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Metric(Icons.Rounded.People, "${live.summary.waitingCount}", "waiting", Modifier.weight(1f))
                Metric(Icons.Rounded.Timer, formatMinutes(live.summary.newcomerWaitMinutes), "wait if you join", Modifier.weight(1f))
            }
            val serving = live.tokens.filter { it.status == TokenStatus.SERVING || it.status == TokenStatus.GRACE }
            val waiting = live.tokens.filter { it.status == TokenStatus.WAITING }
            if (serving.isNotEmpty()) {
                Spacer(Modifier.height(Space.lg))
                Text("Now serving", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Space.sm))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    serving.forEach { TokenChip(it.number, highlighted = it.number == highlight, serving = true) }
                }
            }
            Spacer(Modifier.height(Space.lg))
            Text("Up next", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Space.sm))
            if (waiting.isEmpty()) {
                Text("Nobody waiting — you'd be first!", style = MaterialTheme.typography.bodyMedium)
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    waiting.take(24).forEach { TokenChip(it.number, highlighted = it.number == highlight) }
                    if (waiting.size > 24) Text("+${waiting.size - 24} more", Modifier.align(Alignment.CenterVertically))
                }
            }
        }
    }
}

@Composable
private fun ServiceRow(s: Service, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (selected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.xs).fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = Space.sm, vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Column(Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleSmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Schedule, null, Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(" ~${s.durationMinutes} min", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(formatPrice(s.price), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = Space.sm))
        }
    }
}

private val dayNames = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

@Composable
private fun HoursCard(biz: Business, modifier: Modifier = Modifier) {
    val today = LocalDate.now().dayOfWeek.value
    QCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            if (biz.hours.isEmpty()) Text("Hours not listed.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            biz.hours.sortedBy { it.day }.forEach { h ->
                val isToday = h.day == today
                Row {
                    Text(
                        dayNames.getOrElse(h.day - 1) { "?" } + if (isToday) "  (today)" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        if (h.open) "${formatTime(h.opensAt)} – ${formatTime(h.closesAt)}" else "Closed",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (h.open) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReviewItem(r: Review) {
    Column(Modifier.padding(horizontal = Space.screen, vertical = Space.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            repeat(5) { i ->
                Icon(Icons.Rounded.Star, null, Modifier.size(16.dp), tint = if (i < r.rating) QNowTheme.extended.star else MaterialTheme.colorScheme.outlineVariant)
            }
            Spacer(Modifier.width(Space.sm))
            Icon(Icons.Rounded.CheckCircle, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
            Text(" Verified visit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
        }
        if (r.comment.isNotBlank()) {
            Spacer(Modifier.height(Space.xs))
            Text(r.comment, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun BookBar(
    live: LiveBusiness,
    myToken: String?,
    booking: Boolean,
    onBook: () -> Unit,
    onMyToken: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val open = live.business.status == BusinessStatus.OPEN
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 16.dp,
        shape = MaterialTheme.shapes.extraLarge.copy(bottomStart = androidx.compose.foundation.shape.CornerSize(0), bottomEnd = androidx.compose.foundation.shape.CornerSize(0)),
    ) {
        Row(Modifier.navigationBarsPadding().padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Estimated wait", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatMinutes(live.summary.newcomerWaitMinutes), style = MaterialTheme.typography.headlineSmall)
            }
            when {
                myToken != null -> PrimaryButton("View my token", onClick = { onMyToken(myToken) }, containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)
                else -> PrimaryButton(
                    text = when (live.business.status) {
                        BusinessStatus.OPEN -> "Book token"
                        BusinessStatus.ON_BREAK -> "On a break"
                        BusinessStatus.CLOSED -> "Closed now"
                    },
                    onClick = onBook,
                    enabled = open,
                    loading = booking,
                )
            }
        }
    }
}

@Composable
private fun DetailSkeleton() {
    Column {
        Shimmer(Modifier.fillMaxWidth().height(280.dp), MaterialTheme.shapes.extraSmall)
        Column(Modifier.padding(Space.screen), verticalArrangement = Arrangement.spacedBy(Space.md)) {
            Shimmer(Modifier.width(220.dp).height(32.dp))
            Shimmer(Modifier.width(160.dp).height(20.dp))
            Shimmer(Modifier.fillMaxWidth().height(180.dp), MaterialTheme.shapes.large)
        }
    }
}
