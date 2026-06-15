package com.rp.dedup.feature.voicestorage.data.model

sealed interface SpeechResult {
    data class Partial(val text: String) : SpeechResult
    data class Final(val text: String) : SpeechResult
    data class Error(val code: Int) : SpeechResult
}
