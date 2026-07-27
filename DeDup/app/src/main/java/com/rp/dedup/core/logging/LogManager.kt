package com.rp.dedup.core.logging

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * LogManager handles writing application logs to local files.
 * It manages up to 100 files, each with a maximum size of 2MB.
 *
 * This implementation is independent of android.jar for core logging logic.
 */
class LogManager private constructor(private val logDir: File) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logChannel = Channel<String>(Channel.UNLIMITED)
    private val dateFormat = SimpleDateFormat(DATE_FORMAT_LOG, Locale.getDefault())
    private val fileDateFormat = SimpleDateFormat(DATE_FORMAT_FILE, Locale.getDefault())

    init {
        if (!logDir.exists()) logDir.mkdirs()
        
        // Start a consumer coroutine to handle sequential writes off the main thread
        scope.launch {
            for (logLine in logChannel) {
                writeLogToFile(logLine)
            }
        }
    }

    companion object {
        private const val MAX_FILE_SIZE = 2 * 1024 * 1024 // 2MB
        private const val MAX_FILE_COUNT = 100
        private const val DEFAULT_TAG = "LogManager"

        // region Static Strings
        private const val DIR_NAME = "logs"
        private const val DATE_FORMAT_LOG = "yyyy-MM-dd HH:mm:ss.SSS"
        private const val DATE_FORMAT_FILE = "yyyyMMdd_HHmmss"
        private const val FILE_PREFIX = "log_"
        private const val FILE_EXTENSION = ".txt"
        private const val DEBUG_LEVEL = "DEBUG"
        private const val ERROR_LEVEL = "ERROR"
        private const val MSG_WRITE_FAIL = "Failed to write log to file"
        private const val MSG_DELETE_OLD = "Deleted old log file: "
        // endregion

        @Volatile
        private var INSTANCE: LogManager? = null

        /**
         * Initialize the LogManager with a directory.
         * In Android, this would be context.filesDir.
         */
        fun getInstance(baseDir: File): LogManager {
            val logDir = File(baseDir, DIR_NAME)
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: LogManager(logDir).also { INSTANCE = it }
            }
        }

        /**
         * Convenience method to log a debug message.
         * The actual Android Log.d call should be handled by the caller or a wrapper.
         */
        fun d(baseDir: File, tag: String, message: String) {
            getInstance(baseDir).log(DEBUG_LEVEL, tag, message)
        }

        /**
         * Convenience method to log an error message.
         */
        fun e(baseDir: File, tag: String, message: String, throwable: Throwable? = null) {
            val fullMessage = if (throwable != null) {
                val s = java.io.StringWriter()
                throwable.printStackTrace(java.io.PrintWriter(s))
                "$message\n$s"
            } else {
                message
            }
            getInstance(baseDir).log(ERROR_LEVEL, tag, fullMessage)
        }
    }

    /**
     * Enqueues a log message to be written to disk.
     * TODO: This needs to be integrated with the server to upload logs periodically.
     */
    fun log(level: String, tag: String, message: String) {
        val logLine = "${dateFormat.format(Date())} [$level] $tag: $message\n"
        logChannel.trySend(logLine)
    }

    private suspend fun writeLogToFile(logLine: String) = withContext(Dispatchers.IO) {
        try {
            val currentFile = getCurrentLogFile()
            
            FileOutputStream(currentFile, true).use { fos ->
                fos.write(logLine.toByteArray())
            }

            if (currentFile.length() > MAX_FILE_SIZE) {
                rotateFiles()
            }
        } catch (e: Exception) {
            // Since we're independent of android.jar, we use System.err
            System.err.println("$DEFAULT_TAG: $MSG_WRITE_FAIL - ${e.message}")
        }
    }

    private fun getCurrentLogFile(): File {
        val files = logDir.listFiles()?.filter { it.name.startsWith(FILE_PREFIX) }?.sortedByDescending { it.name }
        return if (files.isNullOrEmpty()) {
            createNewLogFile()
        } else {
            val lastFile = files.first()
            if (lastFile.length() > MAX_FILE_SIZE) {
                createNewLogFile()
            } else {
                lastFile
            }
        }
    }

    private fun createNewLogFile(): File {
        val fileName = "$FILE_PREFIX${fileDateFormat.format(Date())}$FILE_EXTENSION"
        return File(logDir, fileName)
    }

    private fun rotateFiles() {
        val files = logDir.listFiles()?.filter { it.name.startsWith(FILE_PREFIX) }?.sortedBy { it.name }
        if (files != null && files.size > MAX_FILE_COUNT) {
            val filesToDelete = files.size - MAX_FILE_COUNT
            for (i in 0 until filesToDelete) {
                if (files[i].delete()) {
                    println("$DEFAULT_TAG: $MSG_DELETE_OLD${files[i].name}")
                }
            }
        }
    }
    
    fun getAllLogFiles(): List<File> {
        return logDir.listFiles()?.filter { it.name.startsWith(FILE_PREFIX) }?.sortedByDescending { it.name } ?: emptyList()
    }
}
