package app.pursi.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "emodnet_depth_samples",
    indices = [Index("gridKey", unique = true)]
)
data class EmodnetDepthSample(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val gridKey: String,
    val latitude: Double,
    val longitude: Double,
    val depthAvgM: Float,
    val depthMinM: Float = 0f,
    val depthMaxM: Float = 0f,
    val fetchedAt: Long = System.currentTimeMillis()
)
