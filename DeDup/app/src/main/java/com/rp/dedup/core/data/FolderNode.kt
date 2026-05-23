package com.rp.dedup.core.data

data class FolderNode(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val children: List<FolderNode> = emptyList()
) {
    val isLeaf: Boolean get() = children.isEmpty()
}
