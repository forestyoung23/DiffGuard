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
        assertTrue(
            presets.any {
                it.name == "Zhipu GLM" &&
                    it.baseUrl == "https://open.bigmodel.cn/api/coding/paas/v4" &&
                    it.model == "glm-5.1"
            }
        )
        assertTrue(
            presets.any {
                it.name == "MiniMax" &&
                    it.baseUrl == "https://api.minimaxi.com/v1" &&
                    it.model == "MiniMax-M2.7"
            }
        )
        assertTrue(
            presets.any {
                it.name == "Xiaomi MiMo" &&
                    it.baseUrl == "https://token-plan-cn.xiaomimimo.com/v1" &&
                    it.model == "mimo-v2.5-pro"
            }
        )
    }
}
