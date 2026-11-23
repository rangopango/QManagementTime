package com.sabil.qnow.ui.customer

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.sabil.qnow.domain.model.Business
import com.sabil.qnow.domain.model.BusinessCategory
import com.sabil.qnow.domain.model.BusinessStatus
import com.sabil.qnow.domain.model.GeoLocation
import com.sabil.qnow.domain.queue.LiveBusiness
import com.sabil.qnow.ui.components.PrimaryButton
import com.sabil.qnow.ui.components.RatingRow
import com.sabil.qnow.ui.components.StatusPill
import com.sabil.qnow.ui.components.formatMinutes
import com.sabil.qnow.ui.theme.QNowTheme
import com.sabil.qnow.ui.theme.Space
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter

@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
fun MapScreen(onBusiness: (String) -> Unit, vm: MapViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val camera = rememberCameraPositionState()
    val focus = LocalFocusManager.current
    val snackbar = remember { SnackbarHostState() }
    val dark = QNowTheme.extended.isDark

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        vm.onLocationPermission(result.values.any { it })
    }
    LaunchedEffect(Unit) {
        permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    LaunchedEffect(state.center) {
        state.center?.let {
            val zoom = if (state.hasLocation || state.query.isNotBlank()) 14f else 4.5f
            camera.animate(CameraUpdateFactory.newCameraPosition(CameraPosition.fromLatLngZoom(LatLng(it.lat, it.lng), zoom)))
        }
    }
    LaunchedEffect(camera) {
        snapshotFlow { camera.isMoving to camera.position }
            .filter { (moving, pos) -> !moving && pos.zoom >= 11f }
            .debounce(600)
            .collect { (_, pos) -> vm.onCameraSettled(GeoLocation(pos.target.latitude, pos.target.longitude)) }
    }
    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    Box(Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = camera,
            properties = MapProperties(
                isMyLocationEnabled = state.hasLocation,
                mapStyleOptions = if (dark) MapStyleOptions(DARK_MAP_STYLE) else null,
            ),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false),
            onMapClick = { vm.select(null); focus.clearFocus() },
        ) {
            state.businesses.forEach { biz ->
                val loc = biz.location ?: return@forEach
                val selected = state.selected?.business?.id == biz.id
                MarkerComposable(
                    keys = arrayOf<Any>(biz.id, biz.status, selected),
                    state = rememberUpdatedMarkerState(LatLng(loc.lat, loc.lng)),
                    title = biz.name,
                    onClick = { vm.select(biz.id); true },
                ) { BusinessPin(biz, selected) }
            }
        }

        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = Space.md)) {
            SearchBar(
                query = state.query,
                searching = state.searching,
                onQuery = vm::onQuery,
                onSearch = { focus.clearFocus(); vm.search() },
                onClear = { vm.onQuery("") },
            )
            Spacer(Modifier.height(Space.sm))
            CategoryChips(state.category, vm::filter)
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(Space.lg),
            horizontalAlignment = Alignment.End,
        ) {
            SmallFloatingActionButton(
                onClick = {
                    permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            ) { Icon(Icons.Rounded.MyLocation, "My location", tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.height(Space.md))
            AnimatedVisibility(
                state.selected != null,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                state.selected?.let { PinCard(it, onOpen = { onBusiness(it.business.id) }) }
            }
            if (state.selected == null && state.businesses.isEmpty() && state.center != null) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLowest, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "No QNow shops in this area yet. Try searching another area.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(Space.lg),
                    )
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun SearchBar(query: String, searching: Boolean, onQuery: (String) -> Unit, onSearch: () -> Unit, onClear: () -> Unit) {
    TextField(
        value = query,
        onValueChange = onQuery,
        placeholder = { Text("Search an area, street or city") },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            when {
                searching -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                query.isNotEmpty() -> IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, "Clear") }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier.padding(horizontal = Space.lg).fillMaxWidth().shadow(6.dp, CircleShape),
    )
}

@Composable
private fun CategoryChips(selected: BusinessCategory?, onSelect: (BusinessCategory?) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Space.lg),
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        val chipColors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        )
        FilterChip(selected == null, onClick = { onSelect(null) }, label = { Text("All") }, colors = chipColors, shape = CircleShape)
        BusinessCategory.entries.filter { it.enabled }.forEach { c ->
            FilterChip(selected == c, onClick = { onSelect(c) }, label = { Text(c.label) }, colors = chipColors, shape = CircleShape)
        }
    }
}

@Composable
private fun BusinessPin(biz: Business, selected: Boolean) {
    val ext = QNowTheme.extended
    val ring = when (biz.status) {
        BusinessStatus.OPEN -> ext.open
        BusinessStatus.ON_BREAK -> ext.onBreak
        BusinessStatus.CLOSED -> ext.closed
    }
    val size = if (selected) 52.dp else 40.dp
    Box(
        Modifier.size(size).clip(CircleShape).background(ext.heroGradient).border(3.dp, ring, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            biz.name.take(1).uppercase(),
            color = Color.White,
            style = if (selected) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun PinCard(live: LiveBusiness, onOpen: () -> Unit) {
    val biz = live.business
    Surface(
        onClick = onOpen,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(Space.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BusinessThumb(biz.photoUrls.firstOrNull(), biz.name, Modifier.size(56.dp))
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(biz.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    RatingRow(biz.rating, biz.ratingCount, compact = true)
                }
                StatusPill(biz.status)
            }
            Spacer(Modifier.height(Space.md))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                Metric(Icons.Rounded.People, "${live.summary.waitingCount}", "in queue", Modifier.weight(1f))
                Metric(Icons.Rounded.Schedule, formatMinutes(live.summary.newcomerWaitMinutes), "est. wait", Modifier.weight(1f))
            }
            Spacer(Modifier.height(Space.md))
            PrimaryButton("View & book", onClick = onOpen, modifier = Modifier.fillMaxWidth(), height = 48.dp)
        }
    }
}

@Composable
fun Metric(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainer).padding(Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.sm))
        Column {
            Text(value, style = MaterialTheme.typography.titleMedium)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val DARK_MAP_STYLE = """
[{"elementType":"geometry","stylers":[{"color":"#1c1931"}]},
{"elementType":"labels.text.fill","stylers":[{"color":"#b7b1cc"}]},
{"elementType":"labels.text.stroke","stylers":[{"color":"#0f0d1a"}]},
{"featureType":"poi","stylers":[{"visibility":"off"}]},
{"featureType":"road","elementType":"geometry","stylers":[{"color":"#2f2a48"}]},
{"featureType":"road","elementType":"labels.icon","stylers":[{"visibility":"off"}]},
{"featureType":"transit","stylers":[{"visibility":"off"}]},
{"featureType":"water","elementType":"geometry","stylers":[{"color":"#0b0914"}]}]
"""
