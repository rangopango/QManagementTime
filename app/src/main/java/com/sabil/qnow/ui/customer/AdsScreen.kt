package com.sabil.qnow.ui.customer

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import com.sabil.qnow.ui.i18n.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sabil.qnow.domain.model.AdAudience
import com.sabil.qnow.domain.model.Business
import com.sabil.qnow.domain.model.Promo
import com.sabil.qnow.domain.repository.LocationRepository
import com.sabil.qnow.domain.repository.PromoDraft
import com.sabil.qnow.domain.repository.PromoRepository
import com.sabil.qnow.ui.components.Pill
import com.sabil.qnow.ui.components.PrimaryButton
import com.sabil.qnow.ui.components.QCard
import com.sabil.qnow.ui.components.SecondaryButton
import com.sabil.qnow.ui.theme.QNowTheme
import com.sabil.qnow.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AdsViewModel @Inject constructor(
    private val repo: PromoRepository,
    private val location: LocationRepository,
) : ViewModel() {
    val promos = repo.observeAllPromos().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val shops = MutableStateFlow<List<Business>>(emptyList())
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    init { refreshShops() }

    fun refreshShops() = viewModelScope.launch { runCatching { repo.allShops() }.onSuccess { shops.value = it } }

    private fun run(done: String, block: suspend () -> Unit) = viewModelScope.launch {
        runCatching { block() }
            .onSuccess { _messages.send(done) }
            .onFailure { _messages.send("Couldn't save: ${errorCode(it)}. Is this account a QNow admin?") }
    }

    fun save(id: String?, draft: PromoDraft, nearMe: Boolean) = run(if (id == null) "Ad published" else "Ad updated") {
        val area = if (nearMe) location.lastKnownLocation() else null
        repo.savePromo(id, draft.copy(area = area))
    }
    fun setActive(p: Promo, on: Boolean) = run(if (on) "Ad switched on" else "Ad paused") { repo.setPromoActive(p.id, on) }
    fun delete(p: Promo) = run("Ad deleted") { repo.deletePromo(p.id) }
    fun sponsor(b: Business, audience: AdAudience, days: Int, sponsor: String?) =
        run("${b.name} is sponsored for $days days") { repo.setSponsor(b.id, audience, days, sponsor); refreshShops() }
    fun unsponsor(b: Business) = run("Sponsorship removed") { repo.clearSponsor(b.id); refreshShops() }
}

/**
 * Ads manager for the QNow team: banners targeted by gender, area and dates, sponsored shops,
 * and the view/tap totals to show sponsors. Only admin accounts can open it.
 */
