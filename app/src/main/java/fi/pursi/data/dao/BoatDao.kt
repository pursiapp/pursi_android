package fi.pursi.data.dao

import androidx.room.*
import fi.pursi.data.model.Boat
import kotlinx.coroutines.flow.Flow

@Dao
interface BoatDao {
    @Query("SELECT * FROM boats ORDER BY isDefault DESC, name ASC")
    fun getAll(): Flow<List<Boat>>

    @Query("SELECT * FROM boats WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): Boat?

    @Query("SELECT * FROM boats WHERE id = :id")
    suspend fun getById(id: Long): Boat?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(boat: Boat): Long

    @Update
    suspend fun update(boat: Boat)

    @Delete
    suspend fun delete(boat: Boat)

    @Query("UPDATE boats SET isDefault = 0 WHERE isDefault = 1")
    suspend fun clearDefault()

    @Transaction
    suspend fun saveClearingDefault(boat: Boat): Long {
        if (boat.isDefault) clearDefault()
        return if (boat.id == 0L) insert(boat) else { update(boat); boat.id }
    }

    @Transaction
    suspend fun setDefault(boat: Boat) {
        clearDefault()
        update(boat.copy(isDefault = true))
    }

    @Query("SELECT * FROM boats ORDER BY isDefault DESC, name ASC")
    suspend fun getAllSync(): List<Boat>
}
