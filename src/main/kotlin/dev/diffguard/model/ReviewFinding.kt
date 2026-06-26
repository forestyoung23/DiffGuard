package dev.diffguard.model

import kotlinx.serialization.Serializable

@Serializable
data class ReviewFinding(
    val level: String,
    val file: String,
    val line: Int? = null,
    val message: String,
    val category: String? = null,
    val confidence: String? = null,
    val evidence: String? = null
)
