plugins {
    id("pipibooks.kotlin-jvm")
    `java-library`
    `java-test-fixtures`
}

dependencies {
    // Spring Boot BOM으로 버전을 관리한다(앱 모듈은 pipibooks.spring-boot-app이 같은 BOM을 적용).
    api(platform(libs.spring.boot.dependencies))
    testImplementation(platform(libs.spring.boot.dependencies))
    testFixturesApi(platform(libs.spring.boot.dependencies))

    api("org.springframework:spring-web")
    implementation("tools.jackson.core:jackson-databind")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // 서비스 통합 테스트용 Postgres·Redis 컨테이너 픽스처(DbTestFixture)
    testFixturesApi("org.springframework:spring-test")
    testFixturesApi("org.testcontainers:testcontainers-postgresql")

    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
