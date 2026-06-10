package fi.pursi.data.dao

import androidx.room.*
import fi.pursi.data.model.RouteWaypoint
import fi.pursi.data.model.SavedRoute
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedRouteDao {
    @Query("SELECT * FROM saved_routes ORDER BY createdAt DESC")
    fun getAll(): Flow<List<SavedRoute>>

    @Query("SELECT * FROM saved_routes WHERE id = :id")
    suspend fun getById(id: String): SavedRoute?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(route: SavedRoute)

    @Update
    suspend fun update(route: SavedRoute)

    @Delete
    suspend fun delete(route: SavedRoute)

    @Query("SELECT * FROM route_waypoints WHERE routeId = :routeId ORDER BY `order` ASC")
    fun getWaypoints(routeId: String): Flow<List<RouteWaypoint>>

    @Query("SELECT * FROM route_waypoints WHERE routeId = :routeId ORDER BY `order` ASC")
    suspend fun getWaypointsSync(routeId: String): List<RouteWaypoint>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoint(waypoint: RouteWaypoint)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWaypoints(waypoints: List<RouteWaypoint>)

    @Query("DELETE FROM route_waypoints WHERE routeId = :routeId")
    suspend fun deleteWaypoints(routeId: String)
}
