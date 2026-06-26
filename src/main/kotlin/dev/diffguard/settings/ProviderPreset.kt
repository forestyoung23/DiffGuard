package dev.diffguard.settings

data class ProviderPreset(
    val name: String,
    val baseUrl: String,
    val model: String
) {
    val isCustom: Boolean get() = name == CUSTOM_NAME

    override fun toString(): String = name

    companion object {
        private const val CUSTOM_NAME = "Custom"

        fun defaultPresets(): List<ProviderPreset> = listOf(
            ProviderPreset(CUSTOM_NAME, "", ""),
            ProviderPreset("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
            ProviderPreset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
            ProviderPreset("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
            ProviderPreset("Tongyi", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
            ProviderPreset("Volcengine", "https://ark.cn-beijing.volces.com/api/v3", "doubao-seed-1-6"),
            ProviderPreset("Zhipu GLM", "https://open.bigmodel.cn/api/coding/paas/v4", "glm-5.1"),
            ProviderPreset("MiniMax", "https://api.minimaxi.com/v1", "MiniMax-M2.7"),
            ProviderPreset("Xiaomi MiMo", "https://token-plan-cn.xiaomimimo.com/v1", "mimo-v2.5-pro")
        )
    }
}
