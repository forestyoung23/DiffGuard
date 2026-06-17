package dev.diffguard.settings

import dev.diffguard.model.AISettingsState
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntText
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
        settings.apiKey = settings.apiKey.trim()
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
            settings.apiKey.isBlank() -> ApiKeyUpdate.Clear
            else -> ApiKeyUpdate.Replace(settings.apiKey)
        }
        service.updateSettings(nextState, apiKeyUpdate)
        panel.reset()
    }

    fun resetFrom(state: AISettingsState) {
        settings.baseUrl = state.baseUrl
        settings.apiKey = state.apiKey
        settings.model = state.model
        settings.connectTimeoutSeconds = state.connectTimeoutSeconds
        settings.writeTimeoutSeconds = state.writeTimeoutSeconds
        settings.readTimeoutSeconds = state.readTimeoutSeconds
        settings.callTimeoutSeconds = state.callTimeoutSeconds
        panel.reset()
    }
}
