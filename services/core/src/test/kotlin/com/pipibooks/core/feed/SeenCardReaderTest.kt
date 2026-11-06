package com.pipibooks.core.feed

import com.pipibooks.core.support.DbTestSupport
import com.pipibooks.eventschema.RedisKeys
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@SpringBootTest
@Import(SeenCardReaderTest.FixedClock::class)
class SeenCardReaderTest(
    @Autowired private val reader: SeenCardReader,
    @Autowired private val redis: StringRedisTemplate,
) : DbTestSupport() {
    @TestConfiguration
    class FixedClock {
        @Bean
        @Primary
        fun fixedClock(): Clock = Clock.fixed(NOW, ZoneOffset.UTC)
    }

    @Test
    fun `7일 윈도우 안의 카드만 읽는다`() {
        val anon = UUID.randomUUID().toString()
        val window = RedisKeys.SEEN_CARDS_WINDOW.toMillis()

        fun add(
            id: Long,
            at: Long,
        ) = redis.opsForZSet().add(RedisKeys.seenCards(anon), id.toString(), at.toDouble())
        add(1, NOW.toEpochMilli() - window - 1)
        add(2, NOW.toEpochMilli() - window + 1)
        add(3, NOW.toEpochMilli())

        assertEquals(setOf(2L, 3L), reader.seenCardIds(anon))
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-11-06T00:00:00Z")
    }
}
