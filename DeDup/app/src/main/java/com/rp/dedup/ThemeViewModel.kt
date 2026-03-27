package com.rp.dedup

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.DataStoreManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ThemeMode {
    LIGHT, DARK, AUTO
}

class ThemeViewModel(private val dataStoreManager: DataStoreManager) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = dataStoreManager.readData(
        DataStoreManager.THEME_MODE,
        ThemeMode.AUTO.name
    ).map { name ->
        try {
            ThemeMode.valueOf(name)
        } catch (e: Exception) {
            ThemeMode.AUTO
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ThemeMode.AUTO
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            dataStoreManager.writeData(DataStoreManager.THEME_MODE, mode.name)
        }
    }

    @Composable
    fun isDarkTheme(): Boolean {
        val mode = themeMode.collectAsState().value
        return when (mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.AUTO -> isSystemInDarkTheme()
        }
    }
}
