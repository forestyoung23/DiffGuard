package com.commitdiffaireview.settings

import com.commitdiffaireview.model.AISettingsState
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(
    name = "CommitDiffAIReviewSettings",
    storages = [Storage("commitDiffAIReview.xml")]
)
class AIReviewSettingsService : PersistentStateComponent<AISettingsState> {
    private var settings = AISettingsState()

    override fun getState(): AISettingsState = settings

    override fun loadState(state: AISettingsState) {
        settings = state
    }

    companion object {
        fun getInstance(): AIReviewSettingsService =
            ApplicationManager.getApplication().getService(AIReviewSettingsService::class.java)
    }
}
