import org.springframework.boot.gradle.plugin.SpringBootPlugin

// Spring Boot 애플리케이션 모듈 공통 설정: Boot, BOM, Jib 이미지
plugins {
    id("pipibooks.kotlin-jvm")
    id("org.jetbrains.kotlin.plugin.spring")
    id("org.springframework.boot")
    id("com.google.cloud.tools.jib")
}

// io.spring.dependency-management 대신 BOM을 platform으로 쓴다. 그 플러그인은 모든 구성(ktlint·detekt 포함)의
// Kotlin 버전을 덮어써 정적 분석 도구를 깨뜨린다.
dependencies {
    "implementation"(platform(SpringBootPlugin.BOM_COORDINATES))
    "testRuntimeOnly"(platform(SpringBootPlugin.BOM_COORDINATES))
}

jib {
    from {
        image = "eclipse-temurin:25.0.5_8-jre"
    }
    to {
        image = "ghcr.io/okabedev/pipibooks-${project.name}:${project.version}"
        auth {
            username = System.getenv("GITHUB_ACTOR")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
    container {
        jvmFlags = listOf("-XX:MaxRAMPercentage=75")
        user = "1000"
        labels.put("org.opencontainers.image.source", "https://github.com/okabedev/pipibooks")
    }
}
