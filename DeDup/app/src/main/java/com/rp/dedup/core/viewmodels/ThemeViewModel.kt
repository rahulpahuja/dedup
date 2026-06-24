package com.rp.dedup.core.viewmodels

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.caching.DataStoreManager
import com.rp.dedup.core.model.AppPalette
import com.rp.dedup.core.model.ThemeMode
import com.rp.dedup.core.theme.SolarThemeCalculator
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ThemeViewModel(private val dataStoreManager: DataStoreManager) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = dataStoreManager.readData(
        DataStoreManager.THEME_MODE,
        ThemeMode.DARK.name
    ).map { name ->
        try { ThemeMode.valueOf(name) } catch (_: Exception) { ThemeMode.DARK }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ThemeMode.DARK
    )

    val appPalette: StateFlow<AppPalette> = dataStoreManager.readData(
        DataStoreManager.SELECTED_PALETTE,
        AppPalette.OCEAN.name
    ).map { name ->
        try { AppPalette.valueOf(name) } catch (_: Exception) { AppPalette.OCEAN }
    }.stateIn(
        scope          = viewModelScope,
        started        = SharingStarted.WhileSubscribed(5000),
        initialValue   = AppPalette.OCEAN
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            dataStoreManager.writeData(DataStoreManager.THEME_MODE, mode.name)
        }
    }

    fun setPalette(palette: AppPalette) {
        viewModelScope.launch {
            dataStoreManager.writeData(DataStoreManager.SELECTED_PALETTE, palette.name)
        }
    }

    @Composable
    fun isDarkTheme(): Boolean {
        val mode by themeMode.produceAsState()
        return when (mode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.AUTO -> {
                val context = LocalContext.current
                // Re-evaluate every minute so the theme flips exactly at sunrise/sunset.
                val isDark by produceState(
                    initialValue = SolarThemeCalculator.isDarkNow(context),
                    key1 = context
                ) {
                    while (true) {
                        delay(60_000L)
                        value = SolarThemeCalculator.isDarkNow(context)
                    }
                }
                isDark
            }
        }
    }
}

@Composable
private fun <T> StateFlow<T>.produceAsState(): androidx.compose.runtime.State<T> =
    produceState(initialValue = value) {
        collect { value = it }
    }
