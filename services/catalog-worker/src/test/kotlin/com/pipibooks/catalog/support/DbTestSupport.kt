package com.pipibooks.catalog.support

import com.pipibooks.common.test.DbTestFixture
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/** 통합 테스트 기반 클래스. 서비스 계정(catalog_app)으로 접속하는 Postgres 컨테이너를 쓴다(DbTestFixture). */
@Suppress("UtilityClassWithPublicConstructor") // 상속해서 쓰는 기반 클래스
abstract class DbTestSupport {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = DbTestFixture.register(registry, "catalog_app", false)
    }
}
