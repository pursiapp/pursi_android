package app.pursi.data.dao

import androidx.room.*
import app.pursi.data.model.DownloadJob
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadJobDao {

    @Query("SELECT * FROM download_jobs ORDER BY status DESC, createdAt DESC")
    fun getAll(): Flow<List<DownloadJob>>

    @Query("SELECT * FROM download_jobs ORDER BY status DESC, createdAt DESC")
    suspend fun getAllSync(): List<DownloadJob>

    @Query("SELECT * FROM download_jobs WHERE id = :id")
    suspend fun getById(id: String): DownloadJob?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: DownloadJob)

    @Update
    suspend fun update(job: DownloadJob)

    @Delete
    suspend fun delete(job: DownloadJob)

    @Query("DELETE FROM download_jobs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM download_jobs WHERE status = 'COMPLETED'")
    suspend fun countCompleted(): Int
}
