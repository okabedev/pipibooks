package com.pipibooks.catalog

import com.pipibooks.catalog.support.DbTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.r2dbc.core.DatabaseClient

@SpringBootTest
class PersistenceTest(
    @Autowired private val db: DatabaseClient,
) : DbTestSupport() {
    @Test
    fun `서비스 계정으로 접속하고 자기 스키마에 마이그레이션한다`() {
        val user =
            db
                .sql("SELECT current_user")
                .map<String> { row -> row.get(0, String::class.java)!! }
                .one()
                .block()
        assertEquals("catalog_app", user)
        val history =
            db
                .sql(
                    "SELECT count(*)::int FROM information_schema.tables " +
                        "WHERE table_schema = 'catalog' AND table_name = 'flyway_schema_history'",
                ).map<Int> { row -> row.get(0, Int::class.javaObjectType)!! }
                .one()
                .block()
        assertEquals(1, history)
    }
}
