package com.pipibooks.eventschema

import java.time.Instant
import java.time.format.DateTimeParseException
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

            fun <T : Any> parse(
                k: String,
                block: (String) -> T,
            ): T =
                try {
                    block(req(k))
                } catch (e: IllegalArgumentException) {
                    throw e
                } catch (e: DateTimeParseException) {
                    throw IllegalArgumentException("형식이 틀린 필드: $k", e)
                }

            fun <T : Any> optional(
                k: String,
                block: (String) -> T,
            ): T? = if (f.containsKey(k)) parse(k, block) else null
            return UserEventMessage(
                eventId = parse("eventId", UUID::fromString),
                type = parse("type", EventType::valueOf),
                memberId = optional("memberId", String::toLong),
                anonymousId = f["anonymousId"],
                sessionId = f["sessionId"],
                cardId = optional("cardId", String::toLong),
                bookId = optional("bookId", String::toLong),
                slideIndex = optional("slideIndex", String::toInt),
                dwellMs = optional("dwellMs", String::toLong),
                platform = f["platform"],
                appVersion = f["appVersion"],
                occurredAt = parse("occurredAt", Instant::parse),
                receivedAt = parse("receivedAt", Instant::parse),
            )
        }
    }
}
