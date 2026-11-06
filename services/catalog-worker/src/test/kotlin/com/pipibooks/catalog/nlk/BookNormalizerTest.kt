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
    fun `전각 숫자 ISBN은 버린다`() {
        assertNull(BookNormalizer.normalize(record(isbn = "９７８８９１２３４５６７９")))
    }

    @Test
    fun `컬럼 길이를 넘는 값은 잘라서 저장 가능하게 한다`() {
        val book = BookNormalizer.normalize(record().copy(kdc = "8".repeat(40), form = "f".repeat(100)))
        assertEquals(16, book!!.kdc!!.length)
        assertEquals(64, book.form!!.length)
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

    private fun author(raw: String) = BookNormalizer.normalize(record().copy(author = raw))!!.author

    @Test
    fun `저자 역할어를 정리한다`() {
        assertEquals("김하늘, 이바다", author("김하늘 지음 ; 이바다 옮김"))
        assertEquals("류윤환", author("류윤환 지음"))
        assertEquals("이현주", author("이현주 저"))
        assertEquals("엔텔러키", author("엔텔러키 [편]"))
        assertEquals(
            "Park Kwon, Ohdee Lee, translation by K.H. Yoo",
            author("Park Kwon, Ohdee Lee ;translation by K.H. Yoo"),
        )
    }

    @Test
    fun `역할 콜론 이름 형식은 역할을 떼고 이름만 원래 순서로 잇는다`() {
        assertEquals("민병훈", author("저자 : 민병훈"))
        assertEquals(
            "윌리엄 해즐릿, 아이보리잉크 편집부, TR 클럽",
            author("저자 : 윌리엄 해즐릿;편집자 : 아이보리잉크 편집부;역자 : TR 클럽;"),
        )
        assertEquals("임수현, 정인하", author("저자 : 임수현;삽화가(그림작가) : 정인하"))
        assertEquals("박대건, AI 한글연구팀", author("저자 : 박대건;AI 저자 : AI 한글연구팀"))
    }

    @Test
    fun `콜론 앞뒤 공백이 없어도 역할을 뗀다`() {
        assertEquals("오병곤", author("사진작가: 오병곤"))
        assertEquals("고재봉, 김상태, 천영기, 류제혁", author("집필: 고재봉 ;사진: 김상태,천영기,류제혁"))
        assertEquals("김상태, 천영기, 류제혁", author("저자 : 김상태,천영기,류제혁"))
    }

    @Test
    fun `역할이 달라도 이름이 같으면 한 번만 쓴다`() {
        assertEquals("김도훈, 김연우", author("저자 : 김도훈;저자 : 김연우;역자 : 김도훈;"))
    }

    @Test
    fun `KDC가 비면 SUBJECT 대분류를 쓴다`() {
        assertEquals("8", BookNormalizer.normalize(record().copy(kdc = "", subject = "8"))!!.kdc)
        assertEquals("8", BookNormalizer.normalize(record().copy(kdc = null, subject = " 8 "))!!.kdc)
    }

    @Test
    fun `KDC가 있으면 SUBJECT보다 우선하고 한 자리 숫자가 아닌 SUBJECT는 쓰지 않는다`() {
        assertEquals("813.7", BookNormalizer.normalize(record().copy(kdc = "813.7", subject = "8"))!!.kdc)
        assertNull(BookNormalizer.normalize(record().copy(kdc = "", subject = "문학"))!!.kdc)
        assertNull(BookNormalizer.normalize(record().copy(kdc = "", subject = "81"))!!.kdc)
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
