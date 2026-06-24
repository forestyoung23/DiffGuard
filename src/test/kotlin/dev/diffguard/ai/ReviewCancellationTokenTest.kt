package dev.diffguard.ai

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CancellationException

class ReviewCancellationTokenTest {
    @Test
    fun `throws cancellation exception when token is cancelled`() {
        val token = ReviewCancellationToken()

        token.cancel()

        assertThrows(CancellationException::class.java) {
            token.throwIfCancellationRequested()
        }
    }

    @Test
    fun `runs registered callback when token is cancelled`() {
        val token = ReviewCancellationToken()
        var callbackRan = false

        token.invokeOnCancel { callbackRan = true }
        token.cancel()

        assertTrue(callbackRan)
    }
}
