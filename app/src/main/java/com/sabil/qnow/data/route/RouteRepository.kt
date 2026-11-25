package com.sabil.qnow.data.route

import com.sabil.qnow.domain.model.GeoLocation
import com.sabil.qnow.domain.model.kmBetween
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/** A path along real roads, with how long it takes by bike/car and on foot. */
data class Route(
    val points: List<GeoLocation>,
    val distanceMeters: Double,
    val rideMinutes: Int,
    val walkMinutes: Int,
    /** False when no road route could be found and [points] is just a straight line. */
    val onRoads: Boolean,
)

/**
 * Road routes from OSRM (free, open-source routing on OpenStreetMap data, no key or billing).
 * Only the two points (you and the shop) are sent, nothing else. If the routing server can't be
 * reached, a straight line with an estimate is returned so the customer still sees the way.
 */
@Singleton
class RouteRepository @Inject constructor() {

    suspend fun route(from: GeoLocation, to: GeoLocation): Route = withContext(Dispatchers.IO) {
        runCatching { osrm(from, to) }.getOrElse { straight(from, to) }
    }

    private fun osrm(from: GeoLocation, to: GeoLocation): Route {
        val url = "https://router.project-osrm.org/route/v1/driving/" +
            "${from.lng},${from.lat};${to.lng},${to.lat}?overview=full&geometries=geojson"
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 15_000
            setRequestProperty("User-Agent", "QNow-Android")
        }
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val r = json.getJSONArray("routes").getJSONObject(0)
        val coords = r.getJSONObject("geometry").getJSONArray("coordinates")
        val points = (0 until coords.length()).map { i -> coords.getJSONArray(i).let { GeoLocation(it.getDouble(1), it.getDouble(0)) } }
        val meters = r.getDouble("distance")
        // City traffic in India is slower than the router's free-flow estimate: add 40 %.
        val ride = (r.getDouble("duration") * 1.4 / 60).toInt().coerceAtLeast(1)
        return Route(points, meters, ride, walkMinutes(meters), onRoads = true)
    }

    private fun straight(from: GeoLocation, to: GeoLocation): Route {
        val meters = kmBetween(from, to) * 1000 * 1.3 // roads are rarely straight
        return Route(listOf(from, to), meters, (meters / 1000 / 20 * 60).toInt().coerceAtLeast(1), walkMinutes(meters), onRoads = false)
    }

    private fun walkMinutes(meters: Double) = (meters / 80).toInt().coerceAtLeast(1) // ~4.8 km/h
}
