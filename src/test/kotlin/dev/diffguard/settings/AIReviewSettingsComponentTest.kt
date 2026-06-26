package dev.diffguard.settings

import dev.diffguard.model.AISettingsState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import java.awt.event.ActionEvent
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPasswordField
import javax.swing.JTextField

class AIReviewSettingsComponentTest {
    @Test
    fun `reset with existing api key leaves password field empty`() {
        val component = AIReviewSettingsComponent()

        component.resetFrom(AISettingsState(apiKey = "sk-test-key"))

        val apiKeyField = apiKeyFieldIn(component)
        assertEquals("", String(apiKeyField.password))
        assertEquals('\u2022', apiKeyField.echoChar)
        assertFalse(visibleNonPasswordTextIn(component.getPanel()).contains("sk-test-key"))
        assertFalse(component.isModified())
    }

    @Test
    fun `reset with empty api key leaves password field empty`() {
        val component = AIReviewSettingsComponent()

        component.resetFrom(AISettingsState(apiKey = ""))

        assertEquals("", String(apiKeyFieldIn(component).password))
        assertFalse(component.isModified())
    }

    @Test
    fun `typing replacement api key marks settings as modified`() {
        val component = AIReviewSettingsComponent()
        component.resetFrom(AISettingsState(apiKey = "sk-test-key"))

        apiKeyFieldIn(component).text = "sk-new-key"

        assertTrue(component.isModified())
    }

    @Test
    fun `settings panel hides advanced timeout fields`() {
        val component = AIReviewSettingsComponent()
        component.resetFrom(AISettingsState())

        val visibleText = visibleNonPasswordTextIn(component.getPanel())

        assertFalse(visibleText.contains("Connect Timeout Seconds"))
        assertFalse(visibleText.contains("Write Timeout Seconds"))
        assertFalse(visibleText.contains("Read Timeout Seconds"))
        assertFalse(visibleText.contains("Call Timeout Seconds"))
    }

    @Test
    fun `selecting provider preset applies base url and model`() {
        val component = AIReviewSettingsComponent()
        component.resetFrom(AISettingsState())

        providerPresetComboIn(component).selectedItem = "DeepSeek"

        val textValues = textFieldValuesIn(component)
        assertTrue(textValues.contains("https://api.deepseek.com/v1"), textValues.toString())
        assertTrue(textValues.contains("deepseek-chat"), textValues.toString())
        assertTrue(component.isModified())
    }

    @Test
    fun `test connection button shows tester result using saved api key when field is empty`() {
        val component = AIReviewSettingsComponent(
            connectionTester = ProviderConnectionTester(providerFactory = { TestProvider("[]") }),
            savedApiKeyProvider = { "sk-saved" },
            runConnectionTestInBackground = { task, onResult -> onResult(task()) }
        )
        component.resetFrom(AISettingsState(apiKey = "sk-saved"))

        clickButton(component.getPanel(), "Test Connection")
        waitForText(component, "连接成功")

        assertTrue(
            visibleNonPasswordTextIn(component.getPanel()).contains("连接成功，Provider 已返回响应。"),
            visibleNonPasswordTextIn(component.getPanel())
        )
    }

    @Test
    fun `test connection button does not block while provider request is running`() {
        val component = AIReviewSettingsComponent(
            connectionTester = ProviderConnectionTester(providerFactory = { SlowProvider() }),
            savedApiKeyProvider = { "sk-saved" },
            runConnectionTestInBackground = { task, onResult ->
                Thread {
                    onResult(task())
                }.apply {
                    isDaemon = true
                    start()
                }
            }
        )
        component.resetFrom(AISettingsState(apiKey = "sk-saved"))

        val elapsedMs = measureTimeMillis {
            clickButton(component.getPanel(), "Test Connection")
        }

        assertTrue(elapsedMs < 150, "button click blocked for ${elapsedMs}ms")
        assertTrue(
            visibleNonPasswordTextIn(component.getPanel()).contains("正在测试连接..."),
            visibleNonPasswordTextIn(component.getPanel())
        )
    }

    @Test
    fun `test connection recovers when background runner never returns`() {
        val component = AIReviewSettingsComponent(
            savedApiKeyProvider = { "sk-saved" },
            connectionTestUiTimeoutMillis = 50,
            runConnectionTestInBackground = { _, _ -> Unit }
        )
        component.resetFrom(AISettingsState(apiKey = "sk-saved"))

        clickButton(component.getPanel(), "Test Connection")
        waitForText(component, "连接超时")

        val visibleText = visibleNonPasswordTextIn(component.getPanel())
        assertTrue(visibleText.contains("连接超时"), visibleText)
        assertTrue(testConnectionButtonIn(component).isEnabled)
    }

