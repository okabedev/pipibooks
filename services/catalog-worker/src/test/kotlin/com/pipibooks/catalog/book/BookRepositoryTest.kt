package com.pipibooks.catalog.book

import com.pipibooks.catalog.nlk.NormalizedBook
import com.pipibooks.catalog.support.NlkTestSupport
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Instant

@SpringBootTest
class BookRepositoryTest(
    @Autowired private val repository: BookRepository,
) : NlkTestSupport() {
    private fun book(
        title: String = "제목",
        coverUrl: String? = "https://cdn.example.com/c.jpg",
    ) = NormalizedBook(isbn13 = "9788912345679", title = title, author = "저자", coverUrl = coverUrl, introduction = "소개")

    @Test
    fun `같은 ISBN을 다시 저장하면 갱신되고 created는 false다`() =
        runBlocking {
            val first = repository.upsert(book())
            assertTrue(first.created)
            assertTrue(first.cardFieldsChanged)
            assertTrue(first.coverUrlChanged)

            val second = repository.upsert(book(title = "새 제목", coverUrl = null))
            assertFalse(second.created)
            assertEquals(first.bookId, second.bookId)
            assertTrue(second.cardFieldsChanged)
            assertTrue(second.coverUrlChanged)
            assertEquals("새 제목", repository.findByIsbn("9788912345679")!!.title)
        }

    @Test
    fun `카드 필드가 같으면 cardFieldsChanged는 false다`() =
        runBlocking {
            repository.upsert(book())
            val again = repository.upsert(book())
            assertFalse(again.created)
            assertFalse(again.cardFieldsChanged)
            assertFalse(again.coverUrlChanged)
        }

    @Test
    fun `fetched_at만 바뀌어도 cardFieldsChanged는 false다`() =
        runBlocking {
            repository.upsert(book())
            db
                .sql("UPDATE catalog.book SET fetched_at = '2020-01-01T00:00:00Z'")
                .fetch()
                .rowsUpdated()
                .awaitSingle()

            val again = repository.upsert(book())

            assertFalse(again.cardFieldsChanged)
            assertEquals(Instant.parse("2026-11-05T01:00:00Z"), repository.findByIsbn("9788912345679")!!.fetchedAt)
        }

    @Test
    fun `깨진 표지를 다시 받아도 변경으로 보지 않는다`() =
        runBlocking {
            repository.upsert(book())
            repository.clearCover(repository.findByIsbn("9788912345679")!!.id)

            val again = repository.upsert(book())

            assertFalse(again.coverUrlChanged)
            assertFalse(again.cardFieldsChanged)
            assertEquals(null, repository.findByIsbn("9788912345679")!!.coverUrl)
        }
}
