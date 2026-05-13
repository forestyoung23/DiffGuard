package com.commitdiffaireview.settings

import com.commitdiffaireview.model.AISettingsState
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class AIReviewSettingsComponent {
    private val settings = FormState()
    private val panel: DialogPanel = panel {
        group("Provider 配置") {
            row("Base URL") {
                textField()
                    .bindText(settings::baseUrl)
                    .comment("OpenAI-compatible API base URL, e.g. https://api.openai.com/v1")
            }
            row("API Key") {
                passwordField()
                    .bindText(settings::apiKey)
                    .comment("Leave blank to keep the stored key. Enter a new key to replace it.")
            }
            row("Model") {
                textField()
                    .bindText(settings::model)
                    .comment("Model name supported by your provider")
            }
            row {
                checkBox("Clear stored API Key")
                    .bindSelected(settings::clearStoredApiKey)
            }
        }
        group("Advanced / 超时配置") {
            row("Connect Timeout Seconds") {
                intTextField().bindIntText(settings::connectTimeoutSeconds)
            }
            row("Write Timeout Seconds") {
                intTextField().bindIntText(settings::writeTimeoutSeconds)
            }
            row("Read Timeout Seconds") {
                intTextField().bindIntText(settings::readTimeoutSeconds)
            }
            row("Call Timeout Seconds") {
                intTextField().bindIntText(settings::callTimeoutSeconds)
            }
        }
    }

    fun getPanel(): JComponent = panel

    fun isModified(): Boolean = panel.isModified()

    fun applyTo(service: AIReviewSettingsService) {
        panel.apply()
        val nextState = AISettingsState(
            baseUrl = settings.baseUrl.trim(),
            apiKey = "",
            model = settings.model.trim(),
            connectTimeoutSeconds = settings.connectTimeoutSeconds,
            writeTimeoutSeconds = settings.writeTimeoutSeconds,
            readTimeoutSeconds = settings.readTimeoutSeconds,
            callTimeoutSeconds = settings.callTimeoutSeconds
        )
        val apiKeyUpdate = when {
            settings.apiKey.isNotBlank() -> ApiKeyUpdate.Replace(settings.apiKey.trim())
            settings.clearStoredApiKey -> ApiKeyUpdate.Clear
            else -> ApiKeyUpdate.Keep
        }
        service.updateSettings(nextState, apiKeyUpdate)
        settings.apiKey = ""
        settings.clearStoredApiKey = false
        panel.reset()
    }

    fun resetFrom(state: AISettingsState) {
        settings.baseUrl = state.baseUrl
        settings.apiKey = ""
        settings.model = state.model
        settings.connectTimeoutSeconds = state.connectTimeoutSeconds
        settings.writeTimeoutSeconds = state.writeTimeoutSeconds
        settings.readTimeoutSeconds = state.readTimeoutSeconds
        settings.callTimeoutSeconds = state.callTimeoutSeconds
        settings.clearStoredApiKey = false
        panel.reset()
    }

    private data class FormState(
        var baseUrl: String = "https://api.openai.com/v1",
        var apiKey: String = "",
        var model: String = "gpt-4o-mini",
        var connectTimeoutSeconds: Int = 30,
        var writeTimeoutSeconds: Int = 60,
        var readTimeoutSeconds: Int = 300,
        var callTimeoutSeconds: Int = 360,
        var clearStoredApiKey: Boolean = false
    )
}
