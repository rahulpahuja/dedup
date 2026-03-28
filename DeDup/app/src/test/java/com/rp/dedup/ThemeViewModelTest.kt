package com.rp.dedup

import com.rp.dedup.core.caching.DataStoreManager
import com.rp.dedup.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ThemeViewModelTest {

    @get:Rule
    val coroutineRule = MainDispatcherRule()

    private val dataStore = mockk<DataStoreManager>()
    private lateinit var viewModel: ThemeViewModel

    private fun setup(storedValue: String = ThemeMode.AUTO.name) {
        every { dataStore.readData(DataStoreManager.THEME_MODE, ThemeMode.AUTO.name) } returns
                flowOf(storedValue)
        viewModel = ThemeViewModel(dataStore)
    }

    // --- Initial state ---

    @Test
    fun `initial themeMode is AUTO`() = runTest {
        setup("AUTO")
        advanceUntilIdle()
        assertEquals(ThemeMode.AUTO, viewModel.themeMode.value)
    }

    @Test
    fun `themeMode reads DARK from datastore`() = runTest {
        setup("DARK")
        advanceUntilIdle()
        assertEquals(ThemeMode.DARK, viewModel.themeMode.value)
    }

    @Test
    fun `themeMode reads LIGHT from datastore`() = runTest {
        setup("LIGHT")
        advanceUntilIdle()
        assertEquals(ThemeMode.LIGHT, viewModel.themeMode.value)
    }

    @Test
    fun `invalid stored value falls back to AUTO`() = runTest {
        setup("INVALID_MODE")
        advanceUntilIdle()
        assertEquals(ThemeMode.AUTO, viewModel.themeMode.value)
    }

    @Test
    fun `empty stored value falls back to AUTO`() = runTest {
        setup("")
        advanceUntilIdle()
        assertEquals(ThemeMode.AUTO, viewModel.themeMode.value)
    }

    // --- setThemeMode ---

    @Test
    fun `setThemeMode DARK writes DARK to datastore`() = runTest {
        setup()
        coEvery { dataStore.writeData(DataStoreManager.THEME_MODE, any()) } just Runs
        viewModel.setThemeMode(ThemeMode.DARK)
        advanceUntilIdle()
        coVerify { dataStore.writeData(DataStoreManager.THEME_MODE, "DARK") }
    }

    @Test
    fun `setThemeMode LIGHT writes LIGHT to datastore`() = runTest {
        setup()
        coEvery { dataStore.writeData(DataStoreManager.THEME_MODE, any()) } just Runs
        viewModel.setThemeMode(ThemeMode.LIGHT)
        advanceUntilIdle()
        coVerify { dataStore.writeData(DataStoreManager.THEME_MODE, "LIGHT") }
    }

    @Test
    fun `setThemeMode AUTO writes AUTO to datastore`() = runTest {
        setup()
        coEvery { dataStore.writeData(DataStoreManager.THEME_MODE, any()) } just Runs
        viewModel.setThemeMode(ThemeMode.AUTO)
        advanceUntilIdle()
        coVerify { dataStore.writeData(DataStoreManager.THEME_MODE, "AUTO") }
    }

    // --- ThemeMode enum ---

    @Test
    fun `ThemeMode has exactly 3 values`() {
        assertEquals(3, ThemeMode.entries.size)
    }

    @Test
    fun `ThemeMode valueOf is case-sensitive`() {
        assertEquals(ThemeMode.DARK, ThemeMode.valueOf("DARK"))
    }
}
