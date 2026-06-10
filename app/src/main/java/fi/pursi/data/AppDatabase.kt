package fi.pursi.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import fi.pursi.data.dao.BoatDao
import fi.pursi.data.dao.DownloadJobDao
import fi.pursi.data.dao.SavedRouteDao
import fi.pursi.data.dao.TrackDao
import fi.pursi.data.dao.TrackSummaryDao
import fi.pursi.data.dao.WaypointDao
import fi.pursi.data.dao.WfsFeatureDao
import fi.pursi.data.model.Boat
import fi.pursi.data.model.DownloadJob
import fi.pursi.data.model.RouteWaypoint
import fi.pursi.data.model.SavedRoute
import fi.pursi.data.model.TrackPoint
import fi.pursi.data.model.TrackSummary
import fi.pursi.data.model.Waypoint
import fi.pursi.data.model.WfsFeature

@Database(
    entities = [
        TrackPoint::class,
        Waypoint::class,
        WfsFeature::class,
        Boat::class,
        TrackSummary::class,
        SavedRoute::class,
        RouteWaypoint::class,
        DownloadJob::class
    ],
    version = 9,
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

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pursi_database"
                )
                    .addMigrations(MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
