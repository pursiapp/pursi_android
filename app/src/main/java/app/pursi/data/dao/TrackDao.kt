package app.pursi.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.pursi.data.model.TrackPoint
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackDao {

    @Insert
    suspend fun insert(point: TrackPoint)

    @Insert
    suspend fun insertAll(points: List<TrackPoint>)

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY timestamp ASC")
    fun getTrackPoints(trackId: String): Flow<List<TrackPoint>>

    @Query("SELECT * FROM track_points WHERE trackId = :trackId ORDER BY timestamp ASC")
    suspend fun getTrackPointsSync(trackId: String): List<TrackPoint>

    @Query("SELECT DISTINCT trackId FROM track_points ORDER BY timestamp DESC")
    fun getAllTrackIds(): Flow<List<String>>

    @Query("DELETE FROM track_points WHERE trackId = :trackId")
    suspend fun deleteTrack(trackId: String)

    @Query("""
        SELECT * FROM track_points 
        WHERE timestamp BETWEEN :startTime AND :endTime 
        ORDER BY timestamp ASC
    """)
    fun getPointsBetween(startTime: Long, endTime: Long): Flow<List<TrackPoint>>

    @Query("SELECT COUNT(*) FROM track_points WHERE trackId = :trackId")
    suspend fun getPointCount(trackId: String): Int
}
