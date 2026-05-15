package com.rp.dedup.core.utils

import com.rp.dedup.core.data.ScannedImage
import com.rp.dedup.core.data.ScannedFile

object SelectionLogic {
    
    enum class Strategy {
        KEEP_NEWEST,
        KEEP_OLDEST,
        KEEP_LARGEST,
        KEEP_SMALLEST
    }

    fun selectImagesToDelete(groups: List<List<ScannedImage>>, strategy: Strategy): List<String> {
        val toDelete = mutableListOf<String>()
        groups.forEach { group ->
            if (group.size > 1) {
                val kept = when (strategy) {
                    Strategy.KEEP_NEWEST -> group.maxByOrNull { it.dateModified }
                    Strategy.KEEP_OLDEST -> group.minByOrNull { it.dateModified }
                    Strategy.KEEP_LARGEST -> group.maxByOrNull { it.sizeInBytes }
                    Strategy.KEEP_SMALLEST -> group.minByOrNull { it.sizeInBytes }
                } ?: group.first()
                
                group.forEach { if (it.uri != kept.uri) toDelete.add(it.uri) }
            }
        }
        return toDelete
    }

    fun selectFilesToDelete(groups: List<List<ScannedFile>>, strategy: Strategy): List<String> {
        val toDelete = mutableListOf<String>()
        groups.forEach { group ->
            if (group.size > 1) {
                // For ScannedFile, we don't have dateModified in the model yet, 
                // but we can add it if needed. For now, let's use size.
                val kept = when (strategy) {
                    Strategy.KEEP_LARGEST -> group.maxByOrNull { it.size }
                    Strategy.KEEP_SMALLEST -> group.minByOrNull { it.size }
                    else -> group.first()
                } ?: group.first()

                group.forEach { if (it.uri.toString() != kept.uri.toString()) toDelete.add(it.uri.toString()) }
            }
        }
        return toDelete
    }
}
