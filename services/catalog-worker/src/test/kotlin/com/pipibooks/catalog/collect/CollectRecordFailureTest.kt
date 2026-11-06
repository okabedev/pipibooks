package com.pipibooks.catalog.collect

import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.pipibooks.catalog.book.Book
import com.pipibooks.catalog.book.BookRepository
import com.pipibooks.catalog.book.BookUpsertListener
import com.pipibooks.catalog.book.UpsertResult
import com.pipibooks.catalog.support.NlkTestSupport
import com.pipibooks.catalog.support.NlkWireMock
import com.pipibooks.catalog.support.jsonBody
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary

@SpringBootTest
@Import(CollectRecordFailureTest.FailingListenerConfig::class)
class CollectRecordFailureTest(
    @Autowired private val service: CollectService,
    @Autowired private val runs: CollectRunRepository,
    @Autowired private val books: BookRepository,
) : NlkTestSupport() {
    @TestConfiguration
    class FailingListenerConfig {
        @Bean
        @Primary
        fun failingListener() =
            object : BookUpsertListener {
                override suspend fun onUpsert(
                    book: Book,
                    result: UpsertResult,
                ) = check(book.isbn13 != "9788912345679") { "처리 실패 시뮬레이션" }
            }
    }

    @Test
    fun `한 레코드가 실패해도 같은 날짜의 나머지 레코드가 저장된다`() =
        runBlocking {
            val path = urlPathEqualTo("/seoji/SearchApi.do")
            NlkWireMock.server.stubFor(get(path).willReturn(jsonBody(fixture("empty.json"))))
            NlkWireMock.server.stubFor(
                get(path)
                    .withQueryParam("start_publish_date", equalTo("20261105"))
                    .withQueryParam("page_no", equalTo("1"))
                    .willReturn(jsonBody(fixture("synthetic-new-books-hyphen-isbn.json"))),
            )

            val runId = service.runNow(CollectRequest(CollectType.NEW_BOOKS))

            assertEquals("조용한 숲", books.findByIsbn("9788999990014")!!.title)
            val run = runs.recent(10).single { it.id == runId }
            assertEquals("FAILED", run.status)
            assertTrue(run.error!!.contains("978-89-123-4567-9"), run.error)
        }
}
