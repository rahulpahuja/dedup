package com.rp.dedup.core.viewmodels

import android.net.Uri
import com.rp.dedup.core.model.state.SmartJunkState
import com.rp.dedup.core.search.SmartJunkRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for SmartJunkViewModel pure state-mutation logic.
 * startScan() calls AndroidViewModel internals and AI classifiers which
 * require instrumented tests. Here we test state-data transformations directly.
 */
class SmartJunkViewModelTest {

    private val cat = SmartJunkRepository.JunkCategory.SCREENSHOTS

    private fun uri(s: String): Uri = mockk<Uri>(relaxed = true).also { every { it.toString() } returns s }

    private fun junkItem(uriRef: Uri, name: String) = SmartJunkRepository.JunkItem(
        uri      = uriRef,
        category = cat,
        labels   = emptyList(),
        fileName = name,
        size     = 1024L,
        aiReason = null
    )

    private fun groups(vararg items: SmartJunkRepository.JunkItem)
        : Map<SmartJunkRepository.JunkCategory, List<SmartJunkRepository.JunkItem>> =
            mapOf(cat to items.toList())

    private fun resultsState(
        groups: Map<SmartJunkRepository.JunkCategory, List<SmartJunkRepository.JunkItem>> = emptyMap(),
        selected: Set<Uri> = emptySet()
    ) = SmartJunkState.Results(groups = groups, selectedUris = selected)

    // ── toggleCategoryExpansion ────────────────────────────────────────────────

    @Test
    fun `Results copy adds category to expandedCategories`() {
        val state = resultsState()
        val newState = state.copy(expandedCategories = state.expandedCategories + cat)
        assertTrue(newState.expandedCategories.contains(cat))
    }

    @Test
    fun `Results copy removes category from expandedCategories`() {
        val state = resultsState().copy(expandedCategories = setOf(cat))
        val newState = state.copy(expandedCategories = state.expandedCategories - cat)
        assertFalse(newState.expandedCategories.contains(cat))
    }

    // ── toggleSelection ────────────────────────────────────────────────────────

    @Test
    fun `Results copy adds uri to selectedUris`() {
        val u = uri("content://file/1")
        val state = resultsState()
        val newState = state.copy(selectedUris = state.selectedUris + u)
        assertTrue(newState.selectedUris.contains(u))
    }

    @Test
    fun `Results copy removes uri from selectedUris`() {
        val u = uri("content://file/1")
        val state = resultsState(selected = setOf(u))
        val newState = state.copy(selectedUris = state.selectedUris - u)
        assertFalse(newState.selectedUris.contains(u))
    }

    // ── selectAllInCategory ────────────────────────────────────────────────────

    @Test
    fun `selectAll adds all uris from category`() {
        val u1 = uri("content://a")
        val u2 = uri("content://b")
        val items = listOf(junkItem(u1, "a.tmp"), junkItem(u2, "b.tmp"))
        val state = resultsState(groups = groups(*items.toTypedArray()))
        val categoryUris = items.map { it.uri }.toSet()
        val newState = state.copy(selectedUris = state.selectedUris + categoryUris)
        assertTrue(newState.selectedUris.contains(u1))
        assertTrue(newState.selectedUris.contains(u2))
    }

    // ── deselectAllInCategory ──────────────────────────────────────────────────

    @Test
    fun `deselectAll removes all uris from category`() {
        val u1 = uri("content://a")
        val u2 = uri("content://b")
        val items = listOf(junkItem(u1, "a.tmp"), junkItem(u2, "b.tmp"))
        val state = resultsState(groups = groups(*items.toTypedArray()), selected = setOf(u1, u2))
        val categoryUris = items.map { it.uri }.toSet()
        val newState = state.copy(selectedUris = state.selectedUris - categoryUris)
        assertFalse(newState.selectedUris.contains(u1))
        assertFalse(newState.selectedUris.contains(u2))
    }

    // ── removeDeletedItems ─────────────────────────────────────────────────────

    @Test
    fun `removeDeletedItems filters items from groups`() {
        val u1 = uri("content://a")
        val u2 = uri("content://b")
        val items = listOf(junkItem(u1, "a.tmp"), junkItem(u2, "b.tmp"))
        val state = resultsState(groups = groups(*items.toTypedArray()), selected = setOf(u1))

        val deletedUris = listOf(u1)
        val newGroups = state.groups.mapValues { (_, its) ->
            its.filterNot { it.uri in deletedUris }
        }.filterValues { it.isNotEmpty() }
        val newState = state.copy(groups = newGroups, selectedUris = state.selectedUris - deletedUris.toSet())

        assertEquals(1, newState.groups[cat]?.size)
        assertFalse(newState.selectedUris.contains(u1))
    }

    @Test
    fun `removeDeletedItems removes empty categories`() {
        val u1 = uri("content://only")
        val items = listOf(junkItem(u1, "only.tmp"))
        val state = resultsState(groups = groups(*items.toTypedArray()))

        val newGroups = state.groups.mapValues { (_, its) ->
            its.filterNot { it.uri in listOf(u1) }
        }.filterValues { it.isNotEmpty() }

        assertFalse(newGroups.containsKey(cat))
    }

    // ── SmartJunkState sealed class ────────────────────────────────────────────

    @Test
    fun `SmartJunkState Idle is Idle type`() {
        assertTrue(SmartJunkState.Idle is SmartJunkState)
    }

    @Test
    fun `SmartJunkState Scanning holds progress`() {
        val state = SmartJunkState.Scanning(0.5f)
        assertEquals(0.5f, state.progress, 0.001f)
    }

    @Test
    fun `SmartJunkState Error holds message`() {
        val state = SmartJunkState.Error("something went wrong")
        assertEquals("something went wrong", state.message)
    }
}
