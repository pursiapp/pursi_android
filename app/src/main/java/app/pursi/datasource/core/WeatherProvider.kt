package app.pursi.datasource.core

import app.pursi.weather.ForecastPoint
import app.pursi.weather.LightningStrike
import app.pursi.weather.StationWeatherData
import app.pursi.weather.WaterLevelStation
import app.pursi.weather.WaveStation

interface WeatherProvider {
    val providerId: String
    val displayName: String
    val coverage: BoundingBox
    val priority: Int
        get() = 0

    val supportsObservations: Boolean
        get() = true
    val supportsWaves: Boolean
        get() = true
    val supportsForecast: Boolean
        get() = true
    val supportsLightning: Boolean
        get() = true
    val supportsWaterLevel: Boolean
        get() = false

    suspend fun getNearestWeatherStations(
        latitude: Double, longitude: Double, maxStations: Int = 5
    ): List<StationWeatherData>

    suspend fun getWaveObservations(
        latitude: Double, longitude: Double
    ): List<WaveStation>

    /**
     * Returns the hourly forecast for the given point.
     *
     * Contract on returned [ForecastPoint]s:
     *  - `timestamp` MUST be the validity time (epoch seconds, UTC). Consumers
     *    (wind meter, forecast tab) use this to select "now" — provider must
     *    populate it from the source's own time field, not from device clock.
     *  - `referenceTime` SHOULD be the model-run/issuance time (epoch seconds).
     *    May default to 0 if the source does not expose it.
     *
     * Implementations throw [WeatherProviderException] on fetch/parse failure
     * (so [CompositeWeatherProvider] can fall back to the next provider) and
     * return an empty list only when there is genuinely no data.
     */
    suspend fun getForecast(
        latitude: Double, longitude: Double
    ): List<ForecastPoint>

    suspend fun getLightningData(
        minLat: Double, minLng: Double, maxLat: Double, maxLng: Double
    ): List<LightningStrike>

    suspend fun getWaterLevelData(
        latitude: Double, longitude: Double
    ): List<WaterLevelStation> = emptyList()
}
