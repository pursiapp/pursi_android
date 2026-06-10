package fi.pursi.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "track_points", indices = [Index("trackId"), Index("timestamp")])
data class TrackPoint(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val trackId: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val speedOverGround: Float? = null,
    val courseOverGround: Float? = null,
    val accuracy: Float? = null,
    val timestamp: Long,
    val source: String = "gps"
)
