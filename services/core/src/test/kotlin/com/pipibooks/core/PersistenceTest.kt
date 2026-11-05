package com.pipibooks.core

import com.pipibooks.core.support.DbTestSupport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate

@SpringBootTest
class PersistenceTest(
    @Autowired private val jdbc: JdbcTemplate,
) : DbTestSupport() {
    @Test
    fun `서비스 계정으로 접속하고 자기 스키마에 마이그레이션한다`() {
        assertEquals("core_app", jdbc.queryForObject("SELECT current_user", String::class.java))
        val history =
            jdbc.queryForObject(
                "SELECT count(*) FROM information_schema.tables " +
                    "WHERE table_schema = 'core' AND table_name = 'flyway_schema_history'",
                Int::class.java,
            )
        assertEquals(1, history)
    }
}
