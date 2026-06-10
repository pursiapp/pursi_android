package fi.pursi.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import fi.pursi.ais.DigitrafficClient
import fi.pursi.data.AppDatabase
import fi.pursi.datasource.core.AisProvider
import fi.pursi.datasource.core.ChartProvider
import fi.pursi.datasource.core.FeatureRenderer
import fi.pursi.datasource.core.FeatureRendererRegistry
import fi.pursi.datasource.core.IalaFeatureRenderer
import fi.pursi.datasource.core.JsonProviderLoader
import fi.pursi.datasource.core.MarineFeatureProvider
import fi.pursi.datasource.core.PropertyMapper
import fi.pursi.datasource.core.PropertyMapperRegistry
import fi.pursi.datasource.core.RadarProvider
import fi.pursi.datasource.core.SourceResolver
import fi.pursi.datasource.core.WarningProvider
import fi.pursi.datasource.core.WeatherProvider
//import fi.pursi.datasource.dk.DanishChartProvider
import fi.pursi.datasource.fi.DigitrafficAisProvider
import fi.pursi.datasource.fi.FinnishChartProvider
import fi.pursi.datasource.fi.FinnishMarineFeatureProvider
import fi.pursi.datasource.fi.FinnishPropertyMapper
import fi.pursi.datasource.fi.FinnishWarningProvider
import fi.pursi.datasource.fi.FmiRadarProvider
import fi.pursi.datasource.fi.FmiWeatherProvider
//import fi.pursi.datasource.global.EmodnetChartProvider
import fi.pursi.datasource.global.RainViewerRadarProvider
import fi.pursi.datasource.global.RainViewerTimestampSource
import fi.pursi.datasource.global.RainViewerTimestampSourceImpl
import fi.pursi.datasource.no.MetNorwayWarningProvider
import fi.pursi.datasource.no.MetNorwayWeatherProvider
import fi.pursi.datasource.no.NorwegianChartProvider
import fi.pursi.datasource.se.SmhiWarningProvider
import fi.pursi.data.wfs.WfsClient
import fi.pursi.weather.FmiClient
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataSourceModule {

    @Provides
    @IntoSet
    fun provideFinnishChartProvider(loader: JsonProviderLoader): ChartProvider =
        FinnishChartProvider(loader)

    @Provides
    @IntoSet
    fun provideFmiWeatherProvider(client: FmiClient): WeatherProvider =
        FmiWeatherProvider(client)

    @Provides
    @IntoSet
    fun provideMetNorwayWeatherProvider(client: OkHttpClient): WeatherProvider =
        MetNorwayWeatherProvider(client)

    @Provides
    @IntoSet
    fun provideFinnishWarningProvider(client: FmiClient): WarningProvider =
        FinnishWarningProvider(client)

    @Provides
    @IntoSet
    fun provideFinnishMarineFeatureProvider(
        db: AppDatabase,
        client: WfsClient,
        loader: JsonProviderLoader
    ): MarineFeatureProvider =
        FinnishMarineFeatureProvider(db, client, loader)

    @Provides
    @IntoSet
    fun provideNorwegianChartProvider(loader: JsonProviderLoader): ChartProvider =
        NorwegianChartProvider(loader)

//    @Provides
//    @IntoSet
//    fun provideDanishChartProvider(): ChartProvider =
//        DanishChartProvider()

//    @Provides
//    @IntoSet
//    fun provideEmodnetChartProvider(): ChartProvider =
//        EmodnetChartProvider()

    @Provides
    @IntoSet
    fun provideMetNorwayWarningProvider(client: OkHttpClient): WarningProvider =
        MetNorwayWarningProvider(client)

    @Provides
    @IntoSet
    fun provideSmhiWarningProvider(client: OkHttpClient): WarningProvider =
        SmhiWarningProvider(client)

    @Provides
    @IntoSet
    fun provideFmiRadarProvider(): RadarProvider =
        FmiRadarProvider()

    @Provides
    @Singleton
    fun provideRainViewerTimestampSource(client: OkHttpClient): RainViewerTimestampSource =
        RainViewerTimestampSourceImpl(client)

    @Provides
    @IntoSet
    fun provideRainViewerRadarProvider(source: RainViewerTimestampSource): RadarProvider =
        RainViewerRadarProvider(source)

    @Provides
    @IntoSet
    fun provideDigitrafficAisProvider(client: DigitrafficClient): AisProvider =
        DigitrafficAisProvider(client)

    @Provides
    @Singleton
    fun providePropertyMapperRegistry(
        mappers: Set<@JvmSuppressWildcards PropertyMapper>
    ): PropertyMapperRegistry = PropertyMapperRegistry(mappers)

    @Provides
    @IntoSet
    fun provideFinnishPropertyMapper(): PropertyMapper =
        FinnishPropertyMapper()

    @Provides
    @Singleton
    fun provideFeatureRendererRegistry(
        renderers: Set<@JvmSuppressWildcards FeatureRenderer>
    ): FeatureRendererRegistry = FeatureRendererRegistry(renderers)

    @Provides
    @IntoSet
    fun provideIalaFeatureRenderer(
        mapperRegistry: PropertyMapperRegistry
    ): FeatureRenderer = IalaFeatureRenderer(mapperRegistry)

    @Provides
    @Singleton
    fun provideSourceResolver(
        charts: Set<@JvmSuppressWildcards ChartProvider>,
        weather: Set<@JvmSuppressWildcards WeatherProvider>,
        warnings: Set<@JvmSuppressWildcards WarningProvider>,
        features: Set<@JvmSuppressWildcards MarineFeatureProvider>,
        radars: Set<@JvmSuppressWildcards RadarProvider> = emptySet(),
        ais: Set<@JvmSuppressWildcards AisProvider> = emptySet()
    ): SourceResolver =
        SourceResolver(charts, weather, warnings, features, radars, ais)
}
