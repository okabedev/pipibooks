package com.pipibooks.core.card

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.Instant

interface CardRepository : JpaRepository<CardEntity, Long> {
    fun findByBookId(bookId: Long): CardEntity?

    fun findByIdInAndStatus(
        ids: Collection<Long>,
        status: String,
    ): List<CardEntity>

    @Query("SELECT c FROM CardEntity c WHERE c.status = 'PUBLISHED' ORDER BY c.publishedAt DESC, c.id DESC")
    fun findPublishedLatest(page: Pageable): List<CardEntity>

    /** 책에 카드가 이미 있으면 아무것도 하지 않는다. 만든 행 수(0 또는 1)를 돌려준다. */
    @Modifying
    @Query(
        value =
            "INSERT INTO core.card (id, book_id, source, status, published_at, created_at) " +
                "VALUES (:id, :bookId, :source, 'PUBLISHED', :now, :now) ON CONFLICT (book_id) DO NOTHING",
        nativeQuery = true,
    )
    fun insertPublishedIfAbsent(
        @Param("id") id: Long,
        @Param("bookId") bookId: Long,
        @Param("source") source: String,
        @Param("now") now: Instant,
    ): Int
}

interface CardSlideRepository : JpaRepository<CardSlideEntity, Long> {
    fun findByCardIdInOrderByCardIdAscPositionAsc(cardIds: Collection<Long>): List<CardSlideEntity>

    fun deleteByCardId(cardId: Long)
}
