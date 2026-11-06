package com.pipibooks.catalog.nlk

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Collections

class NlkRateLimiterTest {
    @Test
    fun `사용자 요청이 대기 중인 배치보다 먼저 허가된다`() =
        runBlocking {
            val limiter = NlkRateLimiter(20.0)
            val order = Collections.synchronizedList(mutableListOf<String>())
            limiter.withPermit(Priority.BATCH) { order += "first" }
            val requests = listOf("batch1" to Priority.BATCH, "batch2" to Priority.BATCH, "user" to Priority.USER)
            val jobs =
                requests.map { (name, priority) ->
                    async {
                        limiter.withPermit(priority) { order += name }
                    }.also { kotlinx.coroutines.yield() }
                }
            jobs.awaitAll()
            assertEquals(listOf("first", "user", "batch1", "batch2"), order)
        }
}
