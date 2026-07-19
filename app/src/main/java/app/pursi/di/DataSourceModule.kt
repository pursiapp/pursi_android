package app.pursi.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import app.pursi.ais.DigitrafficClient
import app.pursi.data.AppDatabase
import app.pursi.datasource.core.AisProvider
import app.pursi.datasource.core.ChartProvider
import app.pursi.datasource.core.DepthProvider
import app.pursi.datasource.core.FeatureRenderer
import app.pursi.datasource.core.FeatureRendererRegistry
import app.pursi.datasource.core.IalaFeatureRenderer
import app.pursi.datasource.core.JsonProviderLoader
import app.pursi.datasource.core.MarineFeatureProvider
import app.pursi.datasource.core.PropertyMapper
import app.pursi.datasource.core.PropertyMapperRegistry
import app.pursi.datasource.core.RadarProvider
import app.pursi.datasource.core.SourceResolver
import app.pursi.datasource.core.WarningProvider
import app.pursi.datasource.core.WeatherProvider
import app.pursi.datasource.fi.TraficomDepthProvider
import app.pursi.datasource.global.EmodnetDepthProvider
//import app.pursi.datasource.dk.DanishChartProvider
import app.pursi.datasource.fi.DigitrafficAisProvider
// import app.pursi.datasource.fi.FinnishChartProvider  // TEMP: Traficom raster charts disabled
import app.pursi.datasource.fi.FinnishMarineFeatureProvider
import app.pursi.datasource.fi.FinnishPropertyMapper
import app.pursi.datasource.fi.FinnishWarningProvider
import app.pursi.datasource.fi.FmiRadarCapabilities
import app.pursi.datasource.fi.FmiRadarProvider
import app.pursi.datasource.fi.FmiWeatherProvider
import app.pursi.datasource.global.EmodnetDepthClient
import app.pursi.datasource.global.RainViewerRadarProvider
import app.pursi.datasource.global.RainViewerTimestampSource
import app.pursi.datasource.global.RainViewerTimestampSourceImpl
import app.pursi.datasource.no.MetNorwayWarningProvider
import app.pursi.datasource.no.MetNorwayWeatherProvider
// import app.pursi.datasource.no.NorwegianChartProvider  // TEMP: Norwegian raster charts disabled
import app.pursi.datasource.se.SmhiWarningProvider
import app.pursi.data.wfs.WfsClient
import app.pursi.weather.FmiClient
import app.pursi.datasource.fi.FintrafficSeaWarningsClient
import app.pursi.datasource.fi.FintrafficSeaWarningsProvider
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataSourceModule {

    // TEMP: Traficom raster charts disabled — OSM fallback will be shown in Finland
    //    @Provides
    //    @IntoSet
    //    fun provideFinnishChartProvider(loader: JsonProviderLoader): ChartProvider =
    //        FinnishChartProvider(loader)

    // TEMP: No raster chart providers active — provide empty set
    @Provides
    fun provideChartProviders(): Set<@JvmSuppressWildcards ChartProvider> = emptySet()

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

    // TEMP: Norwegian raster charts disabled — OSM fallback will be shown in Norway
    //    @Provides
    //    @IntoSet
    //    fun provideNorwegianChartProvider(loader: JsonProviderLoader): ChartProvider =
    //        NorwegianChartProvider(loader)

//    @Provides
//    @IntoSet
//    fun provideDanishChartProvider(): ChartProvider =
//        DanishChartProvider()

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
    fun provideFintrafficSeaWarningsProvider(client: FintrafficSeaWarningsClient): WarningProvider =
        FintrafficSeaWarningsProvider(client)

    @Provides
    @IntoSet
    fun provideFmiRadarProvider(caps: FmiRadarCapabilities): RadarProvider =
        FmiRadarProvider(caps)

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
    @IntoSet
    fun provideTraficomDepthProvider(dao: app.pursi.data.dao.WfsFeatureDao): DepthProvider =
        TraficomDepthProvider(dao)

    @Provides
    @IntoSet
    fun provideEmodnetDepthProvider(client: OkHttpClient): DepthProvider =
        EmodnetDepthProvider(client)

    @Provides
    @Singleton
    fun provideEmodnetDepthSampleDao(db: AppDatabase): app.pursi.data.dao.EmodnetDepthSampleDao =
        db.emodnetDepthSampleDao()

    @Provides
    @Singleton
    fun provideEmodnetDepthClient(client: OkHttpClient): EmodnetDepthClient =
        EmodnetDepthClient(client)

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
        ais: Set<@JvmSuppressWildcards AisProvider> = emptySet(),
        depths: Set<@JvmSuppressWildcards DepthProvider> = emptySet()
    ): SourceResolver =
        SourceResolver(charts, weather, warnings, features, radars, ais, depths)
}
