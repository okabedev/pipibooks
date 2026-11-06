package com.pipibooks.catalog.draft

import com.pipibooks.catalog.book.Book
import com.pipibooks.catalog.book.BookUpsertListener
import com.pipibooks.catalog.book.UpsertResult
import com.pipibooks.common.slide.SlidePayload
import org.springframework.stereotype.Component
import tools.jackson.core.type.TypeReference
import tools.jackson.databind.json.JsonMapper

/**
 * 도서 저장 직후 카드 초안을 만들고 자동 발행 여부를 판정해 저장한다.
 * 도서가 새로 생겼거나, 카드 필드가 바뀌었거나, 현재 생성기 이름·버전의 초안이 없을 때만 만든다
 * (마지막 조건이 생성기 버전 상승과 이전 실패 복구를 함께 맡는다).
 */
@Component
class DraftOnUpsertListener(
    private val generator: CardDraftGenerator,
    private val drafts: CardDraftRepository,
) : BookUpsertListener {
    private val slideList = object : TypeReference<List<@JvmSuppressWildcards SlidePayload>>() {}
    private val writer = JsonMapper.builder().build().writerFor(slideList)

    override suspend fun onUpsert(
        book: Book,
        result: UpsertResult,
    ): Boolean {
        val needed =
            result.created ||
                result.cardFieldsChanged ||
                !drafts.exists(book.id, generator.name, generator.version)
        val slides = if (needed) generator.generate(book) else null
        if (slides != null) {
            drafts.insert(
                book.id,
                writer.writeValueAsString(slides),
                generator.name,
                generator.version,
                AutoPublishPolicy.evaluate(book),
            )
        }
        return slides != null
    }
}
