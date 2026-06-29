package app.pursi.datasource.core

import app.pursi.weather.ForecastPoint
import app.pursi.weather.LightningStrike
import app.pursi.weather.StationWeatherData
import app.pursi.weather.WaterLevelStation
import app.pursi.weather.WaveStation

/**
 * Returns the first non-empty result across primary and fallbacks.
 *
 * Behaviour:
 *  - Empty list from a provider is treated as "no data for this point" and
 *    triggers fallback to the next provider.
 *  - [WeatherProviderException] from a provider is treated as a fetch/parse
 *    failure and also triggers fallback (R2 — fully provider-independent:
 *    a failing primary doesn't block a working secondary).
 *  - Other exceptions propagate (caller error, e.g. CancellationException).
 */
class CompositeWeatherProvider(
    private val primary: WeatherProvider,
    private val fallbacks: List<WeatherProvider>
) : WeatherProvider by primary {

    private suspend fun <T> withFallback(name: String, fetch: suspend (WeatherProvider) -> T): T {
        try {
            val r = fetch(primary)
            if (!isEmptyResult(r)) return r
        } catch (e: WeatherProviderException) {
            // fall through to fallbacks
        }
        for (fb in fallbacks) {
            try {
                val r = fetch(fb)
                if (!isEmptyResult(r)) return r
            } catch (e: WeatherProviderException) {
                // try next
            }
        }
        return fetch(primary) // primary already returned empty; final answer
    }

    private fun isEmptyResult(r: Any?): Boolean = when (r) {
        is List<*> -> r.isEmpty()
        else -> false
    }

    override suspend fun getNearestWeatherStations(
        latitude: Double, longitude: Double, maxStations: Int
    ): List<StationWeatherData> = withFallback("stations") { p ->
        p.getNearestWeatherStations(latitude, longitude, maxStations)
    }

    override suspend fun getWaveObservations(
        latitude: Double, longitude: Double
    ): List<WaveStation> = withFallback("waves") { p ->
        p.getWaveObservations(latitude, longitude)
    }

    override suspend fun getForecast(
        latitude: Double, longitude: Double
    ): List<ForecastPoint> = withFallback("forecast") { p ->
        p.getForecast(latitude, longitude)
    }

    override suspend fun getLightningData(
        minLat: Double, minLng: Double, maxLat: Double, maxLng: Double
    ): List<LightningStrike> = withFallback("lightning") { p ->
        p.getLightningData(minLat, minLng, maxLat, maxLng)
    }

    override suspend fun getWaterLevelData(
        latitude: Double, longitude: Double
    ): List<WaterLevelStation> = withFallback("waterLevel") { p ->
        p.getWaterLevelData(latitude, longitude)
    }
}
