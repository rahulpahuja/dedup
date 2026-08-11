package com.rp.dedup.core.workers

import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], application = com.rp.dedup.util.TestApp::class)
class ScanWorkerScheduleTest {

    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
    }

    @Test
    fun `enqueuePeriodic schedules a unique periodic scan`() {
        ScanWorker.enqueuePeriodic(ApplicationProvider.getApplicationContext())

        val infos = workManager.getWorkInfosForUniqueWork("periodic_duplicate_scan").get()

        assertEquals(1, infos.size)
        assertTrue(infos.first().state == WorkInfo.State.ENQUEUED)
    }

    @Test
    fun `enqueuePeriodic is idempotent (KEEP policy)`() {
        ScanWorker.enqueuePeriodic(ApplicationProvider.getApplicationContext())
        ScanWorker.enqueuePeriodic(ApplicationProvider.getApplicationContext())

        val infos = workManager.getWorkInfosForUniqueWork("periodic_duplicate_scan").get()

        assertEquals(1, infos.size)
    }

    @Test
    fun `cancelPeriodic removes the scheduled scan`() {
        ScanWorker.enqueuePeriodic(ApplicationProvider.getApplicationContext())
        ScanWorker.cancelPeriodic(ApplicationProvider.getApplicationContext())

        val infos = workManager.getWorkInfosForUniqueWork("periodic_duplicate_scan").get()

        assertTrue(infos.all { it.state == WorkInfo.State.CANCELLED })
    }
}
