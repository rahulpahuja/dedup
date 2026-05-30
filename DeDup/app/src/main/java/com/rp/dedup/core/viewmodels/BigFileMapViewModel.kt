package com.rp.dedup.core.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.rp.dedup.core.model.BigFileMapState
import com.rp.dedup.core.model.FolderNode
import com.rp.dedup.core.deepoptimization.StorageTreeRepository
import com.rp.dedup.core.deepoptimization.StorageTreeRepositoryImpl
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException

class BigFileMapViewModel(
    private val repository: StorageTreeRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _state = MutableStateFlow<BigFileMapState>(BigFileMapState.Idle)
    val state: StateFlow<BigFileMapState> = _state.asStateFlow()

    private var scanJob: Job? = null

    fun startScan(maxDepth: Int = 3) {
        if (_state.value is BigFileMapState.Scanning) return
        scanJob = viewModelScope.launch(ioDispatcher) {
            _state.value = BigFileMapState.Scanning
            try {
                val root = repository.buildTree(maxDepth)
                _state.value = BigFileMapState.Results(root)
            } catch (_: CancellationException) {
                _state.value = BigFileMapState.Idle
            } catch (e: Exception) {
                _state.value = BigFileMapState.Error(e.localizedMessage ?: "Scan failed")
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return BigFileMapViewModel(StorageTreeRepositoryImpl(context)) as T
            }
        }
    }
}
