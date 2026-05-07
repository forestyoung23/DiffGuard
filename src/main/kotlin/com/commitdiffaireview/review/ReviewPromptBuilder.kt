package com.commitdiffaireview.review

class ReviewPromptBuilder {
    fun build(stagedDiff: String): String =
        """
        You are a senior code reviewer. Review the following staged git unified diff.

        Focus on:
        - Bug risk
        - null pointer issues
        - concurrency issues
        - transaction issues
        - SQL risk
        - security issues
        - readability

        Return only a JSON array in this exact structure:
        [
          {
            "level": "HIGH",
            "file": "UserService.java",
            "line": 42,
            "message": "Potential null pointer"
          }
        ]

        Use level HIGH, MEDIUM, or LOW. Use null for line when no exact line is available.

        Diff:
        $stagedDiff
        """.trimIndent()
}
