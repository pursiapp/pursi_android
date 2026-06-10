package fi.pursi.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import fi.pursi.data.model.Waypoint
import kotlinx.coroutines.flow.Flow

@Dao
interface WaypointDao {

    @Query("SELECT * FROM waypoints ORDER BY `order` ASC, createdAt ASC")
    fun getAll(): Flow<List<Waypoint>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(waypoint: Waypoint): Long

    @Update
    suspend fun update(waypoint: Waypoint)

    @Delete
    suspend fun delete(waypoint: Waypoint)

    @Query("DELETE FROM waypoints")
    suspend fun deleteAll()

    @Query("SELECT * FROM waypoints WHERE id = :id")
    suspend fun getById(id: Long): Waypoint?
}
