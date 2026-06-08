package com.rp.dedup.core.repository

import com.rp.dedup.core.dao.ScannedImageDao
import com.rp.dedup.core.model.ScannedImage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ScannedImageRepositoryTest {

    private val dao = mockk<ScannedImageDao>(relaxed = true)
    private lateinit var repository: ScannedImageRepository

    private fun image(uri: String, groupKey: String = "", size: Long = 1024L) = ScannedImage(
        uri          = uri,
        dHash        = 0L,
        sizeInBytes  = size,
        groupKey     = groupKey
    )

    @Before
    fun setUp() {
        repository = ScannedImageRepository(dao)
    }

    // ── getAllImages ───────────────────────────────────────────────────────────

    @Test
    fun `getAllImages returns flow from DAO`() = runTest {
        val images = listOf(image("uri1"), image("uri2"))
        every { dao.getAllImages() } returns flowOf(images)

        val result = repository.getAllImages().first()

        assertEquals(2, result.size)
    }

    // ── getCachedDuplicateGroups ───────────────────────────────────────────────

    @Test
    fun `getCachedDuplicateGroups groups images by groupKey`() = runTest {
        val images = listOf(
            image("a", "group1"),
            image("b", "group1"),
            image("c", "group2"),
            image("d", "group2"),
            image("e", "group2")
        )
        coEvery { dao.getCachedDuplicateImages() } returns images

        val groups = repository.getCachedDuplicateGroups()

        assertEquals(2, groups.size)
        assertTrue(groups.any { it.size == 2 })
        assertTrue(groups.any { it.size == 3 })
    }

    @Test
    fun `getCachedDuplicateGroups filters out singleton groups`() = runTest {
        val images = listOf(
            image("a", "group1"),
            image("b", "group2"),  // singleton
            image("c", "group1")
        )
        coEvery { dao.getCachedDuplicateImages() } returns images

        val groups = repository.getCachedDuplicateGroups()

        assertEquals(1, groups.size)
        assertEquals(2, groups.first().size)
    }

    @Test
    fun `getCachedDuplicateGroups returns empty when all are singletons`() = runTest {
        coEvery { dao.getCachedDuplicateImages() } returns listOf(
            image("a", "g1"),
            image("b", "g2"),
            image("c", "g3")
        )

        val groups = repository.getCachedDuplicateGroups()

        assertTrue(groups.isEmpty())
    }

    @Test
    fun `getCachedDuplicateGroups returns empty when DAO returns empty`() = runTest {
        coEvery { dao.getCachedDuplicateImages() } returns emptyList()

        val groups = repository.getCachedDuplicateGroups()

        assertTrue(groups.isEmpty())
    }

    // ── insertImages ──────────────────────────────────────────────────────────

    @Test
    fun `insertImages delegates to DAO`() = runTest {
        val images = listOf(image("x"), image("y"))
        repository.insertImages(images)
        coVerify(exactly = 1) { dao.insertImages(images) }
    }

    // ── deleteByUri ───────────────────────────────────────────────────────────

    @Test
    fun `deleteByUri delegates to DAO`() = runTest {
        repository.deleteByUri("content://uri/42")
        coVerify(exactly = 1) { dao.deleteByUri("content://uri/42") }
    }

    // ── clearAll ──────────────────────────────────────────────────────────────

    @Test
    fun `clearAll delegates to DAO`() = runTest {
        repository.clearAll()
        coVerify(exactly = 1) { dao.clearAll() }
    }
}
