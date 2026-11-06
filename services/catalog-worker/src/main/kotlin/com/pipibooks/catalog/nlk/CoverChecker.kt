package com.pipibooks.catalog.nlk

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

/** 표지 URL이 실제 이미지인지 한 번 확인한다. 도서 API와 다른 호스트라 [NlkRateLimiter]를 거치지 않는다. */
@Component
class CoverChecker(
    builder: WebClient.Builder,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val web = builder.build()

    suspend fun verify(url: String): Boolean =
        try {
            web
                .get()
                .uri(java.net.URI.create(url))
                .exchangeToMono { response ->
                    val ok =
                        response.statusCode().value() == OK &&
                            response
                                .headers()
                                .contentType()
                                .map { it.type == "image" }
                                .orElse(false)
                    response.releaseBody().thenReturn(ok)
                }.timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .awaitSingle()
        } catch (e: CancellationException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log.warn("표지 확인 실패: {} ({})", url, e.message)
            false
        }

    private companion object {
        const val OK = 200
        const val TIMEOUT_SECONDS = 5L
    }
}
