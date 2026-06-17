package dev.diffguard.ai

interface AIProvider {
    fun review(prompt: String): String

    fun review(prompt: String, cancellationToken: ReviewCancellationToken): String {
        cancellationToken.throwIfCancellationRequested()
        return review(prompt)
    }
}
