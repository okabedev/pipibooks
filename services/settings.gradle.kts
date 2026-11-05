pluginManagement {
    includeBuild("build-logic")
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
}

rootProject.name = "pipibooks-services"

include("core-api", "catalog-worker", "event-collector")
