package com.pipibooks.core.feed

import com.pipibooks.eventschema.RedisKeys
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Component
import java.time.Duration

/** 피드 세션의 정렬된 카드 ID 목록을 Redis에 TTL로 보관한다. */
@Component
class FeedSessionStore(
    private val redis: StringRedisTemplate,
) {
    fun save(
        sessionId: String,
        cardIds: List<Long>,
        ttl: Duration,
    ) {
        redis.opsForValue().set(RedisKeys.feedSession(sessionId), cardIds.joinToString(","), ttl)
    }

    /** 없거나 만료된 세션이면 null. */
    fun load(sessionId: String): List<Long>? =
        redis
            .opsForValue()
            .get(RedisKeys.feedSession(sessionId))
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
}
