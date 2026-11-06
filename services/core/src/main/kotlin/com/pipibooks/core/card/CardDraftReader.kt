package com.pipibooks.core.card

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component
import java.sql.ResultSet

/** catalog.card_draft 한 행. [slides]는 SlidePayload 배열 JSON이다. */
data class CardDraft(
    val id: Long,
    val bookId: Long,
    val slides: String,
    val autoPublishable: Boolean,
)

/** catalog.card_draft 읽기 전용 접근. core는 catalog 스키마에 쓰지 않는다. */
interface CardDraftReader {
    /** [lastId] 이후, 생성된 지 60초가 지난 초안을 id 순으로 최대 [limit]개 읽는다. */
    fun readAfter(
        lastId: Long,
        limit: Int,
    ): List<CardDraft>

    fun find(draftId: Long): CardDraft?

    /** 책의 초안 중 id가 가장 큰 것. */
    fun findLatestByBook(bookId: Long): CardDraft?
}

@Component
class JdbcCardDraftReader(
    private val jdbc: JdbcClient,
) : CardDraftReader {
    // created_at 조건: id가 작은 초안이 나중에 커밋돼도 커서에 건너뛰어지지 않도록 막 생긴 초안은 미룬다.
    override fun readAfter(
        lastId: Long,
        limit: Int,
    ): List<CardDraft> =
        jdbc
            .sql(
                "$SELECT WHERE id > :lastId AND created_at < now() - interval '60 seconds' ORDER BY id LIMIT :limit",
            ).param("lastId", lastId)
            .param("limit", limit)
            .query { rs, _ -> map(rs) }
            .list()

    override fun find(draftId: Long): CardDraft? =
        jdbc
            .sql("$SELECT WHERE id = :id")
            .param("id", draftId)
            .query { rs, _ -> map(rs) }
            .optional()
            .orElse(null)

    override fun findLatestByBook(bookId: Long): CardDraft? =
        jdbc
            .sql("$SELECT WHERE book_id = :bookId ORDER BY id DESC LIMIT 1")
            .param("bookId", bookId)
            .query { rs, _ -> map(rs) }
            .optional()
            .orElse(null)

    private fun map(rs: ResultSet) =
        CardDraft(rs.getLong("id"), rs.getLong("book_id"), rs.getString("slides"), rs.getBoolean("auto_publishable"))

    private companion object {
        const val SELECT = "SELECT id, book_id, slides::text AS slides, auto_publishable FROM catalog.card_draft"
    }
}
