package app.pursi.datasource.no

import app.pursi.datasource.core.BoundingBox
import app.pursi.datasource.core.WeatherProvider
import app.pursi.weather.ForecastPoint
import app.pursi.weather.LightningStrike
import app.pursi.weather.StationWeatherData
import app.pursi.weather.WaterLevelStation
import app.pursi.weather.WaveStation
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject

/**
 * MET Norway provides both ocean wave forecasts and standard weather forecasts.
 *
 * Wave data:    https://api.met.no/weatherapi/oceanforecast/2.0/complete
 * Weather:      https://api.met.no/weatherapi/locationforecast/2.0/compact
 * License:      CC BY 4.0 (free for commercial use)
 * Coverage:     NW Europe including Baltic Sea, North Sea, Norwegian Sea
 * Wave resolution: 4 km (coastal 800 m)
 * Forecast length: 9 days (waves), 10 days (weather)
 *
 * Note: Requires a valid User-Agent header per MET Norway API terms.
 * Does NOT provide wave period.
 */
class MetNorwayWeatherProvider @Inject constructor(
    private val client: OkHttpClient
) : WeatherProvider {
    override val providerId = "no-met-oceanforecast"
    override val displayName = "MET Norway Oceanforecast"
    override val coverage = BoundingBox(52.0, 72.0, -10.0, 35.0)

    override val supportsObservations = false
    override val supportsLightning = false
    override val supportsWaterLevel = false

    override suspend fun getNearestWeatherStations(
        latitude: Double, longitude: Double, maxStations: Int
    ): List<StationWeatherData> = emptyList()

    override suspend fun getLightningData(
        minLat: Double, minLng: Double, maxLat: Double, maxLng: Double
    ): List<LightningStrike> = emptyList()

    override suspend fun getWaterLevelData(
        latitude: Double, longitude: Double
    ): List<WaterLevelStation> = emptyList()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getWaveObservations(
        latitude: Double, longitude: Double
    ): List<WaveStation> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.met.no/weatherapi/oceanforecast/2.0/complete" +
                "?lat=$latitude&lon=$longitude"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Pursi/1.0 (marine navigation app; https://github.com/pursiapp/pursi_android)")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            parseWaveForecast(body, latitude, longitude)
        } catch (_: Exception) { emptyList() }
    }

    override suspend fun getForecast(
        latitude: Double, longitude: Double
    ): List<ForecastPoint> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.met.no/weatherapi/locationforecast/2.0/compact" +
                "?lat=$latitude&lon=$longitude"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Pursi/1.0 (marine navigation app; https://github.com/pursiapp/pursi_android)")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()
            val body = response.body?.string() ?: return@withContext emptyList()
            parseLocationForecast(body)
        } catch (_: Exception) { emptyList() }
    }

    private fun parseWaveForecast(
        geoJson: String, refLat: Double, refLon: Double
    ): List<WaveStation> {
        val root = json.parseToJsonElement(geoJson).jsonObject
        val features = root["features"]?.jsonArray ?: return emptyList()
        if (features.isEmpty()) return emptyList()

        // Find the time step closest to now
        val now = System.currentTimeMillis() / 1000
        var closestTs = Long.MAX_VALUE
        var closestFeature: JsonObject? = null

        for (element in features) {
            val feature = element.jsonObject
            val props = feature["properties"]?.jsonObject ?: continue
            val timeStr = props["time"]?.jsonPrimitive?.content ?: continue
            val ts = parseIso8601(timeStr)
            val diff = kotlin.math.abs(ts - now)
            if (diff < closestTs) {
                closestTs = diff
                closestFeature = feature
            }
        }

        val feature = closestFeature ?: return emptyList()
        val props = feature["properties"]?.jsonObject ?: return emptyList()
        val geometry = feature["geometry"]?.jsonObject ?: return emptyList()
        val coords = geometry["coordinates"]?.jsonArray ?: return emptyList()

        val lon = coords[0].jsonPrimitive.content.toDoubleOrNull() ?: refLon
        val lat = coords[1].jsonPrimitive.content.toDoubleOrNull() ?: refLat
        val timeStr = props["time"]?.jsonPrimitive?.content ?: ""

        val waveHeight = props["wave_height"]?.jsonPrimitive?.content?.toFloatOrNull()
        val waveDir = props["wave_direction"]?.jsonPrimitive?.content?.toFloatOrNull()
        val waterTemp = props["sea_water_temperature"]?.jsonPrimitive?.content?.toFloatOrNull()

        return listOf(
            WaveStation(
                stationName = "MET Norway Oceanforecast",
                latitude = lat, longitude = lon,
                timestamp = timeStr,
                waveHeightM = waveHeight,
                waveDirectionDeg = waveDir,
                waterTemperatureC = waterTemp,
                wavePeriodS = null
            )
        )
    }

    private fun parseLocationForecast(jsonStr: String): List<ForecastPoint> {
        val root = json.parseToJsonElement(jsonStr).jsonObject
        val props = root["properties"]?.jsonObject ?: return emptyList()
        val timeseries = props["timeseries"]?.jsonArray ?: return emptyList()
        val now = System.currentTimeMillis() / 1000
        return timeseries.mapNotNull { element ->
            try {
                val entry = element.jsonObject
                val timeStr = entry["time"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val ts = parseIso8601(timeStr)
                val data = entry["data"]?.jsonObject ?: return@mapNotNull null
                val instant = data["instant"]?.jsonObject ?: return@mapNotNull null
                val details = instant["details"]?.jsonObject ?: return@mapNotNull null

                val temp = details["air_temperature"]?.jsonPrimitive?.content?.toFloatOrNull()
                val wind = details["wind_speed"]?.jsonPrimitive?.content?.toFloatOrNull()
                val dir = details["wind_from_direction"]?.jsonPrimitive?.content?.toFloatOrNull()
                val cloud = details["cloud_area_fraction"]?.jsonPrimitive?.content?.toFloatOrNull()

                // Precipitation is in next_1_hours or next_6_hours summary
                var precip: Float? = null
                val next1h = data["next_1_hours"]?.jsonObject
                if (next1h != null) {
                    val sum = next1h["details"]?.jsonObject
                    precip = sum?.get("precipitation_amount")?.jsonPrimitive?.content?.toFloatOrNull()
                }
                if (precip == null) {
                    val next6h = data["next_6_hours"]?.jsonObject
                    if (next6h != null) {
                        val sum = next6h["details"]?.jsonObject
                        precip = sum?.get("precipitation_amount")?.jsonPrimitive?.content?.toFloatOrNull()
                    }
                }

                ForecastPoint(
                    timestamp = ts,
                    temperatureC = temp,
                    windSpeedMs = wind,
                    windDirectionDeg = dir,
                    cloudiness = cloud,
                    precipitationMm = precip
                )
            } catch (_: Exception) { null }
        }.sortedBy { it.timestamp }
    }

    private fun parseIso8601(timeStr: String): Long {
        try {
            val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
            return sdf.parse(timeStr)?.time?.let { it / 1000 } ?: 0L
        } catch (_: Exception) { return 0L }
    }
}
