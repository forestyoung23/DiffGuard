package dev.diffguard.settings

import dev.diffguard.model.AISettingsState
import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

sealed interface ApiKeyUpdate {
    data object Keep : ApiKeyUpdate
    data class Replace(val apiKey: String) : ApiKeyUpdate
}

@Service(Service.Level.APP)
@State(
    name = "DiffGuardSettings",
    storages = [Storage("DiffGuardSettings.xml")]
)
class AIReviewSettingsService : PersistentStateComponent<AISettingsState> {
    private var settings = AISettingsState()
    @Volatile
    private var legacyApiKey: String = ""
    @Volatile
    private var cachedApiKey: String? = null

    override fun getState(): AISettingsState = settings.copy(apiKey = legacyApiKey)

    override fun loadState(state: AISettingsState) {
        settings = state.copy(apiKey = "")
        legacyApiKey = state.apiKey
        cachedApiKey = null
        if (legacyApiKey.isNotBlank()) {
            migrateLegacyApiKeyAsync(legacyApiKey)
        }
    }

    fun nonSecretState(): AISettingsState = settings.copy(apiKey = "")

    fun stateWithSecrets(): AISettingsState = settings.copy(apiKey = readApiKey())

    fun updateSettings(state: AISettingsState, apiKeyUpdate: ApiKeyUpdate) {
        settings = state.copy(apiKey = "")
        when (apiKeyUpdate) {
            ApiKeyUpdate.Keep -> {
                legacyApiKey = ""
            }
            is ApiKeyUpdate.Replace -> {
                legacyApiKey = ""
                cachedApiKey = apiKeyUpdate.apiKey
                storeApiKeyAsync(apiKeyUpdate.apiKey)
            }
        }
    }

    private fun readApiKey(): String {
        cachedApiKey?.let { return it }
        val storedApiKey = PasswordSafe.instance.getPassword(credentialAttributes())
        if (!storedApiKey.isNullOrBlank()) {
            cachedApiKey = storedApiKey
            return storedApiKey
        }

        val apiKeyToMigrate = legacyApiKey
        if (apiKeyToMigrate.isNotBlank()) {
            storeApiKey(apiKeyToMigrate)
            cachedApiKey = apiKeyToMigrate
            return apiKeyToMigrate
        }
        cachedApiKey = ""
        return ""
    }

    private fun storeApiKeyAsync(apiKey: String?) {
        ApplicationManager.getApplication().executeOnPooledThread {
            storeApiKey(apiKey)
        }
    }

    private fun migrateLegacyApiKeyAsync(apiKey: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            runCatching {
                storeApiKey(apiKey)
            }.onSuccess {
                if (legacyApiKey == apiKey) {
                    legacyApiKey = ""
                    cachedApiKey = apiKey
                }
            }
        }
    }

    private fun storeApiKey(apiKey: String?) {
        val credentials = apiKey?.let { Credentials(API_KEY_USER, it) }
        PasswordSafe.instance.set(credentialAttributes(), credentials)
    }

    private fun credentialAttributes(): CredentialAttributes =
        CredentialAttributes(generateServiceName(SERVICE_NAME, API_KEY_USER))

    companion object {
        private const val SERVICE_NAME = "DiffGuard"
        private const val API_KEY_USER = "OpenAI Compatible API Key"

        fun getInstance(): AIReviewSettingsService =
            ApplicationManager.getApplication().getService(AIReviewSettingsService::class.java)
    }
}
