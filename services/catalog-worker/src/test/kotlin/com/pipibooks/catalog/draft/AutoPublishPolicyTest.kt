package com.pipibooks.catalog.draft

import com.pipibooks.catalog.book.Book
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

fun sampleBook(
    introduction: String? = "가".repeat(120),
    coverUrl: String? = "https://cdn.example.com/c.jpg",
    eaAddCode: String? = "03810",
    form: String? = "종이책",
    ebookYn: Boolean? = false,
    setIsbn: String? = null,
    kdc: String? = "8",
    tableOfContents: String? = null,
    title: String = "조용한 숲",
) = Book(
    id = 1L,
    isbn13 = "9788912345679",
    title = title,
    author = "홍길동",
    publisher = "푸른출판",
    publishPredate = null,
    kdc = kdc,
    pages = 240,
    coverUrl = coverUrl,
    coverSourceUrl = coverUrl,
    introduction = introduction,
    tableOfContents = tableOfContents,
    eaAddCode = eaAddCode,
    form = form,
    ebookYn = ebookYn,
    setIsbn = setIsbn,
    fetchedAt = Instant.EPOCH,
)

class AutoPublishPolicyTest {
    private fun evaluate(book: Book) = AutoPublishPolicy.evaluate(book)

    @Test
    fun `소개 99자는 SHORT_INTRODUCTION, 100자는 통과한다`() {
        assertEquals(listOf(BlockedReason.SHORT_INTRODUCTION), evaluate(sampleBook(introduction = "가".repeat(99))))
        assertEquals(emptyList<BlockedReason>(), evaluate(sampleBook(introduction = "가".repeat(100))))
        assertEquals(listOf(BlockedReason.SHORT_INTRODUCTION), evaluate(sampleBook(introduction = null)))
    }

    @Test
    fun `전자책과 세트 ISBN은 NOT_PRINT_SINGLE`() {
        val blocked = listOf(BlockedReason.NOT_PRINT_SINGLE)
        assertEquals(blocked, evaluate(sampleBook(ebookYn = true)))
        assertEquals(blocked, evaluate(sampleBook(setIsbn = "9788912345686")))
        assertEquals(blocked, evaluate(sampleBook(form = "전자책")))
        assertEquals(blocked, evaluate(sampleBook(form = "오디오북")))
        assertEquals(emptyList<BlockedReason>(), evaluate(sampleBook(form = "", ebookYn = null)))
    }

    @Test
    fun `부가기호 첫 자리 5와 6은 STUDY_REFERENCE, 4는 통과한다`() {
        val blocked = listOf(BlockedReason.STUDY_REFERENCE)
        assertEquals(blocked, evaluate(sampleBook(eaAddCode = "53370")))
        assertEquals(blocked, evaluate(sampleBook(eaAddCode = "63370")))
        assertEquals(emptyList<BlockedReason>(), evaluate(sampleBook(eaAddCode = "43370")))
        assertEquals(emptyList<BlockedReason>(), evaluate(sampleBook(eaAddCode = null)))
    }

    @Test
    fun `표지가 없으면 NO_COVER`() {
        assertEquals(listOf(BlockedReason.NO_COVER), evaluate(sampleBook(coverUrl = null)))
    }

    @Test
    fun `모두 만족하면 빈 목록`() {
        assertEquals(emptyList<BlockedReason>(), evaluate(sampleBook()))
    }
}
