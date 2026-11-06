package com.pipibooks.eventschema

import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDate

/** 서비스들이 공유하는 Redis 키. 키 이름은 여기에만 정의한다. */
object RedisKeys {
    const val EVENTS_STREAM = "events:user"
    const val DEAD_STREAM = "events:dead"
    const val CONSUMER_GROUP = "collector"

    /**
     * 본 카드 윈도우. [seenCards]는 ZSET이다(멤버 = 카드 ID 10진수, 점수 = 노출 시각 epoch millis).
     *
     * 쓰는 쪽(event-collector, `CARD_IMPRESSION`만): `ZADD`(점수 = occurredAt ms),
     * `ZREMRANGEBYSCORE -inf (now - SEEN_CARDS_WINDOW)`, `EXPIRE SEEN_CARDS_WINDOW`.
     * 읽는 쪽(core): `ZRANGEBYSCORE (now - SEEN_CARDS_WINDOW) +inf`.
     */
    val SEEN_CARDS_WINDOW: Duration = Duration.ofDays(7)

    fun seenCards(anonymousId: String) = "feed:seen:$anonymousId"

    /** 일별 카드 카운터 해시. 필드는 `"$cardId:imp"`, `"$cardId:skip"`, `"$cardId:dwell"`. */
    fun cardCounters(day: LocalDate) = "card:counters:$day"

    fun feedSession(sessionId: String) = "feed:session:$sessionId"

    fun searchCache(
        q: String,
        page: Int,
    ) = "search:${sha256(q)}:$page"

    private fun sha256(s: String) =
        MessageDigest
            .getInstance("SHA-256")
            .digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
