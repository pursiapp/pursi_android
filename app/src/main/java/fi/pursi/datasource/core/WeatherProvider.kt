package fi.pursi.datasource.core

import fi.pursi.weather.ForecastPoint
import fi.pursi.weather.LightningStrike
import fi.pursi.weather.StationWeatherData
import fi.pursi.weather.WaterLevelStation
import fi.pursi.weather.WaveStation

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
