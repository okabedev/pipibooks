package com.pipibooks.core.card

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

data class BookSummary(
    val bookId: Long,
    val isbn13: String,
    val title: String,
    val author: String?,
    val publisher: String?,
    val coverUrl: String?,
    val kdcMajor: Int?,
)

/** catalog.book을 읽는 유일한 경로. 다른 core 모듈은 catalog 테이블을 직접 읽지 않고 이 인터페이스를 쓴다. */
interface BookQuery {
    fun find(bookId: Long): BookSummary?

    fun findAll(ids: Collection<Long>): Map<Long, BookSummary>
}

@Component
class JdbcBookQuery(
    private val jdbc: JdbcClient,
) : BookQuery {
    override fun find(bookId: Long): BookSummary? = findAll(listOf(bookId))[bookId]

    override fun findAll(ids: Collection<Long>): Map<Long, BookSummary> {
        if (ids.isEmpty()) return emptyMap()
        return jdbc
            .sql("SELECT id, isbn13, title, author, publisher, cover_url, kdc FROM catalog.book WHERE id IN (:ids)")
            .param("ids", ids)
            .query { rs, _ ->
                BookSummary(
                    bookId = rs.getLong("id"),
                    isbn13 = rs.getString("isbn13"),
                    title = rs.getString("title"),
                    author = rs.getString("author"),
                    publisher = rs.getString("publisher"),
                    coverUrl = rs.getString("cover_url"),
                    kdcMajor = kdcMajor(rs.getString("kdc")),
                )
            }.list()
            .associateBy { it.bookId }
    }

    // KDC 대분류: 첫 글자가 숫자일 때만 인정한다(대분류 숫자 한 자리 또는 KDC 코드).
    private fun kdcMajor(kdc: String?): Int? = kdc?.firstOrNull()?.takeIf { it.isDigit() }?.digitToInt()
}
