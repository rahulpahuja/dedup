package com.rp.dedup.core.logging

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LogManagerTest {

    private lateinit var tempDir: File

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("dedup_log_test").toFile()
        resetLogManagerInstance()
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
        resetLogManagerInstance()
    }

    private fun resetLogManagerInstance() {
        try {
            val field = LogManager::class.java.getDeclaredField("INSTANCE")
            field.isAccessible = true
            field.set(null, null)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Test
    fun testSingleton() {
        val manager1 = LogManager.getInstance(tempDir)
        val manager2 = LogManager.getInstance(tempDir)
        assertSame(manager1, manager2)
    }

    @Test
    fun testLogDebugMessage() = runBlocking {
        val manager = LogManager.getInstance(tempDir)
        LogManager.d(tempDir, "TestTag", "This is a debug message")
        
        // Wait for coroutine to write
        delay(100)

        val files = manager.getAllLogFiles()
        assertEquals(1, files.size)
        
        val content = files.first().readText()
        assertTrue(content.contains("[DEBUG] TestTag: This is a debug message"))
    }

    @Test
    fun testLogErrorWithThrowable() = runBlocking {
        val manager = LogManager.getInstance(tempDir)
        val exception = RuntimeException("Something failed")
        LogManager.e(tempDir, "ErrorTag", "An error occurred", exception)
        
        // Wait for coroutine to write
        delay(100)

        val files = manager.getAllLogFiles()
        assertEquals(1, files.size)
        
        val content = files.first().readText()
        assertTrue(content.contains("[ERROR] ErrorTag: An error occurred"))
        assertTrue(content.contains("java.lang.RuntimeException: Something failed"))
    }

    @Test
    fun testLogErrorWithoutThrowable() = runBlocking {
        val manager = LogManager.getInstance(tempDir)
        LogManager.e(tempDir, "ErrorTag", "Simple error message")
        
        // Wait for coroutine to write
        delay(100)

        val files = manager.getAllLogFiles()
        assertEquals(1, files.size)
        
        val content = files.first().readText()
        assertTrue(content.contains("[ERROR] ErrorTag: Simple error message"))
    }

    @Test
    fun testLogRotationFilesLimit() {
        val manager = LogManager.getInstance(tempDir)
        
        // Access logDir and manually create 105 log files to trigger rotation
        val logDir = File(tempDir, "logs")
        logDir.mkdirs()
        
        // Create 105 dummy files with prefixed names
        for (i in 1..105) {
            val file = File(logDir, "log_20260610_1200%02d.txt".format(i))
            file.writeText("Dummy log line\n")
        }

        assertEquals(105, manager.getAllLogFiles().size)

        // Trigger rotation via reflection
        val method = LogManager::class.java.getDeclaredMethod("rotateFiles")
        method.isAccessible = true
        method.invoke(manager)

        // Rotation keeps up to MAX_FILE_COUNT (100) files.
        val remainingFiles = manager.getAllLogFiles()
        assertEquals(100, remainingFiles.size)
        
        // Verify old files are deleted
        for (i in 1..5) {
            val file = File(logDir, "log_20260610_1200%02d.txt".format(i))
            assertFalse(file.exists())
        }
        
        // Verify newer files remain
        for (i in 6..105) {
            val file = File(logDir, "log_20260610_1200%02d.txt".format(i))
            assertTrue(file.exists())
        }
    }
}
