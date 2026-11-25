package com.sabil.qnow.ui.business

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Coffee
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EventSeat
import androidx.compose.material.icons.rounded.HowToReg
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PersonOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.sabil.qnow.ui.i18n.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabil.qnow.domain.model.BusinessStatus
import com.sabil.qnow.domain.model.QueueToken
import com.sabil.qnow.domain.model.Service
import com.sabil.qnow.domain.model.TokenSource
import com.sabil.qnow.domain.model.TokenStatus
import com.sabil.qnow.ui.components.EmptyState
import com.sabil.qnow.ui.components.LiveDot
import com.sabil.qnow.ui.components.Pill
import com.sabil.qnow.ui.components.PrimaryButton
import com.sabil.qnow.ui.components.QCard
import com.sabil.qnow.ui.components.SectionHeader
import com.sabil.qnow.ui.components.ShimmerList
import com.sabil.qnow.ui.components.formatMinutes
import com.sabil.qnow.ui.theme.Brand
import com.sabil.qnow.ui.theme.QNowTheme
import com.sabil.qnow.ui.theme.Space
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(onEdit: () -> Unit, onSignOut: () -> Unit, vm: DashboardViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var walkIn by remember { mutableStateOf(false) }
    // Ticks every second for the grace countdown and time-in-chair labels.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
    LaunchedEffect(s.message) { s.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }

    val live = s.live
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            item { DashboardHeader(s, vm, onEdit, onSignOut) }
            if (s.loading || live == null) {
                item { ShimmerList(3, 120.dp, Modifier.padding(horizontal = Space.screen)) }
                return@LazyColumn
            }
            item {
                Row(Modifier.padding(horizontal = Space.screen), horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    StatTile("${s.waiting.size}", "waiting", Modifier.weight(1f))
                    StatTile(formatMinutes(live.summary.newcomerWaitMinutes), "wait for new", Modifier.weight(1f))
                    ChairsTile(live.business.activeChairs, live.business.chairs, vm::setActiveChairs, Modifier.weight(1f))
                }
            }
            item { SectionHeader("In the chair", Modifier.padding(horizontal = Space.screen)) }
            if (s.inChair.isEmpty()) {
                item {
                    QCard(Modifier.padding(horizontal = Space.screen).fillMaxWidth()) {
                        Text(
                            if (s.waiting.isEmpty()) "No one in the chair. New tokens will show up here live." else "Chairs are free. Call the next customer.",
                            modifier = Modifier.padding(Space.lg),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            items(s.inChair, key = { it.id }) { t ->
                ServingCard(
                    token = t,
                    label = s.holders[t.id]?.label,
                    now = now,
                    busy = t.id in s.busy,
                    nextNumber = s.waiting.firstOrNull()?.number,
                    onDone = { vm.doneAndNext(t) },
                    onNoShow = { vm.noShow(t) },
                    onArrived = { vm.arrived(t) },
                    onSkip = { vm.noShowNow(t) },
                    modifier = Modifier.padding(horizontal = Space.screen).animateItem(),
                )
            }
            if (s.freeChairs > 0 && s.waiting.isNotEmpty()) {
                item {
                    PrimaryButton(
                        "Call next · #${s.waiting.first().number}",
                        onClick = vm::callNext,
                        icon = Icons.Rounded.PlayArrow,
                        loading = "call-next" in s.busy,
                        height = 64.dp,
                        containerColor = QNowTheme.extended.open,
                        contentColor = Color.White,
                        modifier = Modifier.padding(horizontal = Space.screen).fillMaxWidth(),
                    )
                }
            }
            item { SectionHeader("Waiting · ${s.waiting.size}", Modifier.padding(horizontal = Space.screen)) }
            if (s.waiting.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Rounded.EventSeat, "Queue is empty",
                        if (live.business.status == BusinessStatus.OPEN) "Customers who book will appear here instantly."
                        else "You're not open. Switch to Open so customers can book.",
                    )
                }
            }
            items(s.waiting, key = { it.id }) { t ->
                WaitingRow(
                    token = t,
                    label = s.holders[t.id]?.label,
                    waitMinutes = live.summary.estimates[t.id]?.waitMinutes,
                    onRemove = { vm.removeWaiting(t) },
                    modifier = Modifier.padding(horizontal = Space.screen).animateItem(),
                )
            }
        }

        if (live != null) {
            PrimaryButton(
                "Add walk-in",
                onClick = { walkIn = true },
                icon = Icons.Rounded.PersonAdd,
                height = 60.dp,
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(Space.screen).fillMaxWidth(),
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 96.dp))
    }

    if (walkIn && live != null) {
        WalkInSheet(
            services = live.business.services,
            onDismiss = { walkIn = false },
            onAdd = { service, label -> vm.addWalkIn(service, label); walkIn = false },
        )
    }
}

