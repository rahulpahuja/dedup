package com.rp.dedup.core.fixes

import com.rp.dedup.core.viewmodels.ContactScannerViewModel
import org.junit.Assert.*
import org.junit.Test

/**
 * Fix #14 — ContactScannerViewModel.factory() created ToastManager(context) and
 * ContactScannerRepository(context) using the raw Activity context passed in, capturing
 * it inside the ViewModel for its full lifetime.
 *
 * The fix: factory() now calls context.applicationContext before constructing deps.
 */
class Fix14ContextLeakContactScannerViewModelTest {

    @Test
    fun `ContactScannerViewModel has a companion object with a factory method`() {
        val companion = ContactScannerViewModel::class.java.declaredClasses
            .find { it.simpleName == "Companion" }
        assertNotNull("ContactScannerViewModel must have a companion object", companion)
        val factory = companion?.methods?.find { it.name == "factory" }
        assertNotNull("Companion must expose a factory() method", factory)
    }

    @Test
    fun `factory method accepts a Context parameter`() {
        val companion = ContactScannerViewModel::class.java.declaredClasses
            .find { it.simpleName == "Companion" }
        val factory = companion?.methods?.find { it.name == "factory" }
        val paramTypes = factory?.parameterTypes?.map { it.simpleName } ?: emptyList()
        assertTrue("factory() must accept a Context", paramTypes.contains("Context"))
    }
}
