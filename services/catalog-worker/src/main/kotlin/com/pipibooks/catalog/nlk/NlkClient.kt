package com.pipibooks.catalog.nlk

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactor.awaitSingle
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientRequestException
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeoutException

/**
 * 국립중앙도서관 ISBN 서지정보 API 클라이언트. 모든 요청은 [NlkRateLimiter]를 거친다.
 * 시스템 오류(`000`)·5xx·타임아웃은 지수 백오프로 처음 1회와 재시도 3회(200/400/800ms)까지 모두 4번 시도하고, 인증 오류는 즉시 실패한다.
 */
@Component
class NlkClient(
    private val props: NlkProperties,
    private val limiter: NlkRateLimiter,
    builder: WebClient.Builder,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val web = builder.baseUrl(props.baseUrl).build()

    suspend fun byPublishDate(
        date: LocalDate,
        page: Int,
        priority: Priority = Priority.BATCH,
    ): NlkPage {
        val d = date.format(DATE)
        return search(priority, page, "start_publish_date" to d, "end_publish_date" to d)
    }

    suspend fun byTitle(
        q: String,
        page: Int,
        priority: Priority = Priority.USER,
    ): NlkPage = search(priority, page, "title" to q)

    suspend fun byIsbn(
        isbn13: String,
        priority: Priority = Priority.USER,
    ): NlkRecord? {
        val page = search(priority, 1, "isbn" to isbn13)
        return page.records.firstOrNull { it.eaIsbn?.filter(Char::isDigit) == isbn13 }
    }

    private suspend fun search(
        priority: Priority,
        page: Int,
        vararg filters: Pair<String, String>,
    ): NlkPage {
        requireCertKey()
        val params =
            mapOf(
                "cert_key" to props.certKey,
                "result_style" to "json",
                "page_no" to page.toString(),
                "page_size" to props.pageSize.toString(),
            ) +
                filters
        var lastError: Exception? = null
        repeat(ATTEMPTS) { attempt ->
            try {
                return limiter.withPermit(priority) { NlkParser.parse(fetch(params)) }
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                if (!isRetryable(e)) throw translate(e)
                lastError = e
                log.warn("국립중앙도서관 호출 실패({}/{}): {}", attempt + 1, ATTEMPTS, e.message)
                if (attempt < ATTEMPTS - 1) delay(props.retryBaseDelayMs shl attempt)
            }
        }
        throw NlkUnavailableException("국립중앙도서관 호출이 ${ATTEMPTS}번 실패했다", lastError)
    }

    private fun requireCertKey() {
        if (props.certKey.isBlank()) {
            log.error("NLK_CERT_KEY가 설정되지 않아 국립중앙도서관을 호출할 수 없다")
            throw NlkAuthException("NLK_CERT_KEY가 설정되지 않았다")
        }
    }

    private suspend fun fetch(params: Map<String, String>): String =
        web
            .get()
            .uri { b ->
                b.path(PATH)
                params.keys.forEach { b.queryParam(it, "{$it}") }
                b.build(params)
            }.retrieve()
            .bodyToMono(String::class.java)
            .timeout(Duration.ofSeconds(props.timeoutSeconds))
            .awaitSingle()

    /** 취소와 이미 분류된 [NlkException]은 그대로, 나머지는 [NlkUnavailableException]으로 감싼다. */
    private fun translate(e: Exception): Exception =
        when (e) {
            is CancellationException -> e
            is NlkAuthException -> e.also { log.error("국립중앙도서관 인증 실패: {}", it.message) }
            is NlkException -> e
            else -> NlkUnavailableException(e.message ?: "국립중앙도서관 오류", e)
        }

    private fun isRetryable(e: Exception): Boolean =
        when (e) {
            is NlkRetryableException, is WebClientRequestException, is TimeoutException -> true
            is WebClientResponseException -> e.statusCode.is5xxServerError
            else -> false
        }

    private companion object {
        const val PATH = "/seoji/SearchApi.do"
        const val ATTEMPTS = 4
        val DATE: DateTimeFormatter = DateTimeFormatter.BASIC_ISO_DATE
    }
}
