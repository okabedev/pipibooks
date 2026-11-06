plugins {
    id("pipibooks.spring-boot-app")
}

dependencies {
    implementation(project(":libs:common"))
    implementation(project(":libs:event-schema"))
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-flyway")
    implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation(libs.springdoc.openapi.webmvc)
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation(libs.archunit.junit5)
    testImplementation(testFixtures(project(":libs:common")))
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// 테스트가 catalog-worker의 실제 마이그레이션을 적용하므로, 바뀌면 테스트를 다시 실행한다.
tasks.test {
    inputs.dir(rootProject.file("catalog-worker/src/main/resources/db/migration/catalog"))
}