@Composable
fun AdsScreen(onBack: () -> Unit, vm: AdsViewModel = hiltViewModel()) {
    val promos by vm.promos.collectAsStateWithLifecycle()
    val shops by vm.shops.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Promo?>(null) }
    var creating by remember { mutableStateOf(false) }
    var sponsoring by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Promo?>(null) }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(contentPadding = PaddingValues(bottom = Space.xxl)) {
            item {
                Column(Modifier.fillMaxWidth().background(QNowTheme.extended.heroGradient).statusBarsPadding().padding(bottom = Space.lg)) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
                    Text("Ads manager", style = MaterialTheme.typography.headlineSmall, color = Color.White, modifier = Modifier.padding(horizontal = Space.screen))
                    Text(
                        "Banners and sponsored shops, targeted by gender, area and dates. Targeting happens on each phone; sponsors only see totals.",
                        style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.padding(horizontal = Space.screen, vertical = Space.xs),
                    )
                }
            }
            item {
                Row(Modifier.padding(Space.screen), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    PrimaryButton("New ad", onClick = { creating = true }, icon = Icons.Rounded.Add, modifier = Modifier.weight(1f))
                    SecondaryButton("Sponsor a shop", onClick = { sponsoring = true }, icon = Icons.Rounded.Storefront, modifier = Modifier.weight(1f))
                }
            }
            item { Text("Banner ads (${promos.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = Space.screen)) }
            if (promos.isEmpty()) item {
                Text("No ads yet. Tap \"New ad\". Until then customers see QNow's built-in banners.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Space.screen))
            }
            items(promos, key = { it.id }) { p ->
                AdCard(p, shops.firstOrNull { it.id == p.targetBusinessId }?.name, onToggle = { vm.setActive(p, it) }, onEdit = { editing = p }, onDelete = { deleting = p })
            }
            val sponsored = shops.filter { it.sponsorship != null }
            item { Text("Sponsored shops (${sponsored.size})", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = Space.screen, end = Space.screen, top = Space.xl)) }
            if (sponsored.isEmpty()) item {
                Text("None yet. Sponsored shops appear first in customers' nearby list with a \"Sponsored\" label.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(Space.screen))
            }
            items(sponsored, key = { "s-" + it.id }) { b ->
                val s = b.sponsorship!!
                QCard(Modifier.padding(horizontal = Space.screen, vertical = Space.xs).fillMaxWidth()) {
                    Row(Modifier.padding(Space.md), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            val left = ((s.untilMillis - System.currentTimeMillis()) / 86_400_000L).coerceAtLeast(0)
                            Text("${s.audience.label} · ${if (left > 0) "$left days left" else "ended"}" + (s.sponsor?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        TextButton(onClick = { vm.unsponsor(b) }) { Text("Remove") }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(Space.lg))
    }

    if (creating || editing != null) {
        AdEditor(editing, shops, onDismiss = { creating = false; editing = null }) { draft, nearMe ->
            vm.save(editing?.id, draft, nearMe); creating = false; editing = null
        }
    }
    if (sponsoring) SponsorDialog(shops, onDismiss = { sponsoring = false }) { b, a, d, who -> vm.sponsor(b, a, d, who); sponsoring = false }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete \"${p.title}\"?") },
            text = { Text("Its view and tap totals are deleted too. To stop it but keep the numbers, switch it off instead.") },
            confirmButton = { TextButton(onClick = { vm.delete(p); deleting = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AdCard(p: Promo, shopName: String?, onToggle: (Boolean) -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    QCard(Modifier.padding(horizontal = Space.screen, vertical = Space.xs).fillMaxWidth(), onClick = onEdit) {
        Column(Modifier.padding(Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Campaign, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Space.sm))
                Column(Modifier.weight(1f)) {
                    Text(p.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(p.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Switch(checked = p.active, onCheckedChange = onToggle)
            }
            Spacer(Modifier.height(Space.sm))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalAlignment = Alignment.CenterVertically) {
                Pill(p.audience.label, MaterialTheme.colorScheme.primary)
                Pill(if (p.areaCenter != null) "Within ${p.radiusKm.toInt()} km" else "Everywhere", MaterialTheme.colorScheme.tertiary)
                p.endsAtMillis?.let {
                    val left = ((it - System.currentTimeMillis()) / 86_400_000L)
                    Pill(if (left >= 0) "$left days left" else "Ended", MaterialTheme.colorScheme.secondary)
                }
                p.sponsor?.let { Pill("Sponsor: $it", QNowTheme.extended.star) }
                Pill(
                    "Opens " + when (p.action) {
                        com.sabil.qnow.domain.model.PromoAction.SHOP -> shopName ?: "a shop"
                        com.sabil.qnow.domain.model.PromoAction.LINK -> "a web link"
                        else -> p.action.label
                    },
                    MaterialTheme.colorScheme.outline,
                )
            }
            Spacer(Modifier.height(Space.sm))
            val ctr = if (p.views > 0) " · ${"%.1f".format(p.taps * 100.0 / p.views)}% tapped" else ""
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${p.views} views · ${p.taps} taps$ctr", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onDelete) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

private val dayChoices = listOf(7, 14, 30, 90)

@Composable
private fun AdEditor(existing: Promo?, shops: List<Business>, onDismiss: () -> Unit, onSave: (PromoDraft, Boolean) -> Unit) {
    var title by remember { mutableStateOf(existing?.title.orEmpty()) }
    var subtitle by remember { mutableStateOf(existing?.subtitle.orEmpty()) }
    var cta by remember { mutableStateOf(existing?.ctaLabel ?: "Book now") }
    var image by remember { mutableStateOf(existing?.imageUrl.orEmpty()) }
    var sponsor by remember { mutableStateOf(existing?.sponsor.orEmpty()) }
    var audience by remember { mutableStateOf(existing?.audience ?: AdAudience.ALL) }
    var nearMe by remember { mutableStateOf(existing?.areaCenter != null) }
    var radius by remember { mutableStateOf(existing?.radiusKm?.takeIf { it > 0 }?.toInt() ?: 10) }
    var days by remember { mutableStateOf<Int?>(30) }
    var shopId by remember { mutableStateOf(existing?.targetBusinessId) }
    var action by remember { mutableStateOf(existing?.action ?: com.sabil.qnow.domain.model.PromoAction.LOOKS) }
    var link by remember { mutableStateOf(existing?.linkUrl.orEmpty()) }
    var pickShop by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "New ad" else "Edit ad") },
        text = {
            Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                OutlinedTextField(title, { title = it.take(60) }, label = { Text("Headline") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(subtitle, { subtitle = it.take(120) }, label = { Text("Short message") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(cta, { cta = it.take(20) }, label = { Text("Button text") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(sponsor, { sponsor = it.take(60) }, label = { Text("Sponsor name (empty = QNow's own)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(image, { image = it }, label = { Text("Picture link (optional, https://…)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

                Text("Who sees it", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    AdAudience.entries.forEach { a -> FilterChip(audience == a, { audience = a }, label = { Text(a.label) }, shape = CircleShape) }
                }
                Text("Kids never see targeted ads. \"Men\"/\"Women\" uses the gender customers set in their profile.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Text("Where", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    FilterChip(!nearMe, { nearMe = false }, label = { Text("Everywhere") }, shape = CircleShape)
                    FilterChip(nearMe, { nearMe = true }, label = { Text("Around my location") }, shape = CircleShape)
                }
                if (nearMe) Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    listOf(3, 5, 10, 25, 50).forEach { r -> FilterChip(radius == r, { radius = r }, label = { Text("$r km") }, shape = CircleShape) }
                }

                Text("How long (from today)", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    dayChoices.forEach { d -> FilterChip(days == d, { days = d }, label = { Text("$d days") }, shape = CircleShape) }
                    FilterChip(days == null, { days = null }, label = { Text("No end") }, shape = CircleShape)
                }

                Text("Tapping it opens", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    com.sabil.qnow.domain.model.PromoAction.entries.forEach { a -> FilterChip(action == a, { action = a }, label = { Text(a.label) }, shape = CircleShape) }
                }
                when (action) {
                    com.sabil.qnow.domain.model.PromoAction.SHOP ->
                        SecondaryButton(shops.firstOrNull { it.id == shopId }?.name ?: "Choose shop", onClick = { pickShop = true }, icon = Icons.Rounded.Storefront, modifier = Modifier.fillMaxWidth())
                    com.sabil.qnow.domain.model.PromoAction.LINK ->
                        OutlinedTextField(link, { link = it.trim() }, label = { Text("Link (must start with https://)") }, singleLine = true,
                            isError = link.isNotEmpty() && !link.startsWith("https://"), modifier = Modifier.fillMaxWidth())
                    else -> Unit
                }
            }
        },
        confirmButton = {
            val ready = title.isNotBlank() &&
                (action != com.sabil.qnow.domain.model.PromoAction.SHOP || shopId != null) &&
                (action != com.sabil.qnow.domain.model.PromoAction.LINK || link.startsWith("https://"))
            TextButton(enabled = ready, onClick = {
                onSave(
                    PromoDraft(title, subtitle, cta, image.ifBlank { null }, shopId, audience, null, radius.toDouble(), days, sponsor.ifBlank { null }, existing?.priority ?: 0, action, link.ifBlank { null }),
                    nearMe,
                )
            }) { Text(if (existing == null) "Publish" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pickShop) ShopPicker(shops, onDismiss = { pickShop = false }, allowNone = false) { shopId = it?.id; pickShop = false }
}

@Composable
private fun SponsorDialog(shops: List<Business>, onDismiss: () -> Unit, onSave: (Business, AdAudience, Int, String?) -> Unit) {
    var shop by remember { mutableStateOf<Business?>(null) }
    var audience by remember { mutableStateOf(AdAudience.ALL) }
    var days by remember { mutableStateOf(30) }
    var sponsor by remember { mutableStateOf("") }
    var pick by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sponsor a shop") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                Text("The shop is listed first (with a \"Sponsored\" label) in the nearby list of the customers you choose.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                SecondaryButton(shop?.name ?: "Choose shop", onClick = { pick = true }, icon = Icons.Rounded.Storefront, modifier = Modifier.fillMaxWidth())
                Text("Show first to", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    AdAudience.entries.forEach { a -> FilterChip(audience == a, { audience = a }, label = { Text(a.label) }, shape = CircleShape) }
                }
                Text("For", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    dayChoices.forEach { d -> FilterChip(days == d, { days = d }, label = { Text("$d days") }, shape = CircleShape) }
                }
                OutlinedTextField(sponsor, { sponsor = it.take(60) }, label = { Text("Paid by (optional, for your records)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = shop != null, onClick = { shop?.let { onSave(it, audience, days, sponsor.ifBlank { null }) } }) { Text("Sponsor") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    if (pick) ShopPicker(shops, onDismiss = { pick = false }, allowNone = false) { shop = it; pick = false }
}

@Composable
private fun ShopPicker(shops: List<Business>, onDismiss: () -> Unit, allowNone: Boolean, onPick: (Business?) -> Unit) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose a shop") },
        text = {
            Column {
                OutlinedTextField(query, { query = it }, label = { Text("Search by name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(Space.sm))
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    if (allowNone) item { TextButton(onClick = { onPick(null) }) { Text("Nothing (just a message)") } }
                    items(shops.filter { query.isBlank() || it.name.contains(query, ignoreCase = true) }, key = { it.id }) { b ->
                        TextButton(onClick = { onPick(b) }, modifier = Modifier.fillMaxWidth()) {
                            Text("${b.name} · ${b.category.label}", modifier = Modifier.fillMaxWidth(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
