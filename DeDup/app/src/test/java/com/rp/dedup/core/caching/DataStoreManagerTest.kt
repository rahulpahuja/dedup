package com.rp.dedup.core.caching

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for DataStoreManager companion-object constants and key definitions.
 * Actual read/write operations require a real DataStore and are covered by
 * instrumented tests. Here we verify the key names are stable and unique so that
 * a rename does not silently corrupt persisted user preferences.
 */
class DataStoreManagerTest {

    // ── key naming ─────────────────────────────────────────────────────────────

    @Test
    fun `SIMILARITY_THRESHOLD key name is stable`() {
        assertEquals("similarity_threshold", DataStoreManager.SIMILARITY_THRESHOLD.name)
    }

    @Test
    fun `EXCLUDED_FOLDERS key name is stable`() {
        assertEquals("excluded_folders", DataStoreManager.EXCLUDED_FOLDERS.name)
    }

    @Test
    fun `AUTO_SCAN_ON_STARTUP key name is stable`() {
        assertEquals("auto_scan_on_startup", DataStoreManager.AUTO_SCAN_ON_STARTUP.name)
    }

    @Test
    fun `SELECTED_CURRENCY key name is stable`() {
        assertEquals("selected_currency", DataStoreManager.SELECTED_CURRENCY.name)
    }

    @Test
    fun `THEME_MODE key name is stable`() {
        assertEquals("theme_mode", DataStoreManager.THEME_MODE.name)
    }

    // ── key types ──────────────────────────────────────────────────────────────

    @Test
    fun `SIMILARITY_THRESHOLD is a String key`() {
        // Stored as string to allow generic read/write helper
        assertTrue(DataStoreManager.SIMILARITY_THRESHOLD is Preferences.Key<String>)
    }

    @Test
    fun `EXCLUDED_FOLDERS is a String key`() {
        assertTrue(DataStoreManager.EXCLUDED_FOLDERS is Preferences.Key<String>)
    }

    @Test
    fun `AUTO_SCAN_ON_STARTUP is a Boolean key`() {
        assertTrue(DataStoreManager.AUTO_SCAN_ON_STARTUP is Preferences.Key<Boolean>)
    }

    // ── key uniqueness ─────────────────────────────────────────────────────────

    @Test
    fun `all key names are unique`() {
        val names = listOf(
            DataStoreManager.SIMILARITY_THRESHOLD.name,
            DataStoreManager.EXCLUDED_FOLDERS.name,
            DataStoreManager.AUTO_SCAN_ON_STARTUP.name,
            DataStoreManager.SELECTED_CURRENCY.name,
            DataStoreManager.THEME_MODE.name,
            DataStoreManager.LAST_SCAN_TIME.name,
            DataStoreManager.TUTORIAL_SHOWN.name,
            DataStoreManager.LONG_PRESS_TUTORIAL_SHOWN.name,
            DataStoreManager.SELECTED_LANGUAGE.name,
            DataStoreManager.LAST_IMAGE_SCAN_TIME.name
        )
        assertEquals("All key names must be unique", names.size, names.toSet().size)
    }

    // ── no null keys ───────────────────────────────────────────────────────────

    @Test
    fun `no key has a null or blank name`() {
        listOf(
            DataStoreManager.SIMILARITY_THRESHOLD.name,
            DataStoreManager.EXCLUDED_FOLDERS.name,
            DataStoreManager.AUTO_SCAN_ON_STARTUP.name,
            DataStoreManager.SELECTED_CURRENCY.name,
            DataStoreManager.THEME_MODE.name
        ).forEach { name ->
            assertTrue("Key name should not be blank: '$name'", name.isNotBlank())
        }
    }
}
