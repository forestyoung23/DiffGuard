package dev.diffguard.settings

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ProviderPresetTest {
    @Test
    fun `default presets include common openai compatible providers`() {
        val presets = ProviderPreset.defaultPresets()

        assertEquals("Custom", presets.first().name)
        assertTrue(presets.any { it.name == "OpenAI" && it.baseUrl == "https://api.openai.com/v1" })
        assertTrue(presets.any { it.name == "DeepSeek" && it.model == "deepseek-chat" })
        assertTrue(presets.any { it.name == "OpenRouter" && it.baseUrl == "https://openrouter.ai/api/v1" })
    }
}
