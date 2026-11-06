package com.pipibooks.catalog.book

import com.pipibooks.catalog.nlk.BookNormalizer
import com.pipibooks.catalog.nlk.CoverChecker
import com.pipibooks.catalog.nlk.NlkRecord
import kotlinx.coroutines.CancellationException
import org.springframework.stereotype.Component

/** [BookIngestor.ingest]의 결과. [draftCreated]는 이번 저장으로 카드 초안이 만들어졌는지다. */
data class Ingested(
    val bookId: Long,
    val draftCreated: Boolean,
)

/** 도서는 저장됐지만 [BookUpsertListener]가 실패했다. 호출하는 쪽이 저장 건수에 셀 수 있게 도서 ID를 담는다. */
class ListenerFailedException(
    val bookId: Long,
    cause: Throwable,
) : RuntimeException("도서 $bookId 저장 후 후처리 실패: ${cause.message}", cause)

/** 국립중앙도서관 레코드 하나를 정규화·저장하고 표지 확인과 [BookUpsertListener] 호출까지 마친다. */
@Component
class BookIngestor(
    private val books: BookRepository,
    private val covers: CoverChecker,
    private val listener: BookUpsertListener,
) {
    /** 저장한 도서와 초안 생성 여부. ISBN 검증에 실패해 버린 레코드는 `null`. */
    suspend fun ingest(record: NlkRecord): Ingested? {
        val normalized = BookNormalizer.normalize(record) ?: return null
        val result = books.upsert(normalized)
        val cover = normalized.coverUrl
        if (cover != null && result.coverUrlChanged && covers.verify(cover)) {
            books.setVerifiedCover(result.bookId, cover)
        }
        val draftCreated =
            try {
                books.findByIsbn(normalized.isbn13)?.let { listener.onUpsert(it, result) } ?: false
            } catch (e: CancellationException) {
                throw e
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                throw ListenerFailedException(result.bookId, e)
            }
        return Ingested(result.bookId, draftCreated)
    }
}
