package com.pipibooks.core.card

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.springframework.dao.DataAccessResourceFailureException
import org.springframework.jdbc.BadSqlGrammarException
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.TransactionDefinition
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.transaction.support.TransactionTemplate
import tools.jackson.module.kotlin.jacksonMapperBuilder
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

    private fun service(error: RuntimeException) =
        CardService(
            reader = StubReader(error),
            cards = mock(CardRepository::class.java),
            slides = mock(CardSlideRepository::class.java),
            cursor = mock(SyncCursorStore::class.java),
            mapper = jacksonMapperBuilder().build(),
            tx = noopTx,
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
