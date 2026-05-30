package com.rp.dedup.core.model

sealed class WhatsAppCleanerState {
    object Idle : WhatsAppCleanerState()
    data class Scanning(val phase: String) : WhatsAppCleanerState()
    data class Results(val data: WhatsAppScanResult) : WhatsAppCleanerState()
    data class Error(val message: String) : WhatsAppCleanerState()
}
