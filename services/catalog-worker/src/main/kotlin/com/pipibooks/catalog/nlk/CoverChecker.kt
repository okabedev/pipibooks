package com.pipibooks.catalog.nlk

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import java.time.Duration

/**
 * 표지 URL이 실제 이미지인지 한 번 확인한다. 표지는 최대 3.5MB라 본문은 받지 않고
 * `Range: bytes=0-0` 요청의 상태와 Content-Type 헤더만 본다(2xx이고 image 타입일 때 true).
 * 도서 API와 다른 호스트라 [NlkRateLimiter]를 거치지 않는다.
 */
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
                .header(HttpHeaders.RANGE, "bytes=0-0")
                .exchangeToMono { response ->
                    val ok =
                        response.statusCode().is2xxSuccessful &&
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
        const val TIMEOUT_SECONDS = 5L
    }
}
