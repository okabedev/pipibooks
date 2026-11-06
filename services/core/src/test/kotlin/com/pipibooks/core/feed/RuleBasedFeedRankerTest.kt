package com.pipibooks.core.feed

import com.pipibooks.core.card.CardCandidate
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class RuleBasedFeedRankerTest {
    private val now = Instant.parse("2026-11-06T00:00:00Z")
    private val empty = RankContext(emptySet(), emptyMap(), emptyMap(), now)

    private fun card(
        id: Long,
        daysAgo: Long = 0,
        kdc: Int? = null,
        bookId: Long = id,
    ) = CardCandidate(id, bookId, kdc, now.minusSeconds(daysAgo * 86_400))

    private fun ranker(weights: FeedProperties.Weights = FeedProperties.Weights()): RuleBasedFeedRanker =
        RuleBasedFeedRanker(FeedProperties(weights = weights))

    @Test
    fun `같은 시드면 같은 순서다`() {
        val cards = (1L..30L).map { card(it) }

        assertEquals(ranker().rank(cards, empty, 42), ranker().rank(cards, empty, 42))
    }

    @Test
    fun `시드가 다르면 순서가 달라질 수 있다`() {
        val cards = (1L..30L).map { card(it) }

        assertTrue(ranker().rank(cards, empty, 1) != ranker().rank(cards, empty, 2))
    }

    @Test
    fun `다른 조건이 같으면 최근 발행 카드가 앞선다`() {
        val noRandom = FeedProperties.Weights(random = 0.0)
        val cards = listOf(card(1, daysAgo = 30), card(2, daysAgo = 1), card(3, daysAgo = 10))

        assertEquals(listOf(2L, 3L, 1L), ranker(noRandom).rank(cards, empty, 7))
    }

    @Test
    fun `관심 KDC 카드가 가점을 받는다`() {
        val noRandom = FeedProperties.Weights(random = 0.0)
        val cards = listOf(card(1, kdc = 8), card(2, kdc = 3))
        val ctx = empty.copy(interestKdc = setOf(3))

        assertEquals(listOf(2L, 1L), ranker(noRandom).rank(cards, ctx, 7))
    }

    @Test
    fun `서재 KDC 비율이 높은 카드가 가점을 받는다`() {
        val noRandom = FeedProperties.Weights(random = 0.0)
        val cards = listOf(card(1, kdc = 8), card(2, kdc = 3))
        val ctx = empty.copy(shelfKdcRatio = mapOf(8 to 0.9, 3 to 0.1))

        assertEquals(listOf(1L, 2L), ranker(noRandom).rank(cards, ctx, 7))
    }

    @Test
    fun `인기 책이 가점을 받는다`() {
        val noRandom = FeedProperties.Weights(random = 0.0)
        val cards = listOf(card(1), card(2))
        val ctx = empty.copy(popularity = mapOf(2L to 9, 1L to 1))

        assertEquals(listOf(2L, 1L), ranker(noRandom).rank(cards, ctx, 7))
    }

    @Test
    fun `반감기 14일이 지나면 신선도 점수가 절반이다`() {
        val r = ranker()

        assertEquals(1.0, r.freshness(card(1), now), 1e-9)
        assertEquals(0.5, r.freshness(card(1, daysAgo = 14), now), 1e-9)
        assertEquals(0.25, r.freshness(card(1, daysAgo = 28), now), 1e-9)
    }
}
