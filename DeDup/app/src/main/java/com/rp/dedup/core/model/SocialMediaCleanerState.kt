package com.rp.dedup.core.model

sealed class SocialMediaCleanerState {
    object Idle : SocialMediaCleanerState()
    data class ScanningFiles(val found: Int) : SocialMediaCleanerState()
    data class ComputingChecksums(val progress: Float) : SocialMediaCleanerState()
    data class Results(
        val duplicateGroups: List<List<SocialMediaFile>>,
        val reclaimableBytes: Long
    ) : SocialMediaCleanerState()
    data class Error(val message: String) : SocialMediaCleanerState()
}
