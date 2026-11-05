// 모든 Kotlin 모듈의 공통 설정: 툴체인, 컴파일 옵션, ktlint, detekt, JUnit
plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jlleitschuh.gradle.ktlint")
    id("dev.detekt")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

// detekt 2.0(alpha)을 쓴다. 1.23.x는 Kotlin 2.0.21 기반이라 Kotlin 2.4·JDK 25에서 동작하지 않는다.
detekt {
    buildUponDefaultConfig = true
    config.setFrom(rootProject.file("config/detekt/detekt.yml"))
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}
