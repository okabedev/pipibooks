package com.pipibooks.eventschema

import java.time.Instant
import java.util.UUID

/** Redis Stream `events:user`에 싣는 사용자 이벤트. */
data class UserEventMessage(
    val eventId: UUID,
    val type: EventType,
    val memberId: Long?,
    val anonymousId: String?,
    val sessionId: String?,
    val cardId: Long?,
    val bookId: Long?,
    val slideIndex: Int?,
    val dwellMs: Long?,
    val platform: String?,
    val appVersion: String?,
    val occurredAt: Instant,
    val receivedAt: Instant,
) {
    /** Stream 필드 맵. 값이 null인 필드는 넣지 않는다. 시각은 ISO-8601 문자열이다. */
    fun toFields(): Map<String, String> =
        listOf(
            "eventId" to eventId,
            "type" to type,
            "memberId" to memberId,
            "anonymousId" to anonymousId,
            "sessionId" to sessionId,
            "cardId" to cardId,
            "bookId" to bookId,
            "slideIndex" to slideIndex,
            "dwellMs" to dwellMs,
            "platform" to platform,
            "appVersion" to appVersion,
            "occurredAt" to occurredAt,
            "receivedAt" to receivedAt,
        ).mapNotNull { (k, v) -> v?.let { k to it.toString() } }.toMap()

    companion object {
        /** [toFields]의 역변환. 필수 필드가 없거나 형식이 틀리면 [IllegalArgumentException]. */
        fun fromFields(f: Map<String, String>): UserEventMessage {
            fun req(k: String) = requireNotNull(f[k]) { "필수 필드 없음: $k" }
            return UserEventMessage(
                eventId = UUID.fromString(req("eventId")),
                type = EventType.valueOf(req("type")),
                memberId = f["memberId"]?.toLong(),
                anonymousId = f["anonymousId"],
                sessionId = f["sessionId"],
                cardId = f["cardId"]?.toLong(),
                bookId = f["bookId"]?.toLong(),
                slideIndex = f["slideIndex"]?.toInt(),
                dwellMs = f["dwellMs"]?.toLong(),
                platform = f["platform"],
                appVersion = f["appVersion"],
                occurredAt = Instant.parse(req("occurredAt")),
                receivedAt = Instant.parse(req("receivedAt")),
            )
        }
    }
}
