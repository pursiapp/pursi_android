package fi.pursi.datasource.fi

import fi.pursi.datasource.core.BoundingBox
import fi.pursi.datasource.core.WeatherProvider
import fi.pursi.weather.FmiClient
import fi.pursi.weather.ForecastPoint
import fi.pursi.weather.LightningStrike
import fi.pursi.weather.StationWeatherData
import fi.pursi.weather.WaterLevelStation
import fi.pursi.weather.WaveStation
import javax.inject.Inject

class FmiWeatherProvider @Inject constructor(
    private val client: FmiClient
) : WeatherProvider {
    override val providerId = "fi-fmi"
    override val displayName = "Ilmatieteen laitos"
    override val coverage = BoundingBox(58.5, 70.5, 19.0, 32.0)
    override val priority = 1

    override suspend fun getNearestWeatherStations(
        latitude: Double, longitude: Double, maxStations: Int
    ) = client.getNearestWeatherStations(latitude, longitude, maxStations)

    override suspend fun getWaveObservations(
        latitude: Double, longitude: Double
    ) = client.getWaveObservations(latitude, longitude)

    override suspend fun getForecast(
        latitude: Double, longitude: Double
    ) = client.getForecast(latitude, longitude)

    override suspend fun getLightningData(
        minLat: Double, minLng: Double, maxLat: Double, maxLng: Double
    ) = client.getLightningData(minLat, minLng, maxLat, maxLng)

    override suspend fun getWaterLevelData(
        latitude: Double, longitude: Double
    ) = client.getMareographData(latitude, longitude)
}
