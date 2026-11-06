package com.pipibooks.catalog.health

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.net.URI
import java.time.Duration

/** Healthchecks.io 하트비트. URL이 비어 있으면 아무것도 하지 않고, 실패해도 수집에 영향을 주지 않는다. */
@Component
class Heartbeat(
    builder: WebClient.Builder,
    @Value("\${healthcheck.collect-url:}") private val url: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val web = builder.build()

    suspend fun report(success: Boolean) {
        if (url.isBlank()) return
        val target = if (success) url else url.trimEnd('/') + "/fail"
        try {
            web
                .get()
                .uri(URI.create(target))
                .retrieve()
                .toBodilessEntity()
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .awaitSingleOrNull()
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log.warn("하트비트 전송 실패: {}", e.message)
        }
    }

    private companion object {
        const val TIMEOUT_SECONDS = 10L
    }
}