    @Test
    fun `test connection timeout cancels running provider request`() {
        val cancelled = CountDownLatch(1)
        val component = AIReviewSettingsComponent(
            connectionTester = ProviderConnectionTester(providerFactory = { CancellableSlowProvider(cancelled) }),
            savedApiKeyProvider = { "sk-saved" },
            connectionTestUiTimeoutMillis = 50,
            runConnectionTestInBackground = { task, onResult ->
                Thread {
                    onResult(task())
                }.apply {
                    isDaemon = true
                    start()
                }
            }
        )
        component.resetFrom(AISettingsState(apiKey = "sk-saved"))

        clickButton(component.getPanel(), "Test Connection")
        waitForText(component, "连接超时")

        assertTrue(cancelled.await(1, TimeUnit.SECONDS), "connection request was not cancelled after UI timeout")
    }

    @Test
    fun `long connection error is shortened in settings panel and preserved in tooltip`() {
        val longError = "连接失败：" + "Invalid token ".repeat(40)
        val component = AIReviewSettingsComponent(
            connectionTester = ProviderConnectionTester(providerFactory = { FailingProvider(longError) }),
            savedApiKeyProvider = { "sk-saved" },
            runConnectionTestInBackground = { task, onResult -> onResult(task()) }
        )
        component.resetFrom(AISettingsState(apiKey = "sk-saved"))

        clickButton(component.getPanel(), "Test Connection")
        waitForText(component, "连接失败")

        val statusLabel = connectionStatusLabelIn(component)
        assertTrue(statusLabel.text.length <= 180, statusLabel.text)
        assertTrue(statusLabel.text.endsWith("..."), statusLabel.text)
        assertEquals("连接失败：$longError", statusLabel.toolTipText)
    }

    private fun apiKeyFieldIn(component: AIReviewSettingsComponent): JPasswordField =
        componentsIn(component.getPanel())
            .filterIsInstance<JPasswordField>()
            .single()

    private fun providerPresetComboIn(component: AIReviewSettingsComponent): JComboBox<*> =
        componentsIn(component.getPanel())
            .filterIsInstance<JComboBox<*>>()
            .single()

    private fun textFieldValuesIn(component: AIReviewSettingsComponent): List<String> =
        componentsIn(component.getPanel())
            .filterIsInstance<JTextField>()
            .filterNot { it is JPasswordField }
            .map { it.text }

    private fun testConnectionButtonIn(component: AIReviewSettingsComponent): JButton =
        componentsIn(component.getPanel())
            .filterIsInstance<JButton>()
            .single { it.text == "Test Connection" }

    private fun connectionStatusLabelIn(component: AIReviewSettingsComponent): JLabel =
        componentsIn(component.getPanel())
            .filterIsInstance<JLabel>()
            .single { it.text.contains("连接失败") || it.toolTipText?.contains("连接失败") == true }

    private fun clickButton(component: Component, text: String) {
        val button = componentsIn(component)
            .filterIsInstance<JButton>()
            .single { it.text == text }
        val event = ActionEvent(button, ActionEvent.ACTION_PERFORMED, button.actionCommand)
        button.actionListeners.forEach { it.actionPerformed(event) }
    }

    private fun visibleNonPasswordTextIn(component: Component): String =
        componentsIn(component)
            .flatMap { current ->
                when (current) {
                    is JLabel -> listOfNotNull(current.text)
                    is JButton -> listOf(current.text)
                    else -> emptyList()
                }
            }
            .joinToString(separator = "\n")

    private fun waitForText(component: AIReviewSettingsComponent, text: String) {
        val deadline = System.currentTimeMillis() + 1_000
        while (System.currentTimeMillis() < deadline) {
            if (visibleNonPasswordTextIn(component.getPanel()).contains(text)) {
                return
            }
            Thread.sleep(10)
        }
        assertTrue(false, visibleNonPasswordTextIn(component.getPanel()))
    }

    private fun componentsIn(component: Component): List<Component> =
        listOf(component) + if (component is Container) {
            component.components.flatMap { componentsIn(it) }
        } else {
            emptyList()
        }

    private class TestProvider(private val response: String) : dev.diffguard.ai.AIProvider {
        override fun review(prompt: String): String = response
        override fun review(prompt: String, cancellationToken: dev.diffguard.ai.ReviewCancellationToken): String = response
    }

    private class SlowProvider : dev.diffguard.ai.AIProvider {
        override fun review(prompt: String): String {
            Thread.sleep(500)
            return "[]"
        }

        override fun review(prompt: String, cancellationToken: dev.diffguard.ai.ReviewCancellationToken): String = review(prompt)
    }

    private class CancellableSlowProvider(
        private val cancelled: CountDownLatch
    ) : dev.diffguard.ai.AIProvider {
        override fun review(prompt: String): String = "[]"

        override fun review(prompt: String, cancellationToken: dev.diffguard.ai.ReviewCancellationToken): String {
            cancellationToken.invokeOnCancel { cancelled.countDown() }
            Thread.sleep(5_000)
            return "[]"
        }
    }

    private class FailingProvider(private val message: String) : dev.diffguard.ai.AIProvider {
        override fun review(prompt: String): String = error(message)
        override fun review(prompt: String, cancellationToken: dev.diffguard.ai.ReviewCancellationToken): String = error(message)
    }
}
