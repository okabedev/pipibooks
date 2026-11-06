package com.pipibooks.core.card

import com.pipibooks.common.ApiException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/cards")
class CardController(
    private val query: CardQuery,
) {
    /** 공개 API(공유 페이지용). 없거나 숫자가 아닌 ID, DRAFT·HIDDEN 카드는 모두 404. */
    @GetMapping("/{id}")
    fun get(
        @PathVariable id: String,
    ): CardView {
        val cardId = id.toLongOrNull() ?: throw notFound()
        return query.publishedViews(listOf(cardId)).firstOrNull() ?: throw notFound()
    }

    private fun notFound() = ApiException(HttpStatus.NOT_FOUND, "CARD_NOT_FOUND", "카드를 찾을 수 없다")
}
