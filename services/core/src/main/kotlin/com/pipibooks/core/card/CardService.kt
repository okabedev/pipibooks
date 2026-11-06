package com.pipibooks.core.card

import com.pipibooks.common.ApiException
import com.pipibooks.common.Tsid
import com.pipibooks.common.slide.SlidePayload
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue
import java.sql.SQLException
import java.time.Instant

@Service
class CardService(
    private val reader: CardDraftReader,
    private val cards: CardRepository,
    private val slides: CardSlideRepository,
    private val cursor: SyncCursorStore,
    private val mapper: JsonMapper,
    private val tx: TransactionTemplate,
    private val em: EntityManager,
) {
    /**
     * 커서 이후의 초안을 읽어 자동 발행 가능한 것으로 카드를 만든다. 만든 카드 수를 돌려준다.
     * 초안마다 트랜잭션을 따로 쓴다(카드·슬라이드 생성과 커서 갱신이 함께 커밋). 처리에 실패한 초안은
     * 오류를 남기고 커서만 넘겨, 한 건이 이후 초안을 막지 않게 한다.
     * catalog 테이블이 아직 없으면(첫 기동 순서) 지금까지의 수를 돌려주고 다음 주기에 다시 시도한다.
     */
    fun syncDrafts(): Int {
        var created = 0
        try {
            while (true) {
                val drafts = reader.readAfter(cursor.get(CURSOR_NAME), BATCH_SIZE)
                created += drafts.count { syncOne(it) }
                if (drafts.size < BATCH_SIZE) break
            }
        } catch (e: DataAccessException) {
            if (!isMissingTable(e)) throw e
            log.warn("catalog.card_draft가 아직 없어 카드 동기화를 건너뛴다", e)
        }
        return created
    }

    // 초안은 id 순으로 처리하고, 책당 카드는 UNIQUE(book_id)로 하나만 만든다. 커서는 자동 발행 불가·실패 초안도 지나간다.
    @Suppress("TooGenericExceptionCaught") // 한 초안의 어떤 실패도 동기화 전체를 막지 않는다
    private fun syncOne(draft: CardDraft): Boolean =
        try {
            tx.execute {
                val made = draft.autoPublishable && createCard(draft, CardSource.AUTO, Instant.now()) != null
                cursor.set(CURSOR_NAME, draft.id)
                made
            } == true
        } catch (e: RuntimeException) {
            log.error("초안 {}(책 {}) 처리에 실패해 건너뛴다", draft.id, draft.bookId, e)
            tx.executeWithoutResult { cursor.set(CURSOR_NAME, draft.id) }
            false
        }

    /** 초안을 `CURATED`·`PUBLISHED` 카드로 발행한다(자동 발행 불가 초안도 가능). 만든 카드 ID를 돌려준다. */
    @Transactional
    fun publishDraft(draftId: Long): Long {
        val draft = reader.find(draftId) ?: throw draftNotFound()
        return createCard(draft, CardSource.CURATED, Instant.now())
            ?: throw ApiException(HttpStatus.CONFLICT, "CARD_ALREADY_EXISTS", "이미 카드가 있는 책이다")
    }

    /** 책의 최신 초안(id가 가장 큰 것)으로 카드의 슬라이드를 교체한다. 상태는 유지하고 `edited_at`을 남긴다. */
    @Transactional
    fun reapplyLatestDraft(cardId: Long) {
        val card = cards.findById(cardId).orElseThrow { cardNotFound() }
        val draft = reader.findLatestByBook(card.bookId) ?: throw draftNotFound()
        val parsed = parse(draft.slides)
        slides.deleteByCardId(cardId)
        slides.flush() // 삭제를 먼저 반영하지 않으면 UNIQUE(card_id, position)에 걸린다
        saveSlides(cardId, parsed)
        card.editedAt = Instant.now()
    }

    // 책에 카드가 이미 있으면 null.
    private fun createCard(
        draft: CardDraft,
        source: String,
        now: Instant,
    ): Long? {
        val parsed = parse(draft.slides)
        val id = Tsid.next()
        if (cards.insertPublishedIfAbsent(id, draft.bookId, source, now) == 0) return null
        saveSlides(id, parsed)
        return id
    }

    private fun parse(json: String): List<SlidePayload> = mapper.readValue<List<SlidePayload>>(json)

    private fun saveSlides(
        cardId: Long,
        payloads: List<SlidePayload>,
    ) {
        payloads.forEachIndexed { index, slide ->
            val json = mapper.writeValueAsString(slide)
            val type =
                mapper
                    .readTree(json)
                    .get("type")
                    ?.takeIf { it.isString }
                    ?.asString()
                    ?: throw IllegalArgumentException("슬라이드에 type이 없다")
            // 새 ID이므로 save(merge의 선행 SELECT) 대신 persist한다
            em.persist(CardSlideEntity(Tsid.next(), cardId, index, type, json))
        }
    }

    private fun isMissingTable(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.any { it is SQLException && it.sqlState == UNDEFINED_TABLE }

    private fun cardNotFound() = ApiException(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", "카드를 찾을 수 없다")

    private fun draftNotFound() = ApiException(HttpStatus.NOT_FOUND, "DRAFT_NOT_FOUND", "초안을 찾을 수 없다")

    private companion object {
        const val CURSOR_NAME = "card_draft"
        const val BATCH_SIZE = 500
        const val UNDEFINED_TABLE = "42P01"
        val log = LoggerFactory.getLogger(CardService::class.java)
    }
}
