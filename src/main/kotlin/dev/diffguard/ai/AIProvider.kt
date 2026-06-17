package dev.diffguard.ai

interface AIProvider {
    fun review(prompt: String): String
}
