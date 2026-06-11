package app.pursi.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import app.pursi.data.model.WfsFeature

@Dao
interface WfsFeatureDao {

    @Query("""
        SELECT * FROM wfs_features 
        WHERE featureType = :type 
        AND maxLat >= :minLat AND minLat <= :maxLat 
        AND maxLng >= :minLng AND minLng <= :maxLng
        ORDER BY downloadedAt DESC
    """)
    suspend fun getFeatures(
        type: String,
        minLat: Double, minLng: Double,
        maxLat: Double, maxLng: Double
    ): List<WfsFeature>

    @Query("SELECT * FROM wfs_features WHERE source = :source AND featureType = :type")
    suspend fun getBySourceAndType(source: String, type: String): List<WfsFeature>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(features: List<WfsFeature>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(feature: WfsFeature)

    @Query("DELETE FROM wfs_features WHERE source = :source AND featureType = :type")
    suspend fun clear(source: String, type: String)

    @Query("DELETE FROM wfs_features WHERE source = :source AND featureType = :type AND (maxLat < :minLat OR minLat > :maxLat OR maxLng < :minLng OR minLng > :maxLng)")
    suspend fun clearOutside(source: String, type: String, minLat: Double, minLng: Double, maxLat: Double, maxLng: Double)

    @Query("SELECT COUNT(*) FROM wfs_features WHERE source = :source")
    suspend fun countBySource(source: String): Int

    @Query("DELETE FROM wfs_features WHERE source LIKE 'traficom_%' OR source LIKE 'vayla_%'")
    suspend fun clearAll()

    @Query("DELETE FROM wfs_features WHERE source = :source AND featureType = :type AND downloadedAt < :olderThan")
    suspend fun clearOlderThan(source: String, type: String, olderThan: Long)
}
