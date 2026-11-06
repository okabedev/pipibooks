pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "pipibooks-services"

include("core", "catalog-worker", "event-collector", "libs:common", "libs:event-schema")
