package app.pursi.datasource.core

class SourceResolver(
    val chartProviders: Set<ChartProvider>,
    val weatherProviders: Set<WeatherProvider>,
    val warningProviders: Set<WarningProvider>,
    val marineFeatureProviders: Set<MarineFeatureProvider>,
    val radarProviders: Set<RadarProvider> = emptySet(),
    val aisProviders: Set<AisProvider> = emptySet()
) {
    private inline fun <reified T : Any> resolveAll(
        lat: Double, lon: Double,
        providers: Collection<T>,
        crossinline hasCoverage: (T) -> Boolean,
        crossinline priority: (T) -> Int
    ): List<T> = providers
        .filter { hasCoverage(it) }
        .sortedByDescending { priority(it) }

    private inline fun <reified T : Any> resolveBest(
        lat: Double, lon: Double,
        providers: Collection<T>,
        crossinline hasCoverage: (T) -> Boolean,
        crossinline priority: (T) -> Int
    ): T? = resolveAll(lat, lon, providers, hasCoverage, priority).firstOrNull()

    fun chartProvidersFor(lat: Double, lon: Double): List<ChartProvider> =
        resolveAll(lat, lon, chartProviders, { it.coverage.contains(lat, lon) }, { it.priority })

    fun primaryChartProviderFor(lat: Double, lon: Double): ChartProvider? =
        chartProvidersFor(lat, lon).firstOrNull()

    fun weatherProviderFor(lat: Double, lon: Double): WeatherProvider? =
        resolveBest(lat, lon, weatherProviders, { it.coverage.contains(lat, lon) }, { it.priority })

    fun allWeatherProvidersFor(lat: Double, lon: Double): List<WeatherProvider> =
        resolveAll(lat, lon, weatherProviders, { it.coverage.contains(lat, lon) }, { it.priority })

    fun warningProviderFor(lat: Double, lon: Double): WarningProvider? =
        resolveBest(lat, lon, warningProviders, { it.coverage.contains(lat, lon) }, { it.priority })

    fun marineFeatureProvidersFor(lat: Double, lon: Double): List<MarineFeatureProvider> =
        resolveAll(lat, lon, marineFeatureProviders, { it.coverage.contains(lat, lon) }, { it.priority })

    @Deprecated("Use marineFeatureProvidersFor() which returns all matching providers")
    fun marineFeatureProviderFor(lat: Double, lon: Double): MarineFeatureProvider? =
        marineFeatureProvidersFor(lat, lon).firstOrNull()

    fun radarProviderFor(lat: Double, lon: Double): RadarProvider? =
        resolveBest(lat, lon, radarProviders, { it.coverage.contains(lat, lon) }, { it.priority })

    fun aisProviderFor(lat: Double, lon: Double): AisProvider? =
        resolveBest(lat, lon, aisProviders, { it.coverage.contains(lat, lon) }, { it.priority })
}
