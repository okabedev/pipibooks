package com.pipibooks.catalog

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class CatalogWorkerApplication

fun main(args: Array<String>) {
    runApplication<CatalogWorkerApplication>(*args)
}
