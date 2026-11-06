package com.pipibooks.catalog.draft

import com.pipibooks.common.Tsid
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Clock
import java.time.OffsetDateTime

data class CardDraftRow(
    val id: Long,
    val bookId: Long,
    val slidesJson: String,
    val generatorName: String,
    val generatorVersion: Int,
    val autoPublishable: Boolean,
    val blockedReasons: List<String>,
)

@Repository
class CardDraftRepository(
    private val db: DatabaseClient,
    private val clock: Clock,
) {
    suspend fun insert(
        bookId: Long,
        slidesJson: String,
        generatorName: String,
        generatorVersion: Int,
        blockedReasons: List<BlockedReason>,
    ): Long {
        val id = Tsid.next()
        db
            .sql(
                "INSERT INTO catalog.card_draft (id, book_id, slides, generator_name, generator_version, " +
                    "auto_publishable, blocked_reasons, created_at) " +
                    "VALUES (:id, :bookId, CAST(:slides AS jsonb), :name, :version, :auto, :reasons, :createdAt)",
            ).bind("id", id)
            .bind("bookId", bookId)
            .bind("slides", slidesJson)
            .bind("name", generatorName)
            .bind("version", generatorVersion)
            .bind("auto", blockedReasons.isEmpty())
            .bind("reasons", blockedReasons.map { it.name }.toTypedArray())
            .bind("createdAt", OffsetDateTime.ofInstant(clock.instant(), clock.zone))
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return id
    }

    suspend fun exists(
        bookId: Long,
        generatorName: String,
        generatorVersion: Int,
    ): Boolean =
        db
            .sql(
                "SELECT EXISTS (SELECT 1 FROM catalog.card_draft WHERE book_id = :bookId " +
                    "AND generator_name = :name AND generator_version = :version) AS present",
            ).bind("bookId", bookId)
            .bind("name", generatorName)
            .bind("version", generatorVersion)
            .map { row -> row.get("present", Boolean::class.javaObjectType)!! }
            .one()
            .awaitSingle()

    /** 도서의 초안을 오래된 순(ID 순)으로. */
    suspend fun findAll(bookId: Long): List<CardDraftRow> =
        db
            .sql(
                "SELECT id, book_id, slides::text AS slides, generator_name, generator_version, auto_publishable, " +
                    "blocked_reasons FROM catalog.card_draft WHERE book_id = :bookId ORDER BY id",
            ).bind("bookId", bookId)
            .map { row ->
                CardDraftRow(
                    id = row.get("id", Long::class.javaObjectType)!!,
                    bookId = row.get("book_id", Long::class.javaObjectType)!!,
                    slidesJson = row.get("slides", String::class.java)!!,
                    generatorName = row.get("generator_name", String::class.java)!!,
                    generatorVersion = row.get("generator_version", Int::class.javaObjectType)!!,
                    autoPublishable = row.get("auto_publishable", Boolean::class.javaObjectType)!!,
                    blockedReasons = row.get("blocked_reasons", Array<String>::class.java)!!.toList(),
                )
            }.all()
            .collectList()
            .awaitSingle()
}
