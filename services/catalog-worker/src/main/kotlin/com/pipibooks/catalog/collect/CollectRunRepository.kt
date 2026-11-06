package com.pipibooks.catalog.collect

import com.pipibooks.catalog.bindNullable
import com.pipibooks.common.Tsid
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository
import java.time.Clock
import java.time.OffsetDateTime

data class CollectRun(
    val id: Long,
    val type: String,
    val params: String,
    val status: String,
    val fetchedCount: Int,
    val savedCount: Int,
    val draftCount: Int,
    val error: String?,
    val startedAt: java.time.Instant,
    val finishedAt: java.time.Instant?,
)

@Repository
class CollectRunRepository(
    private val db: DatabaseClient,
    private val clock: Clock,
) {
    suspend fun start(
        type: CollectType,
        paramsJson: String,
    ): Long {
        val id = Tsid.next()
        db
            .sql(
                "INSERT INTO catalog.collect_run (id, type, params, status, started_at) " +
                    "VALUES (:id, :type, CAST(:params AS jsonb), 'RUNNING', :startedAt)",
            ).bind("id", id)
            .bind("type", type.name)
            .bind("params", paramsJson)
            .bind("startedAt", now())
            .fetch()
            .rowsUpdated()
            .awaitSingle()
        return id
    }

    suspend fun finish(
        id: Long,
        status: String,
        fetched: Int,
        saved: Int,
        error: String?,
    ) {
        db
            .sql(
                "UPDATE catalog.collect_run SET status = :status, fetched_count = :fetched, saved_count = :saved, " +
                    "error = :error, finished_at = :finishedAt WHERE id = :id",
            ).bind("id", id)
            .bind("status", status)
            .bind("fetched", fetched)
            .bind("saved", saved)
            .bindNullable("error", error)
            .bind("finishedAt", now())
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    suspend fun recent(size: Int): List<CollectRun> =
        db
            .sql(
                "SELECT id, type, params::text AS params, status, fetched_count, saved_count, draft_count, error, " +
                    "started_at, finished_at FROM catalog.collect_run ORDER BY started_at DESC, id DESC LIMIT :size",
            ).bind("size", size)
            .map { row ->
                CollectRun(
                    id = row.get("id", Long::class.javaObjectType)!!,
                    type = row.get("type", String::class.java)!!,
                    params = row.get("params", String::class.java)!!,
                    status = row.get("status", String::class.java)!!,
                    fetchedCount = row.get("fetched_count", Int::class.javaObjectType)!!,
                    savedCount = row.get("saved_count", Int::class.javaObjectType)!!,
                    draftCount = row.get("draft_count", Int::class.javaObjectType)!!,
                    error = row.get("error", String::class.java),
                    startedAt = row.get("started_at", OffsetDateTime::class.java)!!.toInstant(),
                    finishedAt = row.get("finished_at", OffsetDateTime::class.java)?.toInstant(),
                )
            }.all()
            .collectList()
            .awaitSingle()

    private fun now() = OffsetDateTime.ofInstant(clock.instant(), clock.zone)
}
