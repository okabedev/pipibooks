package com.pipibooks.core.feed

import com.pipibooks.core.card.CardCandidate
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import kotlin.math.ln
import kotlin.math.pow
import kotlin.random.Random

/** 점수 = Σ 가중치 × 항(관심, 서재, 인기도, 신선도, 무작위). */
@Component
class RuleBasedFeedRanker(
    private val props: FeedProperties,
) : FeedRanker {
    override fun rank(
        candidates: List<CardCandidate>,
        ctx: RankContext,
        seed: Long,
    ): List<Long> {
        val w = props.weights
        val maxPopularity = ctx.popularity.values.maxOrNull() ?: 0
        return candidates
            .map { c ->
                val interest = if (c.kdcMajor != null && c.kdcMajor in ctx.interestKdc) 1.0 else 0.0
                val shelf = c.kdcMajor?.let { ctx.shelfKdcRatio[it] } ?: 0.0
                val score =
                    w.interest * interest +
                        w.shelf * shelf +
                        w.popularity * popularity(ctx.popularity[c.bookId] ?: 0, maxPopularity) +
                        w.freshness * freshness(c, ctx.now) +
                        w.random * Random(seed xor c.cardId).nextDouble()
                c.cardId to score
            }.sortedWith(compareByDescending<Pair<Long, Double>> { it.second }.thenBy { it.first })
            .map { it.first }
    }

    /** `0.5^(경과일/반감기)`. 발행 시각이 미래면 1.0. */
    internal fun freshness(
        c: CardCandidate,
        now: Instant,
    ): Double {
        val days = Duration.between(c.publishedAt, now).toMillis().coerceAtLeast(0) / MILLIS_PER_DAY
        return 0.5.pow(days / props.freshnessHalfLifeDays)
    }

    private fun popularity(
        n: Int,
        max: Int,
    ): Double = if (max <= 0) 0.0 else ln(1.0 + n) / ln(1.0 + max)

    private companion object {
        const val MILLIS_PER_DAY = 86_400_000.0
    }
}
