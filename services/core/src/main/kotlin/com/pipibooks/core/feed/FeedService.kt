package com.pipibooks.core.feed

import com.pipibooks.common.ApiException
import com.pipibooks.common.CursorCodec
import com.pipibooks.common.Tsid
import com.pipibooks.core.card.CardQuery
import com.pipibooks.core.card.CardView
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.time.Instant

/** 요청자. 둘 다 없을 수 있다(익명 ID 헤더 없는 비로그인). */
data class Viewer(
    val memberId: Long?,
    val anonymousId: String?,
)

data class FeedPage(
    val items: List<CardView>,
    val nextCursor: String?,
)

@Service
class FeedService(
    private val cards: CardQuery,
    private val ranker: FeedRanker,
    private val sessions: FeedSessionStore,
    private val seen: SeenCardReader,
    private val props: FeedProperties,
) {
    fun page(
        viewer: Viewer,
        cursor: String?,
    ): FeedPage {
        val fields = cursor?.let { CursorCodec.decode(it) }
        val offset = fields?.let { parseOffset(it["o"]) } ?: 0
        val existing = fields?.get("s")?.let { id -> sessions.load(id)?.let { id to it } }
        // 세션이 없거나 만료되었으면 오류 없이 새 세션으로 처음부터 시작한다.
        val (sessionId, list, start) =
            if (existing != null) Triple(existing.first, existing.second, offset) else newSession(viewer)

        val ids = list.drop(start).take(props.pageSize)
        val next = if (start + props.pageSize < list.size) cursorFor(sessionId, start + props.pageSize) else null
        return FeedPage(cards.publishedViews(ids), next)
    }

    private fun newSession(viewer: Viewer): Triple<String, List<Long>, Int> {
        val sessionId = Tsid.format(Tsid.next())
        val excluded = viewer.anonymousId?.let { seen.seenCardIds(it) }.orEmpty()
        val candidates = cards.candidates(props.candidateLimit).filter { it.cardId !in excluded }
        val ctx = RankContext(emptySet(), emptyMap(), emptyMap(), Instant.now())
        val list = ranker.rank(candidates, ctx, sessionId.toLong()).take(props.sessionSize)
        sessions.save(sessionId, list, props.sessionTtl)
        return Triple(sessionId, list, 0)
    }

    private fun parseOffset(raw: String?): Int =
        raw?.toIntOrNull()?.takeIf { it >= 0 }
            ?: throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_CURSOR", "잘못된 커서")

    private fun cursorFor(
        sessionId: String,
        offset: Int,
    ) = CursorCodec.encode(mapOf("s" to sessionId, "o" to offset.toString()))
}
