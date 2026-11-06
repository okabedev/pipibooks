package com.pipibooks.core.feed

import com.pipibooks.eventschema.RedisKeys
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Clock

/**
 * 최근 7일 안에 화면에 표시된(`CARD_IMPRESSION`) 카드. event-collector가 ZSET에 쓰고 core가 읽는다.
 * 응답만 하고 표시되지 않은 카드는 들어 있지 않다.
 */
@Component
class SeenCardReader(
    private val redis: StringRedisTemplate,
    private val clock: Clock,
) {
    fun seenCardIds(anonymousId: String): Set<Long> {
        val since = clock.millis() - RedisKeys.SEEN_CARDS_WINDOW.toMillis()
        val key = RedisKeys.seenCards(anonymousId)
        val members = redis.opsForZSet().rangeByScore(key, since.toDouble(), Double.POSITIVE_INFINITY)
        return members.orEmpty().mapNotNullTo(HashSet()) { it.toLongOrNull() }
    }
}
