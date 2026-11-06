package com.pipibooks.catalog.book

import com.pipibooks.catalog.nlk.CoverChecker
import com.pipibooks.catalog.nlk.NlkRecord
import com.pipibooks.catalog.support.NlkTestSupport
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.web.reactive.function.client.WebClient

@SpringBootTest
class BookIngestorTest(
    @Autowired private val repository: BookRepository,
) : NlkTestSupport() {
    private class StubCover(
        private val ok: Boolean,
    ) : CoverChecker(WebClient.builder()) {
        var calls = 0

        override suspend fun verify(url: String): Boolean {
            calls++
            return ok
        }
    }

    private class Recorder : BookUpsertListener {
        val seen = mutableListOf<Book>()
        val results = mutableListOf<UpsertResult>()

        override suspend fun onUpsert(
            book: Book,
            result: UpsertResult,
        ): Boolean {
            seen += book
            results += result
            return true
        }
    }

    private val record =
        NlkRecord(eaIsbn = "9788912345679", title = "제목", titleUrl = "http://cdn.example.com/c.jpg")
    private val cover = "https://cdn.example.com/c.jpg"

    @Test
    fun `표지 확인에 실패하면 cover_url은 null이고 리스너도 null을 받는다`() =
        runBlocking {
            val recorder = Recorder()
            BookIngestor(repository, StubCover(false), recorder).ingest(record)

            assertNull(repository.findByIsbn("9788912345679")!!.coverUrl)
            assertNull(recorder.seen.single().coverUrl)
        }

    @Test
    fun `표지 확인에 성공하면 검증된 값이 저장되고 리스너가 같은 값을 받는다`() =
        runBlocking {
            val recorder = Recorder()
            val checker = StubCover(true)
            val ingestor = BookIngestor(repository, checker, recorder)
            ingestor.ingest(record)

            assertEquals(cover, repository.findByIsbn("9788912345679")!!.coverUrl)
            assertEquals(cover, recorder.seen.single().coverUrl)

            ingestor.ingest(record)
            assertEquals(1, checker.calls)
        }

    @Test
    fun `리스너는 도서 ID와 알맞은 UpsertResult를 받고 초안 생성 여부가 결과에 담긴다`() =
        runBlocking {
            val recorder = Recorder()
            val ingestor = BookIngestor(repository, StubCover(true), recorder)

            val first = ingestor.ingest(record)!!
            val second = ingestor.ingest(record)!!
            val changed = ingestor.ingest(record.copy(title = "다른 제목"))!!

            val (r1, r2, r3) = recorder.results
            assertTrue(r1.created && r1.cardFieldsChanged && r1.coverUrlChanged)
            assertTrue(!r2.created && !r2.cardFieldsChanged && !r2.coverUrlChanged)
            assertTrue(!r3.created && r3.cardFieldsChanged && !r3.coverUrlChanged)
            assertEquals(first.bookId, r1.bookId)
            assertEquals(recorder.seen[0].id, r1.bookId)
            assertTrue(first.draftCreated && second.draftCreated && changed.draftCreated)
        }
}
