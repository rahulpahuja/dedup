package com.rp.dedup.core.caching

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rp.dedup.core.common.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = Constants.SETTINGS_CACHE)

class DataStoreManager(private val context: Context) {

    companion object {
        val LAST_SCAN_TIME = stringPreferencesKey(Constants.LAST_SCAN_TIME)
        val THEME_MODE = stringPreferencesKey(Constants.THEME_MODE)
        val SELECTED_PALETTE = stringPreferencesKey(Constants.SELECTED_PALETTE)
        val TUTORIAL_SHOWN = booleanPreferencesKey("tutorial_shown")
        val LONG_PRESS_TUTORIAL_SHOWN = booleanPreferencesKey("long_press_tutorial_shown")
        val SIMILARITY_THRESHOLD = stringPreferencesKey("similarity_threshold") // Stores int as string for simpler generic logic
        val EXCLUDED_FOLDERS = stringPreferencesKey("excluded_folders") // Stores comma-separated paths
        val AUTO_SCAN_ON_STARTUP = booleanPreferencesKey("auto_scan_on_startup")
        val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
        val LAST_IMAGE_SCAN_TIME = stringPreferencesKey("last_image_scan_time")
        val SELECTED_CURRENCY = stringPreferencesKey("selected_currency")
    }

    suspend fun <T> writeData(key: Preferences.Key<T>, value: T) {
        context.dataStore.edit { preferences ->
            preferences[key] = value
        }
    }

    suspend fun <T> updateData(key: Preferences.Key<T>, transform: (T?) -> T) {
        context.dataStore.edit { preferences ->
            val currentValue = preferences[key]
            preferences[key] = transform(currentValue)
        }
    }

    fun <T> readData(key: Preferences.Key<T>, defaultValue: T): Flow<T> {
        return context.dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                preferences[key] ?: defaultValue
            }
    }

    suspend fun <T> deleteData(key: Preferences.Key<T>) {
        context.dataStore.edit { preferences ->
            preferences.remove(key)
        }
    }

    suspend fun clearCache() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}
