package com.rp.dedup.core.repository

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for ContactScannerRepository pure-logic helpers.
 * Content-provider calls require a real device and are covered by instrumented tests.
 */
class ContactScannerRepositoryTest {

    private val context = mockk<Context>(relaxed = true)
    private val repository = ContactScannerRepository(context)

    // ── normalizePhone ─────────────────────────────────────────────────────────

    @Test
    fun `normalizePhone strips spaces and dashes`() {
        assertEquals("12345678901", repository.normalizePhone("1 (234) 567-8901"))
    }

    @Test
    fun `normalizePhone strips parentheses and dots`() {
        assertEquals("4155552671", repository.normalizePhone("(415) 555.2671"))
    }

    @Test
    fun `normalizePhone preserves leading plus sign`() {
        val result = repository.normalizePhone("+1 800 555 1234")
        assertTrue(result.startsWith("+"))
        assertEquals("+18005551234", result)
    }

    @Test
    fun `normalizePhone strips leading zeros for non-plus numbers`() {
        val result = repository.normalizePhone("0091-9876543210")
        assertFalse(result.startsWith("0"))
        assertEquals("919876543210", result)
    }

    @Test
    fun `normalizePhone handles empty string`() {
        assertEquals("", repository.normalizePhone(""))
    }

    @Test
    fun `normalizePhone returns digits only for plain number`() {
        assertEquals("9876543210", repository.normalizePhone("9876543210"))
    }

    @Test
    fun `normalizePhone strips all non-digit chars except leading plus`() {
        assertEquals("123456789", repository.normalizePhone("  1-2-3.4(5)6_7_8_9  "))
    }

    @Test
    fun `normalizePhone on international format with plus`() {
        assertEquals("+442071234567", repository.normalizePhone("+44 207 123 4567"))
    }

    // ── normalizePhone — dedup consistency ────────────────────────────────────

    @Test
    fun `two phone numbers with same digits normalize to same string`() {
        val n1 = repository.normalizePhone("(800) 555-1234")
        val n2 = repository.normalizePhone("8005551234")
        assertEquals(n1, n2)
    }

    @Test
    fun `phone numbers with different digits normalize differently`() {
        val n1 = repository.normalizePhone("8005551234")
        val n2 = repository.normalizePhone("8005551235")
        assertNotEquals(n1, n2)
    }
}
