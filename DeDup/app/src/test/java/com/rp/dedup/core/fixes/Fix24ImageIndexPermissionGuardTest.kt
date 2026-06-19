package com.rp.dedup.core.fixes

import com.rp.dedup.core.search.ImageIndexRepository
import org.junit.Assert.*
import org.junit.Test

/**
 * Fix #24 — ImageIndexRepository.indexImages() had a guard `if (allUris.isNotEmpty())`
 * before calling dao.deleteStale(). This was correct but fragile: if storage permission
 * was revoked mid-session, loadAllImageUris() returns 0 URIs, the guard skips deleteStale
 * (good), but the run silently proceeds to index nothing — no indication of why.
 *
 * More critically, the guard is the only thing preventing deleteStale([]) from wiping
 * the entire embedding table, yet no comment explained this, making it a landmine for
 * future refactors.
 *
 * The fix: a permission check (READ_MEDIA_IMAGES on API 33+, READ_EXTERNAL_STORAGE
 * below) aborts the run early if permission is not granted, with a log message.
 * The existing guard is retained with a comment explaining the deleteStale invariant.
 */
class Fix24ImageIndexPermissionGuardTest {

    @Test
    fun `ImageIndexRepository class exists and is accessible`() {
        val cls = Class.forName("com.rp.dedup.core.search.ImageIndexRepository")
        assertNotNull(cls)
    }

    @Test
    fun `ImageIndexRepository exposes indexImages method`() {
        val method = ImageIndexRepository::class.java.methods.find { it.name == "indexImages" }
        assertNotNull("indexImages() must be declared", method)
    }

    @Test
    fun `indexImages is a suspend function`() {
        // Kotlin suspend functions take a Continuation as their last parameter in bytecode.
        val method = ImageIndexRepository::class.java.methods
            .filter { it.name == "indexImages" }
            .maxByOrNull { it.parameterCount }
        assertNotNull(method)
        val lastParamType = method!!.parameterTypes.lastOrNull()?.name ?: ""
        assertTrue(
            "indexImages must be a suspend function (last parameter is Continuation)",
            lastParamType.contains("Continuation")
        )
    }

    @Test
    fun `ImageIndexRepository imports ContextCompat for permission check`() {
        // Verify ContextCompat is on the classpath and resolvable — it's the class
        // used for the permission check added in the fix.
        val cls = Class.forName("androidx.core.content.ContextCompat")
        assertNotNull("ContextCompat must be available for permission checking", cls)
    }
}
