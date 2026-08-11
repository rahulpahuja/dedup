package com.rp.dedup.core.viewmodels

import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.rp.dedup.core.caching.DataStoreManager
import com.rp.dedup.util.MainDispatcherRule
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies SettingsViewModel.setBackgroundAutoScanEnabled actually schedules/cancels
 * ScanWorker's periodic work — requires a real WorkManager instance (Robolectric),
 * unlike the plain-mockk tests in [SettingsViewModelTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = com.rp.dedup.util.TestApp::class)
class SettingsViewModelBackgroundScanTest {

    @get:Rule
    val coroutineRule = MainDispatcherRule()

    private val dataStoreManager = mockk<DataStoreManager>(relaxed = true)
    private lateinit var viewModel: SettingsViewModel
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)

        every { dataStoreManager.readData(DataStoreManager.BACKGROUND_AUTO_SCAN_ENABLED, true) } returns flowOf(true)
        viewModel = SettingsViewModel(dataStoreManager, context)
    }

    @Test
    fun `enabling background auto-scan schedules periodic work`() {
        viewModel.setBackgroundAutoScanEnabled(true)

        val infos = workManager.getWorkInfosForUniqueWork("periodic_duplicate_scan").get()

        assertEquals(1, infos.size)
        assertTrue(infos.first().state == WorkInfo.State.ENQUEUED)
    }

    @Test
    fun `disabling background auto-scan cancels periodic work`() {
        viewModel.setBackgroundAutoScanEnabled(true)
        viewModel.setBackgroundAutoScanEnabled(false)

        val infos = workManager.getWorkInfosForUniqueWork("periodic_duplicate_scan").get()

        assertTrue(infos.all { it.state == WorkInfo.State.CANCELLED })
    }
}
