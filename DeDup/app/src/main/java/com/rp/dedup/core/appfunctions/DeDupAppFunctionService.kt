package com.rp.dedup.core.appfunctions

import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunction
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Placeholder AppFunctions entry point — proves the KSP toolchain (dependency +
 * compiler + generated XML metadata) compiles end-to-end before any real,
 * read-only storage queries are wired in on top of it.
 */
@RequiresApi(36)
@AppFunctionServiceEntryPoint(
    serviceName = "DeDupAppFunctionService",
    appFunctionXmlFileName = "dedup_app_function_service",
)
abstract class BaseDeDupAppFunctionService : AppFunctionService() {

    @AppFunction(isDescribedByKDoc = true)
    suspend fun ping(): String = withContext(Dispatchers.IO) { "pong" }
}
