package com.rp.dedup.core.viewmodels

import com.rp.dedup.core.model.ContactDataEntry
import com.rp.dedup.core.model.MergePreviewGroup
import com.rp.dedup.core.model.ScannedContact
import com.rp.dedup.core.notifications.ToastManager
import com.rp.dedup.core.repository.ContactScannerRepository
import com.rp.dedup.util.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class ContactScannerViewModelTest {

    @get:Rule
    val coroutineRule = MainDispatcherRule()

    private val repository   = mockk<ContactScannerRepository>(relaxed = true)
    private val toastManager = mockk<ToastManager>(relaxed = true)
    private lateinit var viewModel: ContactScannerViewModel

    @Before
    fun setUp() {
        viewModel = ContactScannerViewModel(repository, toastManager)
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun contact(id: String, name: String, phones: List<String> = emptyList()) =
        ScannedContact(id = id, name = name, phoneNumbers = phones, emails = emptyList())

    private fun phoneEntry(dataId: String, value: String, contactId: String, isPrimary: Boolean) =
        ContactDataEntry(
            dataId       = dataId,
            value        = value,
            type         = 2,
            contactId    = contactId,
            contactName  = "name",
            isFromPrimary = isPrimary
        )

    // ── initial state ──────────────────────────────────────────────────────────

    @Test
    fun `initial state has empty groups and not scanning`() {
        assertTrue(viewModel.duplicateGroups.value.isEmpty())
        assertFalse(viewModel.isScanning.value)
    }

    // ── startScanning / findDuplicates ─────────────────────────────────────────

    @Test
    fun `contacts with same name are grouped as duplicates`() = runTest {
        val c1 = contact("1", "John Smith")
        val c2 = contact("2", "John Smith")
        val c3 = contact("3", "Jane Doe")
        every { repository.scanContacts() } returns flowOf(c1, c2, c3)

        viewModel.startScanning()

        assertEquals(1, viewModel.duplicateGroups.value.size)
        val group = viewModel.duplicateGroups.value.first()
        assertTrue(group.any { it.id == "1" })
        assertTrue(group.any { it.id == "2" })
    }

    @Test
    fun `contacts with same normalized phone number are grouped`() = runTest {
        val c1 = contact("1", "Alice", phones = listOf("+1 (555) 123-4567"))
        val c2 = contact("2", "Alice B.", phones = listOf("15551234567"))
        every { repository.scanContacts() } returns flowOf(c1, c2)

        viewModel.startScanning()

        val groups = viewModel.duplicateGroups.value
        assertTrue(groups.any { group -> group.any { it.id == "1" } && group.any { it.id == "2" } })
    }

    @Test
    fun `phone numbers shorter than 10 digits are not used for grouping`() = runTest {
        val c1 = contact("1", "Alice", phones = listOf("12345"))
        val c2 = contact("2", "Bob",   phones = listOf("12345"))
        every { repository.scanContacts() } returns flowOf(c1, c2)

        viewModel.startScanning()

        assertTrue(viewModel.duplicateGroups.value.isEmpty())
    }

    @Test
    fun `unique contacts produce no duplicate groups`() = runTest {
        val c1 = contact("1", "Alice", phones = listOf("15551234567"))
        val c2 = contact("2", "Bob",   phones = listOf("15559876543"))
        every { repository.scanContacts() } returns flowOf(c1, c2)

        viewModel.startScanning()

        assertTrue(viewModel.duplicateGroups.value.isEmpty())
    }

    @Test
    fun `isScanning is false after scan completes`() = runTest {
        every { repository.scanContacts() } returns flowOf()
        viewModel.startScanning()
        assertFalse(viewModel.isScanning.value)
    }

    @Test
    fun `duplicate groups with same ids are deduplicated`() = runTest {
        // c1 and c2 share both name and phone — should appear as ONE group only
        val c1 = contact("1", "John", phones = listOf("15551234567"))
        val c2 = contact("2", "John", phones = listOf("15551234567"))
        every { repository.scanContacts() } returns flowOf(c1, c2)

        viewModel.startScanning()

        assertEquals(1, viewModel.duplicateGroups.value.size)
    }

    // ── prepareMergePreview ────────────────────────────────────────────────────

    @Test
    fun `prepareMergePreview sets mergePreview for matching selected ids`() = runTest {
        val c1 = contact("1", "John"); val c2 = contact("2", "John")
        every { repository.scanContacts() } returns flowOf(c1, c2)
        coEvery { repository.queryPhoneEntries("1", any(), true)  } returns listOf(phoneEntry("d1", "111", "1", true))
        coEvery { repository.queryPhoneEntries("2", any(), false) } returns listOf(phoneEntry("d2", "222", "2", false))
        coEvery { repository.queryEmailEntries(any(), any(), any()) } returns emptyList()

        viewModel.startScanning()
        viewModel.prepareMergePreview(listOf("2"))

        assertNotNull(viewModel.mergePreview.value)
        assertEquals(1, viewModel.mergePreview.value!!.size)
    }

    @Test
    fun `prepareMergePreview returns null when no groups match selected ids`() = runTest {
        val c1 = contact("1", "John"); val c2 = contact("2", "John")
        every { repository.scanContacts() } returns flowOf(c1, c2)
        coEvery { repository.queryPhoneEntries(any(), any(), any()) } returns emptyList()
        coEvery { repository.queryEmailEntries(any(), any(), any()) } returns emptyList()

        viewModel.startScanning()
        viewModel.prepareMergePreview(listOf("99")) // id not in any group

        assertNull(viewModel.mergePreview.value)
    }

    @Test
    fun `isPreparingMerge is false after preview is ready`() = runTest {
        val c1 = contact("1", "John"); val c2 = contact("2", "John")
        every { repository.scanContacts() } returns flowOf(c1, c2)
        coEvery { repository.queryPhoneEntries(any(), any(), any()) } returns emptyList()
        coEvery { repository.queryEmailEntries(any(), any(), any()) } returns emptyList()

        viewModel.startScanning()
        viewModel.prepareMergePreview(listOf("2"))

        assertFalse(viewModel.isPreparingMerge.value)
    }

    // ── dismissMergePreview ────────────────────────────────────────────────────

    @Test
    fun `dismissMergePreview clears mergePreview`() = runTest {
        val c1 = contact("1", "John"); val c2 = contact("2", "John")
        every { repository.scanContacts() } returns flowOf(c1, c2)
        coEvery { repository.queryPhoneEntries(any(), any(), any()) } returns emptyList()
        coEvery { repository.queryEmailEntries(any(), any(), any()) } returns emptyList()

        viewModel.startScanning()
        viewModel.prepareMergePreview(listOf("2"))
        viewModel.dismissMergePreview()

        assertNull(viewModel.mergePreview.value)
    }

    // ── executeConfirmedMerge ─────────────────────────────────────────────────

    @Test
    fun `executeConfirmedMerge removes merged duplicates from groups`() = runTest {
        val c1 = contact("1", "John"); val c2 = contact("2", "John")
        every { repository.scanContacts() } returns flowOf(c1, c2)
        coEvery { repository.mergeContactsWithSelection(any(), any(), any(), any()) } returns Result.success(Unit)
        coEvery { repository.queryPhoneEntries(any(), any(), any()) } returns emptyList()
        coEvery { repository.queryEmailEntries(any(), any(), any()) } returns emptyList()

        viewModel.startScanning()

        val preview = listOf(MergePreviewGroup(
            primaryId    = "1",
            primaryName  = "John",
            duplicateIds = listOf("2"),
            phoneEntries = emptyList(),
            emailEntries = emptyList()
        ))

        var completed = false
        viewModel.executeConfirmedMerge(preview, emptySet()) { completed = true }

        assertTrue(completed)
        assertTrue(viewModel.duplicateGroups.value.isEmpty())
        assertNull(viewModel.mergePreview.value)
    }

    @Test
    fun `executeConfirmedMerge shows success toast on full success`() = runTest {
        coEvery { repository.mergeContactsWithSelection(any(), any(), any(), any()) } returns Result.success(Unit)

        viewModel.executeConfirmedMerge(
            listOf(MergePreviewGroup("1", "John", listOf("2"), emptyList(), emptyList())),
            emptySet()
        )

        verify { toastManager.showShort("Contacts merged successfully") }
    }

    @Test
    fun `executeConfirmedMerge shows failure toast on partial failure`() = runTest {
        coEvery { repository.mergeContactsWithSelection(any(), any(), any(), any()) } returns Result.failure(Exception("fail"))

        viewModel.executeConfirmedMerge(
            listOf(MergePreviewGroup("1", "John", listOf("2"), emptyList(), emptyList())),
            emptySet()
        )

        verify { toastManager.showShort("Some contacts could not be merged") }
    }
}
