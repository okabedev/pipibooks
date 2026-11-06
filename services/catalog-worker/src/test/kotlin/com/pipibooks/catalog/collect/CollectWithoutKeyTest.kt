package com.pipibooks.catalog.collect

import com.pipibooks.catalog.support.DbTestSupport
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

@SpringBootTest
class CollectWithoutKeyTest(
    @Autowired private val service: CollectService,
    @Autowired private val runs: CollectRunRepository,
) : DbTestSupport() {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun emptyKey(registry: DynamicPropertyRegistry) {
            registry.add("nlk.cert-key") { "" }
        }
    }

    @Test
    fun `인증키가 없으면 수집은 FAILED와 오류를 기록하고 예외를 던지지 않는다`() =
        runBlocking {
            val runId = service.runNow(CollectRequest(CollectType.NEW_BOOKS))

            val run = runs.recent(50).single { it.id == runId }
            assertEquals("FAILED", run.status)
            assertTrue(run.error!!.contains("NLK_CERT_KEY"), run.error)
        }
}
