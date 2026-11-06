package com.pipibooks.catalog.book

import com.pipibooks.catalog.nlk.BookNormalizer
import com.pipibooks.catalog.nlk.CoverChecker
import com.pipibooks.catalog.nlk.NlkRecord
import org.springframework.stereotype.Component

/** 국립중앙도서관 레코드 하나를 정규화·저장하고 표지 확인과 [BookUpsertListener] 호출까지 마친다. */
@Component
class BookIngestor(
    private val books: BookRepository,
    private val covers: CoverChecker,
    private val listener: BookUpsertListener,
) {
    /** 저장한 도서 ID. ISBN 검증에 실패해 버린 레코드는 `null`. */
    suspend fun ingest(record: NlkRecord): Long? {
        val normalized = BookNormalizer.normalize(record) ?: return null
        val result = books.upsert(normalized)
        val cover = normalized.coverUrl
        if (result.coverUrlChanged && cover != null && !covers.verify(cover)) books.clearCover(result.bookId)
        books.findByIsbn(normalized.isbn13)?.let { listener.onUpsert(it, result) }
        return result.bookId
    }
}
