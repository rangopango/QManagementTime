package com.sabil.qnow.ui.customer

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Celebration
import androidx.compose.material.icons.rounded.ConfirmationNumber
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sabil.qnow.domain.model.TokenStatus
import com.sabil.qnow.ui.components.EmptyState
import com.sabil.qnow.ui.components.Pill
import com.sabil.qnow.ui.components.PrimaryButton
import com.sabil.qnow.ui.components.QCard
import com.sabil.qnow.ui.components.SecondaryButton
import com.sabil.qnow.ui.components.ShimmerList
import com.sabil.qnow.ui.components.TokenNumber
import com.sabil.qnow.ui.components.formatMinutes
import com.sabil.qnow.ui.navigation.ReviewRoute
import com.sabil.qnow.ui.theme.Brand
import com.sabil.qnow.ui.theme.QNowTheme
import com.sabil.qnow.ui.theme.Space

// ---------------------------------------------------------------- My token

@Composable
fun TokenScreen(
    onBack: () -> Unit,
    onReview: (ReviewRoute) -> Unit,
    onBusiness: (String) -> Unit,
    vm: TokenViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var confirmCancel by remember { mutableStateOf(false) }
    val token = state.token
    val live = state.live

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState())) {
        // Hero
        Box(Modifier.fillMaxWidth().background(QNowTheme.extended.heroGradient)) {
            Column(Modifier.statusBarsPadding().padding(bottom = Space.xxl)) {
                Row(Modifier.padding(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back", tint = Color.White) }
                    Text(
                        live?.business?.name ?: "Your token",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    if (live != null) TextButton(onClick = { onBusiness(live.business.id) }) { Text("Shop", color = Color.White) }
                }
                if (token != null) {
                    TokenHero(state)
                } else if (!state.loading) {
                    Text("Token not found", color = Color.White, modifier = Modifier.padding(Space.xl))
                }
            }
        }

        if (token != null && live != null) {
            Column(Modifier.padding(Space.screen), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                if (token.status == TokenStatus.WAITING) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Metric(Icons.Rounded.People, "${state.estimate?.peopleAhead ?: 0}", "people ahead", Modifier.weight(1f))
                        Metric(Icons.Rounded.ConfirmationNumber, "${state.estimate?.position ?: "-"}", "your position", Modifier.weight(1f))
                    }
                    QCard(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(Modifier.padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.NotificationsActive, null, tint = MaterialTheme.colorScheme.secondary)
                            Spacer(Modifier.width(Space.md))
                            Text(
                                "We'll notify you at about 15 and 5 minutes to go, and when it's your turn.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
                LiveQueueCard(live, highlight = token.number)
                when {
                    token.status == TokenStatus.WAITING -> SecondaryButton(
                        "Cancel my token", onClick = { confirmCancel = true }, modifier = Modifier.fillMaxWidth(),
                        enabled = !state.cancelling,
                    )
                    token.status == TokenStatus.DONE && !state.reviewed -> PrimaryButton(
                        "Rate your visit", onClick = { vm.reviewRoute()?.let(onReview) },
                        icon = Icons.Rounded.Star, modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.navigationBarsPadding())
            }
        } else if (state.loading) {
            ShimmerList(3, modifier = Modifier.padding(Space.screen))
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text("Cancel token #${token?.number}?") },
            text = { Text("You'll lose your place in the queue. You can book again, but you'll join at the back.") },
            confirmButton = { TextButton(onClick = { confirmCancel = false; vm.cancel() }) { Text("Cancel token") } },
            dismissButton = { TextButton(onClick = { confirmCancel = false }) { Text("Keep it") } },
        )
    }
}

@Composable
private fun TokenHero(state: TokenUiState) {
    val token = state.token ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = Space.xl), horizontalAlignment = Alignment.CenterHorizontally) {
        val wait = state.estimate?.waitMinutes ?: 0
        // Ring fills up as the wait counts down (full at the moment it's your turn).
        val progress by animateFloatAsState(
            when (token.status) {
                TokenStatus.WAITING -> (1f - wait / 60f).coerceIn(0.05f, 0.97f)
                else -> 1f
            },
            tween(800), label = "ring",
        )
        Box(Modifier.size(232.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 14.dp.toPx()
                val inset = stroke / 2
                drawArc(Color.White.copy(alpha = 0.15f), 0f, 360f, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke))
                drawArc(Brand.Coral, -90f, 360f * progress, false, Offset(inset, inset), Size(size.width - stroke, size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TOKEN", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = 0.75f))
                TokenNumber(token.number)
            }
        }
        Spacer(Modifier.height(Space.lg))
        AnimatedContent(token.status to wait, label = "status") { (status, minutes) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val (title, subtitle) = when (status) {
                    TokenStatus.WAITING -> formatMinutes(minutes) to if (minutes <= 5) "Almost there — head over now" else "estimated time remaining"
                    TokenStatus.SERVING -> "It's your turn!" to "Please go to the counter"
                    TokenStatus.GRACE -> "You've been called" to "Please come in now, or your token will be skipped"
                    TokenStatus.DONE -> "All done" to "Thanks for visiting"
                    TokenStatus.NO_SHOW -> "Token skipped" to "You weren't there when called. You can book again."
                    TokenStatus.CANCELLED -> "Token cancelled" to "You can book again anytime"
                }
                Text(title, style = MaterialTheme.typography.headlineLarge, color = Color.White, textAlign = TextAlign.Center)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f), textAlign = TextAlign.Center)
            }
        }
    }
}

