package com.pipibooks.catalog.support

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/** 테스트 JVM당 하나의 WireMock(국립중앙도서관 가짜 서버). */
object NlkWireMock {
    val server: WireMockServer by lazy { WireMockServer(wireMockConfig().dynamicPort()).also { it.start() } }
}

/** 국립중앙도서관이 돌려주는 200 JSON 응답. */
fun jsonBody(body: String): ResponseDefinitionBuilder =
    aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body)

/** 2026-11-05 10:00 KST로 고정한 시계. 신간 구간은 2026-10-23 ~ 2026-11-05가 된다. */
@TestConfiguration
class FixedClockConfig {
    @Bean
    @Primary
    fun fixedClock(): Clock = Clock.fixed(Instant.parse("2026-11-05T01:00:00Z"), ZoneId.of("Asia/Seoul"))
}

/** 국립중앙도서관 WireMock과 DB 정리를 더한 통합 테스트 기반 클래스. */
@Import(FixedClockConfig::class)
abstract class NlkTestSupport : DbTestSupport() {
    @Autowired
    protected lateinit var db: DatabaseClient

    @BeforeEach
    fun resetFixtures() {
        NlkWireMock.server.resetAll()
        db
            .sql("DELETE FROM catalog.book")
            .fetch()
            .rowsUpdated()
            .block()
        db
            .sql("DELETE FROM catalog.collect_run")
            .fetch()
            .rowsUpdated()
            .block()
    }

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun nlkProperties(registry: DynamicPropertyRegistry) {
            registry.add("nlk.base-url") { "http://localhost:${NlkWireMock.server.port()}" }
            registry.add("nlk.cert-key") { "test-key" }
            registry.add("nlk.permits-per-second") { "1000" }
            registry.add("nlk.retry-base-delay-ms") { "1" }
        }
    }

    /**
     * 실제 녹화 응답의 표지 호스트(`https://nl.go.kr/`)는 연결이 바로 거부되는 주소로 바꿔,
     * 테스트가 외부 네트워크를 쓰지 않고 표지 확인 실패 경로를 타게 한다.
     */
    protected fun fixture(name: String): String {
        val resource = checkNotNull(javaClass.getResource("/nlk/$name")) { "픽스처 없음: $name" }
        return resource.readText().replace("https://nl.go.kr/", "http://localhost:1/")
    }
}
