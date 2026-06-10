package fi.pursi.navigation

import fi.pursi.data.dao.BoatDao
import fi.pursi.data.model.Boat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class BoatManager(private val boatDao: BoatDao) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val allBoats: Flow<List<Boat>> = boatDao.getAll()

    suspend fun getDefaultBoat(): Boat? = boatDao.getDefault()

    suspend fun getBoat(id: Long): Boat? = boatDao.getById(id)

    fun saveBoat(boat: Boat) {
        scope.launch {
            boatDao.saveClearingDefault(boat)
        }
    }

    fun deleteBoat(boat: Boat) {
        scope.launch { boatDao.delete(boat) }
    }

    fun setDefault(boat: Boat) {
        scope.launch {
            boatDao.setDefault(boat)
        }
    }

    fun cancel() {
        scope.cancel()
    }
}
