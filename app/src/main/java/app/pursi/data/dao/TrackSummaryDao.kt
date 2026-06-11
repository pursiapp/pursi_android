package app.pursi.data.dao

import androidx.room.*
import app.pursi.data.model.TrackSummary
import kotlinx.coroutines.flow.Flow

@Dao
interface TrackSummaryDao {
    @Query("SELECT * FROM track_summaries WHERE isRecording = 0 ORDER BY startTime DESC")
    fun getAllRecorded(): Flow<List<TrackSummary>>

    @Query("SELECT * FROM track_summaries WHERE id = :id")
    suspend fun getById(id: String): TrackSummary?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(summary: TrackSummary)

    @Update
    suspend fun update(summary: TrackSummary)

    @Delete
    suspend fun delete(summary: TrackSummary)

    @Query("UPDATE track_summaries SET isRecording = 0, endTime = :end, pointCount = :pts, distanceNm = :dist, maxSpeedKn = :maxSp WHERE id = :id")
    suspend fun finalize(id: String, end: Long, pts: Int, dist: Double, maxSp: Float?)

    @Query("SELECT COUNT(*) FROM track_summaries WHERE isRecording = 1")
    suspend fun hasActiveRecording(): Int

    @Query("SELECT * FROM track_summaries WHERE isRecording = 1")
    suspend fun getActiveRecordings(): List<TrackSummary>
}
