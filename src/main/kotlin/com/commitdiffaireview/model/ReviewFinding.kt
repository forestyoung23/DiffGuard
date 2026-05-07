package com.commitdiffaireview.model

import kotlinx.serialization.Serializable

@Serializable
data class ReviewFinding(
    val level: String,
    val file: String,
    val line: Int? = null,
    val message: String
)
