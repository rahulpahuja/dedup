package com.rp.dedup.core.viewmodels

import com.rp.dedup.core.caching.DataStoreManager
import com.rp.dedup.util.MainDispatcherRule
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class SettingsViewModelTest {

    @get:Rule
    val coroutineRule = MainDispatcherRule()

    private val dataStoreManager = mockk<DataStoreManager>(relaxed = true)
    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setUp() {
        every { dataStoreManager.readData(DataStoreManager.SIMILARITY_THRESHOLD, "10") } returns flowOf("10")
        every { dataStoreManager.readData(DataStoreManager.EXCLUDED_FOLDERS, "") } returns flowOf("")
        every { dataStoreManager.readData(DataStoreManager.AUTO_SCAN_ON_STARTUP, true) } returns flowOf(true)
        every { dataStoreManager.readData(DataStoreManager.SELECTED_CURRENCY, "") } returns flowOf("")
        viewModel = SettingsViewModel(dataStoreManager)
    }

    // ── initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial similarity threshold is 10`() {
        assertEquals(10, viewModel.similarityThreshold.value)
    }

    @Test
    fun `initial excluded folders is empty list`() {
        assertTrue(viewModel.excludedFolders.value.isEmpty())
    }

    @Test
    fun `initial auto scan on startup is true`() {
        assertTrue(viewModel.autoScanOnStartup.value)
    }

    // ── setSimilarityThreshold ─────────────────────────────────────────────────

    @Test
    fun `setSimilarityThreshold writes to DataStore`() = runTest {
        viewModel.setSimilarityThreshold(15)
        coVerify { dataStoreManager.writeData(DataStoreManager.SIMILARITY_THRESHOLD, "15") }
    }

    // ── setAutoScanOnStartup ───────────────────────────────────────────────────

    @Test
    fun `setAutoScanOnStartup writes to DataStore`() = runTest {
        viewModel.setAutoScanOnStartup(false)
        coVerify { dataStoreManager.writeData(DataStoreManager.AUTO_SCAN_ON_STARTUP, false) }
    }

    // ── setCurrency ────────────────────────────────────────────────────────────

    @Test
    fun `setCurrency writes to DataStore`() = runTest {
        viewModel.setCurrency("USD")
        coVerify { dataStoreManager.writeData(DataStoreManager.SELECTED_CURRENCY, "USD") }
    }

    // ── setLanguage ────────────────────────────────────────────────────────────

    @Test
    fun `setLanguage updates selectedLanguage state`() {
        viewModel.setLanguage("fr")
        assertEquals("fr", viewModel.selectedLanguage.value)
    }

    // ── addExcludedFolder ──────────────────────────────────────────────────────

    @Test
    fun `addExcludedFolder writes folder list to DataStore`() = runTest {
        every { dataStoreManager.readData(DataStoreManager.EXCLUDED_FOLDERS, "") } returns flowOf("")
        viewModel = SettingsViewModel(dataStoreManager)
        viewModel.addExcludedFolder("/storage/DCIM")
        coVerify { dataStoreManager.writeData(DataStoreManager.EXCLUDED_FOLDERS, "/storage/DCIM") }
    }

    @Test
    fun `addExcludedFolder does not add duplicates`() = runTest {
        every { dataStoreManager.readData(DataStoreManager.EXCLUDED_FOLDERS, "") } returns
            flowOf("/storage/DCIM")
        viewModel = SettingsViewModel(dataStoreManager)
        viewModel.addExcludedFolder("/storage/DCIM")
        coVerify(exactly = 0) { dataStoreManager.writeData(DataStoreManager.EXCLUDED_FOLDERS, any<String>()) }
    }

    // ── removeExcludedFolder ───────────────────────────────────────────────────

    @Test
    fun `removeExcludedFolder removes folder from list`() = runTest {
        every { dataStoreManager.readData(DataStoreManager.EXCLUDED_FOLDERS, "") } returns
            flowOf("/storage/DCIM,/storage/Download")
        viewModel = SettingsViewModel(dataStoreManager)
        viewModel.removeExcludedFolder("/storage/DCIM")
        coVerify { dataStoreManager.writeData(DataStoreManager.EXCLUDED_FOLDERS, "/storage/Download") }
    }

    @Test
    fun `removeExcludedFolder does nothing when folder not in list`() = runTest {
        every { dataStoreManager.readData(DataStoreManager.EXCLUDED_FOLDERS, "") } returns flowOf("")
        viewModel = SettingsViewModel(dataStoreManager)
        viewModel.removeExcludedFolder("/nonexistent")
        coVerify(exactly = 0) { dataStoreManager.writeData(DataStoreManager.EXCLUDED_FOLDERS, any<String>()) }
    }

    // ── excludedFolders parsing ────────────────────────────────────────────────

    @Test
    fun `excludedFolders parses comma-separated string`() = runTest {
        every { dataStoreManager.readData(DataStoreManager.EXCLUDED_FOLDERS, "") } returns
            flowOf("/path/one,/path/two")
        viewModel = SettingsViewModel(dataStoreManager)
        assertEquals(listOf("/path/one", "/path/two"), viewModel.excludedFolders.value)
    }
}
