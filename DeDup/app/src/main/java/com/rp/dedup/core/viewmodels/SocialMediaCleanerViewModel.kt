package com.rp.dedup.core.viewmodels

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.model.SocialMediaCleanerState
import com.rp.dedup.core.model.SocialMediaFile
import com.rp.dedup.core.deepoptimization.SocialMediaCleanerRepository
import com.rp.dedup.core.deepoptimization.SocialMediaCleanerRepositoryImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException

class SocialMediaCleanerViewModel(
    private val repository: SocialMediaCleanerRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _state = MutableStateFlow<SocialMediaCleanerState>(SocialMediaCleanerState.Idle)
    val state: StateFlow<SocialMediaCleanerState> = _state.asStateFlow()

    private var scanJob: Job? = null

    fun startScan() {
        val current = _state.value
        if (current is SocialMediaCleanerState.ScanningFiles ||
            current is SocialMediaCleanerState.ComputingChecksums) return

        scanJob = viewModelScope.launch(ioDispatcher) {
            try {
                val allFiles = mutableListOf<SocialMediaFile>()
                _state.value = SocialMediaCleanerState.ScanningFiles(0)

                repository.scanMedia().collect { file ->
                    allFiles.add(file)
                    _state.value = SocialMediaCleanerState.ScanningFiles(allFiles.size)
                }

                if (allFiles.isEmpty()) {
                    _state.value = SocialMediaCleanerState.Results(emptyList(), 0L)
                    return@launch
                }

                // Only hash files that share size — avoids hashing unique-size files
                val sizeCandidates = allFiles.groupBy { it.size }.filter { it.value.size > 1 }.values.flatten()
                val total = sizeCandidates.size
                var processed = 0

                val withChecksums = sizeCandidates.map { file ->
                    val checksum = repository.computeChecksum(file)
                    processed++
                    _state.value = SocialMediaCleanerState.ComputingChecksums(processed.toFloat() / total)
                    file.copy(checksum = checksum)
                }

                val duplicateGroups = withChecksums
                    .groupBy { it.checksum }
                    .filter { (key, group) -> key != null && group.size > 1 }
                    .values
                    .toList()

                val reclaimableBytes = duplicateGroups.sumOf { group -> group.drop(1).sumOf { it.size } }
                _state.value = SocialMediaCleanerState.Results(duplicateGroups, reclaimableBytes)

            } catch (_: CancellationException) {
                _state.value = SocialMediaCleanerState.Idle
            } catch (e: Exception) {
                _state.value = SocialMediaCleanerState.Error(e.localizedMessage ?: "Scan failed")
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _state.value = SocialMediaCleanerState.Idle
    }

    fun deleteFiles(uris: List<Uri>) {
        viewModelScope.launch(ioDispatcher) {
            repository.deleteFiles(uris)
            val current = _state.value as? SocialMediaCleanerState.Results ?: return@launch
            val remaining = current.duplicateGroups
                .map { group -> group.filterNot { it.uri in uris } }
                .filter { it.size > 1 }
            val reclaimable = remaining.sumOf { group -> group.drop(1).sumOf { it.size } }
            _state.value = SocialMediaCleanerState.Results(remaining, reclaimable)
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return SocialMediaCleanerViewModel(SocialMediaCleanerRepositoryImpl(context)) as T
            }
        }
    }
}
