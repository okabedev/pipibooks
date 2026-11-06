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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
                    .withQueryParam("page_no", equalTo("1"))
                    .willReturn(jsonBody(fixture("new-books-20261105.json"))),
            )

            val runId = service.runNow(CollectRequest(CollectType.NEW_BOOKS))

            val requests = wireMock.findAll(getRequestedFor(path))
            assertEquals(15, requests.size) // 14일 + 2일차 이상이 필요한 11-05의 2쪽(빈 쪽)
            val firstPages = requests.filter { it.queryParameter("page_no").firstValue() == "1" }
            assertEquals(14, firstPages.size)
            val days = firstPages.map { it.queryParameter("start_publish_date").firstValue() }.toSet()
            assertEquals(14, days.size)
            assertTrue("20261023" in days && "20261105" in days)
            val run = runs.recent(10).single { it.id == runId }
            assertEquals("SUCCEEDED", run.status)
            assertEquals(5, run.fetchedCount)
            assertEquals(5, run.savedCount)
            assertNull(run.error)
            val book = books.findByIsbn("9791176449236")!!
            assertEquals("해커스 주택관리사 2차 기본서 공동주택관리실무 1", book.title)
            assertEquals("김성환, 해커스 주택관리사시험 연구소", book.author)
            assertEquals("5", book.kdc)
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
                    .withQueryParam("page_no", equalTo("1"))
                    .willReturn(jsonBody(fixture("new-books-20261105.json"))),
            )

            val runId = service.runNow(CollectRequest(CollectType.NEW_BOOKS))

            val run = runs.recent(10).single { it.id == runId }
            assertEquals("FAILED", run.status)
            assertTrue(run.error!!.contains(LocalDate.of(2026, 11, 4).toString()), run.error)
            assertEquals(5, run.savedCount)
        }

    @Test
    fun `받은 건수가 TOTAL_COUNT보다 적으면 경고 로그를 남긴다`(output: CapturedOutput) =
        runBlocking {
            wireMock.stubFor(get(path).willReturn(jsonBody(fixture("empty.json"))))
            wireMock.stubFor(
                get(path)
                    .withQueryParam("start_publish_date", equalTo("20261105"))
                    .withQueryParam("page_no", equalTo("1"))
                    .willReturn(jsonBody(fixture("new-books-20261105.json"))),
            )

            service.runNow(CollectRequest(CollectType.NEW_BOOKS))

            assertTrue(output.all.contains("받은 건수(5)가 TOTAL_COUNT(1259)보다 적다"), "경고 로그 없음")
        }

    @Test
    fun `쪽을 끝까지 넘기고 같은 ISBN이 겹쳐도 한 건으로 저장한다`(output: CapturedOutput) =
        runBlocking {
            val record = """{"TITLE":"겹친 책","EA_ISBN":"9788999990021"}"""
            wireMock.stubFor(get(path).willReturn(jsonBody(fixture("empty.json"))))
            wireMock.stubFor(
                get(path)
                    .withQueryParam("start_publish_date", equalTo("20261105"))
                    .willReturn(jsonBody("""{"TOTAL_COUNT":"1000","PAGE_NO":"1","docs":[$record,$record]}""")),
            )

            val runId = service.runNow(CollectRequest(CollectType.NEW_BOOKS))

            // 500건씩 2쪽이면 끝난다. 같은 응답이 두 번 와도 upsert라 도서는 하나다.
            val day =
                wireMock.findAll(getRequestedFor(path)).filter {
                    it.queryParameter("start_publish_date").firstValue() == "20261105"
                }
            assertEquals(2, day.size)
            assertEquals("겹친 책", books.findByIsbn("9788999990021")!!.title)
            assertEquals("SUCCEEDED", runs.recent(10).single { it.id == runId }.status)
            assertTrue(output.all.contains("받은 건수(4)가 TOTAL_COUNT(1000)보다 적다"))
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

    @Test
    fun `취소돼도 실행 이력은 RUNNING으로 남지 않는다`() =
        runBlocking {
            wireMock.stubFor(
                get(path).willReturn(jsonBody(fixture("empty.json")).withFixedDelay(CANCEL_DELAY_MS)),
            )

            val job = launch(Dispatchers.Default) { service.runNow(CollectRequest(CollectType.NEW_BOOKS)) }
            while (runs.recent(1).isEmpty()) delay(POLL_MS)
            job.cancelAndJoin()

            assertEquals("FAILED", runs.recent(1).single().status)
        }

    private companion object {
        const val CANCEL_DELAY_MS = 3000
        const val POLL_MS = 20L
    }
}
