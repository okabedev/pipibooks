package com.pipibooks.catalog.internal

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.pipibooks.catalog.book.BookRepository
import com.pipibooks.catalog.collect.CollectRunRepository
import com.pipibooks.catalog.nlk.NormalizedBook
import com.pipibooks.catalog.support.NlkTestSupport
import com.pipibooks.catalog.support.NlkWireMock
import com.pipibooks.catalog.support.jsonBody
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
class InternalBookControllerTest(
    @Autowired private val client: WebTestClient,
    @Autowired private val books: BookRepository,
    @Autowired private val runs: CollectRunRepository,
) : NlkTestSupport() {
    private val wireMock = NlkWireMock.server
    private val path = urlPathEqualTo("/seoji/SearchApi.do")

    private fun import(isbn: String) =
        client
            .post()
            .uri("/internal/books/import")
            .bodyValue(mapOf("isbn" to isbn))
            .exchange()

    @Test
    fun `이미 있는 도서는 외부 호출 없이 import된다`() {
        val id = runBlocking { books.upsert(NormalizedBook(isbn13 = "9788912345679", title = "있는 책")).bookId }

        import("978-89-123-4567-9")
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.bookId")
            .isEqualTo(id.toString())

        assertEquals(0, wireMock.findAll(getRequestedFor(path)).size)
    }

    @Test
    fun `없는 도서는 국립중앙도서관에서 가져와 저장한다`() {
        wireMock.stubFor(
            get(path).willReturn(
                jsonBody(fixture("isbn-9791198128058.json")),
            ),
        )

        import("9791198128058")
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.bookId")
            .isNotEmpty

        assertEquals("바울의 삶 바울의 신학", runBlocking { books.findByIsbn("9791198128058") }!!.title)
    }

    @Test
    fun `국립중앙도서관에 없는 도서는 404 BOOK_NOT_FOUND`() {
        wireMock.stubFor(
            get(path).willReturn(jsonBody(fixture("empty.json"))),
        )

        import("9788999990052")
            .expectStatus()
            .isNotFound
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("BOOK_NOT_FOUND")
    }

    @Test
    fun `국립중앙도서관 장애면 503 CATALOG_UNAVAILABLE`() {
        wireMock.stubFor(get(path).willReturn(aResponse().withStatus(500)))

        import("9788999990021")
            .expectStatus()
            .isEqualTo(503)
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("CATALOG_UNAVAILABLE")
    }

    @Test
    fun `제목 검색은 저장하지 않고 목록을 돌려준다`() {
        wireMock.stubFor(
            get(path).willReturn(jsonBody(fixture("search-sea.json"))),
        )

        client
            .get()
            .uri("/internal/books/search?q=바다&page=1")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$.totalCount")
            .isEqualTo(10583)
            .jsonPath("$.items[0].isbn13")
            .isEqualTo("9791196480974")

        assertEquals(null, runBlocking { books.findByIsbn("9791196480974") })
    }

    @Test
    fun `수집 실행은 202와 runId를 돌려주고 이력에 남는다`() {
        wireMock.stubFor(
            get(path).willReturn(jsonBody(fixture("empty.json"))),
        )

        client
            .post()
            .uri("/internal/collect-runs")
            .bodyValue(mapOf("type" to "NEW_BOOKS"))
            .exchange()
            .expectStatus()
            .isAccepted
            .expectBody()
            .jsonPath("$.runId")
            .isNotEmpty

        client
            .get()
            .uri("/internal/collect-runs?size=5")
            .exchange()
            .expectStatus()
            .isOk
            .expectBody()
            .jsonPath("$[0].type")
            .isEqualTo("NEW_BOOKS")

        awaitRunsFinished()
    }

    @Test
    fun `하이픈이 있는 ISBN 시드도 저장된다`() {
        wireMock.stubFor(
            get(path).willReturn(jsonBody(fixture("isbn-9791198128058.json"))),
        )

        client
            .post()
            .uri("/internal/collect-runs")
            .bodyValue(mapOf("type" to "SEED_ISBN", "isbns" to listOf("979-11-98128-05-8")))
            .exchange()
            .expectStatus()
            .isAccepted
        awaitRunsFinished()

        assertEquals("바울의 삶 바울의 신학", runBlocking { books.findByIsbn("9791198128058") }!!.title)
    }

    @Test
    fun `검증숫자가 틀린 ISBN 시드는 400 INVALID_ISBN`() {
        client
            .post()
            .uri("/internal/collect-runs")
            .bodyValue(mapOf("type" to "SEED_ISBN", "isbns" to listOf("9788999990021", "9788999990022")))
            .exchange()
            .expectStatus()
            .isBadRequest
            .expectBody()
            .jsonPath("$.code")
            .isEqualTo("INVALID_ISBN")
    }

    /** 백그라운드 수집이 다른 테스트에 번지지 않도록 끝날 때까지 기다린다. */
    private fun awaitRunsFinished() {
        val deadline = System.currentTimeMillis() + AWAIT_MS
        while (runBlocking { runs.recent(RUN_LIMIT) }.any { it.status == "RUNNING" }) {
            check(System.currentTimeMillis() < deadline) { "수집이 끝나지 않았다" }
            Thread.sleep(POLL_MS)
        }
    }

    private companion object {
        const val AWAIT_MS = 15_000L
        const val POLL_MS = 20L
        const val RUN_LIMIT = 20
    }
}