@Composable
private fun DashboardHeader(s: DashboardState, vm: DashboardViewModel, onEdit: () -> Unit, onSignOut: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val biz = s.live?.business
    Box(Modifier.fillMaxWidth().background(QNowTheme.extended.heroGradient)) {
        Column(Modifier.statusBarsPadding().padding(start = Space.screen, end = Space.sm, top = Space.sm, bottom = Space.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LiveDot(Brand.Coral)
                        Spacer(Modifier.width(6.dp))
                        Text("LIVE QUEUE", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.8f))
                    }
                    Text(biz?.name ?: "Your business", style = MaterialTheme.typography.headlineSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Menu", tint = Color.White) }
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit profile, services & hours") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; onEdit() })
                        DropdownMenuItem(text = { Text("Sign out") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Logout, null) }, onClick = { menu = false; onSignOut() })
                    }
                }
            }
            Spacer(Modifier.height(Space.lg))
            if (biz != null) {
                val options = listOf(
                    Triple(BusinessStatus.OPEN, "Open", Icons.Rounded.Storefront),
                    Triple(BusinessStatus.ON_BREAK, "Break", Icons.Rounded.Coffee),
                    Triple(BusinessStatus.CLOSED, "Closed", Icons.Rounded.DoNotDisturbOn),
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(end = Space.md)) {
                    options.forEachIndexed { i, (status, label, icon) ->
                        val selected = biz.status == status
                        SegmentedButton(
                            selected = selected,
                            onClick = { vm.setStatus(status) },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                            icon = { Icon(icon, null, Modifier.size(18.dp)) },
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = Color.White,
                                activeContentColor = MaterialTheme.colorScheme.primary,
                                inactiveContainerColor = Color.White.copy(alpha = 0.12f),
                                inactiveContentColor = Color.White,
                                activeBorderColor = Color.White,
                                inactiveBorderColor = Color.White.copy(alpha = 0.3f),
                            ),
                        ) { Text(label) }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    QCard(modifier) {
        Column(Modifier.padding(Space.md)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary, maxLines = 1)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChairsTile(active: Int, total: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    QCard(modifier) {
        Column(Modifier.padding(Space.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$active", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                Text("/$total", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                if (total > 1) {
                    Text(
                        "−", style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.clip(CircleShape).clickable(enabled = active > 1) { onChange(active - 1) }.padding(horizontal = 6.dp),
                    )
                    Text(
                        "+", style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.clip(CircleShape).clickable(enabled = active < total) { onChange(active + 1) }.padding(horizontal = 6.dp),
                    )
                }
            }
            Text("chairs active", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


@Composable
private fun ServingCard(
    token: QueueToken,
    label: String?,
    now: Long,
    busy: Boolean,
    nextNumber: Int?,
    onDone: () -> Unit,
    onNoShow: () -> Unit,
    onArrived: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val grace = token.status == TokenStatus.GRACE
    val container by animateColorAsState(
        if (grace) QNowTheme.extended.onBreak.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainerLow, label = "card",
    )
    QCard(modifier.fillMaxWidth(), color = container) {
        Column(Modifier.padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("#${token.number}", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(label ?: "Customer", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(token.serviceName, "~${token.durationMinutes} min").joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (grace) {
                    val left = (((token.graceEndsAtMillis ?: now) - now) / 1000).coerceAtLeast(0)
                    Pill("%d:%02d left".format(left / 60, left % 60), QNowTheme.extended.onBreak, live = true)
                } else {
                    val mins = token.calledAtMillis?.let { ((now - it) / 60_000).toInt() } ?: 0
                    Pill("$mins min in chair", QNowTheme.extended.open)
                }
            }
            Spacer(Modifier.height(Space.lg))
            if (grace) {
                Text("Called — waiting for them to arrive. They'll be skipped when time runs out.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(Space.md))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    PrimaryButton("Arrived", onClick = onArrived, icon = Icons.Rounded.HowToReg, enabled = !busy, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = onSkip, enabled = !busy, modifier = Modifier.weight(1f).height(56.dp), shape = MaterialTheme.shapes.medium) {
                        Icon(Icons.Rounded.SkipNext, null)
                        Spacer(Modifier.width(Space.xs))
                        Text("Skip now")
                    }
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    PrimaryButton(
                        if (nextNumber != null) "Done · Next #$nextNumber" else "Mark done",
                        onClick = onDone,
                        icon = Icons.Rounded.Check,
                        loading = busy,
                        height = 64.dp,
                        modifier = Modifier.weight(1.6f),
                    )
                    OutlinedButton(onClick = onNoShow, enabled = !busy, modifier = Modifier.weight(1f).height(64.dp), shape = MaterialTheme.shapes.medium) {
                        Icon(Icons.Rounded.PersonOff, null)
                        Spacer(Modifier.width(Space.xs))
                        Text("No-show")
                    }
                }
            }
        }
    }
}

@Composable
private fun WaitingRow(token: QueueToken, label: String?, waitMinutes: Int?, onRemove: () -> Unit, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    QCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = Space.lg, top = Space.sm, bottom = Space.sm, end = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            Text("#${token.number}", style = MaterialTheme.typography.titleLarge, modifier = Modifier.width(64.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(label ?: "Customer", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (token.source == TokenSource.WALK_IN) {
                        Spacer(Modifier.width(Space.sm))
                        Pill("Walk-in", MaterialTheme.colorScheme.secondary)
                    }
                }
                Text(
                    listOfNotNull(token.serviceName, waitMinutes?.let { "in ~${formatMinutes(it)}" }).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Options for #${token.number}") }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Remove from queue") }, leadingIcon = { Icon(Icons.Rounded.PersonOff, null) }, onClick = { menu = false; onRemove() })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun WalkInSheet(services: List<Service>, onDismiss: () -> Unit, onAdd: (Service?, String) -> Unit) {
    var label by remember { mutableStateOf("") }
    var service by remember { mutableStateOf(services.firstOrNull()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = Space.screen).padding(bottom = Space.xl).navigationBarsPadding()) {
            Text("Add a walk-in", style = MaterialTheme.typography.headlineSmall)
            Text("Gives a token to someone without the app.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Space.lg))
            OutlinedTextField(
                value = label,
                onValueChange = { label = it.take(40) },
                label = { Text("Name or note (optional)") },
                supportingText = { Text("Only you see this.") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )
            if (services.isNotEmpty()) {
                Spacer(Modifier.height(Space.md))
                Text("Service", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(Space.sm))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    services.forEach { sv ->
                        FilterChip(service?.id == sv.id, onClick = { service = sv }, label = { Text("${sv.name} · ${sv.durationMinutes}m") }, shape = CircleShape)
                    }
                }
            }
            Spacer(Modifier.height(Space.xl))
            PrimaryButton("Issue token", onClick = { onAdd(service, label) }, icon = Icons.Rounded.PersonAdd, modifier = Modifier.fillMaxWidth())
        }
    }
}
