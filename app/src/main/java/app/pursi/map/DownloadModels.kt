package app.pursi.map

import app.pursi.datasource.core.BoundingBox

data class DownloadArea(
    val name: String,
    val rectangles: List<LatLngRect>,
    val minZoom: Int,
    val maxZoom: Int,
    val selectedLayers: List<String>,
    val providerIds: List<String>
)

data class LatLngRect(
    val minLat: Double,
    val maxLat: Double,
    val minLng: Double,
    val maxLng: Double
)

data class TileCoordinate(
    val z: Int,
    val x: Int,
    val y: Int
)

data class TileSource(
    val providerId: String,
    val urlTemplate: String,
    val extension: String,
    val minZoom: Int,
    val maxZoom: Int,
    val displayName: String = providerId,
    val avgTileBytes: Long = 15_000L,
    val coverageName: String = "",
    val description: String = "",
    val category: String = "",
    val coverage: BoundingBox? = null
)

data class DownloadProgress(
    val jobId: String,
    val name: String,
    val status: String,
    val completedTiles: Int,
    val totalTiles: Int,
    val providerId: String? = null,
    val rectanglesJson: String = "",
    val snapshotPath: String? = null,
    val providerIds: String = "",
    val createdAt: Long = 0L
)

fun parseRectanglesJson(json: String): List<LatLngRect> {
    return json.split("|").filter { it.isNotBlank() }.mapNotNull { part ->
        val parts = part.split(",").map { it.toDoubleOrNull() ?: return@mapNotNull null }
        if (parts.size >= 4) LatLngRect(parts[0], parts[1], parts[2], parts[3])
        else null
    }
}

enum class DownloadStatus(val value: String) {
    PENDING("PENDING"),
    RUNNING("RUNNING"),
    PAUSED("PAUSED"),
    COMPLETED("COMPLETED"),
    FAILED("FAILED"),
    CANCELLED("CANCELLED")
}
