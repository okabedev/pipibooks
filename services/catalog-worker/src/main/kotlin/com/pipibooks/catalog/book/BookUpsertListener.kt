package com.pipibooks.catalog.book

import org.springframework.stereotype.Component

/** 도서를 저장한 직후 호출되는 훅. 카드 초안 생성이 구현한다. */
interface BookUpsertListener {
    suspend fun onUpsert(
        book: Book,
        result: UpsertResult,
    )
}

/** 기본 빈. 실제 구현(카드 초안 생성)이 생기면 이 클래스를 지우고 대체한다. */
@Component
class NoOpBookUpsertListener : BookUpsertListener {
    override suspend fun onUpsert(
        book: Book,
        result: UpsertResult,
    ) = Unit
}
