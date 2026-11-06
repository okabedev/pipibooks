package com.pipibooks.core.card

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.dao.DataAccessResourceFailureException
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
}
