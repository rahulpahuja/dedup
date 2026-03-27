package com.rp.dedup.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings_cache")

class DataStoreManager(private val context: Context) {

    companion object {
        val LAST_SCAN_TIME = stringPreferencesKey("last_scan_time")
        val THEME_MODE = stringPreferencesKey("theme_mode")
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
