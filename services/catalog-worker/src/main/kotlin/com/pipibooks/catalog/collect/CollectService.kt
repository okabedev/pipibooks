package com.pipibooks.catalog.collect

import com.pipibooks.catalog.book.BookIngestor
import com.pipibooks.catalog.nlk.NlkAuthException
import com.pipibooks.catalog.nlk.NlkClient
import com.pipibooks.catalog.nlk.NlkPage
import com.pipibooks.catalog.nlk.NlkProperties
import com.pipibooks.catalog.nlk.Priority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.json.JsonMapper
import java.time.Clock
import java.time.LocalDate

enum class CollectType { NEW_BOOKS, SEED_ISBN, SEED_KEYWORD }

data class CollectRequest(
    val type: CollectType,
    val isbns: List<String>? = null,
    val keyword: String? = null,
)

/** 신간·시드 수집. 스케줄러와 내부 API가 함께 쓴다. 호출은 모두 `Priority.BATCH`다. */
@Service
class CollectService(
    private val client: NlkClient,
    private val ingestor: BookIngestor,
    private val runs: CollectRunRepository,
    private val props: CollectProperties,
    private val nlk: NlkProperties,
    private val clock: Clock,
    private val scope: ApplicationScope,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val mapper = JsonMapper.builder().build()

    /** 실행 기록을 `RUNNING`으로 만들고 백그라운드에서 수집한다. 끝나면 [onFinished]에 성공 여부를 알린다. */
    suspend fun launch(
        request: CollectRequest,
        onFinished: suspend (Boolean) -> Unit = {},
    ): Long {
        val runId = start(request)
        scope.launch { onFinished(execute(runId, request)) }
        return runId
    }

    /** 기록을 만들고 끝날 때까지 실행한다(테스트용). */
    suspend fun runNow(request: CollectRequest): Long = start(request).also { execute(it, request) }

    private suspend fun start(request: CollectRequest): Long {
        val params = mapper.writeValueAsString(paramsOf(request))
        return runs.start(request.type, params)
    }

    private fun paramsOf(request: CollectRequest): Map<String, Any?> =
        when (request.type) {
            CollectType.NEW_BOOKS -> window().let { mapOf("from" to "${it.first()}", "to" to "${it.last()}") }
            CollectType.SEED_ISBN -> mapOf("isbns" to request.isbns)
            CollectType.SEED_KEYWORD -> mapOf("keyword" to request.keyword)
        }

    /** 오늘(KST)과 그 전 `window-days - 1`일, 오래된 날부터. */
    private fun window(): List<LocalDate> {
        val today = LocalDate.now(clock)
        return (props.windowDays - 1 downTo 0).map { today.minusDays(it.toLong()) }
    }

    private class Tally {
        var fetched = 0
        var saved = 0
        val failures = mutableListOf<String>()
    }

    private suspend fun execute(
        runId: Long,
        request: CollectRequest,
    ): Boolean {
        val tally = Tally()
        try {
            runType(request, tally)
        } catch (e: NlkAuthException) {
            tally.failures += "인증 실패로 중단: ${e.message}"
        }
        val ok = tally.failures.isEmpty()
        runs.finish(
            runId,
            if (ok) "SUCCEEDED" else "FAILED",
            tally.fetched,
            tally.saved,
            tally.failures.joinToString("; ").ifEmpty { null },
        )
        log.info(
            "수집 종료 runId={} type={} ok={} fetched={} saved={}",
            runId,
            request.type,
            ok,
            tally.fetched,
            tally.saved,
        )
        return ok
    }

    private suspend fun runType(
        request: CollectRequest,
        tally: Tally,
    ) {
        when (request.type) {
            CollectType.NEW_BOOKS -> window().forEach { day -> guarded(tally, "$day") { collectDay(day, tally) } }
            CollectType.SEED_ISBN ->
                request.isbns.orEmpty().forEach { isbn ->
                    guarded(tally, isbn) { seedIsbn(isbn, tally) }
                }
            CollectType.SEED_KEYWORD -> {
                val keyword = request.keyword.orEmpty()
                guarded(tally, keyword) {
                    forEachPage(tally, { page -> client.byTitle(keyword, page, Priority.BATCH) }, SEED_MAX_PAGES)
                }
            }
        }
    }

    private suspend fun collectDay(
        day: LocalDate,
        tally: Tally,
    ) {
        forEachPage(tally, { page -> client.byPublishDate(day, page, Priority.BATCH) }) { total ->
            if (total > DAILY_WARN) log.warn("{} 발행예정 도서가 {}건으로 500건을 넘는다. 일부가 누락될 수 있다", day, total)
        }
    }

    /** 한 단위(날짜·ISBN)가 실패해도 다음 단위를 계속한다. 인증 오류는 모두 같은 원인이라 위로 던진다. */
    private suspend fun guarded(
        tally: Tally,
        label: String,
        block: suspend () -> Unit,
    ) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: NlkAuthException) {
            throw e
        } catch (
            @Suppress("TooGenericExceptionCaught") e: Exception,
        ) {
            log.error("수집 실패: {}", label, e)
            tally.failures += "$label: ${e.message}"
        }
    }

    private suspend fun seedIsbn(
        isbn: String,
        tally: Tally,
    ) {
        val record = client.byIsbn(isbn, Priority.BATCH)
        if (record == null) {
            log.warn("국립중앙도서관에 없는 ISBN: {}", isbn)
            return
        }
        tally.fetched++
        if (ingestor.ingest(record) != null) tally.saved++
    }

    private suspend fun forEachPage(
        tally: Tally,
        fetch: suspend (Int) -> NlkPage,
        maxPages: Int = Int.MAX_VALUE,
        onFirst: (Int) -> Unit = {},
    ) {
        var page = 1
        while (page <= maxPages) {
            val result = fetch(page)
            if (page == 1) onFirst(result.totalCount)
            result.records.forEach { record ->
                tally.fetched++
                if (ingestor.ingest(record) != null) tally.saved++
            }
            if (result.records.isEmpty() || page * nlk.pageSize >= result.totalCount) return
            page++
        }
    }

    private companion object {
        const val DAILY_WARN = 500
        const val SEED_MAX_PAGES = 20
    }
}
