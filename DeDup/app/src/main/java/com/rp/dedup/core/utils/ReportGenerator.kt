package com.rp.dedup.core.utils

import android.content.Context
import com.rp.dedup.core.model.ScanHistory
import java.io.File

object ReportGenerator {
    fun generateCSV(context: Context, history: List<ScanHistory>): File? {
        return try {
            val file = File(context.getExternalFilesDir(null), "scan_report_${System.currentTimeMillis()}.csv")
            file.printWriter().use { out ->
                out.println("Type,Timestamp,Duration(ms),Total Scanned,Groups,Duplicates,Reclaimable(Bytes),Status")
                history.forEach {
                    out.println("${it.scanType},${it.timestamp},${it.durationMs},${it.totalScanned},${it.duplicateGroups},${it.totalDuplicates},${it.reclaimableBytes},${it.status}")
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
