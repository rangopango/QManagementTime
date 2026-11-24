package com.sabil.qnow.ui.components

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.sabil.qnow.domain.model.GeoLocation
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

/** A pin on the map: a round badge with a letter and a status-coloured ring. */
data class MapPin(
    val id: String,
    val location: GeoLocation,
    val letter: String,
    val ring: Color,
    val selected: Boolean = false,
    /** Pin body colour; null = brand colour. Grey is used for shops not on QNow yet. */
    val fill: Color? = null,
)

/** Where the map should look. A new [key] moves the camera even to the same place. */
data class MapCamera(val center: GeoLocation, val zoom: Double, val key: Int = 0)

/**
 * OpenStreetMap view (osmdroid): free, no API key or billing account. Map data and tiles are
 * © OpenStreetMap contributors, credited in the corner as the licence requires.
 */
@Composable
fun OsmMap(
    camera: MapCamera?,
    pins: List<MapPin>,
    modifier: Modifier = Modifier,
    showMyLocation: Boolean = false,
    dark: Boolean = false,
    onPinClick: (String) -> Unit = {},
    onMapTap: (GeoLocation) -> Unit = {},
    onCameraSettled: (GeoLocation, Double) -> Unit = { _, _ -> },
) {
    // Google Maps when a key is configured (better street names), otherwise free OpenStreetMap.
    if (com.sabil.qnow.BuildConfig.GOOGLE_MAPS) {
        GoogleQMap(camera, pins, modifier, showMyLocation, dark, onPinClick, onMapTap, onCameraSettled)
    } else {
        OpenStreetQMap(camera, pins, modifier, showMyLocation, dark, onPinClick, onMapTap, onCameraSettled)
    }
}

@Composable
private fun OpenStreetQMap(
    camera: MapCamera?,
    pins: List<MapPin>,
    modifier: Modifier = Modifier,
    showMyLocation: Boolean = false,
    dark: Boolean = false,
    onPinClick: (String) -> Unit = {},
    onMapTap: (GeoLocation) -> Unit = {},
    onCameraSettled: (GeoLocation, Double) -> Unit = { _, _ -> },
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val pinClick by rememberUpdatedState(onPinClick)
    val tap by rememberUpdatedState(onMapTap)
    val settled by rememberUpdatedState(onCameraSettled)
    val gradientStart = MaterialTheme.colorScheme.primary.toArgb()

    val map = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            minZoomLevel = 3.0
            maxZoomLevel = 19.0
            isTilesScaledToDpi = true
            controller.setZoom(4.5)
            controller.setCenter(GeoPoint(20.5937, 78.9629))
            overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                    tap(GeoLocation(p.latitude, p.longitude)); return true
                }
                override fun longPressHelper(p: GeoPoint) = false
            }))
            addMapListener(DelayedMapListener(object : MapListener {
                override fun onScroll(event: ScrollEvent?) = report()
                override fun onZoom(event: ZoomEvent?) = report()
                private fun report(): Boolean {
                    settled(GeoLocation(mapCenter.latitude, mapCenter.longitude), zoomLevelDouble); return true
                }
            }, 600))
        }
    }

    // Set once the map is torn down; effects that finish later must not touch it (osmdroid crashes).
    val alive = remember { java.util.concurrent.atomic.AtomicBoolean(true) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            alive.set(false)
            map.onDetach()
        }
    }

    LaunchedEffect(camera) {
        camera ?: return@LaunchedEffect
        if (!alive.get()) return@LaunchedEffect
        map.controller.animateTo(GeoPoint(camera.center.lat, camera.center.lng), camera.zoom, 700L)
    }

    LaunchedEffect(dark) {
        if (!alive.get()) return@LaunchedEffect
        map.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
        map.invalidate()
    }

    LaunchedEffect(showMyLocation) {
        if (!alive.get()) return@LaunchedEffect
        map.overlays.removeAll { it is MyLocationNewOverlay }
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (showMyLocation && granted) {
            map.overlays.add(MyLocationNewOverlay(GpsMyLocationProvider(context), map).apply { enableMyLocation() })
        }
        map.invalidate()
    }

    LaunchedEffect(pins) {
        if (!alive.get() || map.repository == null) return@LaunchedEffect
        map.overlays.removeAll { it is Marker }
        pins.forEach { pin ->
            map.overlays.add(Marker(map).apply {
                position = GeoPoint(pin.location.lat, pin.location.lng)
                icon = pinIcon(context, pin, pin.fill?.toArgb() ?: gradientStart)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setInfoWindow(null)
                setOnMarkerClickListener { _, _ -> pinClick(pin.id); true }
            })
        }
        map.invalidate()
    }

    Box(modifier) {
        AndroidView(factory = { map })
        Text(
            "© OpenStreetMap contributors",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp)
                .background(Color.White.copy(alpha = 0.7f)).padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

internal fun pinIcon(context: Context, pin: MapPin, fill: Int): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val sizeDp = if (pin.selected) 52 else 40
    val px = (sizeDp * density).toInt()
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val ring = 3.5f * density
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    val r = px / 2f
    paint.color = pin.ring.toArgb()
    canvas.drawCircle(r, r, r, paint)
    paint.color = fill
    canvas.drawCircle(r, r, r - ring, paint)
    paint.color = android.graphics.Color.WHITE
    paint.textAlign = Paint.Align.CENTER
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textSize = px * 0.42f
    canvas.drawText(pin.letter, r, r - (paint.descent() + paint.ascent()) / 2, paint)
    return BitmapDrawable(context.resources, bitmap)
}
