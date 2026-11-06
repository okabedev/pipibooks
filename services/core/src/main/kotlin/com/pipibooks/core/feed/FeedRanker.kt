package com.pipibooks.core.feed

import com.pipibooks.core.card.CardCandidate
import java.time.Instant

/**
 * 순위 계산에 쓰는 사용자·시점 정보. 비로그인은 [interestKdc]·[shelfKdcRatio]가 비어 있다.
 * [popularity]의 키는 bookId, 값은 최근 7일 서재 추가 수다.
 */
data class RankContext(
    val interestKdc: Set<Int>,
    val shelfKdcRatio: Map<Int, Double>,
    val popularity: Map<Long, Int>,
    val now: Instant,
)

/** 후보를 점수 순으로 정렬한다. 본격 추천 단계에서 구현을 교체한다. */
interface FeedRanker {
    /** 카드 ID를 순서대로 돌려 준다. 같은 [seed]면 같은 순서다. */
    fun rank(
        candidates: List<CardCandidate>,
        ctx: RankContext,
        seed: Long,
    ): List<Long>
}
