package com.pipibooks.catalog.nlk

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.web.reactive.function.client.WebClient

class CoverCheckerTest {
    companion object {
        private val server = WireMockServer(wireMockConfig().dynamicPort())

        @JvmStatic
        @BeforeAll
        fun start() = server.start()

        @JvmStatic
        @AfterAll
        fun stop() = server.stop()
    }

    private fun typed(type: String) = aResponse().withHeader("Content-Type", type).withBody("x")

    private val checker = CoverChecker(WebClient.builder())

    @Test
    fun `200이고 image 타입이면 true다`() =
        runBlocking {
            server.stubFor(get(urlEqualTo("/ok.jpg")).willReturn(typed("image/jpeg")))
            assertTrue(checker.verify("http://localhost:${server.port()}/ok.jpg"))
        }

    @Test
    fun `Range 요청으로 확인하고 206 image도 true다`() =
        runBlocking {
            server.stubFor(
                get(urlEqualTo("/big.jpg"))
                    .withHeader("Range", equalTo("bytes=0-0"))
                    .willReturn(typed("image/jpeg").withStatus(206)),
            )
            assertTrue(checker.verify("http://localhost:${server.port()}/big.jpg"))
        }

    @Test
    fun `Range를 무시하고 200으로 답하는 서버도 image면 true다`() =
        runBlocking {
            server.stubFor(get(urlEqualTo("/ignore-range.jpg")).willReturn(typed("image/png")))
            assertTrue(checker.verify("http://localhost:${server.port()}/ignore-range.jpg"))
            server.verify(
                getRequestedFor(urlEqualTo("/ignore-range.jpg")).withHeader("Range", equalTo("bytes=0-0")),
            )
        }

    @Test
    fun `404나 image가 아닌 타입이면 false다`() =
        runBlocking {
            server.stubFor(get(urlEqualTo("/missing.jpg")).willReturn(aResponse().withStatus(404)))
            server.stubFor(get(urlEqualTo("/page.html")).willReturn(typed("text/html")))
            assertFalse(checker.verify("http://localhost:${server.port()}/missing.jpg"))
            assertFalse(checker.verify("http://localhost:${server.port()}/page.html"))
            assertFalse(checker.verify("http://localhost:1/unreachable.jpg"))
        }
}
