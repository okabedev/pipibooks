package com.pipibooks.core.card

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.module.kotlin.jacksonMapperBuilder
import java.lang.reflect.Proxy
import java.sql.SQLException

class CardSyncUnavailableTest {
    private val noopTx =
        TransactionTemplate(
            object : PlatformTransactionManager {
                override fun getTransaction(definition: TransactionDefinition?) = SimpleTransactionStatus()

                override fun commit(status: org.springframework.transaction.TransactionStatus) = Unit

                override fun rollback(status: org.springframework.transaction.TransactionStatus) = Unit
            },
        )

    // 읽기 단계에서 실패하므로 다른 협력 객체는 호출되지 않는다. 호출되면 바로 실패하는 대역을 쓴다.
    private inline fun <reified T : Any> unused(): T =
        Proxy.newProxyInstance(T::class.java.classLoader, arrayOf(T::class.java)) { _, method, _ ->
            error("호출되면 안 된다: ${method.name}")
        } as T

    private fun service(error: RuntimeException) =
        CardService(
            reader = StubReader(error),
            cards = unused(),
            slides = unused(),
            cursor =
                object : SyncCursorStore {
                    override fun get(name: String) = 0L

                    override fun set(
                        name: String,
                        lastId: Long,
                    ) = error("호출되면 안 된다")
                },
            mapper = jacksonMapperBuilder().build(),
            tx = noopTx,
            em = unused(),
        )

    private class StubReader(
        private val error: RuntimeException,
    ) : CardDraftReader {
        override fun readAfter(
            lastId: Long,
            limit: Int,
        ): List<CardDraft> = throw error

        override fun find(draftId: Long): CardDraft? = throw error

        override fun findLatestByBook(bookId: Long): CardDraft? = throw error
    }

    @Test
    fun `catalog 테이블이 아직 없으면 0을 반환한다`() {
        val e = BadSqlGrammarException("read", "SELECT", SQLException("relation does not exist", "42P01"))
        assertEquals(0, service(e).syncDrafts())
    }

    @Test
    fun `다른 DB 오류는 그대로 던진다`() {
        val e = DataAccessResourceFailureException("down")
        org.junit.jupiter.api
            .assertThrows<DataAccessResourceFailureException> { service(e).syncDrafts() }
    }

    private class Recorder : SyncCursorStore {
        val sets = mutableListOf<Long>()

        override fun get(name: String) = 0L

        override fun set(
            name: String,
            lastId: Long,
        ) {
            sets += lastId
        }
    }

    // 초안 한 건을 읽고, 카드 저장소가 [failure]로 실패하는 서비스.
    private fun failingService(
        failure: RuntimeException,
        recorder: Recorder,
    ): CardService {
        val draft = CardDraft(7, 1, """[{"type":"HOOK","sentences":["한 줄"]}]""", true)
        val reader =
            object : CardDraftReader {
                override fun readAfter(
                    lastId: Long,
                    limit: Int,
                ) = if (lastId < draft.id) listOf(draft) else emptyList()

                override fun find(draftId: Long): CardDraft? = null

                override fun findLatestByBook(bookId: Long): CardDraft? = null
            }
        val cards =
            Proxy.newProxyInstance(
                CardRepository::class.java.classLoader,
                arrayOf(CardRepository::class.java),
            ) { _, _, _ -> throw failure } as CardRepository
        return CardService(reader, cards, unused(), recorder, jacksonMapperBuilder().build(), noopTx, unused())
    }

    @Test
    fun `일시적 DB 오류는 커서를 넘기지 않고 다음 주기에 다시 시도한다`() {
        val recorder = Recorder()
        val service = failingService(DataAccessResourceFailureException("connection lost"), recorder)

        org.junit.jupiter.api
            .assertThrows<DataAccessResourceFailureException> { service.syncDrafts() }

        assertEquals(emptyList<Long>(), recorder.sets)
    }

    @Test
    fun `결정적 오류로 실패한 초안은 커서를 넘긴다`() {
        val recorder = Recorder()
        val service = failingService(DataIntegrityViolationException("check violated"), recorder)

        assertEquals(0, service.syncDrafts())

        assertEquals(listOf(7L), recorder.sets)
    }
}
