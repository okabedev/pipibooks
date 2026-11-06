package com.pipibooks.eventschema

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class EventSchemaTest {
    private val full =
        UserEventMessage(
            eventId = UUID.fromString("0190a1b2-0000-7000-8000-000000000001"),
            type = EventType.CARD_DWELL,
            memberId = 7L,
            anonymousId = "anon",
            sessionId = "s1",
            cardId = 11L,
            bookId = 12L,
            slideIndex = 2,
            dwellMs = 3500L,
            platform = "web",
            appVersion = "0.1.0",
            occurredAt = Instant.parse("2026-11-06T01:02:03.456Z"),
            receivedAt = Instant.parse("2026-11-06T01:02:04Z"),
        )

    @Test
    fun `모든 필드가 있는 메시지는 필드 맵을 거쳐 그대로 복원된다`() {
        assertEquals(full, UserEventMessage.fromFields(full.toFields()))
    }

    @Test
    fun `null 필드는 필드 맵에서 빠지고 복원하면 null이다`() {
        val sparse = full.copy(memberId = null, cardId = null, dwellMs = null, platform = null)

        val fields = sparse.toFields()

        assertFalse(fields.containsKey("memberId"))
        assertFalse(fields.containsKey("cardId"))
        assertEquals(sparse, UserEventMessage.fromFields(fields))
    }

    @Test
    fun `형식이 틀린 필드는 IllegalArgumentException이다`() {
        val ok = full.toFields()
        listOf(
            "occurredAt" to "어제",
            "receivedAt" to "2026-13-45",
            "cardId" to "abc",
            "slideIndex" to "1.5",
            "memberId" to "",
            "eventId" to "not-a-uuid",
            "type" to "UNKNOWN",
        ).forEach { (k, v) ->
            assertThrows<IllegalArgumentException>(k) { UserEventMessage.fromFields(ok + (k to v)) }
        }
    }

    @Test
    fun `필수 필드가 없으면 IllegalArgumentException이다`() {
        assertThrows<IllegalArgumentException> { UserEventMessage.fromFields(full.toFields() - "type") }
    }

    @Test
    fun `Redis 키 값`() {
        assertEquals("events:user", RedisKeys.EVENTS_STREAM)
        assertEquals("events:dead", RedisKeys.DEAD_STREAM)
        assertEquals("collector", RedisKeys.CONSUMER_GROUP)
        assertEquals("feed:seen:abc", RedisKeys.seenCards("abc"))
        assertEquals("card:counters:2026-11-06", RedisKeys.cardCounters(LocalDate.of(2026, 11, 6)))
        assertEquals("feed:session:99", RedisKeys.feedSession("99"))
        assertEquals(
            "search:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad:2",
            RedisKeys.searchCache("abc", 2),
        )
    }
}
