package com.commitdiffaireview.ai

interface AIProvider {
    fun review(prompt: String): String
}
