package com.pipibooks.core.card

import com.pipibooks.common.Tsid
import com.pipibooks.common.slide.SlidePayload
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

/** 다른 모듈(feed, library, admin)이 카드를 읽는 공개 경로. */
interface CardQuery {
    /** 최근 발행 순으로 최대 [limit]개의 발행 카드. */
    fun candidates(limit: Int): List<CardCandidate>

    /** [ids] 순서를 유지하며, 발행(PUBLISHED) 상태가 아닌 카드는 뺀다. */
    fun publishedViews(ids: List<Long>): List<CardView>
}

@Component
@Transactional(readOnly = true)
class JpaCardQuery(
    private val cards: CardRepository,
    private val slides: CardSlideRepository,
    private val books: BookQuery,
    private val mapper: JsonMapper,
) : CardQuery {
    override fun candidates(limit: Int): List<CardCandidate> {
        val published = cards.findPublishedLatest(PageRequest.of(0, limit))
        val kdc = books.findAll(published.map { it.bookId }).mapValues { it.value.kdcMajor }
        return published.mapNotNull { c ->
            c.publishedAt?.let { CardCandidate(c.id, c.bookId, kdc[c.bookId], it) }
        }
    }

    override fun publishedViews(ids: List<Long>): List<CardView> {
        if (ids.isEmpty()) return emptyList()
        val byId = cards.findByIdInAndStatus(ids, CardStatus.PUBLISHED).associateBy { it.id }
        val bookById = books.findAll(byId.values.map { it.bookId })
        val slidesByCard = slides.findByCardIdInOrderByCardIdAscPositionAsc(byId.keys).groupBy { it.cardId }
        return ids.distinct().mapNotNull { id ->
            val card = byId[id] ?: return@mapNotNull null
            val book = bookById[card.bookId] ?: return@mapNotNull null
            CardView(
                id = Tsid.format(card.id),
                isbn13 = book.isbn13,
                title = book.title,
                author = book.author,
                slides = slidesByCard[card.id].orEmpty().map { mapper.readValue<SlidePayload>(it.payload) },
            )
        }
    }
}
