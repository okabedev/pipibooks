package com.pipibooks.catalog.draft

import com.pipibooks.catalog.book.Book
import com.pipibooks.common.slide.SlidePayload

/** 도서에서 카드 슬라이드 묶음을 만든다. 같은 입력이면 같은 결과를 내야 한다. */
interface CardDraftGenerator {
    val name: String
    val version: Int

    /** 소개가 없어 카드를 만들 수 없으면 `null`. */
    fun generate(book: Book): List<SlidePayload>?
}
