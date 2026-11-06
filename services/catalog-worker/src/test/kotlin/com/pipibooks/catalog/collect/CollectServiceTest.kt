package com.pipibooks.catalog.collect

import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.pipibooks.catalog.book.BookRepository
import com.pipibooks.catalog.support.NlkTestSupport
import com.pipibooks.catalog.support.NlkWireMock
import com.pipibooks.catalog.support.jsonBody
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.system.CapturedOutput
import org.springframework.boot.test.system.OutputCaptureExtension
import java.time.LocalDate

@SpringBootTest
@ExtendWith(OutputCaptureExtension::class)
class CollectServiceTest(
    @Autowired private val service: CollectService,
    @Autowired private val runs: CollectRunRepository,
    @Autowired private val books: BookRepository,
) : NlkTestSupport() {
    private val wireMock = NlkWireMock.server
    private val path = urlPathEqualTo("/seoji/SearchApi.do")

    @Test
    fun `최근 14일을 하루씩 조회하고 실행 이력을 남긴다`() =
        runBlocking {
            wireMock.stubFor(get(path).willReturn(jsonBody(fixture("empty.json"))))
            wireMock.stubFor(
                get(path)
                    .withQueryParam("start_publish_date", equalTo("20261105"))
                    .willReturn(jsonBody(fixture("new-books-20261105.json"))),
            )

            val runId = service.runNow(CollectRequest(CollectType.NEW_BOOKS))

            val requests = wireMock.findAll(getRequestedFor(path))
            assertEquals(14, requests.size)
            val days = requests.map { it.queryParameter("start_publish_date").firstValue() }.toSet()
            assertEquals(14, days.size)
            assertTrue("20261023" in days && "20261105" in days)
            val run = runs.recent(10).single { it.id == runId }
            assertEquals("SUCCEEDED", run.status)
            assertEquals(2, run.fetchedCount)
            assertEquals(2, run.savedCount)
            assertNull(run.error)
            assertEquals("바다와 섬 이야기", books.findByIsbn("9788912345679")!!.title)
            assertEquals("김하늘, 이바다", books.findByIsbn("9788912345679")!!.author)
        }

    @Test
    fun `한 날짜가 실패해도 나머지는 수집하고 FAILED와 오류를 기록한다`() =
        runBlocking {
            wireMock.stubFor(get(path).willReturn(jsonBody(fixture("empty.json"))))
            wireMock.stubFor(
                get(path)
                    .withQueryParam("start_publish_date", equalTo("20261104"))
                    .willReturn(aResponse().withStatus(500)),
            )
            wireMock.stubFor(
                get(path)
                    .withQueryParam("start_publish_date", equalTo("20261105"))
                    .willReturn(jsonBody(fixture("new-books-20261105.json"))),
            )

            val runId = service.runNow(CollectRequest(CollectType.NEW_BOOKS))

            val run = runs.recent(10).single { it.id == runId }
            assertEquals("FAILED", run.status)
            assertTrue(run.error!!.contains(LocalDate.of(2026, 11, 4).toString()), run.error)
            assertEquals(2, run.savedCount)
        }

    @Test
    fun `하루 결과가 500건을 넘으면 경고 로그를 남긴다`(output: CapturedOutput) =
        runBlocking {
            wireMock.stubFor(get(path).willReturn(jsonBody(fixture("empty.json"))))
            wireMock.stubFor(
                get(path)
                    .withQueryParam("start_publish_date", equalTo("20261105"))
                    .willReturn(jsonBody("""{"TOTAL_COUNT":"731","PAGE_NO":"1","docs":[]}""")),
            )

            service.runNow(CollectRequest(CollectType.NEW_BOOKS))

            assertTrue(output.all.contains("500건"), "경고 로그 없음")
        }

    @Test
    fun `표지 확인에 실패하면 표지 없이 저장한다`() =
        runBlocking {
            val body =
                """{"TOTAL_COUNT":"1","docs":[{"TITLE":"표지 깨진 책","EA_ISBN":"9788999990045","TITLE_URL":"http://localhost:1/broken.jpg"}]}"""
            wireMock.stubFor(get(path).willReturn(jsonBody(body)))

            service.runNow(CollectRequest(CollectType.SEED_KEYWORD, keyword = "표지"))

            assertNull(books.findByIsbn("9788999990045")!!.coverUrl)
        }
}
