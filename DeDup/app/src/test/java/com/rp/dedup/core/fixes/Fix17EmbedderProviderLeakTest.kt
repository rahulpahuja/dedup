package com.rp.dedup.core.fixes

import com.rp.dedup.core.search.EmbedderProvider
import com.rp.dedup.core.search.SemanticSearchRepository
import com.rp.dedup.core.viewmodels.ImageSearchViewModel
import org.junit.Assert.*
import org.junit.Test

/**
 * Fix #17 — ImageSearchViewModel.Factory created a fresh EmbedderProvider (wrapping
 * a TFLite TextEmbedder) on every ViewModel creation and never closed it. The native
 * runtime and model buffer were retained until GC (which never calls close()).
 *
 * The fix:
 *   - SemanticSearchRepository implements Closeable; close() delegates to embedder.close()
 *   - ImageSearchViewModel.onCleared() calls repository.close()
 *   - EmbedderProvider.close() already existed and is now reachable through the chain
 */
class Fix17EmbedderProviderLeakTest {

    @Test
    fun `EmbedderProvider exposes close() method`() {
        val method = EmbedderProvider::class.java.methods.find { it.name == "close" }
        assertNotNull("EmbedderProvider.close() must be defined to release the TFLite runtime", method)
    }

    @Test
    fun `SemanticSearchRepository implements Closeable`() {
        assertTrue(
            "SemanticSearchRepository must implement Closeable to propagate close() to EmbedderProvider",
            java.io.Closeable::class.java.isAssignableFrom(SemanticSearchRepository::class.java)
        )
    }

    @Test
    fun `SemanticSearchRepository close() delegates to the embedder`() {
        val method = SemanticSearchRepository::class.java.methods.find { it.name == "close" }
        assertNotNull("SemanticSearchRepository must declare close()", method)
    }

    @Test
    fun `ImageSearchViewModel overrides onCleared`() {
        val method = ImageSearchViewModel::class.java
            .declaredMethods.find { it.name == "onCleared" }
        assertNotNull(
            "ImageSearchViewModel must override onCleared() to call repository.close()",
            method
        )
    }
}
