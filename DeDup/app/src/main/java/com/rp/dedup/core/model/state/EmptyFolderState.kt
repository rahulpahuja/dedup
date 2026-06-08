package com.rp.dedup.core.model.state

import com.rp.dedup.core.model.EmptyFolder

sealed class EmptyFolderState {
    object Idle : EmptyFolderState()
    object Scanning : EmptyFolderState()
    data class Results(val folders: List<EmptyFolder>) : EmptyFolderState()
    data class Error(val message: String) : EmptyFolderState()
}
