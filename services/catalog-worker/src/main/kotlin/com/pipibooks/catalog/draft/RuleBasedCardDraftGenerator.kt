package com.pipibooks.catalog.draft

import com.pipibooks.catalog.book.Book
import com.pipibooks.common.slide.SlidePayload
import org.springframework.stereotype.Component

/**
 * 규칙 기반 카드 초안 생성기. 순서는 COVER, HOOK, INFO, KEYWORDS, FOR_WHOM이다.
 *
 * - HOOK: 소개를 `. ! ? 。` 뒤 공백으로 나눠 20~120자 문장 중 앞쪽 1~2개. 없으면 첫 문장을 120자로(말줄임표 포함) 줄인다.
 * - KEYWORDS: [KeywordExtractor]가 3~6개를 뽑는다. 1~2개뿐이면 있는 만큼 만들고, 0개면 슬라이드를 생략해 4장이 된다.
 */
@Component
class RuleBasedCardDraftGenerator : CardDraftGenerator {
    override val name = "rule-based"
    override val version = 1

    override fun generate(book: Book): List<SlidePayload>? {
        val intro = book.introduction?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val keywords = KeywordExtractor.extract(book.title, intro, book.tableOfContents)
        return listOfNotNull(
            SlidePayload.Cover(book.title, book.author.orEmpty(), book.coverUrl),
            SlidePayload.Hook(hook(intro)),
            SlidePayload.Info(
                author = book.author.orEmpty(),
                publisher = book.publisher.orEmpty(),
                publishedOn = book.publishPredate,
                pages = book.pages,
                kdcLabel = KdcTemplates.label(book.kdc),
            ),
            keywords.takeIf { it.isNotEmpty() }?.let { SlidePayload.Keywords(it) },
            SlidePayload.ForWhom(KdcTemplates.forKdc(book.kdc)),
        )
    }

    private fun hook(intro: String): List<String> {
        val sentences = intro.split(sentenceEnd).map { it.trim() }.filter { it.isNotEmpty() }
        val fit = sentences.filter { it.length in MIN_SENTENCE..MAX_SENTENCE }.take(MAX_SENTENCES)
        if (fit.isNotEmpty()) return fit
        val first = sentences.first()
        return listOf(if (first.length <= MAX_SENTENCE) first else first.take(MAX_SENTENCE - 1) + "…")
    }

    private companion object {
        val sentenceEnd = Regex("(?<=[.!?。])\\s+")
        const val MIN_SENTENCE = 20
        const val MAX_SENTENCE = 120
        const val MAX_SENTENCES = 2
    }
}
