package com.pipibooks.catalog.book

import com.pipibooks.catalog.bindNullable
import com.pipibooks.catalog.nlk.NormalizedBook
import com.pipibooks.common.Tsid
import io.r2dbc.spi.Readable
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

data class Book(
    val id: Long,
    val isbn13: String,
    val title: String,
    val author: String?,
    val publisher: String?,
    val publishPredate: LocalDate?,
    val kdc: String?,
    val pages: Int?,
    val coverUrl: String?,
    val introduction: String?,
    val tableOfContents: String?,
    val eaAddCode: String?,
    val form: String?,
    val ebookYn: Boolean?,
    val setIsbn: String?,
    val fetchedAt: Instant,
)

data class UpsertResult(
    val bookId: Long,
    val created: Boolean,
    val cardFieldsChanged: Boolean,
    val coverUrlChanged: Boolean,
)

@Repository
class BookRepository(
    private val db: DatabaseClient,
    private val clock: Clock,
) {
    suspend fun findByIsbn(isbn13: String): Book? =
        db
            .sql("SELECT $COLUMNS FROM catalog.book WHERE isbn13 = :isbn")
            .bind("isbn", isbn13)
            .map { row -> row.toBook() }
            .one()
            .awaitSingleOrNull()

    /** ISBN이 이미 있으면 갱신한다. `fetched_at`은 매번 바뀌지만 카드 필드 비교에는 쓰지 않는다. */
    suspend fun upsert(b: NormalizedBook): UpsertResult {
        val existing = findByIsbn(b.isbn13)
        val now = OffsetDateTime.ofInstant(clock.instant(), clock.zone)
        val spec =
            db
                .sql(if (existing == null) INSERT else UPDATE)
                .bind("isbn", b.isbn13)
                .let { if (existing == null) it.bind("id", Tsid.next()) else it }
                .bind("title", b.title)
                .bind("fetchedAt", now)
                .bindNullable("author", b.author)
                .bindNullable("publisher", b.publisher)
                .bindNullable("predate", b.publishPredate)
                .bindNullable("kdc", b.kdc)
                .bindNullable("pages", b.pages)
                .bindNullable("cover", b.coverUrl)
                .bindNullable("intro", b.introduction)
                .bindNullable("toc", b.tableOfContents)
                .bindNullable("eaAdd", b.eaAddCode)
                .bindNullable("form", b.form)
                .bindNullable("ebook", b.ebookYn)
                .bindNullable("setIsbn", b.setIsbn)
        val row =
            spec
                .map { r ->
                    r.get("id", Long::class.javaObjectType)!! to
                        r.get("created", Boolean::class.javaObjectType)!!
                }.one()
                .awaitSingle()
        return UpsertResult(
            bookId = row.first,
            created = row.second,
            cardFieldsChanged = existing == null || existing.cardFieldsDiffer(b),
            coverUrlChanged = existing?.coverUrl != b.coverUrl,
        )
    }

    /** 표지 확인에 실패했을 때 표지를 비운다. */
    suspend fun clearCover(bookId: Long) {
        db
            .sql("UPDATE catalog.book SET cover_url = NULL WHERE id = :id")
            .bind("id", bookId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    private fun Book.cardFieldsDiffer(b: NormalizedBook) =
        title != b.title ||
            author != b.author ||
            publisher != b.publisher ||
            publishPredate != b.publishPredate ||
            kdc != b.kdc ||
            pages != b.pages ||
            coverUrl != b.coverUrl ||
            introduction != b.introduction ||
            tableOfContents != b.tableOfContents ||
            eaAddCode != b.eaAddCode ||
            form != b.form ||
            ebookYn != b.ebookYn ||
            setIsbn != b.setIsbn

    private fun Readable.toBook() =
        Book(
            id = get("id", Long::class.javaObjectType)!!,
            isbn13 = get("isbn13", String::class.java)!!,
            title = get("title", String::class.java)!!,
            author = get("author", String::class.java),
            publisher = get("publisher", String::class.java),
            publishPredate = get("publish_predate", LocalDate::class.java),
            kdc = get("kdc", String::class.java),
            pages = get("pages", Int::class.javaObjectType),
            coverUrl = get("cover_url", String::class.java),
            introduction = get("introduction", String::class.java),
            tableOfContents = get("table_of_contents", String::class.java),
            eaAddCode = get("ea_add_code", String::class.java),
            form = get("form", String::class.java),
            ebookYn = get("ebook_yn", Boolean::class.javaObjectType),
            setIsbn = get("set_isbn", String::class.java),
            fetchedAt = get("fetched_at", OffsetDateTime::class.java)!!.toInstant(),
        )

    private companion object {
        const val COLUMNS =
            "id, isbn13, title, author, publisher, publish_predate, kdc, pages, cover_url, introduction, " +
                "table_of_contents, ea_add_code, form, ebook_yn, set_isbn, fetched_at"
        const val INSERT =
            "INSERT INTO catalog.book ($COLUMNS) VALUES " +
                "(:id, :isbn, :title, :author, :publisher, :predate, :kdc, :pages, :cover, :intro, :toc, " +
                ":eaAdd, :form, " +
                ":ebook, :setIsbn, :fetchedAt) " +
                "ON CONFLICT (isbn13) DO UPDATE SET title = EXCLUDED.title, author = EXCLUDED.author, " +
                "publisher = EXCLUDED.publisher, publish_predate = EXCLUDED.publish_predate, kdc = EXCLUDED.kdc, " +
                "pages = EXCLUDED.pages, cover_url = EXCLUDED.cover_url, introduction = EXCLUDED.introduction, " +
                "table_of_contents = EXCLUDED.table_of_contents, ea_add_code = EXCLUDED.ea_add_code, " +
                "form = EXCLUDED.form, ebook_yn = EXCLUDED.ebook_yn, set_isbn = EXCLUDED.set_isbn, " +
                "fetched_at = EXCLUDED.fetched_at RETURNING id, (xmax = 0) AS created"
        const val UPDATE =
            "UPDATE catalog.book SET title = :title, author = :author, publisher = :publisher, " +
                "publish_predate = :predate, kdc = :kdc, pages = :pages, cover_url = :cover, introduction = :intro, " +
                "table_of_contents = :toc, " +
                "ea_add_code = :eaAdd, form = :form, ebook_yn = :ebook, set_isbn = :setIsbn, fetched_at = :fetchedAt " +
                "WHERE isbn13 = :isbn RETURNING id, false AS created"
    }
}
