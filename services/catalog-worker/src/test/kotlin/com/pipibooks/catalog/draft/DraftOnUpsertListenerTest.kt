package com.pipibooks.catalog.draft

import com.pipibooks.catalog.book.Book
import com.pipibooks.catalog.book.BookRepository
import com.pipibooks.catalog.nlk.NormalizedBook
import com.pipibooks.catalog.support.NlkTestSupport
import com.pipibooks.common.slide.SlidePayload
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import tools.jackson.module.kotlin.jacksonObjectMapper
import java.time.LocalDate

@SpringBootTest
class DraftOnUpsertListenerTest(
    @Autowired private val books: BookRepository,
    @Autowired private val drafts: CardDraftRepository,
) : NlkTestSupport() {
    private val generator = RuleBasedCardDraftGenerator()
    private val intro = "숲속에서 만난 작은 동물들이 계절마다 건네는 이야기를 담았다. ".repeat(4)

    private fun normalized(
        intro: String? = this.intro,
        title: String = "조용한 숲",
        cover: String? = "https://cdn.example.com/c.jpg",
        predate: LocalDate? = null,
    ) = NormalizedBook(
        isbn13 = "9788912345679",
        title = title,
        author = "홍길동",
        kdc = "8",
        publishPredate = predate,
        pages = 240,
        introduction = intro,
        coverUrl = cover,
        eaAddCode = "03810",
        form = "종이책",
        ebookYn = false,
    )

    private suspend fun upsert(
        b: NormalizedBook,
        gen: CardDraftGenerator = generator,
    ): Pair<Book, Boolean> {
        val result = books.upsert(b)
        if (b.coverUrl != null) books.setVerifiedCover(result.bookId, b.coverUrl!!)
        val book = books.findByIsbn(b.isbn13)!!
        return book to DraftOnUpsertListener(gen, drafts).onUpsert(book, result)
    }

    @Test
    fun `새 도서는 초안을 만든다`() =
        runBlocking {
            val (book, created) = upsert(normalized())
            assertTrue(created)
            val draft = drafts.findAll(book.id).single()
            assertEquals("rule-based", draft.generatorName)
            assertEquals(1, draft.generatorVersion)
        }

    @Test
    fun `카드 필드가 바뀌지 않은 재수집은 초안을 만들지 않는다`() =
        runBlocking {
            val (book, _) = upsert(normalized())
            val (_, again) = upsert(normalized())
            assertFalse(again)
            assertEquals(1, drafts.findAll(book.id).size)
        }

    @Test
    fun `카드 필드가 바뀌면 새 초안을 만든다`() =
        runBlocking {
            val (book, _) = upsert(normalized())
            val (_, changed) = upsert(normalized(title = "고요한 숲"))
            assertTrue(changed)
            val all = drafts.findAll(book.id)
            assertEquals(2, all.size)
            assertTrue(all[1].id > all[0].id)
        }

    @Test
    fun `생성기 버전이 오르면 기존 도서도 새 초안을 만든다`() =
        runBlocking {
            val (book, _) = upsert(normalized())
            val v2 =
                object : CardDraftGenerator {
                    override val name = generator.name
                    override val version = 2

                    override fun generate(book: Book) = generator.generate(book)
                }
            val (_, created) = upsert(normalized(), v2)
            assertTrue(created)
            assertEquals(listOf(1, 2), drafts.findAll(book.id).map { it.generatorVersion })
        }

    @Test
    fun `초안 생성이 실패했던 도서는 다음 수집에서 초안을 만든다`() =
        runBlocking {
            val failing =
                object : CardDraftGenerator {
                    override val name = generator.name
                    override val version = generator.version

                    override fun generate(book: Book): List<SlidePayload>? = error("실패")
                }
            val result = books.upsert(normalized())
            val book = books.findByIsbn("9788912345679")!!
            runCatching { DraftOnUpsertListener(failing, drafts).onUpsert(book, result) }
            assertEquals(0, drafts.findAll(book.id).size)

            val (_, created) = upsert(normalized())
            assertTrue(created)
            assertEquals(1, drafts.findAll(book.id).size)
        }

    @Test
    fun `소개가 없는 도서는 초안을 만들지 않는다`() =
        runBlocking {
            val (book, created) = upsert(normalized(intro = null))
            assertFalse(created)
            assertEquals(0, drafts.findAll(book.id).size)
        }

    @Test
    fun `판정 결과와 사유를 저장한다`() =
        runBlocking {
            val (ok, _) = upsert(normalized())
            val okDraft = drafts.findAll(ok.id).single()
            assertTrue(okDraft.autoPublishable)
            assertEquals(emptyList<String>(), okDraft.blockedReasons)

            val (blocked, _) = upsert(normalized(intro = "짧은 소개 문장이 여기에 들어간다. 그리고 조금 더 이어진다.", cover = null))
            val draft = drafts.findAll(blocked.id).last()
            assertFalse(draft.autoPublishable)
            assertEquals(listOf("SHORT_INTRODUCTION", "NO_COVER"), draft.blockedReasons)
        }

    @Test
    fun `슬라이드는 type 필드와 함께 jsonb로 저장되고 읽힌다`() =
        runBlocking {
            val (book, _) = upsert(normalized())
            val json = drafts.findAll(book.id).single().slidesJson
            val tree = jacksonObjectMapper().readTree(json)
            val types = (0 until tree.size()).map { tree.get(it).get("type").asString() }
            assertEquals(listOf("COVER", "HOOK", "INFO", "KEYWORDS", "FOR_WHOM"), types)
            val back = jacksonObjectMapper().readValue(json, Array<SlidePayload>::class.java)
            assertEquals("https://cdn.example.com/c.jpg", (back[0] as SlidePayload.Cover).coverUrl)
        }

    @Test
    fun `publishedOn은 ISO 문자열로 저장되고 다섯 슬라이드가 원본과 같게 읽힌다`() =
        runBlocking {
            val (book, _) = upsert(normalized(predate = LocalDate.of(2026, 11, 5)))
            val json = drafts.findAll(book.id).single().slidesJson
            val mapper = jacksonObjectMapper()
            val info = mapper.readTree(json).get(2)
            assertTrue(info.get("publishedOn").isString)
            assertEquals("2026-11-05", info.get("publishedOn").asString())

            val back = mapper.readValue(json, Array<SlidePayload>::class.java).toList()
            assertEquals(generator.generate(book), back)
            assertEquals(5, back.size)
        }
}
