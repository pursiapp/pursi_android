package app.pursi.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "track_summaries")
data class TrackSummary(
    @PrimaryKey val id: String,
    val name: String,
    val startTime: Long,
    val endTime: Long? = null,
    val pointCount: Int = 0,
    val distanceNm: Double = 0.0,
    val maxSpeedKn: Float? = null,
    val boatId: Long? = null,
    val isRecording: Boolean = true
)
