package com.commitdiffaireview.settings

import com.commitdiffaireview.model.AISettingsState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPasswordField

class AIReviewSettingsComponentTest {
    @Test
    fun `reset fills api key password field and masks it visually`() {
        val component = AIReviewSettingsComponent()

        component.resetFrom(AISettingsState(apiKey = "sk-test-key"))

        val apiKeyField = apiKeyFieldIn(component)
        assertEquals("sk-test-key", String(apiKeyField.password))
        assertEquals('\u2022', apiKeyField.echoChar)
        assertFalse(visibleNonPasswordTextIn(component.getPanel()).contains("sk-test-key"))
        assertFalse(componentsIn(component.getPanel()).any { it is JButton && it.text.contains("清除") })
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
    fun `clearing api key field marks settings as modified`() {
        val component = AIReviewSettingsComponent()
        component.resetFrom(AISettingsState(apiKey = "sk-test-key"))

        apiKeyFieldIn(component).text = ""

        assertTrue(component.isModified())
    }

    private fun apiKeyFieldIn(component: AIReviewSettingsComponent): JPasswordField =
        componentsIn(component.getPanel())
            .filterIsInstance<JPasswordField>()
            .single()

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

    private fun componentsIn(component: Component): List<Component> =
        listOf(component) + if (component is Container) {
            component.components.flatMap { componentsIn(it) }
        } else {
            emptyList()
        }
}
