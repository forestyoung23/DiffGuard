package dev.diffguard.ai

import java.util.concurrent.CancellationException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class ReviewCancellationToken(
    private val externalCancellationRequested: () -> Boolean = { false }
) {
    private val cancelled = AtomicBoolean(false)
    private val callbacks = CopyOnWriteArrayList<() -> Unit>()

    fun cancel() {
        if (cancelled.compareAndSet(false, true)) {
            callbacks.forEach { it.invoke() }
        }
    }

    fun isCancellationRequested(): Boolean =
        cancelled.get() || externalCancellationRequested()

    fun throwIfCancellationRequested() {
        if (isCancellationRequested()) {
            throw CancellationException("DiffGuard review was cancelled.")
        }
    }

    fun invokeOnCancel(callback: () -> Unit) {
        callbacks.add(callback)
        if (isCancellationRequested()) {
            callback()
        }
    }
}
