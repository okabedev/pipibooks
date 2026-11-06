package com.pipibooks.catalog.book

/** 도서를 저장한 직후 호출되는 훅. 카드 초안 생성이 구현한다. */
interface BookUpsertListener {
    /** 카드 초안을 만들었으면 `true`. */
    suspend fun onUpsert(
        book: Book,
        result: UpsertResult,
    ): Boolean
}
