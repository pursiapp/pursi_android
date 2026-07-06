package app.pursi.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.pursi.data.model.EmodnetDepthSample

@Dao
interface EmodnetDepthSampleDao {

    @Query("""
        SELECT * FROM emodnet_depth_samples 
        WHERE latitude BETWEEN :minLat AND :maxLat 
        AND longitude BETWEEN :minLng AND :maxLng
        ORDER BY fetchedAt DESC
    """)
    suspend fun getInBounds(
        minLat: Double, minLng: Double,
        maxLat: Double, maxLng: Double
    ): List<EmodnetDepthSample>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(samples: List<EmodnetDepthSample>)

    @Query("SELECT * FROM emodnet_depth_samples WHERE gridKey IN (:gridKeys)")
    suspend fun getByGridKeys(gridKeys: List<String>): List<EmodnetDepthSample>

    @Query("DELETE FROM emodnet_depth_samples WHERE fetchedAt < :olderThan")
    suspend fun clearOlderThan(olderThan: Long)

    @Query("SELECT COUNT(*) FROM emodnet_depth_samples WHERE latitude BETWEEN :minLat AND :maxLat AND longitude BETWEEN :minLng AND :maxLng")
    suspend fun countInBounds(minLat: Double, minLng: Double, maxLat: Double, maxLng: Double): Int
}
