package com.pipibooks.core.support

import com.pipibooks.common.Tsid
import com.pipibooks.common.test.DbTestFixture
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.time.Instant

/**
 * catalog 스키마 테스트 데이터. 운영과 같은 catalog-worker 마이그레이션을 catalog_app으로 적용하고,
 * 책·초안도 catalog_app으로 넣는다(core_app은 catalog를 읽기만 한다).
 */
object CatalogTestData {
    private const val CATALOG_MIGRATIONS = "services/catalog-worker/src/main/resources/db/migration/catalog"

    private val migrated: Unit by lazy {
        Flyway
            .configure()
            .dataSource(DbTestFixture.jdbcUrl(), "catalog_app", DbTestFixture.password())
            .schemas("catalog")
            .defaultSchema("catalog")
            .locations("filesystem:" + DbTestFixture.repoPath(CATALOG_MIGRATIONS))
            .load()
            .migrate()
        Unit
    }

    /** catalog 마이그레이션을 JVM당 한 번 적용한다. */
    fun migrate() = migrated

    fun <T> withCatalog(block: (Connection) -> T): T {
        migrate()
        return DbTestFixture.connect("catalog_app").use(block)
    }

    fun clear() =
        withCatalog { c ->
            c.createStatement().use {
                it.execute("DELETE FROM catalog.card_draft")
                it.execute("DELETE FROM catalog.book")
            }
        }

    fun insertBook(
        isbn13: String,
        title: String = "책 $isbn13",
        kdc: String? = "813.7",
        id: Long = Tsid.next(),
    ): Long =
        withCatalog { c ->
            c
                .prepareStatement(
                    "INSERT INTO catalog.book (id, isbn13, title, author, publisher, kdc, cover_url, fetched_at) " +
                        "VALUES (?, ?, ?, '저자', '출판사', ?, 'https://img/c.jpg', now())",
                ).use { ps ->
                    ps.setLong(1, id)
                    ps.setString(2, isbn13)
                    ps.setString(3, title)
                    ps.setString(4, kdc)
                    ps.executeUpdate()
                }
            id
        }

    fun coverSlides(title: String) = """[{"type":"COVER","title":"$title","author":"저자","coverUrl":null}]"""

    fun insertDraft(
        bookId: Long,
        slides: String = coverSlides("표지"),
        autoPublishable: Boolean = true,
        createdAt: Instant = Instant.now().minusSeconds(3600),
        id: Long = Tsid.next(),
    ): Long =
        withCatalog { c ->
            c
                .prepareStatement(
                    "INSERT INTO catalog.card_draft (id, book_id, slides, generator_name, generator_version, " +
                        "auto_publishable, blocked_reasons, created_at) " +
                        "VALUES (?, ?, ?::jsonb, 'rule', 1, ?, ?::text[], ?)",
                ).use { ps ->
                    ps.setLong(1, id)
                    ps.setLong(2, bookId)
                    ps.setString(3, slides)
                    ps.setBoolean(4, autoPublishable)
                    ps.setString(5, if (autoPublishable) "{}" else "{NO_COVER}")
                    ps.setTimestamp(6, java.sql.Timestamp.from(createdAt))
                    ps.executeUpdate()
                }
            id
        }
}
