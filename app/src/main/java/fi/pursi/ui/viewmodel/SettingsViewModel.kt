package fi.pursi.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fi.pursi.data.dao.BoatDao
import fi.pursi.data.model.Boat
import fi.pursi.navigation.BoatManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    val boatManager: BoatManager
) : ViewModel() {

    val allBoats = boatManager.allBoats

    private val _showBoatDialog = MutableStateFlow(false)
    val showBoatDialog: StateFlow<Boolean> = _showBoatDialog.asStateFlow()

    private val _editingBoat = MutableStateFlow<Boat?>(null)
    val editingBoat: StateFlow<Boat?> = _editingBoat.asStateFlow()

    fun showAddBoat() {
        _showBoatDialog.value = true
        _editingBoat.value = null
    }

    fun showEditBoat(boat: Boat) {
        _showBoatDialog.value = true
        _editingBoat.value = boat
    }

    fun dismissBoatDialog() {
        _showBoatDialog.value = false
        _editingBoat.value = null
    }

    fun saveBoat(boat: Boat) {
        boatManager.saveBoat(boat)
        _showBoatDialog.value = false
        _editingBoat.value = null
    }

    fun deleteBoat(boat: Boat) {
        viewModelScope.launch {
            boatManager.deleteBoat(boat)
        }
    }

    fun setDefaultBoat(boat: Boat) {
        boatManager.setDefault(boat)
    }

    override fun onCleared() {
        super.onCleared()
        boatManager.cancel()
    }
}
