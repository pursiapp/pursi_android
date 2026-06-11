package app.pursi.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import app.pursi.data.AppDatabase
import app.pursi.data.dao.BoatDao
import app.pursi.data.dao.DownloadJobDao
import app.pursi.data.dao.SavedRouteDao
import app.pursi.data.dao.TrackDao
import app.pursi.data.dao.TrackSummaryDao
import app.pursi.data.dao.WaypointDao
import app.pursi.data.dao.WfsFeatureDao
import app.pursi.location.TrackRecorder
import app.pursi.map.NetworkMonitor
import app.pursi.map.TileStorage
import app.pursi.map.ParallelTileDownloader
import app.pursi.map.DownloadManager
import app.pursi.map.RectangleTileCalculator

import app.pursi.navigation.BoatManager

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getInstance(context)
    }

    @Provides
    fun provideTrackDao(db: AppDatabase): TrackDao = db.trackDao()

    @Provides
    fun provideWaypointDao(db: AppDatabase): WaypointDao = db.waypointDao()

    @Provides
    fun provideWfsFeatureDao(db: AppDatabase): WfsFeatureDao = db.wfsFeatureDao()

    @Provides
    fun provideBoatDao(db: AppDatabase): BoatDao = db.boatDao()

    @Provides
    fun provideTrackSummaryDao(db: AppDatabase): TrackSummaryDao = db.trackSummaryDao()

    @Provides
    fun provideSavedRouteDao(db: AppDatabase): SavedRouteDao = db.savedRouteDao()

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideTrackRecorder(trackDao: app.pursi.data.dao.TrackDao): TrackRecorder {
        return TrackRecorder(trackDao)
    }

    @Provides
    @Singleton
    fun provideBoatManager(boatDao: app.pursi.data.dao.BoatDao): BoatManager {
        return BoatManager(boatDao)
    }

    @Provides
    @Singleton
    fun provideNetworkMonitor(
        @ApplicationContext context: Context
    ): NetworkMonitor = NetworkMonitor(context)

    @Provides
    fun provideDownloadJobDao(db: AppDatabase): DownloadJobDao = db.downloadJobDao()

    @Provides
    @Singleton
    fun provideTileStorage(
        @ApplicationContext context: Context
    ): TileStorage = TileStorage(context)

    @Provides
    @Singleton
    fun provideRectangleTileCalculator(): RectangleTileCalculator = RectangleTileCalculator()

    @Provides
    @Singleton
    fun provideParallelTileDownloader(
        client: OkHttpClient,
        tileStorage: TileStorage
    ): ParallelTileDownloader = ParallelTileDownloader(client, tileStorage)

    @Provides
    @Singleton
    fun provideDownloadManager(
        @ApplicationContext context: Context,
        dao: DownloadJobDao,
        tileStorage: TileStorage,
        calculator: RectangleTileCalculator,
        downloader: ParallelTileDownloader
    ): DownloadManager = DownloadManager(context, dao, tileStorage, calculator, downloader)

}
