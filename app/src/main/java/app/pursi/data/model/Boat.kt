package app.pursi.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "boats", indices = [Index("isDefault")])
data class Boat(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val cruisingSpeedKn: Float,
    val maxSpeedKn: Float,
    val fuelConsumptionLh: Float? = null,
    val fuelCapacityL: Float? = null,
    val isDefault: Boolean = false,
    val notes: String? = null
)
