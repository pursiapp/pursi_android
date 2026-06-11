package app.pursi.datasource.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceResolverTest {

    private val finlandBbox = BoundingBox(58.5, 70.5, 19.0, 32.0)
    private val swedenBbox = BoundingBox(55.0, 69.0, 10.0, 24.0)
    private val norwayBbox = BoundingBox(57.0, 72.0, 4.0, 32.0)
    private val finlandOutside = BoundingBox(50.0, 55.0, 0.0, 10.0)

    private val finnishChart = TestChartProvider("fi", "Finland", finlandBbox)
    private val swedishChart = TestChartProvider("se", "Sweden", swedenBbox)
    private val norwegianChart = TestChartProvider("no", "Norway", norwayBbox)

    private val finnishWeather = TestWeatherProvider("fi-fmi", finlandBbox)
    private val norwegianWeather = TestWeatherProvider("no-met", norwayBbox)

    private val finnishWarning = TestWarningProvider("fi-warn", finlandBbox)
    private val norwegianWarning = TestWarningProvider("no-warn", norwayBbox)

    @Test
    fun `primaryChartProviderFor returns Finnish provider inside Finland`() {
        val resolver = SourceResolver(
            chartProviders = setOf(finnishChart, swedishChart, norwegianChart),
            weatherProviders = emptySet(),
            warningProviders = emptySet(),
            marineFeatureProviders = emptySet()
        )
        val provider = resolver.primaryChartProviderFor(60.1, 24.9)
        assertNotNull(provider)
        assertEquals("fi", provider?.providerId)
    }

    @Test
    fun `primaryChartProviderFor returns null outside all coverages`() {
        val resolver = SourceResolver(
            chartProviders = setOf(finnishChart),
            weatherProviders = emptySet(),
            warningProviders = emptySet(),
            marineFeatureProviders = emptySet()
        )
        val provider = resolver.primaryChartProviderFor(52.0, 5.0)
        assertNull(provider)
    }

    @Test
    fun `chartProvidersFor returns multiple providers in overlapping regions`() {
        val resolver = SourceResolver(
            chartProviders = setOf(finnishChart, swedishChart, norwegianChart),
            weatherProviders = emptySet(),
            warningProviders = emptySet(),
            marineFeatureProviders = emptySet()
        )
        val providers = resolver.chartProvidersFor(60.0, 20.0)
        assertTrue(providers.size >= 2)
    }

    @Test
    fun `weatherProviderFor returns null where no weather provider covers`() {
        val resolver = SourceResolver(
            chartProviders = setOf(finnishChart),
            weatherProviders = setOf(finnishWeather),
            warningProviders = emptySet(),
            marineFeatureProviders = emptySet()
        )
        val provider = resolver.weatherProviderFor(55.0, 10.0)
        assertNull(provider)
    }

    @Test
    fun `weatherProviderFor returns correct provider based on location`() {
        val resolver = SourceResolver(
            chartProviders = emptySet(),
            weatherProviders = setOf(finnishWeather, norwegianWeather),
            warningProviders = emptySet(),
            marineFeatureProviders = emptySet()
        )
        assertEquals("no-met", resolver.weatherProviderFor(62.0, 5.0)?.providerId)
        assertEquals("fi-fmi", resolver.weatherProviderFor(62.0, 25.0)?.providerId)
    }

    @Test
    fun `weatherProviderFor prefers higher priority in overlapping areas`() {
        val lowPrio = TestWeatherProvider("low", finlandBbox, priority = 0)
        val highPrio = TestWeatherProvider("high", finlandBbox, priority = 1)
        val resolver = SourceResolver(
            chartProviders = emptySet(),
            weatherProviders = setOf(lowPrio, highPrio),
            warningProviders = emptySet(),
            marineFeatureProviders = emptySet()
        )
        assertEquals("high", resolver.weatherProviderFor(60.0, 24.0)?.providerId)
    }

    @Test
    fun `Empty provider sets return null`() {
        val resolver = SourceResolver(
            chartProviders = emptySet(),
            weatherProviders = emptySet(),
            warningProviders = emptySet(),
            marineFeatureProviders = emptySet()
        )
        assertNull(resolver.primaryChartProviderFor(60.0, 24.0))
        assertNull(resolver.weatherProviderFor(60.0, 24.0))
        assertNull(resolver.warningProviderFor(60.0, 24.0))
        assertNull(resolver.marineFeatureProviderFor(60.0, 24.0))
    }

    @Test
    fun `warningProviderFor returns Finnish provider in Finnish waters`() {
        val resolver = SourceResolver(
            chartProviders = emptySet(),
            weatherProviders = emptySet(),
            warningProviders = setOf(finnishWarning, norwegianWarning),
            marineFeatureProviders = emptySet()
        )
        assertEquals("fi-warn", resolver.warningProviderFor(62.0, 25.0)?.providerId)
    }

    @Test
    fun `marineFeatureProviderFor returns null when no providers registered`() {
        val resolver = SourceResolver(
            chartProviders = emptySet(),
            weatherProviders = emptySet(),
            warningProviders = emptySet(),
            marineFeatureProviders = emptySet()
        )
        assertNull(resolver.marineFeatureProviderFor(60.0, 24.0))
    }
}

private class TestChartProvider(
    override val providerId: String,
    override val displayName: String,
    override val coverage: BoundingBox
) : ChartProvider {
    override val attribution = "Test"
    override val layers = emptyList<ChartLayer>()
}

private class TestWeatherProvider(
    override val providerId: String,
    override val coverage: BoundingBox,
    override val priority: Int = 0
) : WeatherProvider {
    override val displayName = "Test"
    override suspend fun getNearestWeatherStations(
        latitude: Double, longitude: Double, maxStations: Int
    ): List<app.pursi.weather.StationWeatherData> = emptyList()
    override suspend fun getWaveObservations(
        latitude: Double, longitude: Double
    ): List<app.pursi.weather.WaveStation> = emptyList()
    override suspend fun getForecast(
        latitude: Double, longitude: Double
    ): List<app.pursi.weather.ForecastPoint> = emptyList()
    override suspend fun getLightningData(
        minLat: Double, minLng: Double, maxLat: Double, maxLng: Double
    ): List<app.pursi.weather.LightningStrike> = emptyList()
}

private class TestWarningProvider(
    override val providerId: String,
    override val coverage: BoundingBox
) : WarningProvider {
    override val displayName = "Test"
    override val supportedLanguages = listOf("fi", "en")
    override suspend fun getMarineWarnings(
        language: String, latitude: Double, longitude: Double
    ): List<app.pursi.weather.MarineWarning> = emptyList()
}
