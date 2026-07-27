package com.rp.dedup.core.viewmodels

import android.content.Context
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.datastore.preferences.core.stringPreferencesKey
import com.rp.dedup.core.caching.DataStoreManager
import com.rp.dedup.core.db.AppDatabase
import com.rp.dedup.core.model.StorageHealthScore
import com.rp.dedup.core.repository.ScanHistoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class StorageHealthViewModel(
    private val historyRepository: ScanHistoryRepository,
    private val dataStore: DataStoreManager,
    private val context: Context
) : ViewModel() {

    companion object {
        internal val PREFS_PREV_SCORE = stringPreferencesKey("health_score_prev")
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val appContext = context.applicationContext
            val db = AppDatabase.getDatabase(appContext)
            return StorageHealthViewModel(
                ScanHistoryRepository(db.scanHistoryDao()),
                DataStoreManager(appContext),
                appContext
            ) as T
        }
    }

    private val _score = MutableStateFlow(StorageHealthScore.empty())
    val score: StateFlow<StorageHealthScore> = _score.asStateFlow()

    init { compute() }

    fun refresh() { compute() }

    private fun compute() {
        viewModelScope.launch(Dispatchers.IO) {
            val prevRaw = dataStore.readData(PREFS_PREV_SCORE, "-1").first().toIntOrNull() ?: -1

            // ── Free Space score (30 pts) ─────────────────────────────────────
            val freeSpaceScore = try {
                val stat = StatFs(Environment.getExternalStorageDirectory().path)
                val free  = stat.availableBlocksLong * stat.blockSizeLong
                val total = stat.blockCountLong * stat.blockSizeLong
                val ratio = if (total > 0) free.toFloat() / total else 0f
                when {
                    ratio >= 0.40f -> 30
                    ratio >= 0.25f -> 22
                    ratio >= 0.15f -> 14
                    ratio >= 0.08f -> 7
                    else           -> 2
                }
            } catch (_: Exception) { 15 }

            // ── Reclaimable score (25 pts) — less reclaimable = better ────────
            val reclaimableScore = try {
                val history = historyRepository.getAll().first()
                val totalReclaimable = history.sumOf { it.reclaimableBytes }
                when {
                    totalReclaimable == 0L         -> 25
                    totalReclaimable < 50_000_000L  -> 20   // <50 MB
                    totalReclaimable < 200_000_000L -> 14   // <200 MB
                    totalReclaimable < 500_000_000L -> 8    // <500 MB
                    else                            -> 3
                }
            } catch (_: Exception) { 12 }

            // ── Scan recency score (25 pts) ────────────────────────────────────
            val scanRecencyScore = try {
                val history = historyRepository.getAll().first()
                val lastScanMs = history.maxOfOrNull { it.timestamp } ?: 0L
                val daysSince = (System.currentTimeMillis() - lastScanMs) / (24 * 60 * 60 * 1000L)
                when {
                    lastScanMs == 0L  -> 0
                    daysSince <= 3    -> 25
                    daysSince <= 7    -> 20
                    daysSince <= 14   -> 14
                    daysSince <= 30   -> 8
                    else              -> 3
                }
            } catch (_: Exception) { 12 }

            // ── Cache size score (20 pts) ──────────────────────────────────────
            val cacheSizeScore = try {
                val cacheBytes = context.cacheDir.walkTopDown().sumOf { it.length() }
                when {
                    cacheBytes < 10_000_000L   -> 20   // <10 MB
                    cacheBytes < 50_000_000L   -> 16   // <50 MB
                    cacheBytes < 200_000_000L  -> 10   // <200 MB
                    cacheBytes < 500_000_000L  -> 5
                    else                        -> 1
                }
            } catch (_: Exception) { 10 }

            val overall = freeSpaceScore + reclaimableScore + scanRecencyScore + cacheSizeScore
            val newScore = StorageHealthScore(
                overallScore     = overall,
                freeSpaceScore   = freeSpaceScore,
                reclaimableScore = reclaimableScore,
                scanRecencyScore = scanRecencyScore,
                cacheSizeScore   = cacheSizeScore,
                label            = StorageHealthScore.labelFor(overall),
                previousScore    = if (prevRaw >= 0) prevRaw else null
            )

            _score.value = newScore

            // Persist current score so next launch shows delta
            dataStore.writeData(PREFS_PREV_SCORE, overall.toString())
        }
    }
}
