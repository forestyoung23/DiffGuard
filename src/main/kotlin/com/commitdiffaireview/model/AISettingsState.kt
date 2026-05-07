package com.commitdiffaireview.model

import kotlinx.serialization.Serializable

@Serializable
data class AISettingsState(
    var baseUrl: String = "https://api.openai.com/v1",
    var apiKey: String = "",
    var model: String = "gpt-4o-mini"
)
