package com.pipibooks.catalog.internal

import com.pipibooks.catalog.book.BookIngestor
import com.pipibooks.catalog.book.BookRepository
import com.pipibooks.catalog.collect.CollectRequest
import com.pipibooks.catalog.collect.CollectRun
import com.pipibooks.catalog.collect.CollectRunRepository
import com.pipibooks.catalog.collect.CollectService
import com.pipibooks.catalog.collect.CollectType
import com.pipibooks.catalog.nlk.BookNormalizer
import com.pipibooks.catalog.nlk.NlkClient
import com.pipibooks.catalog.nlk.NlkException
import com.pipibooks.common.ApiException
import com.pipibooks.common.Tsid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

data class ImportRequest(
    val isbn: String,
)

data class BookIdResponse(
    val bookId: String,
)

data class RunIdResponse(
    val runId: String,
)

data class SearchItem(
    val isbn13: String,
    val title: String,
    val author: String?,
    val publisher: String?,
    val coverUrl: String?,
)

data class SearchResponse(
    val items: List<SearchItem>,
    val totalCount: Int,
)

data class CollectRunResponse(
    val id: String,
    val type: String,
    val params: String,
    val status: String,
    val fetchedCount: Int,
    val savedCount: Int,
    val draftCount: Int,
    val error: String?,
    val startedAt: String,
    val finishedAt: String?,
)

/** `internal` 네트워크 전용 API. 사용자 대기 시간이 걸린 요청이라 국립중앙도서관 호출은 `Priority.USER`다. */
@RestController
@RequestMapping("/internal")
class InternalBookController(
    private val books: BookRepository,
    private val client: NlkClient,
    private val ingestor: BookIngestor,
    private val collect: CollectService,
    private val runs: CollectRunRepository,
) {
    @GetMapping("/books/search")
    suspend fun search(
        @RequestParam q: String,
        @RequestParam(defaultValue = "1") page: Int,
    ): SearchResponse {
        if (q.isBlank() || page < 1) throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "검색어와 페이지가 필요합니다.")
        val result = unavailableOnFailure { client.byTitle(q, page) }
        val items =
            result.records.mapNotNull(BookNormalizer::normalize).map {
                SearchItem(it.isbn13, it.title, it.author, it.publisher, it.coverUrl)
            }
        return SearchResponse(items, result.totalCount)
    }

    @PostMapping("/books/import")
    suspend fun import(
        @RequestBody request: ImportRequest,
    ): BookIdResponse {
        val isbn =
            BookNormalizer.isbn13(request.isbn)
                ?: throw invalidIsbn()
        books.findByIsbn(isbn)?.let { return BookIdResponse(Tsid.format(it.id)) }
        val record = unavailableOnFailure { client.byIsbn(isbn) }
        val bookId =
            record?.let { ingestor.ingest(it) }
                ?: throw ApiException(HttpStatus.NOT_FOUND, "BOOK_NOT_FOUND", "도서를 찾을 수 없습니다.")
        return BookIdResponse(Tsid.format(bookId))
    }

    @PostMapping("/collect-runs")
    suspend fun startRun(
        @RequestBody request: CollectRequest,
    ): ResponseEntity<RunIdResponse> {
        val normalized = request.isbns?.map { BookNormalizer.isbn13(it) ?: throw invalidIsbn() }
        val invalid =
            (request.type == CollectType.SEED_ISBN && request.isbns.isNullOrEmpty()) ||
                (request.type == CollectType.SEED_KEYWORD && request.keyword.isNullOrBlank())
        if (invalid) throw ApiException(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "시드 수집에는 isbns 또는 keyword가 필요합니다.")
        val runId = collect.launch(request.copy(isbns = normalized))
        return ResponseEntity.accepted().body(RunIdResponse(Tsid.format(runId)))
    }

    @GetMapping("/collect-runs")
    suspend fun listRuns(
        @RequestParam(defaultValue = "20") size: Int,
    ): List<CollectRunResponse> = runs.recent(size.coerceIn(1, MAX_RUNS)).map { it.toResponse() }

    private fun invalidIsbn() = ApiException(HttpStatus.BAD_REQUEST, "INVALID_ISBN", "올바르지 않은 ISBN입니다.")

    private suspend fun <T> unavailableOnFailure(block: suspend () -> T): T =
        try {
            block()
        } catch (e: NlkException) {
            val message = "도서 정보를 가져올 수 없습니다."
            throw ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CATALOG_UNAVAILABLE", message).also { it.initCause(e) }
        }

    private fun CollectRun.toResponse() =
        CollectRunResponse(
            Tsid.format(id),
            type,
            params,
            status,
            fetchedCount,
            savedCount,
            draftCount,
            error,
            startedAt.toString(),
            finishedAt?.toString(),
        )

    private companion object {
        const val MAX_RUNS = 50
    }
}
