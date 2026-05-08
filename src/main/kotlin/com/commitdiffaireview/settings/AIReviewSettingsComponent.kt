package com.commitdiffaireview.settings

import com.commitdiffaireview.model.AISettingsState
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class AIReviewSettingsComponent {
    private val settings = AISettingsState()
    private val panel: DialogPanel = panel {
        row("Base URL") {
            textField().bindText(settings::baseUrl)
        }
        row("API Key") {
            passwordField().bindText(settings::apiKey)
        }
        row("Model") {
            textField().bindText(settings::model)
        }
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

    fun getPanel(): JComponent = panel

    fun isModified(): Boolean = panel.isModified()

    fun applyTo(state: AISettingsState) {
        panel.apply()
        state.baseUrl = settings.baseUrl.trim()
        state.apiKey = settings.apiKey
        state.model = settings.model.trim()
        state.connectTimeoutSeconds = settings.connectTimeoutSeconds
        state.writeTimeoutSeconds = settings.writeTimeoutSeconds
        state.readTimeoutSeconds = settings.readTimeoutSeconds
        state.callTimeoutSeconds = settings.callTimeoutSeconds
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
