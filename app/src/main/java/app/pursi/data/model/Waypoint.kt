package app.pursi.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "waypoints")
data class Waypoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val description: String? = null,
    val color: Int = 0xFF2196F3.toInt(),
    val createdAt: Long = System.currentTimeMillis(),
    val order: Int = 0
)
