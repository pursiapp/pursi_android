package app.pursi.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "route_waypoints", indices = [Index("routeId")])
data class RouteWaypoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val routeId: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val order: Int
)
