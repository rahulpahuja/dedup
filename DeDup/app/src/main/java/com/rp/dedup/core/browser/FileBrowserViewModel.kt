package com.rp.dedup.core.browser

import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class SortMode(val label: String) {
    NAME("Name"),
    SIZE("Size"),
    DATE("Date")
}

class FileBrowserViewModel : ViewModel() {

    private val storageRoot = Environment.getExternalStorageDirectory()

    private val _currentDir = MutableStateFlow(storageRoot)
    val currentDir: StateFlow<File> = _currentDir.asStateFlow()

    // Back-navigation history
    private val backStack = ArrayDeque<File>()

    private val _rawItems = MutableStateFlow<List<FileItem>>(emptyList())
    private val _sortMode = MutableStateFlow(SortMode.NAME)
    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(false)
    private val _errorMessage = MutableStateFlow<String?>(null)

    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    val sortMode: StateFlow<SortMode> = _sortMode.asStateFlow()
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /** Sorted + filtered view of the current directory. Dirs always appear before files. */
    val items: StateFlow<List<FileItem>> = combine(
        _rawItems, _searchQuery, _sortMode
    ) { raw, query, sort ->
        val filtered = if (query.isBlank()) raw
        else raw.filter { it.name.contains(query, ignoreCase = true) }

        val dirs = filtered.filter { it.isDirectory }
        val files = filtered.filter { !it.isDirectory }

        val sortedDirs = when (sort) {
            SortMode.NAME -> dirs.sortedBy { it.name.lowercase() }
            SortMode.SIZE -> dirs.sortedBy { it.name.lowercase() }
            SortMode.DATE -> dirs.sortedByDescending { it.lastModified }
        }
        val sortedFiles = when (sort) {
            SortMode.NAME -> files.sortedBy { it.name.lowercase() }
            SortMode.SIZE -> files.sortedByDescending { it.size }
            SortMode.DATE -> files.sortedByDescending { it.lastModified }
        }
        sortedDirs + sortedFiles
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** True when there is at least one directory in the back-stack. */
    val canNavigateUp: Boolean get() = backStack.isNotEmpty()

    /** Human-readable breadcrumb segments, e.g. ["Internal Storage", "Downloads", "Docs"]. */
    val breadcrumbs: StateFlow<List<String>> = combine(
        _currentDir
    ) { dirs ->
        val dir = dirs[0]
        val relativePath = dir.absolutePath.removePrefix(storageRoot.absolutePath)
        val segments = mutableListOf("Internal Storage")
        if (relativePath.isNotEmpty()) {
            relativePath.split("/").filter { it.isNotBlank() }.forEach { segments.add(it) }
        }
        segments
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), listOf("Internal Storage"))

    init {
        loadDirectory(storageRoot)
    }

    fun navigateTo(dir: File) {
        backStack.addLast(_currentDir.value)
        _currentDir.value = dir
        _searchQuery.value = ""
        loadDirectory(dir)
    }

    /** Returns true if navigation happened, false if already at root. */
    fun navigateUp(): Boolean {
        val prev = backStack.removeLastOrNull() ?: return false
        _currentDir.value = prev
        _searchQuery.value = ""
        loadDirectory(prev)
        return true
    }

    fun setSortMode(mode: SortMode) {
        _sortMode.value = mode
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refresh() {
        loadDirectory(_currentDir.value)
    }

    private fun loadDirectory(dir: File) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val files = dir.listFiles()
                if (files == null) {
                    _rawItems.value = emptyList()
                    _errorMessage.value = "Cannot access this folder"
                } else {
                    _rawItems.value = files
                        .filter { !it.name.startsWith(".") }
                        .map { file ->
                            FileItem(
                                name = file.name,
                                path = file.absolutePath,
                                isDirectory = file.isDirectory,
                                size = if (file.isFile) file.length() else 0L,
                                lastModified = file.lastModified(),
                                extension = if (file.isFile) file.extension.lowercase() else "",
                                childCount = if (file.isDirectory) {
                                    file.listFiles()?.filter { !it.name.startsWith(".") }?.size ?: 0
                                } else 0
                            )
                        }
                }
            } catch (e: SecurityException) {
                _rawItems.value = emptyList()
                _errorMessage.value = "Permission denied"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
