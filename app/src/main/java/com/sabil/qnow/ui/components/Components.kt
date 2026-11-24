package com.sabil.qnow.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.automirrored.rounded.StarHalf
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sabil.qnow.domain.model.BusinessStatus
import com.sabil.qnow.ui.theme.QNowTheme
import com.sabil.qnow.ui.theme.Space

/** Big, rounded primary action with a pressed-scale animation and a built-in loading state. */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    height: Dp = 56.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "press")
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        interactionSource = interaction,
        modifier = modifier.heightIn(min = height).scale(scale),
        shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
    ) {
        AnimatedContent(loading, label = "loading") { isLoading ->
            if (isLoading) {
                CircularProgressIndicator(Modifier.size(22.dp), color = contentColor, strokeWidth = 2.5.dp)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (icon != null) {
                        Icon(icon, contentDescription = null, Modifier.size(20.dp))
                        Spacer(Modifier.width(Space.sm))
                    }
                    Text(text, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(Space.sm))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Card with the app's rounded corners and a soft tonal surface instead of heavy shadows. */
@Composable
fun QCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    content: @Composable () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    if (onClick != null) {
        Surface(onClick = onClick, modifier = modifier, shape = shape, color = color, tonalElevation = 0.dp, shadowElevation = 1.dp) { content() }
    } else {
        Surface(modifier = modifier, shape = shape, color = color, tonalElevation = 0.dp, shadowElevation = 1.dp) { content() }
    }
}

@Composable
fun StatusPill(status: BusinessStatus, modifier: Modifier = Modifier) {
    val ext = QNowTheme.extended
    val (label, color) = when (status) {
        BusinessStatus.OPEN -> "Open now" to ext.open
        BusinessStatus.ON_BREAK -> "On a break" to ext.onBreak
        BusinessStatus.CLOSED -> "Closed" to ext.closed
    }
    Pill(label, color, modifier, live = status == BusinessStatus.OPEN)
}

@Composable
fun Pill(label: String, color: Color, modifier: Modifier = Modifier, live: Boolean = false) {
    Row(
        modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (live) LiveDot(color) else Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Pulsing dot that signals "this is live". */
@Composable
fun LiveDot(color: Color, size: Dp = 7.dp) {
    val transition = rememberInfiniteTransition(label = "live")
    val alpha by transition.animateFloat(
        initialValue = 1f, targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "alpha",
    )
    Box(Modifier.size(size).clip(CircleShape).background(color.copy(alpha = alpha)))
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(MaterialTheme.shapes.small).clickable(onClick = onAction).padding(Space.sm),
            )
        }
    }
}

@Composable
fun RatingRow(rating: Double, count: Long, modifier: Modifier = Modifier, compact: Boolean = false) {
    val star = QNowTheme.extended.star
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (count == 0L) {
            Icon(Icons.Rounded.StarOutline, null, tint = star, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text("New", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Row
        }
        if (compact) {
            Icon(Icons.Rounded.Star, null, tint = star, modifier = Modifier.size(16.dp))
        } else {
            repeat(5) { i ->
                val icon = when {
                    rating >= i + 1 -> Icons.Rounded.Star
                    rating >= i + 0.5 -> Icons.AutoMirrored.Rounded.StarHalf
                    else -> Icons.Rounded.StarOutline
                }
                Icon(icon, null, tint = star, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(
            "%.1f".format(rating) + if (compact) "" else "  ($count)",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Friendly illustration-style empty state: a gradient badge with an icon, a title and a hint. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(Space.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(QNowTheme.extended.heroGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(Space.lg))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.xs))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(Space.lg))
            SecondaryButton(actionLabel, onAction)
        }
    }
}

/** Animated placeholder block for loading states. */
@Composable
fun Shimmer(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.medium) {
    val base = MaterialTheme.colorScheme.surfaceContainerHigh
    val highlight = MaterialTheme.colorScheme.surfaceContainerLowest
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue = -400f, targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1300, easing = LinearEasing)),
        label = "x",
    )
    Box(
        modifier.clip(shape).background(
            Brush.linearGradient(listOf(base, highlight, base), start = androidx.compose.ui.geometry.Offset(x, 0f),
                end = androidx.compose.ui.geometry.Offset(x + 400f, 200f)),
        ),
    )
}

@Composable
fun ShimmerList(count: Int = 3, itemHeight: Dp = 88.dp, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.md)) {
        repeat(count) { Shimmer(Modifier.fillMaxWidth().height(itemHeight), MaterialTheme.shapes.large) }
    }
}

/** A large animated token number, the visual hero of the token screens. */
@Composable
fun TokenNumber(number: Int, modifier: Modifier = Modifier, color: Color = Color.White, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.displayLarge) {
    AnimatedContent(
        targetState = number,
        transitionSpec = {
            (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
        },
        label = "token",
        modifier = modifier,
    ) { n ->
        Text("#$n", style = style, color = color)
    }
}

/** Numbers-only chip for a token in a queue (never shows who holds it). */
@Composable
fun TokenChip(number: Int, highlighted: Boolean = false, serving: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val bg = when {
        highlighted -> scheme.secondary
        serving -> QNowTheme.extended.open.copy(alpha = 0.18f)
        else -> scheme.surfaceContainerHigh
    }
    val fg = when {
        highlighted -> scheme.onSecondary
        serving -> QNowTheme.extended.open
        else -> scheme.onSurface
    }
    Box(
        Modifier.size(width = 64.dp, height = 44.dp).clip(MaterialTheme.shapes.small).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text("$number", style = MaterialTheme.typography.titleMedium, color = fg, fontWeight = FontWeight.Bold)
    }
}

fun formatMinutes(minutes: Int): String = when {
    minutes <= 0 -> "Now"
    minutes < 60 -> "$minutes min"
    else -> "${minutes / 60} h ${minutes % 60} min"
}

/** Price in the shop's own currency (e.g. ₹150), whole amounts without decimals. */
fun formatPrice(price: Double, currencyCode: String = "INR"): String =
    java.text.NumberFormat.getCurrencyInstance(Money.displayLocale(currencyCode)).apply {
        currency = Money.currency(currencyCode)
        if (price % 1.0 == 0.0) maximumFractionDigits = 0
    }.format(price)

object Money {
    /** The currency used in a country (two-letter code), falling back to rupees. */
    fun currencyForCountry(countryCode: String?): String =
        countryCode?.let { runCatching { java.util.Currency.getInstance(java.util.Locale("", it)).currencyCode }.getOrNull() } ?: "INR"

    fun currency(code: String): java.util.Currency =
        runCatching { java.util.Currency.getInstance(code) }.getOrElse { java.util.Currency.getInstance("INR") }

    fun symbol(code: String): String = currency(code).getSymbol(displayLocale(code))

    /** Rupees read best with Indian grouping (1,00,000); other currencies use the phone's locale. */
    fun displayLocale(code: String): java.util.Locale =
        if (code == "INR") java.util.Locale("en", "IN") else java.util.Locale.getDefault()
}

fun formatTime(minutesOfDay: Int): String {
    val h = minutesOfDay / 60
    val m = minutesOfDay % 60
    val suffix = if (h < 12) "AM" else "PM"
    val h12 = when (val x = h % 12) { 0 -> 12; else -> x }
    return "%d:%02d %s".format(h12, m, suffix)
}
