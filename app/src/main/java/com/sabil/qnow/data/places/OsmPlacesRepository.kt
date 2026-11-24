package com.sabil.qnow.data.places

import com.sabil.qnow.domain.model.GeoLocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/** A real barber/salon/parlour from public map data that hasn't joined QNow. Not bookable. */
data class OutsideShop(
    val id: String,
    val name: String,
    val kind: String,
    val location: GeoLocation,
    val address: String?,
)

/**
 * Finds hairdressers, barbers and beauty parlours near a point using OpenStreetMap's free
 * Overpass service. No key or cost. Results are cached per area to keep requests rare,
 * as the service's fair-use policy asks.
 */
@Singleton
class OsmPlacesRepository @Inject constructor() {
    private val cache = HashMap<String, List<OutsideShop>>()

    suspend fun nearby(center: GeoLocation, radiusMeters: Int = 5000): List<OutsideShop> {
        // ~1 km grid, so small pans reuse the same answer.
        val key = "${(center.lat * 100).roundToInt()}:${(center.lng * 100).roundToInt()}:$radiusMeters"
        cache[key]?.let { return it }
        val result = withContext(Dispatchers.IO) { runCatching { query(center, radiusMeters) }.getOrDefault(emptyList()) }
        if (result.isNotEmpty()) cache[key] = result
        return result
    }

    private fun query(c: GeoLocation, r: Int): List<OutsideShop> {
        val around = "around:$r,${c.lat},${c.lng}"
        val ql = """
            [out:json][timeout:20];
            (
              nwr["shop"~"^(hairdresser|barber|beauty)$"]($around);
              nwr["amenity"="beauty_salon"]($around);
            );
            out center 80;
        """.trimIndent()
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 25_000
            setRequestProperty("User-Agent", "QNow-Android (queue app)")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        }
        conn.outputStream.use { it.write(("data=" + URLEncoder.encode(ql, "UTF-8")).toByteArray()) }
        if (conn.responseCode != 200) return emptyList()
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val elements = json.optJSONArray("elements") ?: return emptyList()
        return (0 until elements.length()).mapNotNull { i ->
            val e = elements.getJSONObject(i)
            val tags = e.optJSONObject("tags") ?: return@mapNotNull null
            val name = tags.optString("name").ifBlank { tags.optString("name:en") }.ifBlank { return@mapNotNull null }
            val lat = if (e.has("lat")) e.getDouble("lat") else e.optJSONObject("center")?.optDouble("lat") ?: return@mapNotNull null
            val lng = if (e.has("lon")) e.getDouble("lon") else e.optJSONObject("center")?.optDouble("lon") ?: return@mapNotNull null
            val kind = when (tags.optString("shop")) {
                "barber" -> "Barber"
                "beauty" -> "Beauty parlour"
                "hairdresser" -> if (tags.optString("hairdresser") == "barber") "Barber" else "Salon"
                else -> "Beauty parlour"
            }
            val address = listOf(tags.optString("addr:street"), tags.optString("addr:city"))
                .filter { it.isNotBlank() }.joinToString(", ").ifBlank { null }
            OutsideShop("osm-${e.optString("type")}-${e.optLong("id")}", name, kind, GeoLocation(lat, lng), address)
        }
    }

    private companion object {
        const val ENDPOINT = "https://overpass-api.de/api/interpreter"
    }
}
