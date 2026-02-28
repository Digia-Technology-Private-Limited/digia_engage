package com.digia.engage

data class InAppPayload(
    val id: String,
    val content: Map<String, Any?>,
    val cepContext: Map<String, Any?> = emptyMap(),
)
