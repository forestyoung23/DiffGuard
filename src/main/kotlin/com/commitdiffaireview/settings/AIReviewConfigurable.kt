package com.commitdiffaireview.settings

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

class AIReviewConfigurable : Configurable {
    private var component: AIReviewSettingsComponent? = null

    override fun getDisplayName(): String = "CommitDiffAIReview"

    override fun createComponent(): JComponent {
        val settingsComponent = AIReviewSettingsComponent()
        component = settingsComponent
        settingsComponent.resetFrom(AIReviewSettingsService.getInstance().nonSecretState())
        return settingsComponent.getPanel()
    }

    override fun isModified(): Boolean =
        component?.isModified() ?: false

    override fun apply() {
        component?.applyTo(AIReviewSettingsService.getInstance())
    }

    override fun reset() {
        component?.resetFrom(AIReviewSettingsService.getInstance().nonSecretState())
    }

    override fun disposeUIResources() {
        component = null
    }
}
