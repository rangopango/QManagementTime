package com.sabil.qnow.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import com.sabil.qnow.domain.model.GeoLocation
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter

/** Google Maps version of the QNow map: same pins and callbacks as the OpenStreetMap one. */
@OptIn(kotlinx.coroutines.FlowPreview::class)
@Composable
internal fun GoogleQMap(
    camera: MapCamera?,
    pins: List<MapPin>,
    modifier: Modifier,
    showMyLocation: Boolean,
    dark: Boolean,
    onPinClick: (String) -> Unit,
    onMapTap: (GeoLocation) -> Unit,
    onCameraSettled: (GeoLocation, Double) -> Unit,
) {
    val context = LocalContext.current
    val cameraState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(20.5937, 78.9629), 4.5f)
    }
    val brand = MaterialTheme.colorScheme.primary.toArgb()
    val located = showMyLocation && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    LaunchedEffect(camera) {
        camera ?: return@LaunchedEffect
        cameraState.animate(CameraUpdateFactory.newCameraPosition(
            CameraPosition.fromLatLngZoom(LatLng(camera.center.lat, camera.center.lng), camera.zoom.toFloat()),
        ))
    }
    LaunchedEffect(cameraState) {
        snapshotFlow { cameraState.isMoving to cameraState.position }
            .filter { (moving, _) -> !moving }
            .debounce(600)
            .collect { (_, p) -> onCameraSettled(GeoLocation(p.target.latitude, p.target.longitude), p.zoom.toDouble()) }
    }

    GoogleMap(
        modifier = modifier,
        cameraPositionState = cameraState,
        properties = MapProperties(
            isMyLocationEnabled = located,
            mapStyleOptions = if (dark) MapStyleOptions(DARK_STYLE) else null,
        ),
        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false, mapToolbarEnabled = false),
        onMapClick = { onMapTap(GeoLocation(it.latitude, it.longitude)) },
    ) {
        pins.forEach { pin ->
            val icon = remember(pin) { BitmapDescriptorFactory.fromBitmap(pinIcon(context, pin, pin.fill?.toArgb() ?: brand).bitmap) }
            Marker(
                state = rememberUpdatedMarkerState(LatLng(pin.location.lat, pin.location.lng)),
                icon = icon,
                anchor = androidx.compose.ui.geometry.Offset(0.5f, 0.5f),
                zIndex = if (pin.selected) 1f else 0f,
                onClick = { onPinClick(pin.id); true },
            )
        }
    }
}

private const val DARK_STYLE = """
[{"elementType":"geometry","stylers":[{"color":"#1c1931"}]},
{"elementType":"labels.text.fill","stylers":[{"color":"#b7b1cc"}]},
{"elementType":"labels.text.stroke","stylers":[{"color":"#0f0d1a"}]},
{"featureType":"road","elementType":"geometry","stylers":[{"color":"#2f2a48"}]},
{"featureType":"water","elementType":"geometry","stylers":[{"color":"#0b0914"}]}]
"""
