package com.rp.dedup

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rp.dedup.core.viewmodels.UserProfileViewModel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = com.rp.dedup.util.TestApp::class)
class UserProfileViewModelTest {

    private lateinit var viewModel: UserProfileViewModel
    private lateinit var mockPrefs: android.content.SharedPreferences

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        mockPrefs = app.getSharedPreferences("test_profile", Context.MODE_PRIVATE)
        mockPrefs.edit().clear().commit()

        io.mockk.mockkConstructor(androidx.security.crypto.MasterKey.Builder::class)
        io.mockk.every { anyConstructed<androidx.security.crypto.MasterKey.Builder>().build() } returns io.mockk.mockk<androidx.security.crypto.MasterKey>(relaxed = true)

        io.mockk.mockkStatic(androidx.security.crypto.EncryptedSharedPreferences::class)
        io.mockk.every {
            androidx.security.crypto.EncryptedSharedPreferences.create(
                any<android.content.Context>(),
                any<String>(),
                any<androidx.security.crypto.MasterKey>(),
                any<androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme>(),
                any<androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme>()
            )
        } returns mockPrefs

        viewModel = UserProfileViewModel(app)
    }

    @org.junit.After
    fun tearDown() {
        io.mockk.unmockkStatic(androidx.security.crypto.EncryptedSharedPreferences::class)
        io.mockk.unmockkConstructor(androidx.security.crypto.MasterKey.Builder::class)
    }

    // --- Default state ---

    @Test
    fun `default name is User`() {
        assertEquals("User", viewModel.name)
    }

    @Test
    fun `default email is empty string`() {
        assertEquals("", viewModel.email)
    }

    // --- update ---

    @Test
    fun `update sets name and email`() {
        viewModel.update("Alice", "alice@example.com")
        assertEquals("Alice", viewModel.name)
        assertEquals("alice@example.com", viewModel.email)
    }

    @Test
    fun `update with empty name defaults to User`() {
        viewModel.update("", "test@example.com")
        assertEquals("User", viewModel.name)
    }

    @Test
    fun `update with blank name defaults to User`() {
        viewModel.update("   ", "test@example.com")
        assertEquals("User", viewModel.name)
    }

    @Test
    fun `update trims leading and trailing whitespace from name`() {
        viewModel.update("  Bob  ", "bob@example.com")
        assertEquals("Bob", viewModel.name)
    }

    @Test
    fun `update trims whitespace from email`() {
        viewModel.update("Carol", "  carol@example.com  ")
        assertEquals("carol@example.com", viewModel.email)
    }

    @Test
    fun `update allows empty email`() {
        viewModel.update("Dave", "")
        assertEquals("", viewModel.email)
    }

    @Test
    fun `update persists across new ViewModel instance`() {
        viewModel.update("Eve", "eve@example.com")

        val app = ApplicationProvider.getApplicationContext<Application>()
        val newViewModel = UserProfileViewModel(app)

        assertEquals("Eve", newViewModel.name)
        assertEquals("eve@example.com", newViewModel.email)
    }

    @Test
    fun `successive updates overwrite previous values`() {
        viewModel.update("First", "first@example.com")
        viewModel.update("Second", "second@example.com")
        assertEquals("Second", viewModel.name)
        assertEquals("second@example.com", viewModel.email)
    }
}
