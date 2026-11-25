package com.sabil.qnow.ui.customer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Navigation
import androidx.compose.material.icons.rounded.TwoWheeler
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sabil.qnow.data.route.Route
import com.sabil.qnow.data.route.RouteRepository
import com.sabil.qnow.domain.model.GeoLocation
import com.sabil.qnow.domain.repository.LocationRepository
import com.sabil.qnow.ui.components.PrimaryButton
import com.sabil.qnow.ui.components.QCard
import com.sabil.qnow.ui.i18n.Text
import com.sabil.qnow.ui.theme.Space
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import javax.inject.Inject

data class RouteUiState(
    val loading: Boolean = true,
    val here: GeoLocation? = null,
    val route: Route? = null,
    /** False when we don't have the customer's location (permission off or GPS not ready). */
    val located: Boolean = true,
)

@HiltViewModel
class RouteViewModel @Inject constructor(
    private val location: LocationRepository,
    private val routes: RouteRepository,
) : ViewModel() {
    val state = MutableStateFlow(RouteUiState())
    private var started = false

    /** Loads the way to [shop] and refreshes it every minute while the screen is open. */
    fun start(shop: GeoLocation) {
        if (started) return
        started = true
        viewModelScope.launch {
            while (true) {
                val here = runCatching { location.lastKnownLocation() }.getOrNull()
                if (here == null) {
                    state.value = RouteUiState(loading = false, located = false)
                } else {
                    val moved = state.value.here?.let { com.sabil.qnow.domain.model.kmBetween(it, here) > 0.05 } ?: true
                    if (moved || state.value.route == null) {
                        state.value = state.value.copy(here = here, route = routes.route(here, shop), loading = false, located = true)
                    }
                }
                delay(60_000)
            }
        }
    }
}

internal enum class Travel(val label: String, val navMode: String) { WALK("Walk", "w"), BIKE("Bike", "l"), CAR("Car", "d") }

/**
 * "How to get there": a map with the road route from the customer to the shop, travel times,
 * when to leave (from the live wait), and a button that starts turn-by-turn navigation.
 */
@Composable
fun RouteCard(shop: GeoLocation, shopName: String, waitMinutes: Int?, modifier: Modifier = Modifier, vm: RouteViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var travel by remember { mutableStateOf(Travel.BIKE) }
    LaunchedEffect(shop) { vm.start(shop) }

    QCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(Space.lg), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            Text("How to get there", style = MaterialTheme.typography.titleMedium)
            Box(Modifier.fillMaxWidth().height(220.dp).clip(MaterialTheme.shapes.large)) {
                RouteMap(shop, s.here, s.route)
                if (s.loading) CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
            val r = s.route
            if (r != null) {
                val minutes = when (travel) { Travel.WALK -> r.walkMinutes; else -> r.rideMinutes }
                Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Travel.entries.forEach { t ->
                        FilterChip(
                            selected = travel == t, onClick = { travel = t }, shape = CircleShape,
                            leadingIcon = {
                                Icon(
                                    when (t) { Travel.WALK -> Icons.AutoMirrored.Rounded.DirectionsWalk; Travel.BIKE -> Icons.Rounded.TwoWheeler; Travel.CAR -> Icons.Rounded.DirectionsCar },
                                    null, Modifier.size(18.dp),
                                )
                            },
                            label = { Text(t.label) },
                        )
                    }
                }
                val km = if (r.distanceMeters < 1000) "${r.distanceMeters.toInt() / 10 * 10} m" else "%.1f km".format(r.distanceMeters / 1000)
                Text("$km · about $minutes min" + if (!r.onRoads) " (straight-line estimate)" else "", style = MaterialTheme.typography.titleSmall)
                if (waitMinutes != null) {
                    val leaveIn = waitMinutes - minutes - 3 // arrive a few minutes early
                    Text(
                        if (leaveIn <= 0) "Leave now to reach in time for your turn." else "Leave in about $leaveIn min to reach just in time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (leaveIn <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    )
                }
            } else if (!s.located) {
                Text("Turn on location to see the way from where you are.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(Space.xs))
            PrimaryButton("Start navigation", onClick = { navigate(context, shop, shopName, travel) }, icon = Icons.Rounded.Navigation, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Turn-by-turn in Google Maps (free for the customer); any maps app or the browser as backup. */
internal fun navigate(context: Context, to: GeoLocation, name: String, travel: Travel) {
    val gmaps = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${to.lat},${to.lng}&mode=${travel.navMode}"))
        .setPackage("com.google.android.apps.maps")
    val anyMaps = Intent(Intent.ACTION_VIEW, Uri.parse("geo:${to.lat},${to.lng}?q=${to.lat},${to.lng}(${Uri.encode(name)})"))
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${to.lat},${to.lng}&travelmode=" +
        when (travel) { Travel.WALK -> "walking"; Travel.BIKE -> "two-wheeler"; Travel.CAR -> "driving" }))
    for (i in listOf(gmaps, anyMaps, web)) {
        try { context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); return } catch (_: ActivityNotFoundException) { }
    }
}

@Composable
private fun RouteMap(shop: GeoLocation, here: GeoLocation?, route: Route?) {
    val context = LocalContext.current
    val brand = MaterialTheme.colorScheme.primary.toArgb()
    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            controller.setZoom(15.0)
            controller.setCenter(GeoPoint(shop.lat, shop.lng))
        }
    }
    DisposableEffect(Unit) { map.onResume(); onDispose { map.onPause(); map.onDetach() } }
    LaunchedEffect(here, route) {
        if (map.repository == null) return@LaunchedEffect
        map.overlays.clear()
        route?.let { r ->
            map.overlays.add(Polyline(map).apply {
                setPoints(r.points.map { GeoPoint(it.lat, it.lng) })
                outlinePaint.color = brand
                outlinePaint.strokeWidth = 12f
                outlinePaint.strokeCap = android.graphics.Paint.Cap.ROUND
            })
        }
        map.overlays.add(Marker(map).apply {
            position = GeoPoint(shop.lat, shop.lng)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setInfoWindow(null)
        })
        if (here != null) {
            map.overlays.add(com.sabil.qnow.ui.components.MeOverlay(context, map, brand).apply { enableMyLocation() })
            val pts = (route?.points ?: emptyList()) + listOf(here, shop)
            val box = BoundingBox.fromGeoPoints(pts.map { GeoPoint(it.lat, it.lng) })
            map.post { runCatching { map.zoomToBoundingBox(box.increaseByScale(1.4f), false) } }
        }
        map.invalidate()
    }
    AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
}
