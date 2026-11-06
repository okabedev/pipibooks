package com.pipibooks.core.card

import org.springframework.jdbc.core.simple.JdbcClient
import org.springframework.stereotype.Component

/** `core.sync_cursor`: 동기화 작업별로 마지막으로 처리한 원본 ID를 저장한다. */
@Component
class SyncCursorStore(
    private val jdbc: JdbcClient,
) {
    fun get(name: String): Long =
        jdbc
            .sql("SELECT last_id FROM core.sync_cursor WHERE name = :name")
            .param("name", name)
            .query(Long::class.java)
            .optional()
            .orElse(0L)

    fun set(
        name: String,
        lastId: Long,
    ) {
        jdbc
            .sql(
                "INSERT INTO core.sync_cursor (name, last_id) VALUES (:name, :lastId) " +
                    "ON CONFLICT (name) DO UPDATE SET last_id = EXCLUDED.last_id",
            ).param("name", name)
            .param("lastId", lastId)
            .update()
    }
}
