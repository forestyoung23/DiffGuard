package dev.diffguard.settings

import dev.diffguard.model.AISettingsState
import dev.diffguard.ai.ReviewCancellationToken
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextField
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class AIReviewSettingsComponent(
    private val connectionTester: ProviderConnectionTester = ProviderConnectionTester(),
    private val savedApiKeyProvider: () -> String = { "" },
    private val connectionTestUiTimeoutMillis: Long = DEFAULT_CONNECTION_TEST_UI_TIMEOUT_MILLIS,
    private val runConnectionTestInBackground: ((() -> ConnectionTestResult, (ConnectionTestResult) -> Unit) -> Unit)? = null
) {
    private val settings = AISettingsState()
    private val providerPresets = ProviderPreset.defaultPresets()
    private lateinit var baseUrlField: JTextField
    private lateinit var modelField: JTextField
    private lateinit var apiKeyField: javax.swing.JPasswordField
    private lateinit var testConnectionButton: JButton
    private val connectionStatusLabel = JLabel("")
    private val panel: DialogPanel = panel {
        group("Provider 配置") {
            row("Provider") {
                comboBox(DefaultComboBoxModel(providerPresets.map { it.name }.toTypedArray()))
                    .component
                    .addActionListener { event ->
                        val selectedName = event.source
                            .let { it as javax.swing.JComboBox<*> }
                            .selectedItem as? String
                        val preset = providerPresets.firstOrNull { it.name == selectedName }
                        if (preset != null && !preset.isCustom) {
                            baseUrlField.text = preset.baseUrl
                            modelField.text = preset.model
                        }
                    }
            }
            row("Base URL") {
                baseUrlField = textField()
                    .bindText(settings::baseUrl)
                    .component
            }
            row("API Key") {
                apiKeyField = passwordField()
                    .bindText(settings::apiKey)
                    .component
                apiKeyField.echoChar = '\u2022'
            }
            row("Model") {
                modelField = textField()
                    .bindText(settings::model)
                    .component
            }
            row {
                testConnectionButton = button("Test Connection") { testConnection() }.component
                cell(connectionStatusLabel)
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
        connectionStatusLabel.text = ""
    }

    private fun testConnection() {
        val apiKey = String(apiKeyField.password).trim().ifBlank { savedApiKeyProvider().trim() }
        val state = AISettingsState(
            baseUrl = baseUrlField.text.trim(),
            apiKey = apiKey,
            model = modelField.text.trim()
        )
        val cancellationToken = ReviewCancellationToken()
        testConnectionButton.isEnabled = false
        connectionStatusLabel.text = "正在测试连接..."
        runConnectionTest(
            task = { connectionTester.test(state, cancellationToken) },
            onTimeout = { cancellationToken.cancel() },
            onResult = { result ->
                testConnectionButton.isEnabled = true
                showConnectionResult(result)
            }
        )
    }

    private fun showConnectionResult(result: ConnectionTestResult) {
        showConnectionStatus(
            when (result) {
            is ConnectionTestResult.Succeeded -> result.message
            is ConnectionTestResult.Failed -> result.message
            }
        )
    }

    private fun showConnectionStatus(message: String) {
        val displayMessage = message.truncateForStatusLabel()
        connectionStatusLabel.text = displayMessage
        connectionStatusLabel.toolTipText = message.takeIf { it != displayMessage }
    }

    private fun String.truncateForStatusLabel(): String =
        if (length <= MAX_CONNECTION_STATUS_CHARS) {
            this
        } else {
            take(MAX_CONNECTION_STATUS_CHARS - ELLIPSIS.length) + ELLIPSIS
        }

    private fun runConnectionTest(
        task: () -> ConnectionTestResult,
        onTimeout: () -> Unit,
        onResult: (ConnectionTestResult) -> Unit
    ) {
        val customRunner = runConnectionTestInBackground
        if (customRunner != null) {
            runConnectionTestWithTimeout(
                runTask = { deliver ->
                    customRunner(task, deliver)
                },
                onTimeout = onTimeout,
                onResult = onResult
            )
            return
        }

        runConnectionTestWithTimeout(
            runTask = { deliver ->
                ApplicationManager.getApplication().executeOnPooledThread {
                    val result = task()
                    deliver(result)
                }
            },
            onTimeout = onTimeout,
            onResult = onResult
        )
    }

    private fun runConnectionTestWithTimeout(
        runTask: ((ConnectionTestResult) -> Unit) -> Unit,
        onTimeout: () -> Unit,
        onResult: (ConnectionTestResult) -> Unit
    ) {
        val completed = AtomicBoolean(false)
        val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "DiffGuard Connection Test Timeout").apply { isDaemon = true }
        }
        val timeoutTask = scheduler.schedule(
            {
                if (completed.compareAndSet(false, true)) {
                    onTimeout()
                    dispatchResult(
                        ConnectionTestResult.Failed("连接超时：请检查 Base URL、网络代理或 Provider 状态。"),
                        onResult
                    )
                }
                scheduler.shutdown()
            },
            connectionTestUiTimeoutMillis.coerceAtLeast(1),
            TimeUnit.MILLISECONDS
        )

        runTask { result ->
            if (completed.compareAndSet(false, true)) {
                timeoutTask.cancel(false)
                scheduler.shutdown()
                dispatchResult(result, onResult)
            }
        }
    }

    private fun dispatchResult(
        result: ConnectionTestResult,
        onResult: (ConnectionTestResult) -> Unit
    ) {
        val application = ApplicationManager.getApplication()
        if (application != null) {
            application.invokeLater({ onResult(result) }, ModalityState.any())
        } else {
            onResult(result)
        }
    }

    private companion object {
        const val DEFAULT_CONNECTION_TEST_UI_TIMEOUT_MILLIS = 16_000L
        const val MAX_CONNECTION_STATUS_CHARS = 160
        const val ELLIPSIS = "..."
    }
}
