package com.rp.dedup.core.model

data class ScannedContact(
    val id: String,
    val name: String,
    val phoneNumbers: List<String>,
    val emails: List<String>
)
