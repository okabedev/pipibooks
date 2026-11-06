package com.pipibooks.catalog.book

import com.pipibooks.catalog.nlk.CoverChecker
import com.pipibooks.catalog.nlk.NlkRecord
import com.pipibooks.catalog.support.NlkTestSupport
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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

        override suspend fun onUpsert(
            book: Book,
            result: UpsertResult,
        ) {
            seen += book
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
}
