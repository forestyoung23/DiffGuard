package com.commitdiffaireview.settings

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

class AIReviewConfigurable : Configurable {
    private var component: AIReviewSettingsComponent? = null

    override fun getDisplayName(): String = "CommitDiffAIReview"

    override fun createComponent(): JComponent {
        val service = AIReviewSettingsService.getInstance()
        val settingsComponent = AIReviewSettingsComponent()
        component = settingsComponent
        settingsComponent.resetFrom(service.stateWithSecrets())
        return settingsComponent.getPanel()
    }

    override fun isModified(): Boolean =
        component?.isModified() ?: false

    override fun apply() {
        component?.applyTo(AIReviewSettingsService.getInstance())
    }

    override fun reset() {
        val service = AIReviewSettingsService.getInstance()
        component?.resetFrom(service.stateWithSecrets())
    }

    override fun disposeUIResources() {
        component = null
    }
}
