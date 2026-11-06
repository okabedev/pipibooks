package com.pipibooks.core.card

import com.pipibooks.common.slide.SlidePayload
import java.time.Instant

/** 피드 후보 카드. [kdcMajor]는 책의 KDC 대분류(0~9), 모르면 null. */
data class CardCandidate(
    val cardId: Long,
    val bookId: Long,
    val kdcMajor: Int?,
    val publishedAt: Instant,
)

/** 카드 조회 응답. [id]는 TSID 10진수 문자열, [isbn13]은 위시 담기에 쓴다. */
data class CardView(
    val id: String,
    val isbn13: String,
    val title: String,
    val author: String?,
    val slides: List<SlidePayload>,
)
