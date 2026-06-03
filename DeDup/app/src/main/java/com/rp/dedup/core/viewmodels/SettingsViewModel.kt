package com.rp.dedup.core.viewmodels

import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.caching.DataStoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(val dataStoreManager: DataStoreManager) : ViewModel() {

    val similarityThreshold: StateFlow<Int> = dataStoreManager.readData(DataStoreManager.SIMILARITY_THRESHOLD, "5")
        .map { it.toIntOrNull() ?: 5 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    val excludedFolders: StateFlow<List<String>> = dataStoreManager.readData(DataStoreManager.EXCLUDED_FOLDERS, "")
        .map { if (it.isEmpty()) emptyList() else it.split(",") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val autoScanOnStartup: StateFlow<Boolean> = dataStoreManager.readData(DataStoreManager.AUTO_SCAN_ON_STARTUP, true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _selectedLanguage = MutableStateFlow(getCurrentLanguageCode())
    val selectedLanguage: StateFlow<String> = _selectedLanguage.asStateFlow()

    private fun getCurrentLanguageCode(): String {
        val locales = AppCompatDelegate.getApplicationLocales()
        return if (locales.isEmpty) "system" else locales.get(0)?.toLanguageTag() ?: "system"
    }

    fun setSimilarityThreshold(value: Int) {
        viewModelScope.launch {
            dataStoreManager.writeData(DataStoreManager.SIMILARITY_THRESHOLD, value.toString())
        }
    }

    fun setAutoScanOnStartup(enabled: Boolean) {
        viewModelScope.launch {
            dataStoreManager.writeData(DataStoreManager.AUTO_SCAN_ON_STARTUP, enabled)
        }
    }

    fun setLanguage(languageCode: String) {
        _selectedLanguage.value = languageCode
        // Persistence is handled by AppCompatDelegate itself
    }

    fun addExcludedFolder(path: String) {
        viewModelScope.launch {
            val current = excludedFolders.value.toMutableList()
            if (!current.contains(path)) {
                current.add(path)
                dataStoreManager.writeData(DataStoreManager.EXCLUDED_FOLDERS, current.joinToString(","))
            }
        }
    }

    fun removeExcludedFolder(path: String) {
        viewModelScope.launch {
            val current = excludedFolders.value.toMutableList()
            if (current.remove(path)) {
                dataStoreManager.writeData(DataStoreManager.EXCLUDED_FOLDERS, current.joinToString(","))
            }
        }
    }

    class Factory(private val dataStoreManager: DataStoreManager) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SettingsViewModel(dataStoreManager) as T
        }
    }
}
