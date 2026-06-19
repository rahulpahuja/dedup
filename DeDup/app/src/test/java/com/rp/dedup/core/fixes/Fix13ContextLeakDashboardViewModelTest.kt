package com.rp.dedup.core.fixes

import com.rp.dedup.core.viewmodels.DashboardViewModel
import org.junit.Assert.*
import org.junit.Test

/**
 * Fix #13 — DashboardViewModel stored `private val context: Context` directly from
 * the constructor parameter. If an Activity context was passed it would be retained
 * across configuration changes.
 *
 * The fix: parameter is `context: Context` (no val); an internal field is assigned
 * `context.applicationContext`.
 */
class Fix13ContextLeakDashboardViewModelTest {

    @Test
    fun `DashboardViewModel has a private context field`() {
        val field = DashboardViewModel::class.java.declaredFields.find { it.name == "context" }
        assertNotNull("DashboardViewModel must have an internal 'context' field", field)
        assertTrue("context field must be private", java.lang.reflect.Modifier.isPrivate(field!!.modifiers))
    }

    @Test
    fun `DashboardViewModel constructor does not expose context as a public property`() {
        val publicFields = DashboardViewModel::class.java.fields.map { it.name }
        assertFalse(
            "context must not be a public field — callers should not be able to extract a raw Context from the ViewModel",
            publicFields.contains("context")
        )
    }
}
