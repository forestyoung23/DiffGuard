package dev.diffguard.settings

import dev.diffguard.model.AISettingsState
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class AIReviewSettingsComponent {
    private val settings = AISettingsState()
    private val panel: DialogPanel = panel {
        group("Provider 配置") {
            row("Base URL") {
                textField()
                    .bindText(settings::baseUrl)
            }
            row("API Key") {
                val apiKeyField = passwordField()
                    .bindText(settings::apiKey)
                    .component
                apiKeyField.echoChar = '\u2022'
            }
            row("Model") {
                textField()
                    .bindText(settings::model)
            }
        }
    }

    fun getPanel(): JComponent = panel

    fun isModified(): Boolean = panel.isModified()

    fun applyTo(service: AIReviewSettingsService) {
        panel.apply()
        settings.apiKey = settings.apiKey.trim()
        val currentState = service.nonSecretState()
        val nextState = AISettingsState(
            baseUrl = settings.baseUrl.trim(),
            apiKey = "",
            model = settings.model.trim(),
            connectTimeoutSeconds = currentState.connectTimeoutSeconds,
            writeTimeoutSeconds = currentState.writeTimeoutSeconds,
            readTimeoutSeconds = currentState.readTimeoutSeconds,
            callTimeoutSeconds = currentState.callTimeoutSeconds
        )
        val apiKeyUpdate = if (settings.apiKey.isBlank()) {
            ApiKeyUpdate.Keep
        } else {
            ApiKeyUpdate.Replace(settings.apiKey)
        }
        service.updateSettings(nextState, apiKeyUpdate)
        settings.apiKey = ""
        panel.reset()
    }

    fun resetFrom(state: AISettingsState) {
        settings.baseUrl = state.baseUrl
        settings.apiKey = ""
        settings.model = state.model
        panel.reset()
    }
}
