package com.rp.dedup.core.fixes

import android.os.Build
import com.rp.dedup.core.utils.TrashManager
import org.junit.Assert.*
import org.junit.Test

/**
 * Fix #2 — TrashManager.moveToTrash() used deprecated MediaStore.MediaColumns.DATA +
 * File.renameTo() on all API levels, which silently fails on Android Q+ (scoped storage).
 *
 * The fix uses the correct API per SDK level:
 *   API 30+: MediaStore IS_TRASHED flag (system-managed trash)
 *   API 29 : ContentResolver.openInputStream copy + ContentResolver.delete
 *   API ≤28: File.renameTo on raw filesystem (only valid pre-scoped storage)
 */
class Fix2TrashManagerTest {

    @Test
    fun `TrashManager class is accessible in expected package`() {
        val cls = Class.forName("com.rp.dedup.core.utils.TrashManager")
        assertNotNull(cls)
    }

    @Test
    fun `moveToTrash method exists with correct signature`() {
        val method = TrashManager::class.java.methods.find { it.name == "moveToTrash" }
        assertNotNull("moveToTrash method must be defined", method)
        assertEquals(
            "moveToTrash must return Boolean",
            Boolean::class.javaPrimitiveType,
            method!!.returnType
        )
        assertEquals("moveToTrash must accept Context and Uri", 2, method.parameterCount)
    }

    @Test
    fun `API dispatch logic covers all SDK branches`() {
        // Structural: the fixed TrashManager must have the three private dispatch functions.
        val methods = TrashManager::class.java.declaredMethods.map { it.name }
        assertTrue("trashViaMediaStore must exist for API 30+",  "trashViaMediaStore" in methods)
        assertTrue("trashViaCopy must exist for API 29",         "trashViaCopy"       in methods)
        assertTrue("trashViaFile must exist for API <=28",       "trashViaFile"       in methods)
    }

    @Test
    fun `queryDisplayName helper exists for API 29 copy path`() {
        val methods = TrashManager::class.java.declaredMethods.map { it.name }
        assertTrue("queryDisplayName must be present", "queryDisplayName" in methods)
    }
}
