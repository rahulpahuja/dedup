package com.rp.dedup.core.model.state

import com.rp.dedup.core.model.WhatsAppScanResult

sealed class WhatsAppCleanerState {
    object Idle : WhatsAppCleanerState()
    data class Scanning(val phase: String) : WhatsAppCleanerState()
    data class Results(val data: WhatsAppScanResult) : WhatsAppCleanerState()
    data class Error(val message: String) : WhatsAppCleanerState()
}
