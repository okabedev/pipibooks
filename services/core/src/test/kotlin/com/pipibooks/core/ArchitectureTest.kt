package com.pipibooks.core

import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.junit.AnalyzeClasses
import com.tngtech.archunit.junit.ArchTest
import com.tngtech.archunit.lang.ArchRule
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices

@AnalyzeClasses(packages = ["com.pipibooks.core"], importOptions = [ImportOption.DoNotIncludeTests::class])
class ArchitectureTest {
    // core 모듈(member, card, feed, library, stats, admin) 사이에 순환 의존이 없어야 한다.
    @ArchTest
    val modulesAreFreeOfCycles: ArchRule =
        slices()
            .matching("com.pipibooks.core.(*)..")
            .should()
            .beFreeOfCycles()
            .allowEmptyShould(true)
}
