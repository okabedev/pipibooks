package com.pipibooks.catalog.nlk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BookNormalizerTest {
    private fun record(isbn: String? = "9788912345679") = NlkRecord(eaIsbn = isbn, title = "제목")

    @Test
    fun `빈 문자열과 공백 필드는 null이 된다`() {
        val book =
            BookNormalizer.normalize(
                NlkRecord(
                    eaIsbn = "9788912345679",
                    title = "제목",
                    author = "   ",
                    publisher = "",
                    kdc = " ",
                    bookIntroduction = "",
                    bookTbCnt = "  ",
                    titleUrl = "",
                    form = "",
                ),
            )
        assertNotNull(book)
        assertNull(book!!.author)
        assertNull(book.publisher)
        assertNull(book.kdc)
        assertNull(book.introduction)
        assertNull(book.tableOfContents)
        assertNull(book.coverUrl)
        assertNull(book.form)
    }

    @Test
    fun `하이픈이 섞인 ISBN은 숫자 13자리로 정규화된다`() {
        val book = BookNormalizer.normalize(record(isbn = "978-89-123-4567-9"))
        assertEquals("9788912345679", book!!.isbn13)
    }

    @Test
    fun `검증숫자가 틀린 ISBN은 버린다`() {
        assertNull(BookNormalizer.normalize(record(isbn = "978-89-123-4567-8")))
        assertNull(BookNormalizer.normalize(record(isbn = null)))
        assertNull(BookNormalizer.normalize(record(isbn = "12345")))
    }

    @Test
    fun `http 표지는 https로 바뀐다`() {
        val book = BookNormalizer.normalize(record().copy(titleUrl = "http://cdn.example.com/c.jpg"))
        assertEquals("https://cdn.example.com/c.jpg", book!!.coverUrl)
    }

    @Test
    fun `저자 역할어를 정리한다`() {
        val book = BookNormalizer.normalize(record().copy(author = "김하늘 지음 ; 이바다 옮김"))
        assertEquals("김하늘, 이바다", book!!.author)
    }

    @Test
    fun `발행예정일 yyyymmdd를 날짜로 바꾼다`() {
        val book = BookNormalizer.normalize(record().copy(publishPredate = "20261105"))
        assertEquals(LocalDate.of(2026, 11, 5), book!!.publishPredate)
        val bad = BookNormalizer.normalize(record().copy(publishPredate = "2026"))
        assertNull(bad!!.publishPredate)
    }

    @Test
    fun `부가기호와 전자책 여부를 읽는다`() {
        val book =
            BookNormalizer.normalize(
                NlkRecord(
                    eaIsbn = "9788912345679",
                    title = "제목",
                    eaAddCode = "03810",
                    ebookYn = "N",
                    setIsbn = "9788999990014",
                    page = "320",
                ),
            )
        assertEquals("03810", book!!.eaAddCode)
        assertEquals(false, book.ebookYn)
        assertEquals("9788999990014", book.setIsbn)
        assertEquals(320, book.pages)
    }
}