// ---------------------------------------------------------------- My tokens

@Composable
fun MyTokensScreen(onToken: (String, String) -> Unit, onFind: () -> Unit, vm: MyTokensViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(Space.screen),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        item {
            Text("My tokens", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.statusBarsPadding().padding(vertical = Space.md))
        }
        when {
            state.loading -> item { ShimmerList(3) }
            state.items.isEmpty() -> item {
                EmptyState(
                    Icons.Rounded.ConfirmationNumber, "No tokens yet",
                    "Book a token at any shop and you can follow it live from here.",
                    actionLabel = "Find a shop", onAction = onFind,
                )
            }
            else -> items(state.items, key = { it.ref.businessId + it.ref.tokenId }) { item ->
                MyTokenRow(item, Modifier.animateItem()) { onToken(item.ref.businessId, item.ref.tokenId) }
            }
        }
    }
}

@Composable
private fun MyTokenRow(item: ActiveToken, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val ext = QNowTheme.extended
    val (label, color, icon) = when (item.token.status) {
        TokenStatus.WAITING -> Triple("Waiting", MaterialTheme.colorScheme.primary, Icons.Rounded.HourglassTop)
        TokenStatus.SERVING -> Triple("Your turn", ext.open, Icons.AutoMirrored.Rounded.DirectionsWalk)
        TokenStatus.GRACE -> Triple("Called", ext.onBreak, Icons.AutoMirrored.Rounded.DirectionsWalk)
        TokenStatus.DONE -> Triple("Completed", MaterialTheme.colorScheme.tertiary, Icons.Rounded.Celebration)
        TokenStatus.NO_SHOW -> Triple("Skipped", ext.closed, Icons.Rounded.HourglassTop)
        TokenStatus.CANCELLED -> Triple("Cancelled", MaterialTheme.colorScheme.onSurfaceVariant, Icons.Rounded.HourglassTop)
    }
    QCard(modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(Space.lg), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(56.dp).clip(MaterialTheme.shapes.medium).background(color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Text("#${item.token.number}", style = MaterialTheme.typography.titleMedium, color = color) }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(item.ref.businessName, style = MaterialTheme.typography.titleMedium)
                Text(
                    java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(item.ref.createdAtMillis)),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Pill(label, color, live = item.token.status.isActive)
        }
    }
}

// ---------------------------------------------------------------- Review

@Composable
fun ReviewScreen(onDone: () -> Unit, vm: ReviewViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.done) { if (state.done) onDone() }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .statusBarsPadding().navigationBarsPadding().imePadding()
            .verticalScroll(rememberScrollState()).padding(Space.screen),
    ) {
        IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Spacer(Modifier.height(Space.lg))
        Text("How was ${vm.route.businessName}?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Space.xs))
        Text(
            "Your review is anonymous. Other people see the stars and your comment, never your name or number.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.xl))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            (1..5).forEach { i ->
                val selected = i <= state.rating
                val scale by animateFloatAsState(if (selected) 1.15f else 1f, label = "star")
                IconButton(onClick = { vm.rate(i) }, modifier = Modifier.size(56.dp)) {
                    Icon(
                        if (selected) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                        "$i star${if (i > 1) "s" else ""}",
                        tint = if (selected) QNowTheme.extended.star else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size((40 * scale).dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.xl))
        OutlinedTextField(
            value = state.comment,
            onValueChange = vm::comment,
            label = { Text("Tell others about your visit (optional)") },
            minLines = 4,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("${state.comment.length}/500") },
        )
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Space.sm)) }
        Spacer(Modifier.height(Space.xl))
        PrimaryButton("Submit review", onClick = vm::submit, enabled = state.rating > 0, loading = state.sending, modifier = Modifier.fillMaxWidth())
    }
}

// ---------------------------------------------------------------- AI Style Try-On (placeholder)

/**
 * Entry point for the planned AI Style Try-On, where a customer uploads a photo to preview
 * styles. Not built yet: nothing is uploaded or processed here.
 */
@Composable
fun TryOnScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().padding(Space.screen)) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Spacer(Modifier.weight(1f))
        EmptyState(
            Icons.Rounded.AutoAwesome,
            "AI Style Try-On is coming soon",
            "Soon you'll be able to preview haircuts and styles on your own photo before you book. We'll let you know when it's ready.",
        )
        Spacer(Modifier.weight(1.4f))
    }
}
