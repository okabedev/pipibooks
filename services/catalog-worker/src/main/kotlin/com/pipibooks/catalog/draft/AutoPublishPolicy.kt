package com.pipibooks.catalog.draft

import com.pipibooks.catalog.book.Book

enum class BlockedReason { SHORT_INTRODUCTION, NOT_PRINT_SINGLE, STUDY_REFERENCE, NO_COVER }

/** 자동 발행 기준(ADR 0011). 걸린 사유가 없으면 자동 발행할 수 있다. */
object AutoPublishPolicy {
    private const val MIN_INTRODUCTION = 100
    private const val PRINT_FORM = "종이책"
    private val studyReferenceDigits = setOf('5', '6')

    fun evaluate(book: Book): List<BlockedReason> =
        buildList {
            if ((book.introduction?.trim()?.length ?: 0) < MIN_INTRODUCTION) add(BlockedReason.SHORT_INTRODUCTION)
            if (!isPrintSingle(book)) add(BlockedReason.NOT_PRINT_SINGLE)
            if (book.eaAddCode?.firstOrNull() in studyReferenceDigits) add(BlockedReason.STUDY_REFERENCE)
            if (book.coverUrl == null) add(BlockedReason.NO_COVER)
        }

    /** 전자책이거나 세트 ISBN이 있거나 형태가 종이책이 아니면(비어 있으면 통과) 종이책 단권이 아니다. */
    private fun isPrintSingle(book: Book): Boolean {
        val form = book.form?.trim().orEmpty()
        return book.ebookYn != true && book.setIsbn == null && (form.isEmpty() || form == PRINT_FORM)
    }
}
