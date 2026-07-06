package app.pursi.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import app.pursi.data.dao.BoatDao
import app.pursi.data.dao.DownloadJobDao
import app.pursi.data.dao.EmodnetDepthSampleDao
import app.pursi.data.dao.SavedRouteDao
import app.pursi.data.dao.TrackDao
import app.pursi.data.dao.TrackSummaryDao
import app.pursi.data.dao.WaypointDao
import app.pursi.data.dao.WfsFeatureDao
import app.pursi.data.model.Boat
import app.pursi.data.model.DownloadJob
import app.pursi.data.model.EmodnetDepthSample
import app.pursi.data.model.RouteWaypoint
import app.pursi.data.model.SavedRoute
import app.pursi.data.model.TrackPoint
import app.pursi.data.model.TrackSummary
import app.pursi.data.model.Waypoint
import app.pursi.data.model.WfsFeature

@Database(
    entities = [
        TrackPoint::class,
        Waypoint::class,
        WfsFeature::class,
        Boat::class,
        TrackSummary::class,
        SavedRoute::class,
        RouteWaypoint::class,
        DownloadJob::class,
        EmodnetDepthSample::class
    ],
    version = 11,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun waypointDao(): WaypointDao
    abstract fun wfsFeatureDao(): WfsFeatureDao
    abstract fun boatDao(): BoatDao
    abstract fun trackSummaryDao(): TrackSummaryDao
    abstract fun savedRouteDao(): SavedRouteDao
    abstract fun downloadJobDao(): DownloadJobDao
    abstract fun emodnetDepthSampleDao(): EmodnetDepthSampleDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS navtex_messages")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {}
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {}
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE INDEX IF NOT EXISTS index_track_points_timestamp ON track_points(timestamp)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DELETE FROM wfs_features")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS emodnet_depth_samples (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        gridKey TEXT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        depthM REAL NOT NULL,
                        fetchedAt INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
                    )"""
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_emodnet_depth_samples_gridKey ON emodnet_depth_samples(gridKey)")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("DROP TABLE IF EXISTS emodnet_depth_samples")
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS emodnet_depth_samples (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        gridKey TEXT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        depthAvgM REAL NOT NULL,
                        depthMinM REAL NOT NULL DEFAULT 0,
                        depthMaxM REAL NOT NULL DEFAULT 0,
                        fetchedAt INTEGER NOT NULL DEFAULT (strftime('%s','now') * 1000)
                    )"""
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_emodnet_depth_samples_gridKey ON emodnet_depth_samples(gridKey)")
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pursi_database"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
