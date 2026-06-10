package fi.pursi.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_jobs")
data class DownloadJob(
    @PrimaryKey
    val id: String,
    val name: String,
    val minZoom: Int,
    val maxZoom: Int,
    val selectedLayers: String,
    val providerIds: String,
    val status: String = "PENDING",
    val progressTiles: Int = 0,
    val totalTiles: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val rectanglesJson: String = "[]",
    val snapshotPath: String? = null
)
