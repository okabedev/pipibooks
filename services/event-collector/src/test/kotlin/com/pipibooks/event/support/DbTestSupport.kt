package com.pipibooks.event.support

import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.MountableFile
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * 운영과 같은 계정·스키마(infra/postgres/init/01-init.sh)를 가진 Postgres와 Redis를 Testcontainers로 띄우는 통합 테스트 기반 클래스.
 * 상속해서 쓰는 기반 클래스라 유틸리티 클래스 탐지(UtilityClassWithPublicConstructor)는 끈다.
 * 컨테이너는 테스트 JVM당 한 번만 시작한다. 서비스 계정(events_app)으로 접속하도록 DB_*·REDIS_* 프로퍼티를 주입한다.
 */
@Suppress("UtilityClassWithPublicConstructor")
abstract class DbTestSupport {
    companion object {
        private const val SERVICE_PASSWORD = "test-pw"

        private val postgres: PostgreSQLContainer =
            PostgreSQLContainer("postgres:18.6-alpine")
                .withDatabaseName("pipibooks")
                .withEnv("CORE_APP_PASSWORD", SERVICE_PASSWORD)
                .withEnv("CATALOG_APP_PASSWORD", SERVICE_PASSWORD)
                .withEnv("EVENTS_APP_PASSWORD", SERVICE_PASSWORD)
                .withCopyFileToContainer(
                    MountableFile.forHostPath(initScript(), "755".toInt(8)),
                    "/docker-entrypoint-initdb.d/01-init.sh",
                ).also { it.start() }

        private val redis: GenericContainer<*> =
            GenericContainer("redis:8.10.3-alpine")
                .withExposedPorts(6379)
                .also { it.start() }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("DB_HOST") { postgres.host }
            registry.add("DB_PORT") { postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) }
            registry.add("DB_NAME") { postgres.databaseName }
            registry.add("DB_USER") { "events_app" }
            registry.add("DB_PASSWORD") { SERVICE_PASSWORD }
            registry.add("REDIS_HOST") { redis.host }
            registry.add("REDIS_PORT") { redis.getMappedPort(6379) }
        }

        // 작업 디렉터리(서비스 모듈)에서 위로 올라가며 저장소 루트의 init 스크립트를 찾는다.
        private fun initScript(): Path {
            var dir: Path? = Paths.get("").toAbsolutePath()
            while (dir != null) {
                val candidate = dir.resolve("infra/postgres/init/01-init.sh")
                if (Files.exists(candidate)) return candidate
                dir = dir.parent
            }
            error("infra/postgres/init/01-init.sh를 찾을 수 없다")
        }
    }
}
