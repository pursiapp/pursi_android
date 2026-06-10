package fi.pursi.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "wfs_features", indices = [Index("source", "featureType")])
data class WfsFeature(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val source: String,
    val featureType: String,
    val geometry: String,
    val properties: String,
    val latitude: Double,
    val longitude: Double,
    val minLat: Double,
    val minLng: Double,
    val maxLat: Double,
    val maxLng: Double,
    val downloadedAt: Long = System.currentTimeMillis()
)
