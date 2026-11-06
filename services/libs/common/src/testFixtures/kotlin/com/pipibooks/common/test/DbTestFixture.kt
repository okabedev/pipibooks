package com.pipibooks.common.test

import org.springframework.test.context.DynamicPropertyRegistry
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.MountableFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.sql.Connection
import java.sql.DriverManager

/**
 * 운영과 같은 계정·스키마(infra/postgres/init/01-init.sh)를 가진 Postgres와 선택적 Redis를 Testcontainers로 띄운다.
 * 컨테이너는 테스트 JVM당 한 번만 시작한다. 각 서비스의 DbTestSupport가 이 객체를 호출한다.
 */
object DbTestFixture {
    private const val SERVICE_PASSWORD = "test-pw"
    private const val REDIS_PORT = 6379

    private val postgres: PostgreSQLContainer by lazy {
        PostgreSQLContainer("postgres:18.6-alpine")
            .withDatabaseName("pipibooks")
            .withEnv("CORE_APP_PASSWORD", SERVICE_PASSWORD)
            .withEnv("CATALOG_APP_PASSWORD", SERVICE_PASSWORD)
            .withEnv("EVENTS_APP_PASSWORD", SERVICE_PASSWORD)
            .withCopyFileToContainer(
                MountableFile.forHostPath(initScript(), "755".toInt(8)),
                "/docker-entrypoint-initdb.d/01-init.sh",
            ).also { it.start() }
    }

    private val redis: GenericContainer<*> by lazy {
        GenericContainer("redis:8.10.3-alpine")
            .withExposedPorts(REDIS_PORT)
            .also { it.start() }
    }

    /** 서비스 계정([dbUser])으로 접속하도록 DB_*(와 [withRedis]이면 REDIS_*) 프로퍼티를 등록한다. */
    fun register(
        registry: DynamicPropertyRegistry,
        dbUser: String,
        withRedis: Boolean,
    ) {
        registry.add("DB_HOST") { postgres.host }
        registry.add("DB_PORT") { postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) }
        registry.add("DB_NAME") { postgres.databaseName }
        registry.add("DB_USER") { dbUser }
        registry.add("DB_PASSWORD") { SERVICE_PASSWORD }
        if (withRedis) {
            registry.add("REDIS_HOST") { redis.host }
            registry.add("REDIS_PORT") { redis.getMappedPort(REDIS_PORT) }
        }
    }

    /** 서비스 계정([dbUser])으로 접속하는 JDBC 연결을 연다. 다른 서비스 소유 스키마의 테스트 데이터를 넣을 때 쓴다. */
    fun connect(dbUser: String): Connection = DriverManager.getConnection(jdbcUrl(), dbUser, SERVICE_PASSWORD)

    /** 컨테이너 접속 JDBC URL. */
    fun jdbcUrl(): String = postgres.jdbcUrl

    /** 서비스 계정 공통 비밀번호(테스트 전용). */
    fun password(): String = SERVICE_PASSWORD

    /** 저장소 루트 기준 상대 경로의 파일·디렉터리를 찾는다. 작업 디렉터리(서비스 모듈)에서 위로 올라가며 찾는다. */
    fun repoPath(relative: String): Path {
        var dir: Path? = Paths.get("").toAbsolutePath()
        while (dir != null) {
            val candidate = dir.resolve(relative)
            if (Files.exists(candidate)) return candidate
            dir = dir.parent
        }
        error("$relative 을(를) 찾을 수 없다")
    }

    private fun initScript(): Path = repoPath("infra/postgres/init/01-init.sh")
}
