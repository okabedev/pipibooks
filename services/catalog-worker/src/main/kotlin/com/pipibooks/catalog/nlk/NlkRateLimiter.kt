package com.pipibooks.catalog.nlk

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.ArrayDeque

enum class Priority { USER, BATCH }

/**
 * 국립중앙도서관 호출 한도(초당 [permitsPerSecond])를 공유하는 제한기.
 * 허가는 [permitsPerSecond] 간격으로 하나씩 나가며, 대기자 중 `USER`가 `BATCH`보다 먼저 받는다.
 */
class NlkRateLimiter(
    permitsPerSecond: Double,
) {
    private val intervalNanos = (NANOS_PER_SECOND / permitsPerSecond).toLong()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Any()
    private val user = ArrayDeque<CompletableDeferred<Unit>>()
    private val batch = ArrayDeque<CompletableDeferred<Unit>>()
    private var dispatching = false
    private var nextAllowedNanos = System.nanoTime()

    suspend fun <T> withPermit(
        priority: Priority,
        block: suspend () -> T,
    ): T {
        val ticket = CompletableDeferred<Unit>()
        val startDispatcher =
            synchronized(lock) {
                (if (priority == Priority.USER) user else batch).add(ticket)
                (!dispatching).also { dispatching = true }
            }
        if (startDispatcher) scope.launch { dispatch() }
        ticket.await()
        return block()
    }

    private suspend fun dispatch() {
        while (true) {
            val wait = synchronized(lock) { nextAllowedNanos - System.nanoTime() }
            if (wait > 0) delay((wait + NANOS_PER_MILLI - 1) / NANOS_PER_MILLI)
            val next =
                synchronized(lock) {
                    // 허가 직전에 고른다. 기다리는 동안 들어온 USER 요청이 BATCH를 앞지른다.
                    val ticket = user.poll() ?: batch.poll()
                    if (ticket == null) dispatching = false else nextAllowedNanos = System.nanoTime() + intervalNanos
                    ticket
                } ?: return
            next.complete(Unit)
        }
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val NANOS_PER_MILLI = 1_000_000L
    }
}
