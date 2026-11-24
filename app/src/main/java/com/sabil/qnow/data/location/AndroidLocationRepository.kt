package com.sabil.qnow.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.sabil.qnow.domain.model.GeoLocation
import com.sabil.qnow.domain.repository.LocationRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

@Singleton
class AndroidLocationRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : LocationRepository {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    private fun hasPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    override suspend fun lastKnownLocation(): GeoLocation? {
        if (!hasPermission()) return null
        return runCatching {
            val loc = client.lastLocation.await()
                ?: client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token).await()
            loc?.let { GeoLocation(it.latitude, it.longitude) }
        }.getOrNull()
    }

    override suspend fun geocode(query: String): GeoLocation? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent() || query.isBlank()) return@withContext null
        val geocoder = Geocoder(context, Locale.getDefault())
        runCatching {
            val address = if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocationName(query, 1) { cont.resume(it.firstOrNull()) }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(query, 1)?.firstOrNull()
            }
            address?.let { GeoLocation(it.latitude, it.longitude) }
        }.getOrNull()
    }

    override suspend fun countryCode(location: GeoLocation): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val geocoder = Geocoder(context, Locale.getDefault())
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(location.lat, location.lng, 1) { cont.resume(it.firstOrNull()?.countryCode) }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(location.lat, location.lng, 1)?.firstOrNull()?.countryCode
            }
        }.getOrNull()
    }

    override suspend fun reverseGeocode(location: GeoLocation): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        val geocoder = Geocoder(context, Locale.getDefault())
        runCatching {
            val address = if (Build.VERSION.SDK_INT >= 33) {
                suspendCancellableCoroutine { cont ->
                    geocoder.getFromLocation(location.lat, location.lng, 1) { cont.resume(it.firstOrNull()) }
                }
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocation(location.lat, location.lng, 1)?.firstOrNull()
            }
            address?.getAddressLine(0)
        }.getOrNull()
    }
}
