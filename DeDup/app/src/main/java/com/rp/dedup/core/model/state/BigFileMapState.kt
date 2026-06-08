package com.rp.dedup.core.model.state

import com.rp.dedup.core.model.FolderNode

sealed class BigFileMapState {
    object Idle : BigFileMapState()
    object Scanning : BigFileMapState()
    data class Results(val root: FolderNode) : BigFileMapState()
    data class Error(val message: String) : BigFileMapState()
}
