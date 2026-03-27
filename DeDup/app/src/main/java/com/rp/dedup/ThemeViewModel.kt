package com.rp.dedup

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

enum class ThemeMode {
    LIGHT, DARK, AUTO
}

class ThemeViewModel : ViewModel() {
    private val _themeMode = mutableStateOf(ThemeMode.AUTO)
    val themeMode: ThemeMode get() = _themeMode.value

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    @Composable
    fun isDarkTheme(): Boolean {
        return when (themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            ThemeMode.AUTO -> isSystemInDarkTheme()
        }
    }
}
