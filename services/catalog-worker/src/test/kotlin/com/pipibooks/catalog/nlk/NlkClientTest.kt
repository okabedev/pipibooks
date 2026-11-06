package com.pipibooks.catalog.nlk

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.pipibooks.catalog.support.jsonBody
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.web.reactive.function.client.WebClient
import java.time.LocalDate

class NlkClientTest {
    companion object {
        private val server = WireMockServer(wireMockConfig().dynamicPort())

        @JvmStatic
        @BeforeAll
        fun start() = server.start()

        @JvmStatic
        @AfterAll
        fun stop() = server.stop()
    }

    private val path = urlPathEqualTo("/seoji/SearchApi.do")

    private fun client(certKey: String = "test-key") =
        NlkClient(
            NlkProperties(baseUrl = "http://localhost:${server.port()}", certKey = certKey, retryBaseDelayMs = 1),
            NlkRateLimiter(1000.0),
            WebClient.builder(),
        )

    private fun fixture(name: String) = checkNotNull(javaClass.getResource("/nlk/$name")).readText()

    @BeforeEach
    fun reset() = server.resetAll()

    @Test
    fun `발행예정일 하루를 페이지 단위로 조회한다`() =
        runBlocking {
            server.stubFor(get(path).willReturn(jsonBody(fixture("new-books-20261105.json"))))
            val page = client().byPublishDate(LocalDate.of(2026, 11, 5), 1)
            assertEquals(2, page.totalCount)
            assertEquals(
                "9788912345679".length,
                page.records
                    .first()
                    .eaIsbn!!
                    .filter { it.isDigit() }
                    .length,
            )
            server.verify(
                getRequestedFor(path)
                    .withQueryParam("start_publish_date", equalTo("20261105"))
                    .withQueryParam("end_publish_date", equalTo("20261105"))
                    .withQueryParam("page_no", equalTo("1"))
                    .withQueryParam("page_size", equalTo("50"))
                    .withQueryParam("result_style", equalTo("json"))
                    .withQueryParam("cert_key", equalTo("test-key")),
            )
        }

    @Test
    fun `시스템 오류는 3회 재시도 후 실패한다`() {
        server.stubFor(get(path).willReturn(jsonBody(fixture("error-000.json"))))
        assertThrows<NlkUnavailableException> { runBlocking { client().byTitle("바다", 1) } }
        assertEquals(4, server.findAll(getRequestedFor(path)).size)
    }

    @Test
    fun `인증 오류는 재시도하지 않는다`() {
        server.stubFor(get(path).willReturn(jsonBody(fixture("error-011.json"))))
        assertThrows<NlkAuthException> { runBlocking { client().byTitle("바다", 1) } }
        assertEquals(1, server.findAll(getRequestedFor(path)).size)
    }

    @Test
    fun `한글 검색어는 URL 인코딩된다`() =
        runBlocking {
            server.stubFor(get(path).willReturn(jsonBody(fixture("search-sea.json"))))
            val page = client().byTitle("바다 & 섬", 1)
            assertEquals(1, page.records.size)
            val request = server.findAll(getRequestedFor(path)).single()
            assertEquals("바다 & 섬", request.queryParameter("title").firstValue())
            assertTrue(request.url.contains("%26"), request.url)
        }

    @Test
    fun `인증키가 없으면 요청 없이 인증 오류가 난다`() {
        assertThrows<NlkAuthException> { runBlocking { client(certKey = "").byTitle("바다", 1) } }
        assertEquals(0, server.findAll(getRequestedFor(path)).size)
    }
}
