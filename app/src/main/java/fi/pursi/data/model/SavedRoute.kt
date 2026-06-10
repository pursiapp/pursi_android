package fi.pursi.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_routes")
data class SavedRoute(
    @PrimaryKey val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val sourceTrackId: String? = null,
    val waypointCount: Int = 0,
    val totalDistanceNm: Double = 0.0,
    val estimatedHours: Double? = null,
    val boatId: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)
